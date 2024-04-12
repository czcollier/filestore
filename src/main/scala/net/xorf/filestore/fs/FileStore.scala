package net.xorf.filestore.fs

import net.xorf.filestore.Config
import net.xorf.filestore.db.FileDb

import scala.concurrent.{ExecutionContext, Future}

abstract class FileStore {

  def withPermDir(fname: String) = "%s/%s/%s".format(Config.FileStore.baseDir, "files", fname)
  def formatPermFileName(id: String) = "%s".format(id)

  def moveToPerm(tempName: String, id: String)(implicit ctx: ExecutionContext): Future[String]
  def deleteTemp(tempName: String)(implicit ctx: ExecutionContext): Unit
}
