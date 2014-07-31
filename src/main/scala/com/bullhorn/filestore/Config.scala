package com.bullhorn.filestore

import com.typesafe.config.ConfigFactory

object Config {
  val KEY_PREFIX = "com.bullhorn.filestore"

  private def withKeyPrefix(n: String) = s"$KEY_PREFIX.$n"

  lazy val config = ConfigFactory.load()

  object FileStore {
    def baseDir = config.getString(withKeyPrefix("store.basedir"))
    def tmpDir = config.getString(withKeyPrefix("store.tempdir"))
  }

  object SuspendingQueue {
    def suspendThreshold = config.getInt(withKeyPrefix("suspendingQueue.suspendThreshold"))
    def resumeThreshold = config.getInt(withKeyPrefix("suspendingQueue.resumeThreshold"))
  }
}
