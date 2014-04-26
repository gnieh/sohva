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
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.OptionValues._

import sync._

import java.io.{
  File,
  FileWriter
}

import resource._

class TestAttachments extends SohvaTestSpec with ShouldMatchers {

  case class TestDoc(_id: String, value: Int) extends IdRev with Attachments

  "a document with no attached file" should "have an empty attachment field" in {

    val doc = TestDoc("doc-with-attachments", 4)
    val saved = db.saveDoc(doc)

    saved._attachments should be('empty)

  }

  "a document for which a file was attached" should "contain the attachment information" in {

    val saved = db.getDocById[TestDoc]("doc-with-attachments")

    val f = File.createTempFile("sohva", "test")
    val content = "this is the content"
    for(fw <- managed(new FileWriter(f)))
      fw.write(content)

    val ok = db.attachTo("doc-with-attachments", f, "text/plain")

    ok should be(true)

    val withAttachment = db.getDocById[TestDoc]("doc-with-attachments")
    withAttachment should be('defined)
    withAttachment.value._rev should not be(saved.value._rev)
    withAttachment.value._attachments should not be('empty)
    withAttachment.value._attachments.get(f.getName) should be('defined)

    val attachment = withAttachment.value._attachments.get(f.getName).value
    attachment.content_type should be("text/plain")
    attachment.revpos should be(2)
    attachment.length should be(content.length)
    attachment.stub should be(true)

  }

}

