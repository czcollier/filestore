package net.xorf.filestore

import akka.actor.{Props, ActorLogging, Actor}
import net.xorf.filestore.PermStorageActor.{FileWithSignature, FileStored}
import net.xorf.filestore.StorageCoordinatorActor.{FileSignature, FileChunk}
import net.xorf.filestore.fs.FileStore

object StorageCoordinatorActor {
  case class FileSignature(v: String)
  case class FileChunk(bytes: Array[Byte])

  def apply(store: FileStore) = Props(new StorageCoordinatorActor(store))
}

class StorageCoordinatorActor(store: FileStore) extends Actor with ActorLogging {
  val permStorageActor = context.actorOf(PermStorageActor(store).withDispatcher("io-dispatcher"))
  val tempStorageActor = context.actorOf(TempStorageActor(store).withDispatcher("io-dispatcher"))

  def receive = {
    case c: FileChunk => tempStorageActor ! c
    case s: FileSignature =>
      val origSender = sender
      context.actorOf(Props(new Actor() {
        tempStorageActor ! s
        def receive = {
          case fws: FileWithSignature =>
            log.debug("Coordinator Got: %s".format(fws))
            permStorageActor ! fws
          case fs: FileStored =>
            origSender ! fs
            context.stop(self)
        }
      }))
  }
}
