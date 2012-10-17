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
    database.create

Pretty easy, huh?

Working with documents is a piece of cake as well. Before continuing, one has to know a bit about couchdb and documents, but I am pretty sure you do, otherwise you wouldn't be here... However if anybody has no idea about couchdb, you should probably first have a look at the couchdb documentation first and then come back here!

So a couchdb document is a json object that *must* have at least one field named `_id` and containing the document id. In addition the class representing the object must have an optional field named `_rev` that will hold the document revision.

Let's say one want to save a test document into the above created database, the class representing this document may look like this:

    case class Test(_id: String, value: String)(val _rev: Option[String] = None)

So we defined a class that has three attributes `_id`, `value` and `_rev`. I used here two parameter lists to separate the parameters the user has to specify when creating a new document of this type from the ones having default values. It is not mandatory to do so, but that's a convention I used in this project...

Then instantiate a test object and save it to the database:

    val test = Test("test1", "this is a test")()
    database.saveDoc(test)

One can then retrieve the saved document by using the `getDocById` method:

    val fromDb = database.getDocById[Test]("test1")

That's it!

Oh, wait! and what if one wants to use designs and views? Well, the Database class gives access to designs and designs gives access to view. In Sohva, views are typed so the user has to specify the different types used in the view to work with. Be careful though because no check is performed to check that the types are correct with what is saved into the database, so you must ensure that your types are right to avoid weird exceptions! Three types have to be specified for a view:
 - Key: which is the key type used to query this view. It may be any type that will be serialized to a JSON object,
 - Value: which is the type of the value returned by this view,
 - Doc: which is the type of the associated document that is returned in the case where the view is queried with the `include_docs` option.

Documentation
-------------

The ScalaDoc can be found there: http://www.gnieh.org/sohva/old/api/
