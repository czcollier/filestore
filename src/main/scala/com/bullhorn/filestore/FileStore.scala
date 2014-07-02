package com.bullhorn.filestore

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.nio.ByteBuffer
import java.util.UUID

import akka.actor._
import com.bullhorn.filestore.FileStore.FileRecord
import com.google.common.base.Stopwatch
import com.sleepycat.je.{Environment, EnvironmentConfig, SequenceConfig}
import com.sleepycat.persist.model.{SecondaryKey, Entity, PrimaryKey}
import com.sleepycat.persist.{EntityStore, StoreConfig}
import com.sleepycat.persist.model.Relationship.ONE_TO_ONE
import com.typesafe.config.ConfigFactory
import spray.http.HttpData

object FileStore {

  case object GetWriter

  case class Writer(actor: ActorRef)

  @Entity
  class FileRecord(idParm: Long, sigParm: String) {
    @PrimaryKey
    val id: Long = idParm

    @SecondaryKey(relate=ONE_TO_ONE)
    val signature: String = sigParm

    def this() = this(0, null)
  }
}

trait FileStore {
  def newTempFile: String
  def finish(key: String, tmpFile: File): Boolean
  def getByID(id: Long): Option[FileRecord]
  def getBySignature(sig: String): Option[FileRecord]
}

class BDBStore extends FileStore {
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

  def put(f: FileRecord) = {
    //val txn = env.beginTransaction(null, null)
    primaryIndex.put(f)
    //txn.commit()
    f.id
  }

  def withTmpDir(fname: String) = "%s/%s".format(tmpDir, fname)
  def IDToStorePath(id: Long) = "%s/%s/%010d".format(baseDir, "files", id)

  def newTempFile: String = {
    val name = UUIDFromLong(tmpSequence.get(null, 1))
    withTmpDir(name)
  }

  private def UUIDFromLong(l: Long) = {
    val buf = ByteBuffer.allocate(java.lang.Long.SIZE / 8)
    buf.putLong(l)
    UUID.nameUUIDFromBytes(buf.array).toString
  }

  def getByID(id: Long) = Option(primaryIndex.get(id))

  def getBySignature(sig: String): Option[FileRecord] = {
    //val timer = Stopwatch.createStarted
    val rec = keyIndex.get(sig)
    Option(rec)
  }

  def storeFile(key: String, tmpFile: File) = {
    val timer = Stopwatch.createStarted
    val id = primarySequence.get(null, 1)
    println(" ===> getKey: %s".format(timer))
    timer.reset;timer.start
    val storeFile = new File(IDToStorePath(id))
    println(" ===> makeFile: %s".format(timer))
    timer.reset;timer.start
    val renameSuccess = (tmpFile renameTo storeFile)
    println(" ===> rename: %s".format(timer))
    timer.reset
    if (renameSuccess) {
      timer.start
      put(new FileRecord(id, key))
      println(" ===> put: %s".format(timer))
      timer.stop
    }
    else
      throw new RuntimeException("could not store file move to permanent store failed")
  }

  def finish(key: String, tmpFile: File): Boolean = {
    getBySignature(key) match {
      case Some(f) =>
        tmpFile.delete(); true
      case None =>
        storeFile(key, tmpFile); false
    }
  }

  def list = primaryIndex.map()
}

