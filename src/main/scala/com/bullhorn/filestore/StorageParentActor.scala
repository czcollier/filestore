package com.bullhorn.filestore

import akka.actor.{Props, ActorLogging, Actor}
import com.bullhorn.filestore.PermStorageActor.{FileStored}
import com.bullhorn.filestore.StorageParentActor.{FileSignature, FileChunk}

object StorageParentActor {
  case class FileSignature(v: String)
  case class FileChunk(bytes: Array[Byte])

  def apply(db: FileDb, store: FileStore) = Props(new StorageParentActor(db, store))
}

class StorageParentActor(db: FileDb, store: FileStore) extends Actor with ActorLogging {
  val fileDbActor = context.actorOf(FileDbActor(db))
  val permStorageActor = context.actorOf(PermStorageActor(store, self, fileDbActor).withDispatcher("io-dispatcher"))
  val tempStorageActor = context.actorOf(TempStorageActor(store, permStorageActor).withDispatcher("io-dispatcher"))

  def receive = {
    case c: FileChunk => tempStorageActor ! c
    case s: FileSignature => {
      val client = sender
      tempStorageActor ! s
      client ! "Done"
    }
    case FileStored => {
      log.info("stored a file")
    }
  }
}
