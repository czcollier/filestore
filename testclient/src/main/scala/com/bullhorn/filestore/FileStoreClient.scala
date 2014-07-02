package com.bullhorn.filestore

import java.io.{File, BufferedInputStream, FileInputStream, InputStream}

import akka.actor._
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.Timeout
import com.bullhorn.filestore.FileStoreClient.AckChunk
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
  case class StoreFile(data: InputStream, id: String)
  abstract class StoreFileResult
  case class StoreFileSuccess(info: String) extends StoreFileResult
  case class StoreFileError(info: String) extends StoreFileResult

  implicit val system = ActorSystem()

  val bufSize = system.settings.config.getInt(
    "com.bullhorn.filestore.client.chunkSize")

  val serverURI = Uri(system.settings.config.getString(
    "com.bullhorn.filestore.client.serverURI"))

  import scala.concurrent.ExecutionContext.Implicits.global

  //val storeClient = system.actorOf(Props[StoreFileActor])
  implicit val timeout = Timeout(120 seconds)

  val testFilesPath = "/home/ccollier/Pictures/tests"

  val testFiles = new File(testFilesPath).listFiles
  val fcount = testFiles.length
  val numRuns = 10
  val testCnt = fcount * numRuns

  val coordinator = system.actorOf(Props[StoreFileCoordinator])

  for (i <- 1 to numRuns)
    for (f <- testFiles.zipWithIndex) {
      try {
        val stream = new BufferedInputStream(new FileInputStream(f._1))
        coordinator ! StoreFile(stream, s"${f._2}_$i")
        Thread.sleep(100)
      }
      catch {
        case e: Exception =>
          println("error opening file: " + e)
      }
    }

  object StoreFileCoordinator {
    case object Tick
  }

  class StoreFileCoordinator extends Actor with ActorLogging {
    import StoreFileCoordinator._

    var successCount = 0
    var failCount = 0
    val ticker = context.system.scheduler.schedule(100 millis, 100 millis, self, Tick)
    def receive = {
      case sf: StoreFile =>
        val worker = system.actorOf(Props(new StoreFileWorker), "storeFileWorker_%s".format(sf.id))
        worker ! sf
      case s: StoreFileSuccess =>
        successCount += 1
      //println("SUCC: %d/%d/%d".format(successCount, failCount, fcount))
      case f: StoreFileError =>
        failCount += 1
        log.error("FAIL: %d/%d/%d".format(successCount, failCount, fcount))
      case Tick =>
        if (successCount + failCount == testCnt) {
          if (context != null)
            system.scheduler.scheduleOnce(3 seconds) {
              log.info("stopping...")
              system.shutdown()
            }
        }
      case x =>
        //println("??? %s".format(x.toString))
        failCount += 1
    }
  }

  class StoreFileWorker extends Actor with ActorLogging {
    val buf = Array.fill[Byte](bufSize)(0)

    var client: ActorRef = null//context.system.deadLetters
    var inStream: Option[InputStream] = None
    var cnt: Option[String] = None
    val timer = Stopwatch.createUnstarted
    var bSent = 0

    private def nextChunk(sender: ActorRef) {
      val client = sender
      val br = nextBytes()
      if (br.length > 0) {
        client ! MessageChunk(br).withAck(AckChunk)
        bSent += br.length
      }
      else {
        client ! ChunkedMessageEnd
      }
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
        val server = sender
        log.info("sent file #%s of %d bytes in %s".format(cnt.get, bSent, timer.stop))
        client ! StoreFileSuccess(entity.asString)
        server ! Http.Close
      case Http.Closed =>
        //log.debug("connection for %d closed".format(cnt.get))
        //context.stop(self)
      case Tcp.ErrorClosed(reason) =>
        log.error("ERROR: %s".format(reason))
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
