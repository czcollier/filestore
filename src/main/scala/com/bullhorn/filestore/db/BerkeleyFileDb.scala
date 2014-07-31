package com.bullhorn.filestore.db

import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

import com.bullhorn.filestore.db.FileDb.FileRecord
import com.google.common.base.Stopwatch
import com.sleepycat.je._
import com.sleepycat.persist.model.Relationship.ONE_TO_ONE
import com.sleepycat.persist.model.{Entity, PrimaryKey, SecondaryKey}
import com.sleepycat.persist.{EntityStore, StoreConfig}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory


class BerkeleyFileDb extends FileDb {

  import com.bullhorn.filestore.db.BerkeleyFileDb._

  val config = ConfigFactory.load()

  def baseDir = config.getString("com.bullhorn.filestore.store.basedir")
  def tmpDir = config.getString("com.bullhorn.filestore.store.tempdir")

  val envConfig = EnvironmentConfig.DEFAULT
  envConfig.setAllowCreate(true)
  envConfig.setTransactional(true)
  envConfig.setTxnSerializableIsolation(true)

  val env = new Environment(new File("%s/db".format(baseDir)), envConfig)

  val storeConfig = StoreConfig.DEFAULT
  storeConfig.setAllowCreate(true)
  storeConfig.setTransactional(true)
  val store = new EntityStore(env, "files", storeConfig)

  val primaryIndex = store.getPrimaryIndex(classOf[String], classOf[BdbFileRecord])
  val keyIndex = store.getSecondaryIndex(primaryIndex, classOf[String], "key")

  store.setSequenceConfig("tmp", SequenceConfig.DEFAULT)
  store.getSequenceConfig("tmp").setAllowCreate(true)
  val tmpSequence = store.getSequence("tmp")


  def newTempFileId: String = UUIDFromLong(tmpSequence.get(null, 1))

  private def UUIDFromLong(l: Long) = {
    val buf = ByteBuffer.allocate(java.lang.Long.SIZE / 8)
    buf.putLong(l)
    UUID.nameUUIDFromBytes(buf.array).toString
  }

  def getByID(id: String) = Option(keyIndex.get(id))

  def getBySignature(sig: String): Option[FileRecord] = {
    val rec = primaryIndex.get(sig)
    Option(rec)
  }

  //private val writeLock = new Object()

  def finish(sig: String): Option[String] = {
    val timer = Stopwatch.createStarted
    //Why do I need to synchronize here?  I thought BDB transactions
    //combinded with RMW lock mode would sequence all reads and writes
    //but if I do not synchronize here, I get a race condition whereby
    //duplicate keyIndex values are generated.
      val txn = env.beginTransaction(null, null)
      try {
        val key = UUID.randomUUID().toString
        val ret = primaryIndex.putNoOverwrite(txn, new BdbFileRecord(sig, key)) match {
          case true => Some(key)
          case false => None
        }
        txn.commit()
        log.debug("finish took: %s".format(timer))
        ret
      }
      catch {
        case e: Throwable =>
          txn.abort()
          throw e
      }
    }
  }

object BerkeleyFileDb {
  lazy val log = LoggerFactory.getLogger(classOf[BerkeleyFileDb])

  @Entity
  class BdbFileRecord(sigParm: String, keyParm: String) extends FileRecord {
    @PrimaryKey
    val signature: String = sigParm

    @SecondaryKey(relate = ONE_TO_ONE)
    val key: String = keyParm

    def this() = this(null, null)

    def this(sig: String) = this(null, sig)
  }
}