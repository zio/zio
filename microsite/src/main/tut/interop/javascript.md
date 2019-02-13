---
layout: docs
section: interop
title:  "Javascript"
---

# {{page.title}}

Include ZIO in your Scala.js project by adding the following to your `build.sbt`:

```tut:evaluated
println("""scalaJSUseMainModuleInitializer := true""")
if (scalaz.zio.BuildInfo.isSnapshot) println(s"""resolvers += Resolver.sonatypeRepo("snapshots")""")
println(s"""libraryDependencies += "org.scalaz" %%% "scalaz-zio" % "${scalaz.zio.BuildInfo.version}"""")
```

### Example

Your main function can extend `App` as follows.
This example uses [scala-js-dom](https://github.com/scala-js/scala-js-dom) to access the DOM; to run the example you
will need to add that library as a dependency to your `build.sbt`.

```scala
import org.scalajs.dom.document
import scalaz.zio.{App, IO}

object MyApp extends App {

  def run(args: List[String]): IO[Nothing, Unit] =
    for {
      p <- IO.sync(document.createElement("p"))
      t <- IO.sync(document.createTextNode("Hello World"))
      _ <- IO.sync(p.appendChild(t))
      _ <- IO.sync(document.body.appendChild(p))
    } yield ()
}

```
