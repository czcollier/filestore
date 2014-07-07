package com.bullhorn.filestore

import java.io.{File, BufferedOutputStream, FileOutputStream}
import java.security.MessageDigest

import akka.actor.{Props, Actor, ActorLogging}
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import com.bullhorn.filestore.Codec.StoredFile
import com.bullhorn.filestore.DigestActor.{BytesConsumed, GetDigest}
import com.bullhorn.filestore.PermStorageActor.FileStored
import com.bullhorn.filestore.StorageParentActor.{FileChunk, FileSignature}
import com.bullhorn.filestore.SuspendingQueue.AckConsumed
import com.google.common.base.Stopwatch
import spray.http.HttpHeaders.RawHeader
import spray.http._
import spray.io.CommandWrapper

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scalax.io.{End, Resource}

object FileWriterActor {
  object FileNameHeader extends RawHeader("file-name", "fn")
}

class FileWriterActor(store: FileStore, start: ChunkedRequestStart) extends Actor with ActorLogging {
  import com.bullhorn.filestore.FileWriterActor._
  import start.request._
  import spray.json._
  import Codec.FileStoreJsonProtocol._
  import scala.language.implicitConversions
  import Resources._
  
  val contentType = header[HttpHeaders.`Content-Type`].getOrElse(ContentTypes.`text/plain(UTF-8)`)
  val fileName = start.message.headers.find(h => h.name == "file-name").map { hdr =>
    hdr.value
  }.getOrElse("unknown")

  val digestActor = context.actorOf(Props[DigestActor],
    "digester_%d".format(System.identityHashCode(this)))

  val storageActor = context.actorOf(StorageParentActor(db, store))

  var cnt = 0
  var bytesWritten = 0
  val timer = Stopwatch.createStarted

  implicit val timeout = Timeout(90 seconds)

  import ExecutionContext.Implicits.global

  //try creating an intermediary "responder" actor that
  //brokers between client and storageActor -- solves
  //the problem of maintaining connection betw. client
  //and storage actor that must respond to client when
  //done with work
  def receive = {
    case chunk: MessageChunk => {
      val client = sender
      val fChunk = FileChunk(chunk.data.toByteArray)
      digestActor ! fChunk
      storageActor ! fChunk
      cnt += 1
      bytesWritten += fChunk.bytes.length
      client ! AckConsumed(fChunk.bytes.length)
    }
    case e: ChunkedMessageEnd =>
      val client = sender
      val f = for {
        sig <- (digestActor ? GetDigest).mapTo[FileSignature]
        dup <- (storageActor ? sig)
      } yield {
        log.info("done: %d chunks, %d bytes in %s".format(cnt, bytesWritten, timer.stop))
        HttpResponse(
          status = 200,
          entity = HttpEntity(StoredFile(
            fileName,
            contentType.toString,
            false,
            bytesWritten,
            sig).toJson.prettyPrint))
      }
      f.pipeTo(client).onComplete { f =>
        client ! CommandWrapper(SetRequestTimeout(90.seconds)) // reset timeout to original value
        context.stop(self)
      }
  }
}
