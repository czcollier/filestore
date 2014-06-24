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
  import SuspendingQueue._

  val LowWaterMark = context.system.settings.config.getInt(
    "com.bullhorn.filestore.suspendingQueue.suspendThreshold")

  val HighWaterMark = context.system.settings.config.getInt(
    "com.bullhorn.filestore.suspendingQueue.resumeThreshold")

  var unackedBytes = 0L
  var suspended = false

  context.watch(worker)
  client ! CommandWrapper(SetRequestTimeout(Duration.Inf)) // cancel timeout

  def receive = {
    case part: HttpRequestPart => 
      log.debug(s"queue received part: ${part}")
      dispatch(part)
    case AckConsumed(bytes) =>
      log.debug(s"queue received ack: ${bytes}")
      unackedBytes -= bytes
      require(unackedBytes >= 0)
      checkResume()
    case Terminated(worker) => context.stop(self)
    case x => {
      log.debug(s"queue received: ${x}")
      client ! x
    }
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
object SuspendingQueue {
  case class AckConsumed(bytes: Long)
}
