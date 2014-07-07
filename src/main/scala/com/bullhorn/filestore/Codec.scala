package com.bullhorn.filestore

import com.bullhorn.filestore.StorageParentActor.FileSignature
import spray.httpx.unmarshalling.DeserializationError
import spray.json._

object Codec {

  case class StoredFile(
   name: String,
   contentType: String,
   duplicate: Boolean,
   size: Long,
   signature: FileSignature)

  object FileStoreJsonProtocol extends DefaultJsonProtocol {
    implicit val signatureFormat = new RootJsonFormat[FileSignature] {
      override def write(obj: FileSignature): JsValue = JsString(obj.v)
      override def read(json: JsValue): FileSignature = json match {
        case JsString(v) => FileSignature(v)
        case _ => throw new DeserializationException("FileSignature expected")
      }
    }
    implicit val storedFileFormat = jsonFormat5(StoredFile)
  }
}
