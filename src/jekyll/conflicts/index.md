---
layout: default
title: Automatic conflict handling
---

Concurrent updates in CouchDB are handled with a revision field in documents. When saving a document, if the sent revision does not match the one in the database, then a conflict occurs. It is up to the client to resolve it and then retry saving the document.

Sohva natively supports conflict resolution so that you do not have to bother with it.

Conflict resolution
-------------------

When getting a database representation using the `database` method, you can provide to parameters in addition to the database name:

 1. the conflict resolution credit, indicating how many times you want to retry saving a document when a conflict occurs
 2. the conflict resolution strategy, instructing sohva how to resolve conflicts automatically.

By default the credit is `0`, hence no resolution is performed. If the credit is not sufficient (say in the case you have a successful document update from another client between each attempted resolution), then a final conflict is issued.

Conflict resolution strategy
----------------------------

The [Strategy](/latest/api/#gnieh.sohva.strategy.Strategy) trait defines the way a conflict is resolved, using three documents:

 1. `baseDoc` is the last revision known by this client.
 2. `lastDoc` is the last revision saved in the database as the time the conflict occurred.
 3. `currentDoc` is the document the client is trying to save.

There is three builtin strategies in Sohva:

 1. `BarneyStinsonStrategy` (the default) which always replaces the document from the database by the one you are trying to save.
 2. `TedMosbyStrategy` which always discard your change in favour of the one already in the database.
 3. `StructuralMergeStrategy` which tries to merge your document with the one from the database. For more details on how conflicts are resolved for this strategy, refer to [the documentation](/latest/api/#gnieh.sohva.strategy.StructuralMergeStrategy$).

 You can provide your custom strategy by implementing the `Strategy` trait if you want another strategy (e.g. three-way merge).
