package com.bullhorn.filestore

import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.bullhorn.filestore.FileDbActor.{DuplicateFile, StorableFile}
import com.bullhorn.filestore.PermStorageActor.{FileStored, FileWithSignature}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

object PermStorageActor {
  case class FileStored(name: String)
  case class FileWithSignature(signature: String, tempName: String)

  def apply(store: FileStore) = Props(new PermStorageActor(store))
}
class PermStorageActor(
        store: FileStore)
    extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(90 seconds)

  val dbActor = context.actorOf(FileDbActor(FileDb.instance))

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
