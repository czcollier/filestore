package com.bullhorn.filestore

import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import akka.util.Timeout
import com.bullhorn.filestore.PermStorageActor.{FileStored, DuplicateFile, FileWithSignature, StorableFile}
import scala.concurrent.duration._
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure}

object PermStorageActor {
  case class StorableFile(tempName: String, id: Long)
  case object FileStored
  case class DuplicateFile(tempName: String)
  case class FileWithSignature(signature: String, tempName: String)

  def apply(store: FileStore, parent: ActorRef, dbActor: ActorRef) = Props(new PermStorageActor(store, parent, dbActor))
}
class PermStorageActor(
        store: FileStore,
        parent: ActorRef,
        dbActor: ActorRef)
    extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(90 seconds)

  def receive = {
    case fs: FileWithSignature => dbActor ! fs
    case StorableFile(n, id) =>
      val fut = Future {
        store.moveToPerm(n, id)
      }
      fut.onComplete {
        case Failure(e) => throw e
        case Success(v: String) => {
          log.info("stored file: %s".format(v.toString))
          parent ! FileStored
        }
      }
    case DuplicateFile(f) =>
      val logg = log
      val fut = Future {
        store.deleteTempFile(f)
      }
      fut.onComplete {
        case Failure(e) => throw e
        case Success(v) => {
          logg.info("deleted temp file: %s".format(v.toString))
          parent ! FileStored
        }
      }
  }
}
