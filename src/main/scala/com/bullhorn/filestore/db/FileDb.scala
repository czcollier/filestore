package com.bullhorn.filestore.db

object FileDb {

  trait FileRecord {
    val signature: String
    val key: String
  }

  val instance = new BerkeleyFileDb
  def apply() = instance
}

trait FileDb {
  import FileDb._

  def newTempFileId: String
  def finish(signature: String): Option[String]
  def getByID(id: String): Option[FileRecord]
  def getBySignature(sig: String): Option[FileRecord]
}




