package com.bullhorn.filestore
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
import spray.routing.HttpService
import spray.http._
import spray.httpx.SprayJsonSupport
import akka.util.Timeout
import scala.concurrent.Future
import spray.client.pipelining._
import akka.event.LoggingAdapter

class FileStoreServiceActor extends Actor with ActorLogging with FileStoreService {
  def actorRefFactory = context

  override def receive = runRoute(mainRoute)
}

trait FileStoreService extends HttpService with SprayJsonSupport {
  val mainRoute = path("file") {
    get {
      complete("OK")
    }
  }
}
