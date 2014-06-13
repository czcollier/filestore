package com.bullhorn.filestore

import akka.actor._
import akka.io.Tcp.{ResumeReading, SuspendReading}
import scala.concurrent.duration._
import java.io._
import org.jvnet.mimepull.{MIMEPart, MIMEMessage}
import spray.http._
import MediaTypes._
import HttpHeaders._
import parser.HttpParser
import HttpHeaders.RawHeader
import spray.io.CommandWrapper
import scala.annotation.tailrec
import scala.collection.immutable.Queue
import spray.can.Http

/**
 * A backpressure queue that automatically handles Suspend/ResumeReading for incoming
 * MessageChunks and requires `AckConsumed` acknowledgements to manage the workers queue size.
 */
class SuspendingQueue(client: ActorRef, worker: ActorRef) extends Actor with ActorLogging {
  val LowWaterMark = 1024*1024*20
  val HighWaterMark = 1024*1024*50

  var unackedBytes = 0L
  var suspended = false

  context.watch(worker)
  client ! CommandWrapper(SetRequestTimeout(Duration.Inf)) // cancel timeout

  def receive = {
    case part: HttpRequestPart => dispatch(part)
    case AckConsumed(bytes) =>
      unackedBytes -= bytes
      require(unackedBytes >= 0)
      checkResume()
    case Terminated(worker) => context.stop(self)
    case x => client ! x
  }

  private def dispatch(part: HttpRequestPart): Unit = {
    unackedBytes += messageBytes(part)
    worker ! part

    if (unackedBytes > HighWaterMark && !suspended) {
      log.debug(s"Suspending with $unackedBytes bytes unacked")
      suspended = true
      client ! SuspendReading
    }
  }
  def checkResume(): Unit =
    if (unackedBytes < LowWaterMark && suspended) {
      suspended = false
      log.debug(s"Resuming with $unackedBytes bytes unacked")
      client ! ResumeReading
    }

  private def messageBytes(part: HttpRequestPart): Long = part match {
    case MessageChunk(data, _) => data.length
    case _ => 0
  }
}
case class AckConsumed(bytes: Long)

class FileUploadHandler(start: ChunkedRequestStart) extends Actor with ActorLogging {
  import start.request._

  val dir = new File("/tmp")
  dir.mkdirs()
  val tmpFile = File.createTempFile("chunked-receiver", ".tmp", dir)
  tmpFile.deleteOnExit()
  val output = new BufferedOutputStream( new FileOutputStream(tmpFile))
  val Some(HttpHeaders.`Content-Type`(ContentType(multipart: MultipartMediaType, _))) = header[HttpHeaders.`Content-Type`]
  val boundary = multipart.parameters("boundary")

  log.info(s"Got start of chunked request $method $uri with multipart boundary '$boundary' writing to $tmpFile")
  var bytesWritten = 0L

  def receive = {
    case c: MessageChunk =>
      log.info(s"Got ${c.data.length} bytes of chunked request $method $uri")
      output.write(c.data.toByteArray)
      bytesWritten += c.data.length
      sender ! AckConsumed(c.data.length)

    case e: ChunkedMessageEnd =>
      log.info(s"Got end of chunked request $method $uri")
      output.close()

      sender ! HttpResponse(status = 200, entity = renderResult())
      sender ! CommandWrapper(SetRequestTimeout(2.seconds)) // reset timeout to original value
      //tmpFile.delete()
      context.stop(self)
  }

  def renderResult(): HttpEntity = {
    HttpEntity(`text/html`,
      <html><body>OK</body></html>.toString)
  }
}
