---
id: sized
title: "Sized"
---

The `Sized` service enables the _Sized Generators_ to access the _size_ from the ZIO Test environment:

```scala
trait Sized extends Serializable {
  def size: UIO[Int]
  def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A]
}
```

For example, the `Gen.sized` generator has the following signature:

```scala
def sized[R <: Has[Sized], A](f: Int => Gen[R, A]): Gen[R, A]
```

It applies the _size_ to the given function of type `Int => Gen[R, A]`. We should note that the `sized` generator obtains the `size` value from the environment internally.

In the following example, we are creating a sized generator, which generates integer values within the specified range. In this generator, the lower bound is bound to zero, and the upper bound is bound to the _size_ value, which is obtained from the ZIO environment:

```scala mdoc:silent:nest
import zio._
import zio.test._

val sizedInts: Gen[Has[Random] with Has[Sized], Int] = 
  Gen.sized(Gen.int(0, _))
```

To generate some sample values, we can use `Gen#runCollectN` operator on that:

```scala mdoc:silent:nest
val samples: URIO[Has[Random] with Has[Sized], List[Int]] = 
  sizedInts.runCollectN(5).debug
```

The return type requires _Random_ and _Sized_ services. Therefore, to run this effect, we need to provide these two services. As the `ZEnv` has the _Random_ service, we only need to provide the `Sized` implementation:

```scala mdoc:silent:nest
zio.Runtime.default.unsafeRun(
  samples.provideCustomLayer(Sized.live(100)) 
)
// Sample Output: List(34, 44, 89, 14, 15)
```

The previous example was for educational purposes. In the real world, when we are testing, we don't need to manually provide the `Sized.live` layer. The ZIO Test Runner has a built-in `TestEnvironment` which contains all required services for testing as well as `Sized` service:

```scala
type TestEnvironment =
  Has[Annotations]
    with Has[Live]
    with Has[Sized]
    with Has[TestClock]
    with Has[TestConfig]
    with Has[TestConsole]
    with Has[TestRandom]
    with Has[TestSystem]
    with ZEnv
```

So when we test a property with ZIO Test, all the required services will be provided to the ZIO Test Runner:

```scala mdoc:compile-only
object SizedSpec extends DefaultRunnableSpec {
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
