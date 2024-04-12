package net.xorf.filestore.fs.stdio

import java.io.File

import net.xorf.filestore.fs.FileStore
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future, future}

class StdIOFileStore extends FileStore {

  import StdIOFileStore._

  override def moveToPerm(tempName: String, id: String)(implicit ec: ExecutionContext): Future[String] = {
    val permFile = new File(withPermDir(formatPermFileName(id)))
    val tempFile = new File(tempName)
    log.debug("moving temp file %s to permanent location: %s".format(tempFile.getPath, permFile.getPath))
    Future {
      (tempFile renameTo permFile)
    } map {  res =>
      if (!res)
        throw new RuntimeException("could not store file move to permanent store failed")
      permFile.getPath
    }
  }

  override def deleteTemp(tempName: String)(implicit ec: ExecutionContext): Unit = {
    Future { new File(tempName).delete() }
    Unit
  }
}
object StdIOFileStore {
  lazy val log = LoggerFactory.getLogger(classOf[StdIOFileStore])
}
