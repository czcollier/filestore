package com.bullhorn.filestore

import java.io.File
import com.typesafe.config.ConfigFactory

class FileStore(db: FileDb) {

  val config = ConfigFactory.load()

  val baseDir = config.getString("com.bullhorn.filestore.store.basedir")
  val tmpDir = config.getString("com.bullhorn.filestore.store.tempdir")

  private def withTempDir(fname: String) = "%s/%s".format(tmpDir, fname)
  private def withPermDir(fname: String) = "%s/%s/%s".format(baseDir, "files", fname)

  private def formatPermFileName(id: Long) = "%010d".format(id)

  def newTempFile: String = withTempDir(db.newTempFileId)

  def storeFile(idOption: Option[Long], tmpFile: File): Boolean = {
    idOption match {
      case None => tmpFile.delete(); true
      case Some(id) =>
        val path = withPermDir(formatPermFileName(id))
        println("storing file at: %s".format(path))

        val storeFile = new File(path)
        val renameSuccess = (tmpFile renameTo storeFile)
        if (!renameSuccess)
          throw new RuntimeException("could not store file move to permanent store failed")
        false
    }
  }
}

