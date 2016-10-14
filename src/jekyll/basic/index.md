---
layout: default
title: Sohva - Getting Started
---

Instantiating a client
----------------------

The basic class in Sohva is the [CouchClient](/latest/api/#gnieh.sohva.CouchClient) class. It gives access to features related to the CouchDB instance.

Because sohva is based on akka, it needs to have an actor system implicitly available in scope, along with a timeout, so that futures can be scheduled with timeout.

```scala
import gnieh.sohva._
import akka.actor.ActorSystem
import akka.util.Timeout
import scala.concurrent.duration._

implicit val system = ActorSystem("sohva-system")
implicit val timeout = Timeout(5.seconds)

val couch = new CouchClient
```

The constructors takes several parameters with default values, for more information refer to [the class documentation](/latest/api/#gnieh.sohva.CouchClient).

Once you have a client, you can start working with Sohva. You can get the database information, UUIDs, etc.

Access to databases
-------------------

If you use Sohva it is probably because you want access to databases. The CouchDB client allows you to get a database representation by using the `database` method which returns a [Database](/latest/api/index.html#gnieh.sohva.Database) object.

```scala
val db = couch.database("db_name")
```

The documents
-------------

This `Database` object makes it possible to work with documents (I am sure you are aware that CouchDB is a document oriented database).
As an example, let’s define the usual `Post` class representing a blog post, and describe the mechanics necessary to store them in CouchDB with Sohva.

First define a basic case class representing this document.

```scala
import java.util.Date

case class Post(_id: String, var title: String, content: String, author: String, date: Date, tags: Vector[String])  extends IdRev
```

The `IdRev` trait gives you the mechanics to automatically handle revisions. It is not required to extend this trait, you can define your own case class with your custom id and rev fields. The idea is that a document saved into and retrieved from a CouchDB database must have a typeclass of type [CouchFormat](/latest/api/index.html#gnieh.sohva.CouchFormat).

This typeclass allows Sohva to serialize and deserialize documents into json and to manage the special identifier and revision fields for documents. It is based upon `JsonFormat` from [spray-json](https://github.com/spray/spray-json).

If your document class extends the `IdRev` trait, you use the macro `couchFormat` provided by the [SohvaProtocol](/latest/api/index.html#gnieh.sohva.SohvaProtocol).

```scala
import SohvaProtocol._

implicit val postFormat = couchFormat[Post]
```

If your class do not extend the `IdRev` trait or you want to have custom format for it, you can use the [couchFormatF](/latest/api/index.html#gnieh.sohva.SohvaProtocol@couchFormatF[T](id:T=>String,rev:T=>Option[String],withRevF:(T,Option[String])=>T)(implicitevidence$1:spray.json.JsonFormat[T]):gnieh.sohva.CouchFormat[T]) method from the protocol, which requires you to provide the `JsonFormat` to use to (de)serialize the document and the way to access, the identifier, the revision, and to get a copy of the document with the revision changed.

Attachments
-----------

If your document must deal with attachments, extend the [Attachments](/latest/api/index.html#gnieh.sohva.Attachments) trait.

Basic document actions
----------------------

The database object we obtained previously exposes all the methods required to save, update, retrieve and delete objects from a CouchDB database.
Basic examples of these operations are:

```scala

val post1 = Post("post1", "My First Post", "This is my very first post", "lucas", new Date, Vector("test", "post"))
val post2 = Post("post2", "My Second Post", "I have nothing more to say", "lucas", new Date, Vector("post"))

for {
  savedPost1 <- db.saveDoc(post1)
  savedPost2 <- db.saveDoc(post2)
  p1 <- db.getDocById[Post]("post1")
  _ <- db.deleteDoc(savedPost2)
} println("I am done testing")
```

Refer to the [class documentation](/latest/api/index.html#gnieh.sohva.Database) for more details.

Designs and views
-----------------

Finally, another notable feature of CouchDB is the designs and views. From a database representation in sohva you may easily access designs through the `design` method.

```scala
val design = db.design("my_design")
```

The returned [Design](/latest/api/index.html#gnieh.sohva.Design) class exposes methods to create and manages views, updates, filters, rewrites, and list.

The views are of particular interest in Sohva, as they make it possible to query aggregated documents from the database.

```scala
val view = design.view("my_view")

view.query[Key, Value, Post](keys = List("post1", "post2"))
```

The `query` method takes a lot of different parameters, matching the feature in CouchDB. For more details, refer to the [documentation](/latest/api/index.html#gnieh.sohva.View@query[Key,Value,Doc](key:Option[Key],keys:List[Key],startkey:Option[Key],startkey_docid:Option[String],endkey:Option[Key],endkey_docid:Option[String],limit:Int,stale:Option[String],descending:Boolean,skip:Int,group:Boolean,group_level:Int,reduce:Boolean,include_docs:Boolean,inclusive_end:Boolean,update_seq:Boolean)(implicitevidence$1:spray.json.JsonFormat[Key],implicitevidence$2:spray.json.JsonReader[Value],implicitevidence$3:spray.json.JsonReader[Doc]):scala.concurrent.Future[gnieh.sohva.ViewResult[Key,Value,Doc]])
