package com.bullhorn.filestore

import akka.actor._
import akka.io.Tcp.{ResumeReading, SuspendReading}
import spray.http._
import spray.io.CommandWrapper

import scala.concurrent.duration._

/**
 * A backpressure queue that automatically handles Suspend/ResumeReading for incoming
 * MessageChunks and requires `AckConsumed` acknowledgements to manage the workers queue size.
 */
class SuspendingQueue(client: ActorRef, worker: ActorRef) extends Actor with ActorLogging {
  import com.bullhorn.filestore.SuspendingQueue._

  var unackedBytes = 0L
  var suspended = false

  context.watch(worker)
  client ! CommandWrapper(SetRequestTimeout(Duration.Inf)) // cancel timeout

  def receive = {
    case part: HttpRequestPart => 
      dispatch(part)
    case AckConsumed(bytes) =>
      unackedBytes -= bytes
      require(unackedBytes >= 0)
      checkResume()
    case Terminated(worker) => context.stop(self)
    case x => {
      client ! x
    }
  }

  private def dispatch(part: HttpRequestPart): Unit = {
    unackedBytes += messageBytes(part)
    worker ! part

    if (unackedBytes > Config.SuspendingQueue.suspendThreshold && !suspended) {
      log.debug(s"Suspending with $unackedBytes bytes unacked")
      suspended = true
      client ! SuspendReading
    }
  }
  def checkResume(): Unit =
    if (unackedBytes < Config.SuspendingQueue.resumeThreshold && suspended) {
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
