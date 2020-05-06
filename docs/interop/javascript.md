---
id: interop_javascript
title:  "JavaScript"
---

Include ZIO in your Scala.js project by adding the following to your `build.sbt`:

```scala mdoc:passthrough
println(s"""```""")
println("""scalaJSUseMainModuleInitializer := true""")
if (zio.BuildInfo.isSnapshot) println(s"""resolvers += Resolver.sonatypeRepo("snapshots")""")
println(s"""libraryDependencies += "dev.zio" %%% "zio" % "${zio.BuildInfo.version}"""")
println(s"""```""")
```

Java Time API which is not a part of ScalaJS. You might have to add dependency that provides `java.time` package to
avoid linker errors when using `Clock`. ZIO uses [scala-java-time](https://github.com/cquiroz/scala-java-time) for
running it's test.

## Example

Your main function can extend `App` as follows.
This example uses [scala-js-dom](https://github.com/scala-js/scala-js-dom) to access the DOM; to run the example you
will need to add that library as a dependency to your `build.sbt`.

```scala
import org.scalajs.dom.document
import zio.{App, IO}

object MyApp extends App {

  def run(args: List[String]): IO[Nothing, Int] =
    for {
      p <- IO.effectSuspendTotal(document.createElement("p"))
      t <- IO.effectSuspendTotal(document.createTextNode("Hello World"))
      _ <- IO.effectSuspendTotal(p.appendChild(t))
      _ <- IO.effectSuspendTotal(document.body.appendChild(p))
    } yield 0
}

```
