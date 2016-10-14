---
layout: default
title: Using Sohva synchronously
---

Sohva is an asynchronous library but you may want to use it in a synchronous context. You can easily define your own wrapper to make all call to sohva synchronous

```scala
import scala.language.implicitConversions

implicit def synced[T](result: Future[T]): T =
  try {
    Await.result(result, Duration.Inf)
  } catch {
    case t: SohvaException => throw t
  }

val post: Option[Post] = db.getDocById("post1")
```
