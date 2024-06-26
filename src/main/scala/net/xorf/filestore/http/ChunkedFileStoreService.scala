package net.xorf.filestore.http

import akka.actor._
import SuspendingQueue.AckConsumed
import net.xorf.filestore.{Resources, FileHandlerActor}
import spray.can.Http
import spray.can.Http.RegisterChunkHandler
import spray.http.HttpMethods._
import spray.http._

/**
 * The front-line Spray HTTP handler for the filestore service.
 */
class ChunkedFileStoreService extends Actor with ActorLogging {

  def receive = {
    case AckConsumed(bytes) => log.info("%d bytes acked".format(bytes))
    // when a new connection comes in we register ourselves as the connection handler
    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
      sender ! HttpResponse(entity = "PONG!")

    case HttpRequest(GET, Uri.Path("/shutdown"), _, _, _) =>
      context.system.terminate()

    case r@HttpRequest(POST, Uri.Path("/file-upload"), headers, entity: HttpEntity.NonEmpty, protocol) =>
      val client = sender
      // emulate chunked behavior for POST requests to this path
      val parts = r.asPartStream()
      val assumedStart = parts.head

      val worker = context.actorOf(Props(new FileHandlerActor(Resources.store, assumedStart.asInstanceOf[ChunkedRequestStart])))
      val queue = context.actorOf(Props(new SuspendingQueue(client, worker)))

      client ! RegisterChunkHandler(queue)
      parts.tail.foreach(worker !)

    case s@ChunkedRequestStart(HttpRequest(POST, Uri.Path("/file-upload"), _, _, _)) =>
      val client = sender
      val worker = context.actorOf(Props(new FileHandlerActor(Resources.store, s)))
      val queue = context.actorOf(Props(new SuspendingQueue(client, worker)))

      client ! RegisterChunkHandler(queue)

    case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "Unknown resource!")
 
    case Timedout(HttpRequest(_, Uri.Path("/timeout/timeout"), _, _, _)) =>
      log.info("Dropping Timeout message")

    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(
        status = 500,
        entity = "The " + method + " request to '" + uri + "' has timed out..."
      )
  }
}
