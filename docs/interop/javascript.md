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

## Example

Your main function can extend `App` as follows.
This example uses [scala-js-dom](https://github.com/scala-js/scala-js-dom) to access the DOM; to run the example you
will need to add that library as a dependency to your `build.sbt`.

```scala mdoc:silent
import org.scalajs.dom.document
import zio.{App, IO}

object MyApp extends App {

  def run(args: List[String]): IO[Nothing, Int] =
    for {
      p <- IO.effectTotal(document.createElement("p"))
      t <- IO.effectTotal(document.createTextNode("Hello World"))
      _ <- IO.effectTotal(p.appendChild(t))
      _ <- IO.effectTotal(document.body.appendChild(p))
    } yield ExitCode.success
}

```
