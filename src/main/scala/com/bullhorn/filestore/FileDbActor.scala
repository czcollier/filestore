package com.bullhorn.filestore

import akka.actor.{Actor, ActorLogging}

object FileDbActor {
  case class Finish(signature: String)
}
class FileDbActor(db: FileDb) extends Actor with ActorLogging {
  import FileDbActor._

  def receive = {
    case Finish(sig) => sender ! db.finish(sig)
  }
}
