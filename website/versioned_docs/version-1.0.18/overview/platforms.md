---
id: overview_platforms
title: "Platforms"
---

ZIO provides a consistent interface across platforms to the maximum extent possible, allowing developers to write code once and deploy it everywhere. However, there are some unavoidable differences between platforms to be aware of.

## JVM

ZIO supports Java versions 8 and above and Scala versions 2.11, 2.12, 2.13, and Dotty.

On the JVM, the `Blocking` service is available to lock effects on the blocking thread pool and is included as part of the `ZEnv`. See the documentation on [Creating Effects](../overview/creating_effects.md) for further discussion on blocking synchronous side-effects.

## Scala.js

ZIO supports Scala.js 1.0.

While ZIO is a zero dependency library, some basic capabilities of the platform are assumed. In particular, due to the absence of implementations for certain `java.time` methods in Scala.js, users must bring their own `java.time` dependency. The one used by ZIO in its own internal test suites is [scala-java-time](https://github.com/cquiroz/scala-java-time). It can be added as a dependency like so:

```scala
libraryDependencies ++= Seq(
  "io.github.cquiroz" %%% "scala-java-time" % "2.2.0",
  "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.2.0"
)
```

Because of its single threaded execution model, blocking operations are not supported on Scala.js. As such, the `Blocking` service is not available and is not included in the `ZEnv`. In addition, several other methods are not supported or are unsafe on Scala.js:

* The `readLine` method in the `Console` service is not supported because reading a line from the console blocks until input is received and the underlying method from the Scala standard library is not implemented on Scala.js.
* The `unsafeRun`, `unsafeRunTask`, and `unsafeRunSync` methods on `Runtime` are not safe. All of these methods return a value synchronously and may require blocking if the effect includeds asynchronous steps, including yield points introduced by the runtime to guarantee fairness. Users should use the `unsafeRunAsync`, `unsafeRunAsync_`, or `unsafeRunToFuture` methods instead.

## Scala Native

Support for Scala Native is currently experimental. More details will be added regarding support for the Scala Native platform when they are available.
