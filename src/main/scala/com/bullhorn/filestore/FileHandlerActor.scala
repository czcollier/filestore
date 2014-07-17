package com.bullhorn.filestore

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.bullhorn.filestore.DigestActor.GetDigest
import com.bullhorn.filestore.http.{SuspendingQueue, JsonCodec}
import JsonCodec.StoredFile
import com.bullhorn.filestore.PermStorageActor.FileStored
import com.bullhorn.filestore.StorageCoordinatorActor.{FileChunk, FileSignature}
import SuspendingQueue.AckConsumed
import com.bullhorn.filestore.storage.FileStore
import com.google.common.base.Stopwatch
import spray.http.HttpHeaders.RawHeader
import spray.http._
import spray.io.CommandWrapper

import scala.concurrent.duration._

object FileHandlerActor {
  object FileNameHeader extends RawHeader("file-name", "fn")
}

class FileHandlerActor(store: FileStore, start: ChunkedRequestStart) extends Actor with ActorLogging {
  import JsonCodec.FileStoreJsonProtocol._
  import spray.json._
  import start.request._

import scala.language.implicitConversions

  val contentType = header[HttpHeaders.`Content-Type`].getOrElse(ContentTypes.`text/plain(UTF-8)`)
  val fileName = start.message.headers.find(h => h.name == "file-name").map { hdr =>
    hdr.value
  }.getOrElse("unknown")

  val digestActor = context.actorOf(Props[DigestActor],
    "digester_%d".format(System.identityHashCode(this)))

  val storageActor = context.actorOf(StorageCoordinatorActor(store),
    "storage_%d".format(System.identityHashCode(this)))

  var chunkCount = 0
  var bytesWritten = 0
  val timer = Stopwatch.createStarted

  implicit val timeout = Timeout(90 seconds)

  def receive = {
    case chunk: MessageChunk => {
      val client = sender
      val fChunk = FileChunk(chunk.data.toByteArray)
      digestActor ! fChunk
      storageActor ! fChunk
      chunkCount += 1
      bytesWritten += fChunk.bytes.length
      client ! AckConsumed(fChunk.bytes.length)
    }
    case e: ChunkedMessageEnd =>
      val client = sender
      context.actorOf(Props(new Actor() {
        var sig: Option[FileSignature] = None

        digestActor ! GetDigest
        def receive = {
          case fs: FileSignature =>
            sig = Some(fs)
            storageActor ! fs
          case stored: FileStored => {
            log.debug("file stored: %d chunks, %d bytes in %s".format(chunkCount, bytesWritten, timer.stop))
            client ! HttpResponse(
              status = 200,
              entity = HttpEntity(StoredFile(
                fileName,
                contentType.toString,
                false,
                bytesWritten,
                sig.get).toJson.prettyPrint))
            client ! CommandWrapper(SetRequestTimeout(90.seconds)) // reset timeout to original value
            context.stop(self)
          }
        }
      }))
      //this was causing connections to be dropped.
      //context.stop(self)
  }
}
