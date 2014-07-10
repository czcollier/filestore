package com.bullhorn.filestore

import akka.actor.{Props, ActorLogging, Actor}
import com.bullhorn.filestore.PermStorageActor.{FileWithSignature, FileStored}
import com.bullhorn.filestore.StorageParentActor.{FileSignature, FileChunk}

object StorageParentActor {
  case class FileSignature(v: String)
  case class FileChunk(bytes: Array[Byte])

  def apply(store: FileStore) = Props(new StorageParentActor(store))
}

class StorageParentActor(store: FileStore) extends Actor with ActorLogging {
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
            permStorageActor ! fws
          case fs: FileStored =>
            log.info("stored a file: %s".format(fs.name))
            origSender ! fs
        }
      }))
  }
}
