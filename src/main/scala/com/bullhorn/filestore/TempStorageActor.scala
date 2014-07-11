package com.bullhorn.filestore

import akka.actor._
import com.bullhorn.filestore.PermStorageActor.FileWithSignature
import com.bullhorn.filestore.StorageCoordinatorActor.{FileChunk, FileSignature}
import com.bullhorn.filestore.TempStorageActor.WriteDone
import com.bullhorn.filestore.storage.{AsyncTempFile, FileStore}

import scala.concurrent.ExecutionContext

object TempStorageActor {
  case class WriteDone(len: Int)
  def apply(store: FileStore) = Props(new TempStorageActor(store))
}

class TempStorageActor(store: FileStore) extends Actor with ActorLogging with Stash {
  implicit val ec: ExecutionContext = context.dispatcher

  val tmpFile = store.newTempFile
  val atf = new AsyncTempFile(tmpFile)

  def receive = {
    case FileChunk(bytes) => {
      context.become({
        case WriteDone(l) => context.unbecome(); unstashAll()
        case msg => stash()
      })
      atf.write(bytes).map { l =>
        log.debug("atf wrote %d bytes".format(l))
        self ! WriteDone(l)
      }
    }
    case WriteDone(l) => {
      log.info("-----------> GOT: %s".format(l.toString))
    }
    case FileSignature(fileSig) =>
      val client = sender
      atf.close()
      client ! FileWithSignature(fileSig, tmpFile)
      context.stop(self)
  }
}
