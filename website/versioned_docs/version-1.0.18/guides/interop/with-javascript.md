---
id: with-javascript
title: "How to Interop with JavaScript?"
---

Include ZIO in your Scala.js project by adding the following to your `build.sbt`:

```
scalaJSUseMainModuleInitializer := true
libraryDependencies += "dev.zio" %%% "zio" % "1.0.18"
```

## Example

Your main function can extend `App` as follows.
This example uses [scala-js-dom](https://github.com/scala-js/scala-js-dom) to access the DOM; to run the example you
will need to add that library as a dependency to your `build.sbt`.

```scala
import org.scalajs.dom.{document, raw}
import zio._
import zio.duration._
import zio.clock._

object Main extends App {

  def run(args: List[String]) = {
    for {
      _      <- putStrLn("Starting progress bar demo.")  // Outputs on browser console log.
      target <- IO.effectTotal(document.createElement("pre"))
      _      <- update(target).repeat(Schedule.spaced(1.seconds))
      _      <- IO.effectTotal(node.appendChild(target)) // "node" is provided in this page by mdoc.
    } yield ExitCode.success
  }

  def update(target: raw.Element) = {
      for {
        time   <- currentTime(TimeUnit.SECONDS)
        output <- UIO.effectTotal(progress((time % 11).toInt, 10))
        _      <- UIO.effectTotal(target.innerHTML = output)
      } yield ()
  }

  def progress(tick: Int, size: Int) = {
      val bar_length = tick
      val empty_length = size - tick
      val bar = "#" * bar_length + " " * empty_length
      s"$bar $bar_length%"
  }

}
```
