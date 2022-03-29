---
id: sized
title: "Sized"
---

The `Sized` service enables the _Sized Generators_ to access the _size_ from the ZIO Test environment:

```scala mdoc:invisible
import zio._
import zio.test._
```

```scala mdoc:compile-only
trait Sized extends Serializable {
  def size: UIO[Int]
  def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A]
}
```

The `Sized` service has two APIs:
1. `Sized.size`
2. `Sized.withSize`

Let's go through each one:

## Operations

### size

To access the default _size_ value from the environment, we can use the `Sized.size` API. In ZIO Test, it is used to enable the _sized generators_ access the _size_ from the environment:


```scala mdoc:compile-only
object Sized {
  def withSize[R <: Sized, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] = ???
}
```

For example, the `Gen.sized` generator has the following signature:

```scala mdoc:compile-only
object Gen {
  def sized[R <: Sized, A](f: Int => Gen[R, A]): Gen[R, A] = ???
}
```

It applies the _size_ to the given function of type `Int => Gen[R, A]`. We should note that the `sized` generator obtains the `size` value from the environment internally.

In the following example, we are creating a sized generator, which generates integer values within the specified range. In this generator, the lower bound is bound to zero, and the upper bound is bound to the _size_ value, which is obtained from the ZIO environment:

```scala mdoc:silent:nest
import zio._
import zio.test._

val sizedInts: Gen[Sized, Int] = 
  Gen.sized(Gen.int(0, _))
```

To generate some sample values, we can use `Gen#runCollectN` operator on that:

```scala mdoc:silent:nest
val samples: URIO[Sized, List[Int]] = 
  sizedInts.runCollectN(5).debug
```

The return type require the _Sized_ service. Therefore, to run this effect, we need to provide this service:

```scala mdoc:silent:nest
zio.Runtime.default.unsafeRun(
  samples.provide(Sized.live(100)) 
)
// Sample Output: List(34, 44, 89, 14, 15)
```

The previous example was for educational purposes. In the real world, when we are testing, we don't need to manually provide the `Sized.live` layer. The ZIO Test Runner has a built-in `TestEnvironment` which contains all required services for testing as well as `Sized` service:

```scala mdoc:compile-only
type TestEnvironment =
  Annotations
    with Live
    with Sized
```

So when we test a property with ZIO Test, all the required services will be provided to the ZIO Test Runner:

```scala mdoc:compile-only
object SizedSpec extends ZIOSpecDefault {
  def spec =
    suite("sized") {
      test("bounded int generator shouldn't cross its boundaries") {
        check(Gen.sized(Gen.int(0, _))) { n =>
          assertTrue(n >= 0 && n <= 100)  // The default size is 100
        }
      }
    }
}
```

### withSize

To change the default _size_ temporarily, we can use the `Size.withSize`. It takes a `size` and a ZIO effect, and runs that effect bounded with the given `size`:

```scala mdoc:compile-only
object Sized {
  def withSize[R <: Sized, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] = ???
}
```

```scala mdoc:compile-only
import zio._
import zio.test._

val effect     : UIO[String]             = ZIO.succeed("effect")
val sizedEffect: RIO[Sized, String] = Sized.withSize(10)(effect)
```

ZIO Test has a test aspect called `TestAspect.sized` which is a helper method for this operation. This test aspect runs each test with the given _size_ value:

```scala mdoc:compile-only
import zio._
import zio.test._

object SizedSpec extends ZIOSpecDefault {
  def spec =
    suite("sized") {
      test("bounded int generator shouldn't cross its boundaries") {
        check(Gen.sized(Gen.int(0, _))) { n =>
          assertTrue(n >= 0 && n <= 200)
        }
      } @@ TestAspect.sized(200)
    }
}
```
