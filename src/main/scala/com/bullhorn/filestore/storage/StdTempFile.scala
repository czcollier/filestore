package com.bullhorn.filestore.storage

import java.io.{FileOutputStream, BufferedOutputStream}

import scala.concurrent.{ExecutionContext, Future, future}

class StdTempFile(path: String)(implicit ctx: ExecutionContext) extends TempFile {

  val os = new BufferedOutputStream(new FileOutputStream((path)))
  override def write(data: Array[Byte]): Future[Int] = {
    future {
      os.write(data)
    }.map(x => data.length)
  }

  override def close(): Unit = os.close()
}
