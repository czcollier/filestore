package com.bullhorn.filestore

import akka.actor._
import com.bullhorn.filestore.PermStorageActor.FileWithSignature
import com.bullhorn.filestore.StorageCoordinatorActor.{FileChunk, FileSignature}
import com.bullhorn.filestore.TempStorageActor.WriteDone
import com.bullhorn.filestore.storage.FileStore

import scala.concurrent.ExecutionContext

object TempStorageActor {
  case class WriteDone(len: Int)
  def apply(store: FileStore) = Props(new TempStorageActor(store))
}

class TempStorageActor(store: FileStore) extends Actor with ActorLogging with Stash {
  implicit val ec: ExecutionContext = context.dispatcher

  val tempFile = ResourcesStuff.tempStorage.newTempFile

  def receive = {
    case FileChunk(bytes) => {
      context.become({
        case WriteDone(l) => context.unbecome(); unstashAll()
        case msg => stash()
      })
      tempFile.write(bytes).map { l =>
        log.debug("wrote %d bytes to temp file %s".format(l, tempFile.path))
        self ! WriteDone(l)
      }
    }
    case WriteDone(l) => {
      log.info("-----------> GOT: %s".format(l.toString))
    }
    case FileSignature(fileSig) =>
      val client = sender
      tempFile.close()
      client ! FileWithSignature(fileSig, tempFile.path)
      context.stop(self)
  }
}
