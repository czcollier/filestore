package com.bullhorn.filestore

import java.io.File
import com.typesafe.config.ConfigFactory
import scala.concurrent._
import scala.concurrent.{ExecutionContext, Future}

class FileStore(db: FileDb) {

  val config = ConfigFactory.load()

  val baseDir = config.getString("com.bullhorn.filestore.store.basedir")
  val tmpDir = config.getString("com.bullhorn.filestore.store.tempdir")

  private def withTempDir(fname: String) = "%s/%s".format(tmpDir, fname)
  private def withPermDir(fname: String) = "%s/%s/%s".format(baseDir, "files", fname)

  private def formatPermFileName(id: Long) = "%010d".format(id)

  def newTempFile: String = withTempDir(db.newTempFileId)

  def moveToPerm(tempName: String, id: Long)(implicit ec: ExecutionContext): Future[String] = {
    val permFile = new File(withPermDir(formatPermFileName(id)))
    val tempFile = new File(tempName)
    println("moving temp file %s to permanent location: %s".format(tempFile.getPath, permFile.getPath))
    Future {
      (tempFile renameTo permFile)
    } map {  res =>
      if (!res)
        throw new RuntimeException("could not store file move to permanent store failed")
      permFile.getPath
    }
  }

  def deleteTempFile(tempName: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    future { new File(tempName).delete() }
  }
}

