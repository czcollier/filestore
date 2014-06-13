package com.bullhorn.filestore

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.nio.ByteBuffer
import java.util.UUID

import akka.actor._
import com.bullhorn.filestore.FileStore.FileRecord
import com.sleepycat.je.{Environment, EnvironmentConfig, SequenceConfig}
import com.sleepycat.persist.model.{SecondaryKey, Entity, PrimaryKey}
import com.sleepycat.persist.{EntityStore, StoreConfig}
import com.sleepycat.persist.model.Relationship.ONE_TO_ONE
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
  def newTempFile: File
  def finish(key: String, tmpFile: File)
  def getByID(id: Long): FileRecord
  def getBySignature(sig: String): FileRecord
}

class BDBStore extends FileStore {

  val baseDir = "/mnt/bigvol/filestore/store"
  val tmpDir  = "/mnt/bigvol/filestore/temp"

  val envConfig = EnvironmentConfig.DEFAULT
  envConfig.setAllowCreate(true)
  envConfig.setTransactional(true)
  val env = new Environment(new File("/mnt/bigvol/filestore/db"), envConfig)

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
    val txn = env.beginTransaction(null, null)
    primaryIndex.put(f)
    txn.commit()
    f.id
  }

  def withTmpDir(fname: String) = "%s/%s".format(tmpDir, fname)
  def IDToStorePath(id: Long) = "%s/%010d".format(baseDir, id)

  def newTempFile: File = {
    val name = UUIDFromLong(tmpSequence.get(null, 1))
    new File(withTmpDir(name))
  }

  private def UUIDFromLong(l: Long) = {
    val buf = ByteBuffer.allocate(java.lang.Long.SIZE / 8)
    buf.putLong(l)
    UUID.nameUUIDFromBytes(buf.array).toString
  }

  def getByID(id: Long) = primaryIndex.get(id)

  def getBySignature(sig: String) = keyIndex.get(sig)

  def finish(key: String, tmpFile: File) = {
    val dup = getBySignature(key)
    if (dup == null) {
      val id = primarySequence.get(null, 1)
      val storeFile = new File(IDToStorePath(id))
      println("storing new file to: " + storeFile)
      val renameSuccess = (tmpFile renameTo storeFile)
      println("rename success: " + renameSuccess)
      if (renameSuccess)
        put(new FileRecord(id, key))
    }
    else {
      println("dup found; deleting temp file")
      tmpFile.delete()
    }
  }

  def list = primaryIndex.map()
}

