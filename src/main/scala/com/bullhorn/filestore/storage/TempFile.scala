package com.bullhorn.filestore.storage

import scala.concurrent.Future

/**
 * Created by ccollier on 7/11/14.
 */
trait TempFile {
  def write(data: Array[Byte]): Future[Int]
  def close(): Unit
}
