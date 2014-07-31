package com.bullhorn.filestore

import akka.actor.{Actor, ActorLogging, Props}
import com.bullhorn.filestore.PermStorageActor.FileWithSignature
import com.bullhorn.filestore.db.FileDb

object FileDbActor {
  case class StorableFile(tempName: String, id: String)
  case class DuplicateFile(tempName: String)

  def apply(db: FileDb) = Props(new FileDbActor(db))
}

class FileDbActor(db: FileDb) extends Actor with ActorLogging {
  import com.bullhorn.filestore.FileDbActor._
  implicit val ec = context.dispatcher

  def receive = {
    case FileWithSignature(sig, file) =>
      val client = sender
      db.finish(sig) match {
          case Some(id: String) =>
            log.info("new: %s -- %s".format(id, sig))
            client ! StorableFile(file, id)
          case None =>
            log.info("dup: %s".format(sig))
            client ! DuplicateFile(file)
        }
      }
}
