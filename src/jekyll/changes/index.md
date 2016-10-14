---
layout: default
title: Being notified about changes
---

It is possible to be notified about changes that occur in CouchDB databases.

In Sohva, change stream is implemented using [akka streams](http://doc.akka.io/docs/akka/2.4/scala/stream/index.html).
Once materialized, the streams provided by Sohva can return a `UniqueKillSwitch` to close the streams.

Database updates
----------------

Given a `CouchClient` or a session, you can be notified about updates in databases

```scala
val stream = couch.dbUpdates()
```

you can provide a timeout and a heartbeat to this method.

Database changes
----------------

A more interesting change stream is the one on databases, allowing to be notified about all document updates.

```scala
val stream = db.changes.stream()
// print on stdout all the events
val killSwitch = stream.toMat(Sink.foreach(println _))(Keep.left).run()
// wait a while and close the stream
killSwitch.shutdown()
```

This method takes several parameters to reflect the underlying CouchDB API, refer to [the documentation](/latest/api/index.html#gnieh.sohva.ChangeStream) for details.
