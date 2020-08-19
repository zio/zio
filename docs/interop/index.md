---
id: interop_index
title:  "Summary"
---

ZIO provides the ability to interoperate with other parts of the broader ecosystem, including:

 - **Future** — ZIO has built-in conversion between ZIO data types (like `ZIO` and `Fiber`) and Scala concurrent data types like `Future`.
 - **Java** — ZIO has built-in conversion between ZIO data types (like `ZIO` and `Fiber`) and Java concurrent data types like [`CompletionStage`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html), [`Future`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html) and [`CompletionHandler`](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/CompletionHandler.html).
 - **JavaScript** — ZIO has first-class support for Scala.js.
 - **Scalaz 8** — Scalaz 8 depends on ZIO, and contains instances for all ZIO data types inside the library. No additional modules are needed.
- [`interop-cats`](https://github.com/zio/interop-cats) has instances for the [Cats](https://typelevel.org/cats/), [Cats MTL](https://github.com/typelevel/cats-mtl) and [Cats Effect](https://typelevel.org/cats-effect/) libraries, which allow you to use ZIO with any libraries that rely on these, like [Doobie](https://github.com/tpolecat/doobie), [Http4s](https://github.com/http4s/http4s), [FS2](https://github.com/functional-streams-for-scala/fs2) or [Circe](https://github.com/circe/circe)
- [`interop-reactive-streams`](https://github.com/zio/interop-reactive-streams) for [Reactive Streams](http://www.reactive-streams.org/), has conversion from ZIO Streams and Sinks to Reactive Streams Producers and Consumers
- [`interop-scalaz`](https://github.com/zio/interop-scalaz) for [ScalaZ 7](https://scalaz.github.io/7/), has instances of `Monad` and other type classes for the ZIO data types
- [`interop-twitter`](https://github.com/zio/interop-twitter) for [Twitter's `com.twitter.util.Future`](https://twitter.github.io/util/docs/com/twitter/util/Future.html)
- [`interop-monix`](https://github.com/zio/interop-monix) for [Monix's `monix.eval.Task` and `monix.eval.Coeval`](https://monix.io/docs/3x/)
- [`interop-guava`](https://github.com/zio/interop-guava) for [Guava's `com.google.common.util.concurrent.ListenableFuture`](https://github.com/google/guava/wiki/ListenableFutureExplained)

Explore the sections above to learn how easy it is to integrate ZIO with whatever libraries or platforms you are already using. 
