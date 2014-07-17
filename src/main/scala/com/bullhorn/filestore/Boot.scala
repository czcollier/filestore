package com.bullhorn.filestore

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import com.bullhorn.filestore.http.ChunkedFileStoreService
import spray.can.Http

object Boot extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  val handler = system.actorOf(Props[ChunkedFileStoreService], name = "chunked-filestore-service")

  // start a new HTTP server on port 8080 with our service actor as the handler
  system.actorOf(Props(new Actor() {
    IO(Http) ! Http.Bind(handler, "0.0.0.0", port = 8080)
    def receive = {
      case x => println("handler said: %s".format(x.toString))
    }
  }))
}
