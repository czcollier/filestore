package com.bullhorn.filestore

import java.io.{BufferedInputStream, File, FileInputStream, InputStream}

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
import scala.util.Random


object FileStoreClient extends App {
  case object StartTest
  case object AckChunksStart
  case object AckChunk
  case class StoreFile(data: InputStream, id: String)
  abstract class StoreFileResult
  case class StoreFileSuccess(info: String) extends StoreFileResult
  case class StoreFileError(info: String) extends StoreFileResult

  implicit val system = ActorSystem()

  def bufSize = system.settings.config.getInt(
    "com.bullhorn.filestore.client.chunkSize")

  def serverURI = Uri(system.settings.config.getString(
    "com.bullhorn.filestore.client.serverURI"))

  def testFilesPath = system.settings.config.getString(
    "com.bullhorn.filestore.client.testFileDir")

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val timeout = Timeout(90 seconds)

  val testFiles = new File(testFilesPath).listFiles
  val testCnt = testFiles.length * 5

  val coordinator = system.actorOf(Props[StoreFileCoordinator])
  val tests = Stream.continually(Random.nextInt(testFiles.length)).take(testCnt)
  for (i <- tests.zipWithIndex) {
    try {
      val stream = new BufferedInputStream(new FileInputStream(testFiles(i._1)))
      coordinator ! StoreFile(stream, s"${i._2}")
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
    import com.bullhorn.filestore.FileStoreClient.StoreFileCoordinator._

    var successCount = 0
    var failCount = 0
    var lastTick = System.currentTimeMillis
    var lastCount = 0

    def doneCount = successCount + failCount

    val ticker = context.system.scheduler.schedule(3 seconds, 3 seconds, self, Tick)
    def receive = {
      case sf: StoreFile =>
        val worker = context.actorOf(Props(new StoreFileWorker), "storeFileWorker_%s".format(sf.id))
        log.info("storing: %s.".format(sf.id))
        val timer = Stopwatch.createStarted
        (worker ? sf).collect {
          case s: StoreFileSuccess =>
            successCount += 1
            log.info("SUCCESS: %d/%d/%d in %s".format(successCount, failCount, testCnt, timer.toString))
          case f: StoreFileError =>
            failCount += 1
            log.info("FAIL: %d/%d/%d".format(successCount, failCount, testCnt))
          case x =>
            failCount += 1
            log.info("FAIL: unexpected response: %s".format(x.toString))
        }
      case Tick =>
        log.info("---->> TICK: %d/%d uploads completed".format(doneCount, testCnt))
        log.info("done: %d, last: %d".format(doneCount, lastCount))
        if ((doneCount - lastCount) < 1) {
          if (context != null)
            system.scheduler.scheduleOnce(3 seconds) {
              log.info("rate low; stopping...")
              system.shutdown()
            }
        }
        lastCount = doneCount
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
        //log.info("sent file #%s of %d bytes in %s".format(cnt.get, bSent, timer.stop))
        client ! StoreFileSuccess(entity.asString)
        server ! Http.Close
      case Http.Closed =>
        log.debug("connection for %s closed".format(cnt.get))
        //context.stop(self)

      case Tcp.ErrorClosed(reason) =>
        log.error("ERROR: %s".format(reason))
        client ! StoreFileError(reason)
        //context.stop(self)
      case x =>
        println("OOPS! ====> received unhandled message: %s".format(x.toString))
        //context.stop(self)
    }

    override def postStop() {
      inStream.foreach { s =>
        s.close()
      }
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
