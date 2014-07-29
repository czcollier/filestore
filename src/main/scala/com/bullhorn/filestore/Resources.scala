package com.bullhorn.filestore

import com.bullhorn.filestore.db.{FileDb, BerkeleyFileDb}
import com.bullhorn.filestore.fs.nio.NIOFileStore
import com.bullhorn.filestore.fs.stdio.StdIOFileStore
import com.bullhorn.filestore.fs.{TempStorage, FileStore}

object Resources {
  val db: FileDb = new BerkeleyFileDb
  val store: FileStore = new NIOFileStore
  val tempStorage = new TempStorage
}
