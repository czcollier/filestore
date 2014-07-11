package com.bullhorn.filestore

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.pipe
import com.bullhorn.filestore.FileDbActor.{DuplicateFile, StorableFile}
import com.bullhorn.filestore.PermStorageActor.{FileStored, FileWithSignature}

object PermStorageActor {
  case class FileStored(name: String)
  case class FileWithSignature(signature: String, tempName: String)

  def apply(store: FileStore) = Props(new PermStorageActor(store))
}
class PermStorageActor(
        store: FileStore)
    extends Actor with ActorLogging {

  val dbActor = context.actorOf(FileDbActor(Resources.db))

  implicit val ec = context.dispatcher

  def receive = {
    case fs: FileWithSignature =>
      val topSender = sender
      context.actorOf(Props(new Actor() {
        dbActor ! fs
        def receive = {
          case StorableFile(n, id) =>
            log.info("got storable file: %s".format(n.toString))
            val x = store.moveToPerm(n, id) map { f: String =>
              log.info("stored file: %s".format(f.toString))
              FileStored(f)
            }
            x.pipeTo(topSender)
            context.stop(self)

          case DuplicateFile(f) =>
            val logg = log
            store.deleteTempFile(f)
            logg.info("deleted temp file: %s".format(f.toString))
            topSender ! FileStored(f)
            context.stop(self)
        }
      }))
  }
}
