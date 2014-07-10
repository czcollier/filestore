package com.bullhorn.filestore

import akka.actor.{Props, Actor, ActorLogging}
import com.bullhorn.filestore.PermStorageActor.FileWithSignature

import scala.concurrent.Future

object FileDbActor {
  case class StorableFile(tempName: String, id: Long)
  case class DuplicateFile(tempName: String)

  def apply(db: FileDb) = Props(new FileDbActor(db))
}

class FileDbActor(db: FileDb) extends Actor with ActorLogging {
  import FileDbActor._
  implicit val ec = context.dispatcher

  def receive = {
    case FileWithSignature(sig, file) =>
      val client = sender
      db.finish(sig) match {
          case Some(id: Long) => client ! StorableFile(file, id)
          case None => log.info("=====> DUP!"); client ! DuplicateFile(file)
        }
      }
}
