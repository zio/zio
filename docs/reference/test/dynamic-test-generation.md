---
id: dynamic-test-generation
title: "Dynamic Test Generation"
---

Tests in ZIO are dynamic. Meaning that they are not required to be statically defined at compile time. They can be generated at runtime effectfully.

Assume we have implemented the `add` operator which adds two numbers:

```scala mdoc:silent
def add(a: Int, b: Int): Int = ???
```

We want to test this function using the following test data inside the `resources` directory:

```scala title="src/test/resources/test-data.csv"
0, 0, 0
1, 0, 1
0, 1, 1
0, -1, -1
-1, 0, -1
1, 1, 2
1, -1, 0
-1, 1, 0
```

Let's load it and create a bunch of tests using this test data:

```scala mdoc:silent
import zio._
import zio.test._
import zio.test.test

def loadTestData: Task[List[((Int, Int), Int)]] =
  ZIO.attemptBlocking(
    scala.io.Source
      .fromResource("test-data.csv")
      .getLines()
      .toList
      .map(_.split(',').map(_.strip()))
      .map(i => ((i(0).toInt, i(1).toInt), i(2).toInt))
  )
  
def makeTest(a: Int, b: Int)(expected: Int): Spec[Any, Nothing] =
  test(s"test add($a, $b) == $expected") {
    assertTrue(add(a, b) == expected)
  }

def makeTests: ZIO[Any, Throwable, List[Spec[Any, Nothing]]] =
  loadTestData.map { testData =>
    testData.map { case ((a, b), expected) =>
      makeTest(a, b)(expected)
    }
  }
```

Now we are ready to run all generated tests:

```scala mdoc:silent
import zio._
import zio.test._

object AdditionSpec extends ZIOSpecDefault {
  override def spec = suite("add")(makeTests)
}
```

Here is the test runner's output:

```scala
+ add
  + test add(0, 0) == 0
  + test add(1, 0) == 1
  + test add(0, -1) == -1
  + test add(0, 1) == 1
  + test add(-1, 1) == 0
  + test add(1, -1) == 0
  + test add(1, 1) == 2
  + test add(-1, 0) == -1
8 tests passed. 0 tests failed. 0 tests ignored.
```
