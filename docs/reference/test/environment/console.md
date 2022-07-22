---
id: console
title: "TestConsole"
---

`TestConsole` allows testing of applications that interact with the console by modeling working with standard input and output as writing and reading to and from internal buffers:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.Assertion._

val consoleSuite = suite("ConsoleTest")(
  test("One can test output of console") {
    for {
      _              <- TestConsole.feedLines("Jimmy", "37")
      _              <- Console.printLine("What is your name?")
      name           <- Console.readLine
      _              <- Console.printLine("What is your age?")
      age            <- Console.readLine.map(_.toInt)
      questionVector <- TestConsole.output
      q1             = questionVector(0)
      q2             = questionVector(1)
    } yield {
      assertTrue(name == "Jimmy") &&
        assertTrue(age == 37) &&
        assertTrue(q1 == "What is your name?\n") &&
        assertTrue(q2 == "What is your age?\n")
    }
  }
)
```

The above code simulates an application that will ask for the name and age of the user. To test it we prefill buffers with answers with the call to `TestConsole.feedLines` method. Calls to `Console.readLine` will get the value from the buffers instead of interacting with the users keyboard.

Also, all output that our program produces by calling `Console.printLine` (and other printing methods) is being gathered and can be accessed with a call to `TestConsole.output`.

`TestConsole` provides a testable interface for programs interacting with the console by modeling input and output as reading from and writing to input and output buffers maintained by `TestConsole` and backed by a `Ref`.

All calls to `print` and `printLine` using the `TestConsole` will write the string to the output buffer and all calls to `readLine` will take a string from the input buffer. 

To facilitate debugging, by default output will also be rendered to standard output. We can enable or disable this for a scope using `debug`, `silent`, or the corresponding test aspects. 

`TestConsole` has several methods to access and manipulate the content of these buffers including:
- **`feedLines`** to feed strings to the input  buffer that will then be returned by calls to `readLine`.
- **`output`** to get the content of the output buffer from calls to `print` and `printLine`
- **`clearInput`** and **`clearOutput`** to clear the respective buffers.

Together, these functions make it easy to test programs interacting with the console.
