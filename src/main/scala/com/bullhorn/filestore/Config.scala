package com.bullhorn.filestore

import com.typesafe.config.ConfigFactory

object Config {
  val KEY_PREFIX = "com.bullhorn.filestore"

  private def withKeyPrefix(n: String) = s"$KEY_PREFIX.$n"

  val config = ConfigFactory.load()

  object FileStore {
    val baseDir = config.getString(withKeyPrefix("store.basedir"))
    val tmpDir = config.getString(withKeyPrefix("store.tempdir"))
  }

  object SuspendingQueue {
    val suspendThreshold = config.getInt(withKeyPrefix("suspendingQueue.suspendThreshold"))
    val resumeThreshold = config.getInt(withKeyPrefix("suspendingQueue.resumeThreshold"))
  }
}
