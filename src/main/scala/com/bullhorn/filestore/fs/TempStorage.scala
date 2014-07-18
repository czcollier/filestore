package com.bullhorn.filestore.fs

import java.util.UUID

import com.bullhorn.filestore.Config
import com.bullhorn.filestore.fs.nio.AsyncTempFile

import scala.concurrent.ExecutionContext

class TempStorage {
  private def newTempFilePath = "%s/%s".format(Config.FileStore.tmpDir, UUID.randomUUID.toString)
  def newTempFile(implicit ec: ExecutionContext) = new AsyncTempFile(newTempFilePath)(ec)
}
