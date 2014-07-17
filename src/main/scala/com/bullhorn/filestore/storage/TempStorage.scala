package com.bullhorn.filestore.storage

import java.util.UUID

import com.bullhorn.filestore.Config

class TempStorage {
  private def newTempFilePath = "%s/%s".format(Config.FileStore.tmpDir, UUID.randomUUID.toString)
  def newTempFile = new AsyncTempFile(newTempFilePath)
}
