package com.bullhorn.filestore

import com.typesafe.config.ConfigFactory

object Config {
  val KEY_PREFIX = "com.bullhorn.filestore"

  private def withKeyPrefix(n: String) = s"$KEY_PREFIX.$n"

  val config = ConfigFactory.load()

  val baseDir = config.getString(withKeyPrefix("store.basedir"))
  val tmpDir = config.getString(withKeyPrefix("store.tempdir"))
}
