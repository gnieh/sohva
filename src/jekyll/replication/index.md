---
layout: default
title: Database Replication
---

CouchDB provides a simple to use and efficient master-to-master replication protocol.

In Sohva you can use this feature using the `replicator`.

```scala
val replicator = couch.replicator
```

The [Replicator](/latest/api/index.html#gnieh.sohva.Replicator) class provides methods to start and stop replication.

```scala
val replication = Replication("replication1", LocalDb("source_db"), RemoteDb(new URL("http://host/5984/target_db"), continuous = Some(true))

replicator.start(replication)
// after a while
replicator.stop("replication1")
```
