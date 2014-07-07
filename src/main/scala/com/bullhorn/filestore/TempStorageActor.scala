package com.bullhorn.filestore

import java.io.{FileOutputStream, BufferedOutputStream}

import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import akka.util.Timeout
import com.bullhorn.filestore.PermStorageActor.FileWithSignature
import com.bullhorn.filestore.StorageParentActor.{FileSignature, FileChunk}
import scala.concurrent.duration._

object TempStorageActor {
  def apply(store: FileStore, permActor: ActorRef) = Props(new TempStorageActor(store, permActor))
}

class TempStorageActor(store: FileStore, permActor: ActorRef) extends Actor with ActorLogging {

  val tmpFile = store.newTempFile
  val os = new BufferedOutputStream(new FileOutputStream((tmpFile)))

  implicit val timeout = Timeout(90 seconds)

  def receive = {
    case FileChunk(bytes) => os.write(bytes)
    case FileSignature(fileSig) =>
      os.close()
      permActor ! FileWithSignature(fileSig, tmpFile)
  }
}
