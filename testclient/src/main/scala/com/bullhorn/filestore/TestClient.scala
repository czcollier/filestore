package com.bullhorn.filestore

import akka.actor.{Props, Actor, ActorLogging, ActorSystem}
import akka.io.{Tcp, IO}
import spray.can.Http
import spray.http._
import spray.client.pipelining._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object TestClient extends App {
  case object StartTest
  implicit val system = ActorSystem()

  import ExecutionContext.Implicits.global
  system.scheduler.scheduleOnce(5 seconds) {
    system.shutdown()
  }

  val clientActor = system.actorOf(Props[TestClientActor])
  clientActor ! StartTest

  class TestClientActor extends Actor with ActorLogging {

    def receive = {
      case StartTest =>
        IO(Http) ! Http.Connect("localhost", port = 8080)
      case Tcp.Connected(remoteAddr, localAddr) =>
        val sdr = sender
        println("hey, the server at %s connected back to me (%s) !".format(remoteAddr, localAddr))
        sdr ! ChunkedRequestStart(HttpRequest(entity = "foo")).withAck("bar")
        sdr ! ChunkedMessageEnd
      case "bar" =>
        sender ! MessageChunk("hello, chunks")
      case x =>
        println("received unhandled: " + x.toString)
    }
  }
}
