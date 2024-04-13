package net.xorf.filestore.fs.nio

import java.io.File
import java.nio.ByteBuffer
import java.nio.file.StandardOpenOption._
import java.nio.file.Paths

import net.xorf.filestore.fs.TempFile

import scala.concurrent.{ExecutionContext, Future}

import akka.io.IO
import akka.actor.ActorSystem

class AsyncTempFile(val path: String)(implicit ec: ExecutionContext) extends TempFile {
  val fc = new AsyncFileChannel(new File(path), READ, WRITE, CREATE)
  var writePos = 0

   val as = ActorSystem("testing") 

  override def write(data: Array[Byte]): Future[Int] = {
    val io = IO(as)
    io.outputStream(Paths.get(""))

    val ret = fc.write(ByteBuffer.wrap(data), writePos)
    writePos += data.length
    ret
  }

  override def close(): Unit = fc.close()

}
