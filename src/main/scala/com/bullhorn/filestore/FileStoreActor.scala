package com.bullhorn.filestore

import java.io.{BufferedOutputStream, FileOutputStream}

import akka.actor.{Actor, ActorLogging}
import spray.http.HttpData

object FileStoreActor {
  case class Start(filename: String)
  case class Done(key: String)
}

class FileStoreActor extends Actor with ActorLogging {
  import FileStoreActor._
  var os: BufferedOutputStream = null
  var cnt = 0
  def receive = {
    case Start(n) => {
      os = new BufferedOutputStream(new FileOutputStream("tmp." + n))
      println("started")
    }
    case chunk: HttpData => {
      os.write(chunk.toByteArray)
      println("saved chunk: " + cnt + " (" + chunk.length + ")")
      cnt += 1
    }
    case done: Done => {
      println("done: " + done.key)
      os.close()
    }
    case x: Any => println(x.getClass)
  }
}
