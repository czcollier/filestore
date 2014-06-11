package com.bullhorn.filestore

import java.io.{BufferedOutputStream, FileOutputStream}
import java.security.MessageDigest

import akka.actor._
import spray.http.MediaTypes._
import spray.http.{BodyPart, _}
import spray.routing._



class FileStoreServiceActor extends Actor with ActorLogging with FileStoreService {
  def actorRefFactory = context

  implicit def system = context.system

  override def receive = runRoute(signatureRoute)
}

object FileStoreService {
  val chunkSize = 1024L
  val filenameDispositionPattern = """filename=(.*)($|;)""".r
}

trait FileStoreService extends HttpService {
  import com.bullhorn.filestore.FileStoreService._

  def system: ActorSystem

  def extractContentTypeHeader(hdrs: Seq[HttpHeader]) = {
    for {
      hdr <- hdrs.find(h => h.is("content-type"))
    } yield hdr.value
  }

  def extractFileName(hdrs: Seq[HttpHeader]) = {
    for {
      hdr <- hdrs.find(h => h.is("content-disposition"))
      fn <- filenameDispositionPattern.findFirstMatchIn(hdr.value)
    } yield fn.group(1)
  }

  val signatureRoute = path("file") {
    get {
      complete("OK")
    } ~
    put {
      respondWithMediaType(`application/json`) {
        entity(as[MultipartFormData]) { formData =>
            detach() {
              complete {
                val details = formData.fields.collect {
                  case (BodyPart(entity, headers)) =>

                    for {
                      contentType <- extractContentTypeHeader(headers)
                      fileName <- extractFileName(headers)
                    }
                    yield {
                      val fileSize = entity.data.length
                      val digest = MessageDigest.getInstance("SHA-1")

                      val store: ActorRef = system.actorOf(Props[FileStoreActor])
                      store ! FileStoreActor.Start

                      entity.data.toChunkStream(chunkSize).foreach { chunk =>
                        digest.update(chunk.toByteArray)
                        store ! chunk
                      }

                      val fileSig = hexEncode(digest.digest)

                      store ! FileStoreActor.Done(fileSig)
                      (fileName, contentType, fileSize, fileSig)
                    }
                }

                val fileDescs = details.map { d =>
                  d match {
                    case Some(v) => formatResult(v)
                  }
                }

                """{ "uploads": [
              |%s
              |]
              |}""". stripMargin.format(fileDescs.mkString(","))
            }
          }
        }
      }
    }
  }

  def hexEncode(bytes: Array[Byte]) =
    bytes.map(0xFF & _).map { "%02x".format(_) }.foldLeft("") { _ + _ }

  def formatResult(v: (String, String, Long, String)) = {
    s"""{
     |  "contentType": "${v._1}",
     |  "fileName": "${v._2}",
     |  "size": "${v._3}",
     |  "signature": "${v._4}"
     |}""".stripMargin
  }
}


object FileStoreActor {
  case object Start
  case class Done(key: String)
}

class FileStoreActor extends Actor with ActorLogging {
  import com.bullhorn.filestore.FileStoreActor._
  val os = new BufferedOutputStream(new FileOutputStream("tmp.txt"))
  def receive = {
    case Start => {
      println("started")
    }
    case chunk: HttpData => {
      println("got chunk: " + chunk.length)
      os.write(chunk.toByteArray)
    }
    case done: Done => {
      println("done: " + done.key)
      os.close()
    }
    case x: Any => println(x.getClass)
  }
}
