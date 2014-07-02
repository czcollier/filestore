package com.bullhorn.filestore

import java.io.File

import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.bullhorn.filestore.FileDbActor.Finish
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class PermStorageActor(store: FileStore, dbActor: ActorRef) extends Actor with ActorLogging {
  implicit val ec: ExecutionContext = context.dispatcher

  implicit val timeout = Timeout(90 seconds)

  def receive = {
    case (sig: String, f: File) =>
      val client = sender
      (dbActor ? Finish(sig)).mapTo[Option[Long]].map { id =>
        store.storeFile(id, f)
      }.pipeTo(client)
  }
}
