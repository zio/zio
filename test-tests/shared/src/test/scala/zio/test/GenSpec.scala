package zio.test

import zio.random.Random
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.ZIO

object GenSpec extends ZIOBaseSpec {

  def spec = suite("GenSpec")(
    testM("run") {
      assertM(Gen.int(-10, 10).run, isSome(isWithin(-10, 10)))
    },
    testM("runAll") {
      val domain = -10 to 10
      val gen    = Gen.fromIterable(domain)
      for {
        a <- gen.runAll
        b <- gen.runAll
      } yield assert(a, equalTo(domain)) &&
        assert(b, equalTo(domain))
    } @@ scala2Only,
    testM("runSome") {
      val gen = Gen.int(-10, 10)
      for {
        a <- gen.runSome(100)
        b <- gen.runSome(100)
      } yield assert(a, not(equalTo(b))) &&
        assert(a, hasSize(equalTo(100))) &&
        assert(b, hasSize(equalTo(100)))

    },
    suite("zipWith")(
      testM("left preservation") {
        checkM(deterministic, deterministic) { (a, b) =>
          for {
            left  <- sample(a.zip(b).map(_._1))
            right <- sample(a)
          } yield assert(left, startsWith(right))
        }
      } @@ scala2Only,
      testM("right preservation") {
        checkM(deterministic, deterministic) { (a, b) =>
          for {
            left  <- sample(a.zip(b).map(_._2))
            right <- sample(b)
          } yield assert(left, startsWith(right))
        }
      } @@ scala2Only,
      testM("shrinking") {
        checkM(random, random) { (a, b) =>
          for {
            left  <- shrink(a.zip(b))
            right <- shrink(a.cross(b))
          } yield assert(left, equalTo(right))
        }
      },
      testM("shrink search") {
        val smallInt = Gen.int(0, 9)
        checkM(Gen.const(shrinkable.zip(shrinkable)), smallInt, smallInt) { (gen, m, n) =>
          for {
            result <- shrinkWith(gen) { case (x, y) => x < m && y < n }
          } yield assert(result.reverse.headOption, isSome(equalTo((m, 0)) || equalTo((0, n))))
        }
      }
    )
  )

  val deterministic: Gen[Random with Sized, Gen[Any, Int]] =
    Gen.listOf1(Gen.int(-10, 10)).map(as => Gen.fromIterable(as))

  val random: Gen[Any, Gen[Random, Int]] =
    Gen.const(Gen.int(-10, 10))

  def sample[R, A](gen: Gen[R, A]): ZIO[R, Nothing, List[A]] =
    gen.sample.map(_.value).runCollect

  def shrink[R, A](gen: Gen[R, A]): ZIO[R, Nothing, A] =
    gen.sample.take(1).flatMap(_.shrinkSearch(_ => true)).take(1000).runLast.map(_.get)

  val shrinkable: Gen[Random, Int] =
    Gen.fromRandomSample(_.nextInt(90).map(_ + 10).map(Sample.shrinkIntegral(0)))

  def shrinkWith[R, A](gen: Gen[R, A])(f: A => Boolean): ZIO[R, Nothing, List[A]] =
    gen.sample.take(1).flatMap(_.shrinkSearch(!f(_))).take(1000).filter(!f(_)).runCollect
}
