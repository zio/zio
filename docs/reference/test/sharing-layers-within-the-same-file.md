---
id: sharing-layers-within-the-same-file
title: "Sharing Layers within the Same File"
---

The `Spec` data type has a very nice mechanism to share layers within all tests in a suite. So instead of acquiring and releasing dependencies for each test, we can share the layer within all tests. The test framework acquires that layer for once and shares that between all tests. When the execution of all tests is finished, that layer will be released. To share layers between multiple specs we can use `Spec#provideXYZShared` methods:

```scala
{
  test("test1")(???) +
    test("test2")(???)
}.provideZYZShared(sharedLayer)

suite("suite1")(
  test("test1")(???),
  test("test2")(???)
).provideZYZShared(sharedLayer)

suite("all suites")(
  suite("suite1")(
    test("test1")(???),
  ),
  suite("suite2")(
    test("test1")(???),
    test("test2")(???),
    test("test3")(???)
  )
).provideXYZShared(sharedLayer)
```

To demonstrate this, let's try an example. In this example, instead of using the built-in features of the ZIO Test, we want to write our own basic solution to count the number of times tests are executed.

First, we need a counter service like the below:

```scala mdoc:silent
import zio._

case class Counter(value: Ref[Int]) {
  def inc: UIO[Unit] = value.update(_ + 1)
  def get: UIO[Int] = value.get
}

object Counter {
  val layer =
    ZLayer.scoped(
      ZIO.acquireRelease(
        Ref.make(0).map(Counter(_)) <* ZIO.debug("Counter initialized!")
      )(c => c.get.debug("Number of tests executed"))
    )
  def inc = ZIO.service[Counter].flatMap(_.inc)
}
```

We use this service to count the number of times the tests are executed, by calling the `Counter.inc` operator after each test:

```scala mdoc:compile-only
import zio._
import zio.test._

object MySpecs extends ZIOSpecDefault {
  def spec = {
    suite("Spec1")(
      test("test1") {
        assertTrue(true)
      } @@ TestAspect.after(Counter.inc),
      test("test2") { assertTrue(true)
      } @@ TestAspect.after(Counter.inc)
    ) +
      suite("Spec2") {
        test("test1") {
          assertTrue(true)
        } @@ TestAspect.after(Counter.inc)
      }
  }.provideShared(Counter.layer)
}
```

If we execute all tests, we will see an output like this:

```
Counter initialized!
+ Spec1
  + test2
  + test1
+ Spec2
  + test1
Number of tests executed: 3
3 tests passed. 0 tests failed. 0 tests ignored.
```

In the above example, the `Counter.layer` is shared between all tests, and only acquired and released once.
