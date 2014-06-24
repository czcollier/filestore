package com.bullhorn.filestore

import java.io.{BufferedOutputStream, FileOutputStream}
import java.security.MessageDigest

import akka.actor.{Actor, ActorLogging}
import com.bullhorn.filestore.Codec.StoredFile
import com.bullhorn.filestore.SuspendingQueue.AckConsumed
import spray.http.HttpHeaders.RawHeader
import spray.http._
import spray.io.CommandWrapper

import scala.concurrent.duration._

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
  import DefaultJsonProtocol._
  import Codec.FileStoreJsonProtocol._
  import scala.language.implicitConversions
  
  val contentType = header[HttpHeaders.`Content-Type`].getOrElse(ContentTypes.`text/plain(UTF-8)`)
  val fileName = start.message.headers.find(h => h.name == "name").map { hdr =>
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
      log.debug("wrote %d bytes to file".format(bytesWritten))
      sender ! AckConsumed(bytes.length)
    }
    case e: ChunkedMessageEnd =>
      val client = sender
      log.info(start.message.headers.find(h => h.lowercaseName == "name").toString)
      log.info(s"Got end of chunked request $method $uri")
      log.info(s"sender is: $sender")
      val fileSig = hexEncode(digest.digest)
      os.close()
      val dup = store.finish(fileSig, tmpFile)
      client ! HttpResponse(status = 200, entity =
        HttpEntity(StoredFile(
        fileName,
        contentType.toString,
        dup,
        bytesWritten,
        FileSignature(fileSig)).toJson.prettyPrint))

      client ! CommandWrapper(SetRequestTimeout(2.seconds)) // reset timeout to original value
      println("done: " + fileSig)
      context.stop(self)

    case Done => {
      val fileSig = hexEncode(digest.digest)
      os.close()

      store.finish(fileSig, tmpFile)

      println("done: " + fileSig)
      sender ! FileSignature(fileSig)
      context.system.stop(self)
    }
  }
}
