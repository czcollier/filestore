package com.bullhorn.filestore

import java.io.{File, BufferedInputStream, FileInputStream, InputStream}

import akka.actor._
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.Timeout
import com.google.common.base.Stopwatch
import spray.can.Http
import spray.http.HttpHeaders.RawHeader
import spray.http.Uri.Path
import spray.http._
import scala.concurrent.duration._
import scala.concurrent.Future

import scala.collection.JavaConversions._


object FileStoreClient extends App {
  case object StartTest
  case object AckChunksStart
  case object AckChunk
  case class StoreFile(data: InputStream, idx: Int)
  abstract class StoreFileResult
  case class StoreFileSuccess(info: String) extends StoreFileResult
  case class StoreFileError(info: String) extends StoreFileResult

  implicit val system = ActorSystem()


  val bufSize = system.settings.config.getInt(
    "com.bullhorn.filestore.client.chunkSize")

  val serverURI = Uri(system.settings.config.getString(
    "com.bullhorn.filestore.client.serverURI"))

  import scala.concurrent.ExecutionContext.Implicits.global

  val storeClient = system.actorOf(Props[StoreFileActor])
  implicit val timeout = Timeout(120 seconds)

  val testFile = "/home/ccollier/Pictures/20131116_152313.jpg"
  val testFilesPath = "/home/ccollier/Pictures/tests"

  val testFiles = new File(testFilesPath).listFiles
  val fcount = testFiles.length

  for (f <- testFiles.zipWithIndex) {
    try {
      val stream = new BufferedInputStream(new FileInputStream(f._1))
      (storeClient ! StoreFile(stream, f._2))
      Thread.sleep(200)
    }
    catch {
      case e: Exception =>
        println("error opening file: " + e)
    }
  }

  class StoreFileActor extends Actor with ActorLogging {
    var cnt = 0
    def receive = {
      case s:StoreFile =>
         println("starting: %d".format(s.idx))
         val worker = context.actorOf(Props(new StoreFileWorker), "storeFileWorker_%d".format(s.idx))
         val res = (worker ? s).asInstanceOf[Future[StoreFileResult]]
         res.onComplete {
           r => r.map { x =>
             x match {
               case s: StoreFileSuccess =>
                 cnt += 1
                 //println(x.info)
                 println("SUCC: %d/%d".format(cnt, fcount))
               case f: StoreFileError =>
                 cnt += 1
                 println("FAIL: %d/%d".format(cnt, fcount))
               case x =>
                 println("??? %s".format(x.toString))
                 cnt += 1
             }
             if (cnt == fcount)
               system.shutdown
           }
         }
    }
  }

  class StoreFileWorker extends Actor with ActorLogging {
    val buf = Array.fill[Byte](bufSize)(0)

    var client: ActorRef = null//context.system.deadLetters
    var inStream: Option[InputStream] = None
    var cnt: Option[Int] = None
    val timer = Stopwatch.createUnstarted
    var bSent = 0

    private def nextChunk(sender: ActorRef) {
      val br = nextBytes()
      if (br.length > 0) {
        sender ! MessageChunk(br).withAck(AckChunk)
        bSent += br.length
      }
      else {
        sender ! ChunkedMessageEnd
      }
    }

    private def allChunks(sender: ActorRef) {
      var b = nextBytes()
      if (b.length > 0) {
        sender ! MessageChunk(b)
        bSent += b.length
      }
      b = nextBytes()
      while (b.length > 0) {
        sender ! MessageChunk(b)
        bSent += b.length
        b = nextBytes()
      }
      sender ! ChunkedMessageEnd
    }

    private def startRequest(sender: ActorRef) {
      sender ! ChunkedRequestStart(HttpRequest(
        method = HttpMethods.POST,
        uri = serverURI.path match { case Path(x) => x },
        headers = List(RawHeader("file-name", "myPicture.jpg")),
        entity = nextBytes()))
        .withAck(AckChunksStart)
    }

    def receive = {
      case StoreFile(stream, idx) =>
        client = sender
        timer.start
        inStream = Some(stream)
        cnt = Some(idx)
        IO(Http) ! Http.Connect(
          host = serverURI.authority.host.address,
          port = serverURI.authority.port)
      case Http.Connected(remoteAddr, localAddr) => startRequest(sender)
      case AckChunksStart => nextChunk(sender)
      case AckChunk => nextChunk(sender)
      case response@HttpResponse(status, entity, _, _) =>
        println("sent file #%d of %d bytes in %s".format(cnt.get, bSent, timer.stop))
        client ! StoreFileSuccess(entity.asString)
        sender ! Http.Close
      case Http.Closed =>
        println("connection for %d closed".format(cnt.get))
        context.stop(self)
      case Tcp.ErrorClosed(reason) =>
        println("ERROR: %s".format(reason))
        client ! StoreFileError(reason)
      case x =>
        println("OOPS! ====> received unhandled message: %s".format(x.toString))
    }

    private def nextBytes(): Array[Byte] = {
      inStream match {
        case Some(is) =>
          val bytesRead = is.read(buf, 0, bufSize)
          val br = if (bytesRead == -1) 0 else bytesRead
          val ret = Array.fill[Byte](br)(0)
          Array.copy(buf, 0, ret, 0, br)
          ret
        case None => throw new RuntimeException("input stream not initialized")
      }
    }
  }
}
