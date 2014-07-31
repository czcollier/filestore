package com.bullhorn.filestore.fs.nio

import java.nio.file.{Files, Paths}

import com.bullhorn.filestore.fs.FileStore

import scala.concurrent.{ExecutionContext, Future, future}

class NIOFileStore extends FileStore {

  override def deleteTemp(tempName: String)(implicit ec: ExecutionContext): Unit = {
    Future { Files.delete(Paths.get(tempName)) }
  }

  override def moveToPerm(tempName: String, id: String)(implicit ec: ExecutionContext): Future[String] = {
    Future {
      val targetPath = withPermDir(formatPermFileName(id))
      Files.move(Paths.get(tempName), Paths.get(targetPath))
    } map { p => p.toString  }
  }
}
