package com.bullhorn.filestore

import java.io.{BufferedOutputStream, FileOutputStream, File}
import java.security.MessageDigest

import akka.actor.{Actor, ActorLogging}
import spray.http.HttpData

object FileWriterActor {
  case object Done
  case class FileSignature(v: String)

  def hexEncode(bytes: Array[Byte]) =
    bytes.map(0xFF & _).map { "%02x".format(_) }.foldLeft("") { _ + _ }
}

class FileWriterActor(store: FileStore) extends Actor with ActorLogging {
  import com.bullhorn.filestore.FileWriterActor._
  import scala.language.implicitConversions

  //TODO: use async I/O
  var cnt = 0
  val tmpFile = store.newTempFile
  val os = new BufferedOutputStream(new FileOutputStream((tmpFile)))
  val digest = MessageDigest.getInstance("SHA-1")

  def receive = {
    case chunk: HttpData => {
      digest.update(chunk.toByteArray)
      os.write(chunk.toByteArray)
      //println("saved chunk: " + cnt + " (" + chunk.length + ")")
      cnt += 1
    }
    case Done => {
      val fileSig = hexEncode(digest.digest)
      os.close()

      store.finish(fileSig, tmpFile)

      println("done: " + fileSig)
      sender ! FileSignature(fileSig)
      context.system.stop(self)
    }
  }
}
