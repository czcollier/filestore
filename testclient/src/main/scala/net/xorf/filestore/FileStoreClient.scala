package net.xorf.filestore

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
import net.xorf.filestore.Throttler.{SetTarget, RateInt}

object FileStoreClient extends App {
  case object StartTest
  case object AckChunksStart
  case object AckChunk
  case class StoreFile(data: InputStream, id: String, time: Long)
  abstract class StoreFileResult
  case class StoreFileSuccess(info: String, size: Int) extends StoreFileResult
  case class StoreFileError(info: String) extends StoreFileResult

  implicit val system = ActorSystem()

  def bufSize = system.settings.config.getInt(
    "net.xorf.filestore.client.chunkSize")

  def serverURI = Uri(system.settings.config.getString(
    "net.xorf.filestore.client.serverURI"))

  def testFilesPath = system.settings.config.getString(
    "net.xorf.filestore.client.testFileDir")

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val timeout = Timeout(5000 minutes)

  val testFiles = new File(testFilesPath).listFiles
  val testCnt = testFiles.length * 3

  println("testing with %d files over %d runs".format(testFiles.length, testCnt))

  val coordinator = system.actorOf(Props[StoreFileCoordinator])
  val throttler = system.actorOf(Props(classOf[TimerBasedThrottler], 15 msgsPer 500.milliseconds))
  throttler ! SetTarget(Some(coordinator))

  val tests = Stream.continually(Random.nextInt(testFiles.length)).take(testCnt)
  val totalTimer = Stopwatch.createStarted

  tests.zipWithIndex foreach { zi =>
      val stream = new BufferedInputStream(new FileInputStream(testFiles(zi._1)))
      println("sending: %s".format(zi._2))
      (throttler ? StoreFile(stream, s"${zi._2}", System.currentTimeMillis))
        .mapTo[(Int, Int, Int, Long)]
        .map(x => println(x.toString))
  }

  Thread.sleep(30);

  object StoreFileCoordinator {
    case object Tick
  }

  class StoreFileCoordinator extends Actor with ActorLogging {
    import net.xorf.filestore.FileStoreClient.StoreFileCoordinator._

    var successCount = 0
    var failCount = 0
    var lastTick = System.currentTimeMillis
    var lastCount = 0

    var flightTime = 0L
    var totalBytes = 0

    def doneCount = successCount + failCount

    private def handleResponse(r: StoreFileResult): (Int, Int, Int) = {
      r match {
        case StoreFileSuccess(info, size) => {
          successCount += 1
          totalBytes += size
          log.info("got success for store: %s.".format(info))
        }
        case _ => failCount += 1
      }
      log.info("%s: %d/%d/%d".format(r, successCount, failCount, testCnt))
      (successCount, failCount, testCnt)
    }

    val ticker = context.system.scheduler.schedule(1 seconds, 1 seconds, self, Tick)
    def receive = {
      case sf: StoreFile =>
        log.info("storing: %s.".format(sf.id))
        val worker = context.actorOf(Props(new StoreFileWorker), "storeFileWorker_%s".format(sf.id))
        worker ! sf
      case r: StoreFileResult => handleResponse(r)
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
      case StoreFile(stream, idx, time) =>
        log.debug("received")
        client = sender
        timer.start
        inStream = Some(stream)
        cnt = Some(idx)
        IO(Http) ! Http.Connect(
          host = serverURI.authority.host.address,
          port = serverURI.authority.port)
      case Http.Connected(remoteAddr, localAddr) => {
        log.debug("starting request")
        startRequest(sender)
      }

      case AckChunksStart => nextChunk(sender)
      case AckChunk => nextChunk(sender)
      case response@HttpResponse(status, entity, _, _) =>
        log.debug("got HTTP response: %s".format(status))
        val server = sender
        client ! StoreFileSuccess(entity.asString, bSent)
        server ! Http.Close
      case Http.Closed =>
        log.debug("connection for %s closed".format(cnt.get))
        context.system.stop(self)
      case Tcp.ErrorClosed(reason) =>
        log.error("ERROR: %s".format(reason))
        client ! StoreFileError(reason)
        context.stop(self)
      case x =>
        println("OOPS! ====> received unhandled message: %s".format(x.toString))
        context.stop(self)
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
