package net.xorf.filestore.fs.stdio

import java.io.{BufferedOutputStream, FileOutputStream}

import net.xorf.filestore.fs.TempFile

import scala.concurrent.{ExecutionContext, Future, future}

class StdTempFile(val path: String)(implicit ctx: ExecutionContext) extends TempFile {

  val os = new BufferedOutputStream(new FileOutputStream((path)))

  override def write(data: Array[Byte]): Future[Int] = {
    Future {
      os.write(data)
    }.map(x => data.length)
  }

  override def close(): Unit = os.close()
}
