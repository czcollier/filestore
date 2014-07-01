package com.bullhorn.filestore

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http

object Boot extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  // create and start our service actor
  //val service = system.actorOf(Props[FileStoreServiceActor], "filestore-service")
  val handler = system.actorOf(Props[ChunkedFileStoreService], name = "filestore-handler")
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ! Http.Bind(handler, "0.0.0.0", port = 8080)
}
