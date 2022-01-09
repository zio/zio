---
id: console
title: MockConsole 
---

`MockConsole` is a built-in mock version of the [`Console`](../../contextual/services/console.md) service.

Here is an example of mocking `Console.readLine` capability:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

test("calling mocked readline should return expected value") {
  for {
    line <- Console.readLine.provideLayer(
      MockConsole.ReadLine(Expectation.value("foo"))
    )
  } yield assertTrue(line == "foo")
}
```