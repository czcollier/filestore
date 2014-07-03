package com.bullhorn.filestore

import akka.actor.{Actor, ActorLogging}
import com.bullhorn.filestore.PermStorageActor.{DuplicateFile, FileWithSignature, StorableFile}

import scala.concurrent.Future

class FileDbActor(db: FileDb) extends Actor with ActorLogging {

  def receive = {
    case FileWithSignature(sig, file) =>
      val client = sender
      val msgFut = Future {
        db.finish(sig)
      }

      msgFut.onComplete {
        case Some(id: Long) => client ! StorableFile(id, file)
        case None => client ! DuplicateFile(file)
      }
  }
}
