/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva
package test

import org.scalatest._
import org.scalatest.OptionValues._

import java.io.{ ByteArrayInputStream, File, FileWriter }

case class TestDocAtt(_id: String, value: Int) extends IdRev with Attachments

class TestAttachments extends SohvaTestSpec with Matchers {

  implicit val testDocAttFormat = couchFormat[TestDocAtt]

  "a document with no attached file" should "have an empty attachment field" in {

    val doc = TestDocAtt("doc-with-attachments", 4)
    val saved = synced(db.saveDoc(doc))

    saved._attachments should be('empty)

  }

  "a document for which a file was attached" should "contain the attachment information" in {

    val saved = synced(db.getDocById[TestDocAtt]("doc-with-attachments"))

    val f = File.createTempFile("sohva", "test")
    val content = "this is the content"
    for (fw <- new FileWriter(f).autoClose)
      fw.write(content)

    val ok = synced(db.attachTo("doc-with-attachments", f, "text/plain; charset=UTF-8"))

    ok should be(true)

    val withAttachment = synced(db.getDocById[TestDocAtt]("doc-with-attachments"))
    withAttachment should be('defined)
    withAttachment.value._rev should not be (saved.value._rev)
    withAttachment.value._attachments should not be ('empty)
    withAttachment.value._attachments.get(f.getName) should be('defined)

    val attachment = withAttachment.value._attachments.get(f.getName).value
    attachment.content_type should be("text/plain; charset=UTF-8")
    attachment.revpos should be(2)
    attachment.length should be(content.length)
    attachment.stub should be(true)
  }

  "an attachment supplied by inputstream" should "have predictable ID" in {
    val doc = TestDocAtt("doc-with-stream-attachments", 5)
    val saved = synced(db.saveDoc(doc))
    saved._attachments should be('empty)

    val is = new ByteArrayInputStream("attachment-contents".getBytes("utf8"))

    synced(db.attachTo(doc._id, "attachment-id", is, "text/plain; charset=UTF-8"))
    val withAttachment = synced(db.getDocById[TestDocAtt](doc._id))
    withAttachment.value._attachments.headOption match {
      case Some(a) => a._1 should equal("attachment-id")
      case None    => fail("no attachment")
    }
  }

}

