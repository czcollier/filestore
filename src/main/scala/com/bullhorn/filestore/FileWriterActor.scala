package com.bullhorn.filestore

import java.io.{File, BufferedOutputStream, FileOutputStream}
import java.security.MessageDigest

import akka.actor.{Props, Actor, ActorLogging}
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import com.bullhorn.filestore.Codec.StoredFile
import com.bullhorn.filestore.DigestActor.{BytesConsumed, GetDigest}
import com.bullhorn.filestore.SuspendingQueue.AckConsumed
import com.google.common.base.Stopwatch
import spray.http.HttpHeaders.RawHeader
import spray.http._
import spray.io.CommandWrapper

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scalax.io.Resource

object FileWriterActor {
  case class Done(signature: String)
  case class FileSignature(v: String)
  case class Data(bytes: Array[Byte])

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

  val digestActor = context.actorOf(Props[DigestActor], "digester_%d".format(System.identityHashCode(this)))
  val storageActor = context.actorOf(Props(new StorageActor(store)).withDispatcher("io-dispatcher"))

  var cnt = 0
  var bytesWritten = 0

  implicit val timeout = Timeout(30 seconds)

  import ExecutionContext.Implicits.global

  def receive = {
    case chunk: MessageChunk => {
      val client = sender
      val data = Data(chunk.data.toByteArray)
      (digestActor ? data).mapTo[BytesConsumed].map { c =>
        storageActor ! data
        cnt += 1
        bytesWritten += c.cnt
        AckConsumed(c.cnt)
      }
      .pipeTo(client)
    }
    case e: ChunkedMessageEnd =>
      val client = sender
      val f = for {
        sig <- (digestActor ? GetDigest).mapTo[FileSignature]
        dup <- (storageActor ? Done(sig.v))
      } yield {
        log.info("done")
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
        client ! CommandWrapper(SetRequestTimeout(30.seconds)) // reset timeout to original value
        context.stop(self)
      }

  }
}
