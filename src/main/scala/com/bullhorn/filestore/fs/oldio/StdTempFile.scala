package com.bullhorn.filestore.fs.oldio

import java.io.{BufferedOutputStream, FileOutputStream}

import com.bullhorn.filestore.fs.TempFile

import scala.concurrent.{ExecutionContext, Future, future}

class StdTempFile(val path: String)(implicit ctx: ExecutionContext) extends TempFile {

  val os = new BufferedOutputStream(new FileOutputStream((path)))

  override def write(data: Array[Byte]): Future[Int] = {
    future {
      os.write(data)
    }.map(x => data.length)
  }

  override def close(): Unit = os.close()
}
