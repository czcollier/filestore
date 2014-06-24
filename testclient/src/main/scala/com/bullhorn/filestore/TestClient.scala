package com.bullhorn.filestore

import java.io.{FileInputStream, BufferedInputStream}

import akka.actor.{Props, Actor, ActorLogging, ActorSystem}
import akka.io.{Tcp, IO}
import spray.can.Http
import spray.http._
import spray.client.pipelining._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions._
import scala.collection.JavaConversions._

object TestClient extends App {
  case object StartTest
  case object AckChunksStart
  case object AckChunk

  implicit val system = ActorSystem()

  import ExecutionContext.Implicits.global
  system.scheduler.scheduleOnce(60 seconds) {
    system.shutdown()
  }

  val clientActor = system.actorOf(Props[TestClientActor])
  clientActor ! StartTest

  class TestClientActor extends Actor with ActorLogging {
    val bufSize = 1024
    var offset = 0
    val buf = Array.fill[Byte](bufSize)(0)
    val inStream = new BufferedInputStream(new FileInputStream("/home/ccollier/Pictures/DarkB_1920x1200.jpg"))

    def nextBytes(): Array[Byte] = {
      val bytesRead = inStream.read(buf, 0, bufSize)
      val br = if (bytesRead == -1) 0 else bytesRead
      offset += br
      val ret = Array.fill[Byte](br)(0)
      Array.copy(buf, 0, ret, 0, br)
      println("sending %d bytes".format(ret.length))
      ret
    }

    def receive = {
      case StartTest =>
        IO(Http) ! Http.Connect("localhost", port = 8080)
      case Tcp.Connected(remoteAddr, localAddr) =>
        val sdr = sender
        println("hey, the server at %s connected back to me (%s) !".format(remoteAddr, localAddr))
        sdr ! ChunkedRequestStart(HttpRequest(
            method = HttpMethods.POST,
            uri = "/file-upload",
            entity = nextBytes()))
          .withAck(AckChunksStart)
      case AckChunksStart =>
        val br = nextBytes()
        if (br.length > 0)
          sender ! MessageChunk(br).withAck(AckChunk)
        else {
          sender ! ChunkedMessageEnd
        }
      case AckChunk =>
        val br = nextBytes()
        if (br.length > 0)
          sender ! MessageChunk(br).withAck(AckChunk)
        else {
          sender ! ChunkedMessageEnd
        }
      case r: HttpResponse =>
        println("received response:")
        println(r.entity)
        sender ! Http.Close
      case Http.Closed =>
        println("Done!")
        system.shutdown()
      case x =>
        println("received unhandled: " + x.toString)
    }
  }
}
