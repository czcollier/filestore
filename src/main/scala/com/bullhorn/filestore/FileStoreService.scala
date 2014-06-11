package com.bullhorn.filestore

import java.io.{BufferedOutputStream, FileOutputStream}
import java.security.MessageDigest

import akka.actor._
import spray.http.MediaTypes._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.routing._



class FileStoreServiceActor extends Actor with ActorLogging with FileStoreService {
  def actorRefFactory = context

  implicit def system = context.system

  override def receive = runRoute(signatureRoute)
}

object FileStoreService {
  //size of chunks we will break HTTP entity into for processing
  val chunkSize = 1024L
  //file name containing any characters optionally ending with dot and extension
  val filenameDispositionPattern = """filename=(.+?)(\.(.+))?$""".r

  case class StoredFile(name: String, contentType: String, size: Long, signature: String)

  object FileStoreJsonProtocol extends DefaultJsonProtocol {
    implicit val storedFileFormat = jsonFormat4(StoredFile)
  }
}

trait FileStoreService extends HttpService with SprayJsonSupport {
  import FileStoreService._
  import FileStoreService.FileStoreJsonProtocol._

  def system: ActorSystem

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
                    val signature = signatureAndSendToStore(entity, fileName)
                    StoredFile(fileName.toString, contentType, entity.data.length, signature)
                  }
              }

              details.collect { case Some(d) => d }
            }
          }
        }
      }
    }
  }

  def signatureAndSendToStore(entity: HttpEntity, fileName: (String, Option[String])) = {

    val digest = MessageDigest.getInstance("SHA-1")

    val store: ActorRef = system.actorOf(Props[FileStoreActor])
    store ! FileStoreActor.Start(fileName._2.getOrElse("unknown"))

    entity.data.toChunkStream(chunkSize).foreach { chunk =>
      digest.update(chunk.toByteArray)
      store ! chunk
    }

    val fileSig = hexEncode(digest.digest)

    store ! FileStoreActor.Done(fileSig)

    fileSig
  }

  def extractContentTypeHeader(hdrs: Seq[HttpHeader]) = {
    for {
      hdr <- hdrs.find(h => h.is("content-type"))
    } yield hdr.value
  }

  def extractFileName(hdrs: Seq[HttpHeader]): Option[(String, Option[String])] = {
    for {
      hdr <- hdrs.find(h => h.is("content-disposition"))
      fn <- filenameDispositionPattern.findFirstMatchIn(hdr.value)
    } yield (fn.group(1), Option(fn.group(3)))
  }

  def hexEncode(bytes: Array[Byte]) =
    bytes.map(0xFF & _).map { "%02x".format(_) }.foldLeft("") { _ + _ }
}


