package net.xorf.filestore

import akka.actor._
import net.xorf.filestore.PermStorageActor.FileWithSignature
import net.xorf.filestore.StorageCoordinatorActor.{FileChunk, FileSignature}
import net.xorf.filestore.TempStorageActor.WriteDone
import net.xorf.filestore.fs.FileStore

import scala.concurrent.ExecutionContext
 import akka.pattern.pipe

object TempStorageActor {
  case class WriteDone(len: Int)
  def apply(store: FileStore) = Props(new TempStorageActor(store))
}

class TempStorageActor(store: FileStore) extends Actor with ActorLogging with Stash {
  implicit val ec: ExecutionContext = context.dispatcher

  val tempFile = Resources.tempStorage.newTempFile

  def receive = {
    case FileChunk(bytes) => {
      context.become({
        case WriteDone(l) => 
          log.info("-----------> Write Done: %s".format(l.toString))
          context.unbecome(); unstashAll()
        case msg => stash()
      })
      
      tempFile.write(bytes).map(b => WriteDone(b)).pipeTo(self)
      
      //  { l =>
      //   log.debug("wrote %d bytes to temp file %s".format(l, tempFile.path))
      //   self ! WriteDone(l)
      // }
    }
    case WriteDone(l) => {
      log.info("-----------> GOT: %s".format(l.toString))
    }
    case FileSignature(fileSig) =>
      log.info("file info: %s  --- %s".format(fileSig, tempFile.path))
      val client = sender
      client ! FileWithSignature(fileSig, tempFile.path)
      tempFile.close()
      //context.stop(self)
  }
}
