package com.bullhorn.filestore

import com.bullhorn.filestore.FileWriterActor.FileSignature
import spray.json.DefaultJsonProtocol

object Codec {

  case class StoredFile(
   name: String,
   contentType: String,
   dupe: Boolean,
   size: Long,
   signature: FileSignature)

  object FileStoreJsonProtocol extends DefaultJsonProtocol {
    implicit val fileSignatureFormat = jsonFormat1(FileSignature)
    implicit val storedFileFormat = jsonFormat5(StoredFile)
  }
}
