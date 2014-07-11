package com.bullhorn.filestore.storage

import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Paths

import java.nio.file.StandardOpenOption._
import scala.concurrent.{ExecutionContext, Future}

class AsyncTempFile(name: String)(implicit ec: ExecutionContext) extends TempFile {
  val fc = new AsyncFileChannel(new File(name), READ, WRITE, CREATE)
  var writePos = 0

  override def write(data: Array[Byte]): Future[Int] = {
    val ret = fc.write(ByteBuffer.wrap(data), writePos)
    writePos += data.length
    ret
  }

  override def close(): Unit = fc.close()
}
