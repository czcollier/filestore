package com.bullhorn.filestore.storage

import java.nio.file.{Paths, Files}

import scala.concurrent.{ExecutionContext, Future, future}

class ScalaIOFileStore extends FileStore {
  override def newTempFile: String = ???

  override def deleteTemp(tempName: String)(implicit ec: ExecutionContext): Unit = {
    Future { Files.delete(Paths.get(tempName)) }
  }

  override def moveToPerm(tempName: String, id: Long)(implicit ec: ExecutionContext): Future[String] = {
    future {
      Files.move(Paths.get(tempName), Paths.get(withPermDir(formatPermFileName(id))))
    } map { p => p.toString  }
  }
}
