package com.bullhorn.filestore.storage

import com.bullhorn.filestore.Config

import scala.concurrent.{ExecutionContext, Future}

trait FileStore {

  def withTempDir(fname: String) = "%s/%s".format(Config.tmpDir, fname)
  def withPermDir(fname: String) = "%s/%s/%s".format(Config.baseDir, "files", fname)
  def formatPermFileName(id: Long) = "%010d".format(id)

  def newTempFile: String
  def moveToPerm(tempName: String, id: Long)(implicit ctx: ExecutionContext): Future[String]
  def deleteTemp(tempName: String)(implicit ctx: ExecutionContext): Unit
}
