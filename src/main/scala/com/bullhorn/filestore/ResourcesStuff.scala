package com.bullhorn.filestore

import com.bullhorn.filestore.db.{FileDb, BerkeleyFileDb}
import com.bullhorn.filestore.storage.{StdIOFileStore, FileStore}

object ResourcesStuff {
  val db: FileDb = new BerkeleyFileDb
  val store: FileStore = new StdIOFileStore(db)
}
