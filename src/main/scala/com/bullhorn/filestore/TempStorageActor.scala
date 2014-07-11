package com.bullhorn.filestore

import java.io.{FileOutputStream, BufferedOutputStream}

import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import akka.util.Timeout
import com.bullhorn.filestore.PermStorageActor.FileWithSignature
import com.bullhorn.filestore.StorageCoordinatorActor.{FileSignature, FileChunk}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object TempStorageActor {
  def apply(store: FileStore) = Props(new TempStorageActor(store))
}

class TempStorageActor(store: FileStore) extends Actor with ActorLogging {

  val tmpFile = store.newTempFile
  val os = new BufferedOutputStream(new FileOutputStream((tmpFile)))

  def receive = {
    case FileChunk(bytes) => os.write(bytes)
    case FileSignature(fileSig) =>
      val client = sender
      os.close()
      client ! FileWithSignature(fileSig, tmpFile)
  }
}
