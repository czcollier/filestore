package com.bullhorn.filestore.db

object FileDb {

  trait FileRecord {
    val id: Long
    val signature: String
  }

  val instance = new BerkeleyFileDb
  def apply() = instance
}

trait FileDb {
  import FileDb._

  def newTempFileId: String
  def finish(signature: String): Option[Long]
  def getByID(id: Long): Option[FileRecord]
  def getBySignature(sig: String): Option[FileRecord]
}




