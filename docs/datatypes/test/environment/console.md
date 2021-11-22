---
id: console
title: "TestConsole"
---

`TestConsole` provides a testable interface for programs interacting with the console by modeling input and output as reading from and writing to input and output buffers maintained by `TestConsole` and backed by a `Ref`.

All calls to `print` and `printLine` using the `TestConsole` will write the string to the output buffer and all calls to `readLine` will take a string from the input buffer. To facilitate debugging, by default output will also be rendered to standard output. You can enable or disable this for a scope using `debug`, `silent`, or the corresponding test aspects. 

`TestConsole` has several methods to access and manipulate the content of these buffers including `feedLines` to feed strings to the input  buffer that will then be returned by calls to `readLine`, `output` to get the content of the output buffer from calls to `print` and `printLine`, and `clearInput` and `clearOutput` to clear the respective buffers.

Together, these functions make it easy to test programs interacting with the console.

```scala mdoc:compile-only
import zio._
import zio.test._
import zio.Console._

val sayHello = for {
  name <- readLine
  _    <- printLine("Hello, " + name + "!")
} yield ()

for {
  _ <- TestConsole.feedLines("John", "Jane", "Sally")
  _ <- ZIO.collectAll(List.fill(3)(sayHello))
  result <- TestConsole.output
} yield result == Vector("Hello, John!\n", "Hello, Jane!\n", "Hello, Sally!\n")
```