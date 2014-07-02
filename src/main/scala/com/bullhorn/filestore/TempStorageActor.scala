package com.bullhorn.filestore

import java.io.{File, FileOutputStream, BufferedOutputStream}

import akka.actor.{ActorRef, Actor, ActorLogging}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.bullhorn.filestore.FileWriterActor.{Done, Data}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TempStorageActor(store: FileStore, permStorage: ActorRef) extends Actor with ActorLogging {
  implicit val ec: ExecutionContext = context.dispatcher
  val tmpFile = store.newTempFile
  val os = new BufferedOutputStream(new FileOutputStream((tmpFile)))

  implicit val timeout = Timeout(90 seconds)

  def receive = {
    case Data(bytes) => os.write(bytes)
    case Done(fileSig) =>
      val client = sender
      os.close()
      (permStorage ? (fileSig, new File(tmpFile))).mapTo[Boolean].pipeTo(client)
  }
}
