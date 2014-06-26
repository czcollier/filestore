package com.bullhorn.filestore

import java.io.{File, BufferedOutputStream, FileOutputStream}
import java.security.MessageDigest

import akka.actor.{Actor, ActorLogging}
import com.bullhorn.filestore.Codec.StoredFile
import com.bullhorn.filestore.SuspendingQueue.AckConsumed
import com.google.common.base.Stopwatch
import spray.http.HttpHeaders.RawHeader
import spray.http._
import spray.io.CommandWrapper

import scala.concurrent.duration._
import scalax.io.Resource

object FileWriterActor {
  case object Done
  case class FileSignature(v: String)

  def hexEncode(bytes: Array[Byte]) =
    bytes.map(0xFF & _).map { "%02x".format(_) }.foldLeft("") { _ + _ }

  object FileNameHeader extends RawHeader("file-name", "fn")
}

class FileWriterActor(store: FileStore, start: ChunkedRequestStart) extends Actor with ActorLogging {
  import com.bullhorn.filestore.FileWriterActor._
  import start.request._
  import spray.json._
  import Codec.FileStoreJsonProtocol._
  import scala.language.implicitConversions
  
  val contentType = header[HttpHeaders.`Content-Type`].getOrElse(ContentTypes.`text/plain(UTF-8)`)
  val fileName = start.message.headers.find(h => h.name == "file-name").map { hdr =>
    hdr.value
  }.getOrElse("unknown")

  var cnt = 0
  var bytesWritten = 0

  val tmpFile = store.newTempFile
  val os = new BufferedOutputStream(new FileOutputStream((tmpFile)))
  val digest = MessageDigest.getInstance("SHA-1")

  def receive = {
    case chunk: MessageChunk => {
      val bytes = chunk.data.toByteArray
      digest.update(bytes)
      os.write(bytes)
      cnt += 1
      bytesWritten += bytes.length
      sender ! AckConsumed(bytes.length)
    }
    case e: ChunkedMessageEnd =>
      val client = sender
      val fileSig = hexEncode(digest.digest)
      os.close()
      val dup = store.finish(fileSig, new File(tmpFile))
      client ! HttpResponse(
        status = 200,
        entity = HttpEntity(StoredFile(
          fileName,
          contentType.toString,
          dup,
          bytesWritten,
          FileSignature(fileSig)).toJson.prettyPrint))

      client ! CommandWrapper(SetRequestTimeout(10.seconds)) // reset timeout to original value
      log.info("done: " + fileSig)
      context.stop(self)
  }
}
