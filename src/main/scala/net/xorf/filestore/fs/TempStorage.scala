package net.xorf.filestore.fs

import java.util.UUID

import net.xorf.filestore.Config
import net.xorf.filestore.fs.nio.AsyncTempFile

import scala.concurrent.ExecutionContext

class TempStorage {
  private def newTempFilePath = "%s/%s".format(Config.FileStore.tmpDir, UUID.randomUUID.toString)
  def newTempFile(implicit ec: ExecutionContext) = new AsyncTempFile(newTempFilePath)(ec)
}
