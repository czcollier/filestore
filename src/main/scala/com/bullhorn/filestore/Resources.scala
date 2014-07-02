package com.bullhorn.filestore

object Resources {
  val db = FileDb()
  val store: FileStore = new FileStore(db)
}
