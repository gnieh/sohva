---
layout: default
title: An asynchronous CouchDB client library for Scala
noToc: true
---

Sohva is an asynchronous <a href="http://couchdb.apache.org">CouchDB</a> client library for Scala. It is based on [akka](http://akka.io/) and its [http module](http://doc.akka.io/docs/akka/2.4/scala/http/index.html).
Sohva can be used to access data in CouchDB databases from version 1.4 onward. Some features are only available in CouhDB 2.0 (such as [mango queries](mango/)) and will result in runtime errors if you use them with an earlier version of CouhDB.

# Installation

To use Sohva in your code, add it to your dependencies in your build.

```scala
// sbt
libraryDependencies += "org.gnieh" %% "sohva" % "2.0.0"
```

```xml
<!-- maven -->
<dependency>
  <groupId>org.gnieh</groupId>
  <artifactId>sohva_${scala.version}</artifactId>
  <version>2.0.0</version>
</dependency>
```

Sohva is built for scala 2.11.

# Next steps

Once your project is linked to Sohva, you can start using it. Please have a look at the links below for documentation.

 - [Basic usage](basic/)
 - [Authentication in CouchDB](sessions/)
 - [Automatic conflict handling](conflicts/)
 - [Mango queries](mango/)
 - [Being notified of changes in database](changes/)
 - [Using sohva synchronously](synchronous/)
 - [API documentation](latest/api/)
