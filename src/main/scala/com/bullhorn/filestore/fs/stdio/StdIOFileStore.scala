package com.bullhorn.filestore.fs.stdio

import java.io.File

import com.bullhorn.filestore.fs.FileStore

import scala.concurrent.{ExecutionContext, Future, future}

class StdIOFileStore extends FileStore {

  override def moveToPerm(tempName: String, id: Long)(implicit ec: ExecutionContext): Future[String] = {
    val permFile = new File(withPermDir(formatPermFileName(id)))
    val tempFile = new File(tempName)
    println("moving temp file %s to permanent location: %s".format(tempFile.getPath, permFile.getPath))
    future {
      (tempFile renameTo permFile)
    } map {  res =>
      if (!res)
        throw new RuntimeException("could not store file move to permanent store failed")
      permFile.getPath
    }
  }

  override def deleteTemp(tempName: String)(implicit ec: ExecutionContext): Unit = {
    future { new File(tempName).delete() }
    Unit
  }
}
