---
id: ecosystem
title:  "Ecosystem"
---

## Official ZIO Libraries

These libraries are hosted in the [ZIO organization](https://github.com/zio/) on Github, and are generally maintained by core contributors to ZIO.

- [ZIO Actors](https://github.com/zio/zio-actors): A high-performance, purely-functional library for building, composing, and supervising typed actors based on ZIO
- [ZIO Akka Cluster](https://github.com/zio/zio-akka-cluster): A ZIO wrapper for Akka Cluster
- [ZIO Kafka](https://github.com/zio/zio-kafka): A Kafka client for ZIO and ZIO Streams
- [ZIO Keeper](https://github.com/zio/zio-keeper): A functional library for consistent replication of metadata across dynamic clusters
- [ZIO NIO](https://github.com/zio/zio-nio): A performant, purely-functional, low-level, and unopinionated wrapper around Java NIO functionality
- [ZIO SQS](https://github.com/zio/zio-sqs): A ZIO-powered client for AWS SQS


## Libraries with Direct ZIO Support

If you know a useful library that has direct support for ZIO, please consider [submitting a pull request](https://github.com/zio/zio/pulls) to add it to this list.

- [cakeless](https://github.com/itkpi/cakeless): Better reader monad for deeply-nested cakes
- [distage](https://github.com/7mind/izumi): Staged, transparent and debuggable runtime & compile-time Dependency Injection Framework
- [logstage](https://github.com/7mind/izumi): Automatic structural logs from Scala string interpolations
- [idealingua](https://github.com/7mind/izumi): API Definition, Data Modeling and RPC Language, optimized for fast prototyping – like gRPC, but with a human face
- [scanamo](https://github.com/scanamo/scanamo): Simpler DynamoDB access for Scala
- [sttp](https://github.com/softwaremill/sttp): The Scala HTTP client you always wanted!
- [zio-saga](https://github.com/VladKopanev/zio-saga): Purely functional transaction management with Saga pattern
- [zio-slf4j](https://github.com/NeQuissimus/zio-slf4j): Referentially transparent logging with slf4j
- [slf4zio](https://github.com/mlangc/slf4zio): Simple convenience layer on top of SLF4J for ZIO
- [zio-slick](https://github.com/rleibman/zio-slick): Bridge library between ZIO and Slick Functional Relational Mapping Library
- [zio-email](https://github.com/funcit/zio-email): Purely functional email client

## ZIO Compatible Libraries

If you know a useful library that works with ZIO, please consider [submitting a pull request](https://github.com/zio/zio/pulls) to add it to this list.

- [Cats](https://github.com/typelevel/cats), [Cats MTL](https://github.com/typelevel/cats-mtl) and [Cats Effect](https://github.com/typelevel/cats-effect)
- [Scalaz](https://github.com/scalaz/scalaz)
- [Monix](https://github.com/monix/monix)
- [Doobie](https://github.com/tpolecat/doobie)
- [Http4s](https://github.com/http4s/http4s)
- [FS2](https://github.com/functional-streams-for-scala/fs2)
- [Circe](https://github.com/circe/circe)
- [Reactive Streams](https://github.com/reactive-streams/reactive-streams-jvm)
- [Twitter Futures](https://github.com/twitter/util)

## ZIO Interoperability Libraries

- [`interop-cats`](https://github.com/zio/interop-cats) for [`cats`](https://typelevel.org/cats/) and [`cats-effect`](https://typelevel.org/cats-effect/)
- [`interop-reactive-streams`](https://github.com/zio/interop-reactive-streams) for [Reactive Streams](http://www.reactive-streams.org/)
- [`interop-java`](https://github.com/zio/interop-java) for [`java.util.concurrent.CompletionStage`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html), [`java.util.concurrent.Future`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html) and [`java.nio.channels.CompletionHandler`](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/CompletionHandler.html) ([`java.util.concurrent.Flow`](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.html) TODO)
- [`interop-scalaz`](https://github.com/zio/interop-scalaz) for [ScalaZ 7](https://scalaz.github.io/7/)
- [`interop-future`](https://github.com/zio/interop-future) for [Scala's own `scala.concurrent.Future`](https://www.scala-lang.org/api/2.12.x/scala/concurrent/Future.html)
- [`interop-twitter`](https://github.com/zio/interop-twitter) for [Twitter's `com.twitter.util.Future`](https://twitter.github.io/util/docs/com/twitter/util/Future.html)
- [`interop-monix`](https://github.com/zio/interop-monix) for [Monix's `monix.eval.Task` and `monix.eval.Coeval`](https://monix.io/docs/3x/)
- [`interop-guava`](https://github.com/zio/interop-guava) for [Guava's `com.google.common.util.concurrent.ListenableFuture`](https://github.com/google/guava/wiki/ListenableFutureExplained)
