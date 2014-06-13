package com.bullhorn.filestore

import akka.actor._
import akka.pattern.ask
import com.bullhorn.filestore.FileWriterActor.{FileSignature, Done}
import spray.http.MediaTypes._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.routing._
import akka.util.Timeout
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._


class FileStoreServiceActor extends Actor with ActorLogging with FileStoreService {
  def actorRefFactory = context

  implicit def system = context.system

  override def receive = runRoute(signatureRoute)
}

object FileStoreService {
  //size of chunks we will break HTTP entity into for processing
  val chunkSize = 16384L
  //file name containing any characters optionally ending with dot and extension
  val filenameDispositionPattern = """filename=(.+?)(\.(.+))?$""".r

  case class StoredFile(name: String, contentType: String, size: Long, signature: FileSignature)

  object FileStoreJsonProtocol extends DefaultJsonProtocol {
    implicit val fileSignatureFormat = jsonFormat1(FileSignature)
    implicit val storedFileFormat = jsonFormat4(StoredFile)
  }
}

trait FileStoreService extends HttpService with SprayJsonSupport {
  import com.bullhorn.filestore.FileStoreService._
  import FileStoreJsonProtocol._

  import ExecutionContext.Implicits.global

  def system: ActorSystem

  val store: FileStore = new BDBStore
  def createWriter(): ActorRef = {
    actorRefFactory.actorOf(Props(new FileWriterActor(this.store)))
  }

  implicit val timeout = Timeout(5 seconds)

  val signatureRoute = path("file") {
    get {
      complete("OK")
    } ~
    put {
      respondWithMediaType(`application/json`) {
        entity(as[MultipartFormData]) { formData =>
          detach() {
            val details = formData.fields.collect {
              case (BodyPart(entity, headers)) =>
                for {
                  contentType <- extractContentTypeHeader(headers)
                  fileName <- extractFileName(headers)
                }
                yield {
                  val writer = createWriter
                  entity.data.toChunkStream(chunkSize).foreach { chunk =>
                    writer ! chunk
                  }

                  (writer ? Done)
                    .mapTo[FileSignature]
                    .map(result => StoredFile(fileName.toString, contentType, entity.data.length, result))
                }
            }

            complete {
              Future.sequence(details.flatMap(x => x))
            }
          }
        }
      }
    }
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
}


