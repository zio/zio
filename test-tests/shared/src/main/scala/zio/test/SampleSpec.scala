package zio.test

import scala.concurrent.Future

import zio.{ UIO, ZIO }
import zio.stream.ZStream
import zio.test.TestUtils.label

object SampleSpec extends AsyncBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(monadLeftIdentity, "monad left identity"),
    label(monadRightIdentity, "monad right identity"),
    label(monadAssociativity, "monad associativity"),
    label(traverseFusion, "traverse fusion")
  )

  def monadLeftIdentity: Future[Boolean] =
    unsafeRunToFuture {
      val sample = Sample.shrinkIntegral(0)(5)
      equalSamples(sample.flatMap(Sample.noShrink), sample)
    }

  def monadRightIdentity: Future[Boolean] =
    unsafeRunToFuture {
      val n                           = 5
      def f(n: Int): Sample[Any, Int] = Sample.shrinkIntegral(0)(n)
      equalSamples(Sample.noShrink(n).flatMap(f), f(n))
    }

  def monadAssociativity: Future[Boolean] =
    unsafeRunToFuture {
      val sample                      = Sample.shrinkIntegral(0)(2)
      def f(n: Int): Sample[Any, Int] = Sample.shrinkIntegral(0)(n + 3)
      def g(n: Int): Sample[Any, Int] = Sample.shrinkIntegral(0)(n + 5)
      equalSamples(sample.flatMap(f).flatMap(g), sample.flatMap(a => f(a).flatMap(g)))
    }

  def traverseFusion: Future[Boolean] =
    unsafeRunToFuture {
      val sample              = Sample.shrinkIntegral(0)(5)
      def f(n: Int): UIO[Int] = ZIO.succeed(n + 2)
      def g(n: Int): UIO[Int] = ZIO.succeed(n * 3)
      equalEffects(
        sample.traverse(a => f(a).flatMap(g)),
        sample.traverse(f).flatMap(_.traverse(g))
      )
    }

  def equalEffects[A, B](
    left: ZIO[Any, Nothing, Sample[Any, A]],
    right: ZIO[Any, Nothing, Sample[Any, B]]
  ): UIO[Boolean] =
    left.flatMap(a => right.flatMap(b => equalSamples(a, b)))

  def equalSamples[A, B](left: Sample[Any, A], right: Sample[Any, B]): UIO[Boolean] =
    if (left.value != right.value) UIO.succeed(false) else equalShrinks(left.shrink, right.shrink)

  def equalShrinks[A, B](
    left: ZStream[Any, Nothing, Sample[Any, A]],
    right: ZStream[Any, Nothing, Sample[Any, B]]
  ): UIO[Boolean] =
    left.zip(right).mapM { case (a, b) => equalSamples(a, b) }.fold(true)(_ && _)
}
