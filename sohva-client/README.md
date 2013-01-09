Gnieh Sohva
===========

Introduction
------------

Gnieh Sohva is a simple scala library allowing users to simply work with a couchdb instance.

Download
--------

To use sohva in your project, if you are using maven, just add the following dependency to your pom.xml file:

```xml
<dependency>
  <groupId>org.gnieh</groupId>
  <artifactId>sohva_2.9.2</artifactId>
  <version>0.1</version>
</dependency>
```

If you are using sbt, simply add the following line to your project descriptor:

```scala
libraryDependencies += "org.gnieh" % "sohva_2.9.2" % "0.1"
```

A Snapshot version is published in the Maven Central Snapshot repository. Add it to your resolver or repositories and you can use the version 0.2-SNAPSHOT of the module sohva-client (for Scala 2.9.2 only at this time)

Basic Usage
-----------

First of all you should import all the stuffs to work with couchdb:

```scala
import gnieh.sohva._
```

In Sohva, all starts with an instance of `CouchClient` which gives all the indication on the server location and port.

```scala
val couch = new CouchClient
```

By default this gives a connection to a couchdb instance at localhost on port 5984.
Of course, one can override these settings by passing parameters to the constructor. See the documentation of the `CouchClient` class.

Once you have a `CouchClient` instance, you can access a database by using the `database` method:

```scala
val database = couch.database("test")
database.create!
```

Pretty easy, huh?

Working with documents is a piece of cake as well. Before continuing, one has to know a bit about couchdb and documents, but I am pretty sure you do, otherwise you wouldn't be here... However if anybody has no idea about couchdb, you should probably have a look at the couchdb documentation first and then come back here!

So a couchdb document is a json object that **must** have at least one field named `_id` and containing the document id. In addition the class representing the object must have an optional field named `_rev` that will hold the document revision.

Let's say one wants to save a test document into the above created database, the class representing this document may look like this:

```scala
case class Test(_id: String, value: String)(val _rev: Option[String] = None)
```

So we defined a class that has three attributes `_id`, `value` and `_rev`. we used here two parameter lists to separate the parameters the user has to specify when creating a new document of this type from the ones having default values. It is not mandatory to do so, but that's a convention we used in this project.

Then instantiate a test object and save it to the database:

```scala
val test = Test("test1", "this is a test")()
database.saveDoc(test)!
```

One can then retrieve the saved document by using the `getDocById` method:

```scala
val fromDb = database.getDocById[Test]("test1")!
```

That's it!

Oh, wait! What if one wants to use designs and views? Well, the `Database` class gives access to designs and design gives access to view. In Sohva, views are typed so the user has to specify the different types used in the view to work with. Be careful though, because no check is performed to ensure that the types are correct with respect to what is saved in the database, so you must ensure that your types are right to avoid weird exceptions! Three types have to be specified for a view:
 - `Key`: which is the key type used to query this view. It may be any type that will be serialized to a JSON object,
 - `Value`: which is the type of the value returned by this view,
 - `Doc`: which is the type of the associated document that is returned in the case where the view is queried with the `include_docs` option.

Shutting Down
-------------

So you played with your couchdb client, stored and retrieved a lot of documents into and from your couchdb instance. That's good! But don't forget to shut down every couchdb client instance you created once you do not need it anymore. Each instance starts background threads that must be stopped once they become useless. To do this, it is, once more, pretty easy, you just have to run:

```scala
couch.shutdown
```

And voilà!

Getting Asynchronous
--------------------

Did you noticed so far that every call to a method that queries the database is postfixed with an extra `!`?
Well, if not, don't worry we will explain why in a few seconds.

Actually, by default, all the methods that send request to the database server do this in an asynchronous way and return immediately. So _what if this method returns a document fetched from the server?_ you'll ask. Well, these methods return a `Promise` object that encapsulates the value that will be eventually returned in response to the query. Sohva is based on the http library [Dispatch](http://dispatch.databinder.net/Dispatch.html), so to understand what the `Promise` object does, take a look at the documentation of this project.

The extra `!` at the end of the method calls makes these calls block until the response is available to then unpack it from the `Promise` object and return it. Using blocking calls may look easier when starting to work with Sohva, but actually, once you understood the power of `Promise`s, you will probably continue using Sohva without these bangs.

Working With Sessions, Authentication and Friends
-------------------------------------------------

One other nice feature of couchdb, is that it provides user management, authentication and authorization out of the box.
So why would you rebuild an entirely new user management system for your applicaiton if your database already provides all you need?
That is why Sohva provides a simple way to manage users and sessions so you can benefit from it directly.

The `CouchClient` provides user management methods in an object called `users`

```scala
val created = couch.users.add("username", "password")!
```

This call will create a new user with the given `username` and `password`.

Until now, all the queries we sent to the couchdb instance were anonymous. If you want to start sending authenticated requests you will need to start a session

```scala
val session = couch.startSession
```

The `CouchSession` object returned exposes merely the same interface as `CouchClient` plus some methods to login, logout, test login status, current user, current roles, ... All the queries sent from a `CouchSession` belong to the same session, and are authenticated (if you logged in of course). To login, simply run

```scala
session.login("username", "password")!
```

From now on (and as long as you do not logout or the session does not expire), all database accesses using the `session` object are authenticated as originating from user `username`.

```scala
val authTest = session.database("test")!
authTest.saveDoc(Test("test2", "this is another test document")())!
```

Documentation
-------------

The ScalaDoc can be found there: http://www.gnieh.org/sohva/old/api/
