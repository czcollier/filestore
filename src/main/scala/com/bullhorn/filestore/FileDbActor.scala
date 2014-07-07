package com.bullhorn.filestore

import akka.actor.{Props, Actor, ActorLogging}
import com.bullhorn.filestore.PermStorageActor.{DuplicateFile, FileWithSignature, StorableFile}

import scala.concurrent.Future

object FileDbActor {
  def apply(db: FileDb) = Props(new FileDbActor(db))
}

class FileDbActor(db: FileDb) extends Actor with ActorLogging {
  implicit val ec = context.dispatcher

  def receive = {
    case FileWithSignature(sig, file) =>
      val client = sender
      val msgFut = Future {
        db.finish(sig)
      }

      msgFut.onComplete { res =>
        res map {
          case Some(id: Long) => client ! StorableFile(file, id)
          case None => client ! DuplicateFile(file)
        }
      }
  }
}
