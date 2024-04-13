package net.xorf.filestore

import net.xorf.filestore.db.{FileDb, BerkeleyFileDb}
import net.xorf.filestore.fs.nio.NIOFileStore
import net.xorf.filestore.fs.{TempStorage, FileStore}

object Resources {
  println("loading resources...")
  val db: FileDb = new BerkeleyFileDb
  val store: FileStore = new NIOFileStore
  val tempStorage = new TempStorage
  println("done")
}
