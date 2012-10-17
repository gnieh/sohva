Gnieh Sohva
===========

Introduction
------------

Gnieh Sohva is a simple scala library allowing users to simply work with a couchdb instance.

Basic Usage
-----------

First of all you should import all the stuffs to work with couchdb:
    import gnieh.sohva._

In Sohva, all starts with an instance of CouchDB which gives all the indication on the server location and port.

    val couch = CouchDB()

By default this gives a connection to a couchdb instance at localhost on port 5984 and with no authentication.
Of course, one can override these settings by passing parameters to the constructor. See the documentation of the CouchDB class.

Once you have a CouchDB instance, you can access to a database by using the `database` method:

    val database = couch.database("test")
    database.create()

Pretty easy, huh?

Working with documents is a piece of cake as well. Before continuing, one has to know a bit about couchdb and documents, but I am pretty sure you do, otherwise you wouldn't be here... However if anybody has no idea about couchdb, you should probably first have a look at the couchdb documentation first and then come back here!

So a couchdb document is a json object that *must* have at least one field named `_id` and containing the document id. In addition the class representing the object must have an optional field named `_rev` that will hold the document revision.

Let's say one want to save a test document into the above created database, the class representing this document may look like this:

    case class Test(_id: String, value: String)(val _rev: Option[String] = None)

So we defined a class that has three attributes `_id`, `value` and `_rev`. I used here two parameter lists to separate the parameters the user has to specify when creating a new document of this type from the ones having default values. It is not mandatory to do so, but that's a convention I used in this project...

Then instantiate a test object and save it to the database:

    val test = Test("test1", "this is a test")()
    database.saveDoc(test)()

One can then retrieve the saved document by using the `getDocById` method:

    val fromDb = database.getDocById[Test]("test1")()

That's it!

Oh, wait! and what if one wants to use designs and views? Well, the Database class gives access to designs and designs gives access to view. In Sohva, views are typed so the user has to specify the different types used in the view to work with. Be careful though because no check is performed to check that the types are correct with what is saved into the database, so you must ensure that your types are right to avoid weird exceptions! Three types have to be specified for a view:
 - Key: which is the key type used to query this view. It may be any type that will be serialized to a JSON object,
 - Value: which is the type of the value returned by this view,
 - Doc: which is the type of the associated document that is returned in the case where the view is queried with the `include_docs` option.

Shutting Down
-------------

So you played with your couchdb client, stored and retrieved a lot of documents from your couchdb instance. That's good! But don't forget to shut down every couchdb client instance you created once you do not need it anymore. Each instance starts background threads that must be stopped once they became useless. To do this, it is once more pretty easy, just run:

    couch.shutdown

And voilà!

Getting Asynchronous
--------------------

Did you noticed so far that every call to methods that query the database is postfixed with an extra `()`?
Well, if not, don't worry we will explain why in a few seconds.

Actually, by default, all the methods that send request to the database server do this in an asynchronous way and returns immediately. So _what if this method returns a document fetched from the server?_ you'll ask. Well, these methods return a `Promise` object that encapsulates the value that will be eventually returned in response to the query. Sohva is based on the http library [Dispatch](http://dispatch.databinder.net/Dispatch.html), so to understand what the `Promise` object does, take a look at the documentation of this project.

The extra `()` at the end of the method call makes this call block until the response is available and unpack it from the `Promise` object. Using it may looks easier when starting to work with Sohva, but actually, once you understood the power of `Promise`s, you will probably continue using Sohva without these parentheses...

User Management
---------------

*TODO*

Documentation
-------------

The ScalaDoc can be found there: http://www.gnieh.org/sohva/api/
