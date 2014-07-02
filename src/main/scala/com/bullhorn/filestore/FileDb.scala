package com.bullhorn.filestore

import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

import com.bullhorn.filestore.FileDb.FileRecord
import com.google.common.base.Stopwatch
import com.sleepycat.je._
import com.sleepycat.persist.model.{SecondaryKey, PrimaryKey, Entity}
import com.sleepycat.persist.model.Relationship.ONE_TO_ONE
import com.sleepycat.persist.{EntityStore, StoreConfig}
import com.typesafe.config.ConfigFactory


object FileDb {
  @Entity
  class FileRecord(idParm: Long, sigParm: String) {
    @PrimaryKey(sequence="pk")
    val id: Long = idParm

    @SecondaryKey(relate=ONE_TO_ONE)
    val signature: String = sigParm

    def this() = this(0, null)
    def this(sig: String) = this(0, sig)
  }

  val instance = new BerkeleyFileDb
  def apply() = instance
}

trait FileDb {
  def newTempFileId: String
  def finish(signature: String): Option[Long]
  def getByID(id: Long): Option[FileRecord]
  def getBySignature(sig: String): Option[FileRecord]
}

class BerkeleyFileDb extends FileDb {
  val config = ConfigFactory.load()

  val baseDir = config.getString("com.bullhorn.filestore.store.basedir")
  val tmpDir = config.getString("com.bullhorn.filestore.store.tempdir")

  val envConfig = EnvironmentConfig.DEFAULT
  envConfig.setAllowCreate(true)
  envConfig.setTransactional(true)
  val env = new Environment(new File("%s/db".format(baseDir)), envConfig)

  val storeConfig = StoreConfig.DEFAULT
  storeConfig.setAllowCreate(true)
  storeConfig.setTransactional(true)
  val store = new EntityStore(env, "files", storeConfig)

  val primaryIndex = store.getPrimaryIndex(classOf[java.lang.Long], classOf[FileRecord])
  val keyIndex = store.getSecondaryIndex(primaryIndex, classOf[String], "signature")

  store.setSequenceConfig("pk", SequenceConfig.DEFAULT)
  store.getSequenceConfig("pk").setAllowCreate(true)
  val primarySequence = store.getSequence("pk")

  store.setSequenceConfig("tmp", SequenceConfig.DEFAULT)
  store.getSequenceConfig("tmp").setAllowCreate(true)
  val tmpSequence = store.getSequence("tmp")

  def newTempFileId: String = UUIDFromLong(tmpSequence.get(null, 1))

  private def UUIDFromLong(l: Long) = {
    val buf = ByteBuffer.allocate(java.lang.Long.SIZE / 8)
    buf.putLong(l)
    UUID.nameUUIDFromBytes(buf.array).toString
  }

  def getByID(id: Long) = Option(primaryIndex.get(id))

  def getBySignature(sig: String): Option[FileRecord] = {
    val rec = keyIndex.get(sig)
    Option(rec)
  }

  def finish(signature: String): Option[Long] = {
    val timer = Stopwatch.createStarted
    implicit val txn = env.beginTransaction(null, null)
    try {
      val ret = Option(keyIndex.get(txn, signature, LockMode.READ_UNCOMMITTED)) match {
        case Some(f) => None
        case None => {
          val rec = new FileRecord(signature)
          primaryIndex.put(txn, rec)
          txn.commit()
          Some(rec.id)
        }
      }
      println("finish took: %s".format(timer))
      ret
    }
    catch {
      case e: Exception => txn.abort()
      throw e
    }
  }
}
