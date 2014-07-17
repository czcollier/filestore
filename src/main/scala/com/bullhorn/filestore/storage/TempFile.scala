package com.bullhorn.filestore.storage

import scala.concurrent.Future

trait TempFile {
  val path: String

  def write(data: Array[Byte]): Future[Int]
  def close(): Unit
}
