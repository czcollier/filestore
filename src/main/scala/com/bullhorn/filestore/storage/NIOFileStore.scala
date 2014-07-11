package com.bullhorn.filestore.storage

import java.nio.file.{Paths, Files}

import com.bullhorn.filestore.db.FileDb

import scala.concurrent.{ExecutionContext, Future, future}

class NIOFileStore(db: FileDb) extends FileStore(db) {

  override def deleteTemp(tempName: String)(implicit ec: ExecutionContext): Unit = {
    Future { Files.delete(Paths.get(tempName)) }
  }

  override def moveToPerm(tempName: String, id: Long)(implicit ec: ExecutionContext): Future[String] = {
    future {
      val targetPath = withPermDir(formatPermFileName(id))
      Files.move(Paths.get(tempName), Paths.get(targetPath))
    } map { p => p.toString  }
  }
}
