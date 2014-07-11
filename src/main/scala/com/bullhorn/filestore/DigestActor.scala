package com.bullhorn.filestore

import java.security.MessageDigest

import akka.actor.{Actor, ActorLogging}
import com.bullhorn.filestore.StorageCoordinatorActor.{FileChunk, FileSignature}

object DigestActor {
  case object GetDigest
  case class BytesConsumed(cnt: Int)
}

class DigestActor extends Actor with ActorLogging {
  import DigestActor._

  val digest = MessageDigest.getInstance("SHA-1")

  private def hexEncode(bytes: Array[Byte]): String =
    bytes.map(0xFF & _).map { "%02x".format(_) }.foldLeft("") { _ + _ }

  def receive: Receive = {
    case FileChunk(bytes) =>
      val client = sender
      digest.update(bytes)
      client ! BytesConsumed(bytes.length)

    case GetDigest =>
      sender ! FileSignature(hexEncode(digest.digest))
  }
}
