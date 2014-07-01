package com.bullhorn.filestore

import java.io.{File, FileOutputStream, BufferedOutputStream}

import akka.actor.{Actor, ActorLogging}
import com.bullhorn.filestore.FileWriterActor.{Done, Data}

class StorageActor(store: FileStore) extends Actor with ActorLogging {

  val tmpFile = store.newTempFile
  val os = new BufferedOutputStream(new FileOutputStream((tmpFile)))

  def receive = {
    case Data(bytes) => os.write(bytes)
    case Done(fileSig) =>
      val client = sender
      os.close()
      client ! store.finish(fileSig, new File(tmpFile))
  }
}
