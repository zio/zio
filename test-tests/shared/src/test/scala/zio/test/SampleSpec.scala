package zio.test

import zio.stream.ZStream
import zio.test.Assertion._
import zio.{UIO, ZIO}

object SampleSpec extends ZIOBaseSpec {

  def spec = suite("SampleSpec")(
    test("monad left identity") {
      val sample = Sample.shrinkIntegral(0)(5)
      val result = equalSamples(sample.flatMap(Sample.noShrink), sample)
      assertZIO(result)(isTrue)
    },
    test("monad right identity") {
      val n                           = 5
      def f(n: Int): Sample[Any, Int] = Sample.shrinkIntegral(0)(n)
      val result                      = equalSamples(Sample.noShrink(n).flatMap(f), f(n))
      assertZIO(result)(isTrue)
    },
    test("monad associativity") {
      val sample                      = Sample.shrinkIntegral(0)(2)
      def f(n: Int): Sample[Any, Int] = Sample.shrinkIntegral(0)(n + 3)
      def g(n: Int): Sample[Any, Int] = Sample.shrinkIntegral(0)(n + 5)
      val result                      = equalSamples(sample.flatMap(f).flatMap(g), sample.flatMap(a => f(a).flatMap(g)))
      assertZIO(result)(isTrue)
    },
    test("traverse fusion") {
      val sample              = Sample.shrinkIntegral(0)(5)
      def f(n: Int): UIO[Int] = ZIO.succeed(n + 2)
      def g(n: Int): UIO[Int] = ZIO.succeed(n * 3)
      val result = equalEffects(
        sample.foreach(a => f(a).flatMap(g)),
        sample.foreach(f).flatMap(_.foreach(g))
      )
      assertZIO(result)(isTrue)
    }
  )

  def equalEffects[A, B](
    left: ZIO[Any, Nothing, Sample[Any, A]],
    right: ZIO[Any, Nothing, Sample[Any, B]]
  ): UIO[Boolean] =
    left.flatMap(a => right.flatMap(b => equalSamples(a, b)))

  def equalSamples[A, B](left: Sample[Any, A], right: Sample[Any, B]): UIO[Boolean] =
    if (left.value != right.value) ZIO.succeed(false) else equalShrinks(left.shrink, right.shrink)

  def equalShrinks[A, B](
    left: ZStream[Any, Nothing, Option[Sample[Any, A]]],
    right: ZStream[Any, Nothing, Option[Sample[Any, B]]]
  ): UIO[Boolean] =
    left
      .zip(right)
      .mapZIO {
        case (Some(a), Some(b)) => equalSamples(a, b)
        case (None, None)       => ZIO.succeedNow(true)
        case _                  => ZIO.succeedNow(false)
      }
      .runFold(true)(_ && _)
}
