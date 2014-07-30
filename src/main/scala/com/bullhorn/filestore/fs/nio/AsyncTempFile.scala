package com.bullhorn.filestore.fs.nio

import java.io.File
import java.nio.ByteBuffer
import java.nio.file.StandardOpenOption._

import com.bullhorn.filestore.fs.TempFile

import scala.concurrent.{ExecutionContext, Future}

class AsyncTempFile(val path: String)(implicit ec: ExecutionContext) extends TempFile {
  val fc = new AsyncFileChannel(new File(path), READ, WRITE, CREATE)
  var writePos = 0

  override def write(data: Array[Byte]): Future[Int] = {
    val ret = fc.write(ByteBuffer.wrap(data), writePos)
    writePos += data.length
    ret
  }

  override def close(): Unit = fc.close()

}
