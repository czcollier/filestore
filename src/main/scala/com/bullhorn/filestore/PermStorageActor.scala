package com.bullhorn.filestore

import java.io.File

import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.bullhorn.filestore.FileDbActor.Finish
import com.bullhorn.filestore.PermStorageActor.{FileWithSignature, StorableFile}
import scala.concurrent.duration._
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure}

object PermStorageActor {
  case class StorableFile(id: Long, file: File)
  case class DuplicateFile(file: File)
  case class FileWithSignature(signature: String, file: File)
}
class PermStorageActor(
        store: FileStore,
        dbActor: ActorRef,
        tmpActor: ActorRef)
    extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher

  implicit val timeout = Timeout(90 seconds)

  var client: Option[ActorRef] = None

  def receive = {
    case fs: FileWithSignature => dbActor ! fs
    case StorableFile(id, f) =>
      val fut = Future {
        store.storeFile(id, f)
      }
      fut.onComplete {
        case Failure(x) => throw x
        case Success(v) => tmpActor  ! v
      }
  }
}
