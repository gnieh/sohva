---
layout: default
title: Sessions
---

You may want to authenticate with the CouchDB server, if it requires it. Sohva supports two authentication methods from CouchDB:

 1. basic authentication
 2. OAuth 1

The `CouchClient` class has methods to start sessions with either of these methods. A [Session](/latest/api/index.html#gnieh.sohva.Session) is basically the same as `CouchClient` except that all issued requests will include proper credentials so that the server can authenticate your requests.

Once you get a session object, instead of using the basic `couch` object from [Basic usage](/basic/), use the session to access the databases.

Basic authentication
--------------------

To start a basic authentication session, use the `startBasicSession` method.

```scala
val session = couch.startBasicSession("username", "password")

val db = session.database("test")
```

OAuth authentication
--------------------

To start an OAuth session, use the `startOAuthSession` method.

```scala
val session = couch.startOAuthSession("consumer_key", "consumer_secret", "token", "secret")

val db = session.database("test")
```
