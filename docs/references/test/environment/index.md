---
id: index
title: "Introduction"
---

ZIO Test has out of the box test implementations for all standard ZIO environment types, such as `Console`, `Clock`, `Random` and `System` through the following modules:
- [`TestConsole`](./console.md)
- [`TestClock`](./clock.md)
- [`TestRandom`](./random.md)
- [`TestSystem`](./system.md)

Stability is what we expect from tests, at least those we consider unit tests. Consecutive runs should yield the same results and take more or less the same amount of time.

External services, such as payment APIs, object storages, HTTP APIs, are the biggest source of complexity during testing. It is normal to hide these kinds of services behind an interface and provide test instances to regain control and determinism.

However, there is another source of complexity that comes from the local infrastructure that is also hard to control without building prior abstractions. Things like stdin/stdout, clocks, random generators, schedulers can make writing tests hard or even impossible.

Fortunately, ZIO abstracted most of it in its runtime under `Environment` type. Thanks to this design ZIO Test could easily provide its own implementation named `TestEnvironment` which gives us test implementations of mentioned infrastructure.

If we are using ZIO Test and extending `ZIOSpecDefault` a `TestEnvironment` containing all of them will be automatically provided to each of our tests. Otherwise, the easiest way to use the test implementations in ZIO Test is by providing the `TestEnvironment` to our program:

```scala mdoc:invisible:nest
import zio.Scope
import zio.test._
val myProgram: Spec[TestEnvironment, Nothing] =
  test("my suite")(assertTrue(true))
```

```scala mdoc:compile-only
myProgram.provide(testEnvironment)
```

Then all environmental effects, such as printing to the console or generating random numbers, will be implemented by the `TestEnvironment` and will be fully testable. When we do need to access the "live" environment, for example to print debugging information to the console, we just use the `live` combinator along with the effect as our normally would. 

If we are only interested in one of the test implementations for our application, we can also access them a la carte through the `make` method on each module. Each test module requires some data on initialization:

```scala mdoc:invisible:nest
import zio.test._
val myProgram: Spec[TestConsole, Nothing] = test("my suite")(assertTrue(true))
```

```scala mdoc:compile-only
myProgram.provideCustom(TestConsole.make(TestConsole.Data()))
```

Finally, we can create a `Test` object that implements the test interface directly using the `makeTest` method. This can be useful when we want to access some testing functionality without using the environment type:

```scala mdoc:compile-only
for {
  testRandom <- TestRandom.makeTest(TestRandom.DefaultData)
  n          <- testRandom.nextInt
} yield n
```

This can also be useful when we are creating a more complex environment to provide the implementation for test services that we mix in.
