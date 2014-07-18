package com.bullhorn.filestore.fs

import com.bullhorn.filestore.Config
import com.bullhorn.filestore.db.FileDb

import scala.concurrent.{ExecutionContext, Future}

abstract class FileStore {

  def withPermDir(fname: String) = "%s/%s/%s".format(Config.FileStore.baseDir, "files", fname)
  def formatPermFileName(id: Long) = "%010d".format(id)

  def moveToPerm(tempName: String, id: Long)(implicit ctx: ExecutionContext): Future[String]
  def deleteTemp(tempName: String)(implicit ctx: ExecutionContext): Unit
}
