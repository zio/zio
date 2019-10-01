package zio.test

import scala.concurrent.Future

import zio.{ UIO, ZIO }
import zio.stream.ZStream
import zio.test.TestUtils.label

object SampleSpec extends BaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(monadLeftIdentity, "monad left identity"),
    label(monadRightIdentity, "monad right identity"),
    label(monadAssociativity, "monad associativity"),
    label(traverseFusion, "traverse fusion")
  )

  def monadLeftIdentity: Future[Boolean] =
    unsafeRunToFuture {
      val sample = Sample.shrinkIntegral(0)(5)
      equal(sample.flatMap(Sample.noShrink), sample)
    }

  def monadRightIdentity: Future[Boolean] =
    unsafeRunToFuture {
      val n                           = 5
      def f(n: Int): Sample[Any, Int] = Sample.shrinkIntegral(0)(n)
      equal(Sample.noShrink(n).flatMap(f), f(n))
    }

  def monadAssociativity: Future[Boolean] =
    unsafeRunToFuture {
      val sample                      = Sample.shrinkIntegral(0)(2)
      def f(n: Int): Sample[Any, Int] = Sample.shrinkIntegral(0)(n + 3)
      def g(n: Int): Sample[Any, Int] = Sample.shrinkIntegral(0)(n + 5)
      equal(sample.flatMap(f).flatMap(g), sample.flatMap(a => f(a).flatMap(g)))
    }

  def traverseFusion: Future[Boolean] =
    unsafeRunToFuture {
      val sample              = Sample.shrinkIntegral(0)(5)
      def f(n: Int): UIO[Int] = ZIO.succeed(n + 2)
      def g(n: Int): UIO[Int] = ZIO.succeed(n * 3)
      equal(
        sample.traverse(a => f(a).flatMap(g)),
        sample.traverse(f).flatMap(_.traverse(g))
      )
    }

  def equal[A, B](a: ZIO[Any, Nothing, Sample[Any, A]], b: ZIO[Any, Nothing, Sample[Any, B]]): UIO[Boolean] =
    for {
      a      <- a
      b      <- b
      result <- equal(a, b)
    } yield result

  def equal[A, B](a: Sample[Any, A], b: Sample[Any, B]): UIO[Boolean] =
    equal(a.shrink, b.shrink).map(_ && (a.value == b.value))

  def equal[A, B](a: ZStream[Any, Nothing, Sample[Any, A]], b: ZStream[Any, Nothing, Sample[Any, B]]): UIO[Boolean] =
    for {
      as <- a.runCollect
      bs <- b.runCollect
      result <- if (as.length != bs.length) ZIO.succeed(false)
               else ZIO.foreach(as.zip(bs)) { case (a, b) => equal(a, b) }.map(_.forall(identity))
    } yield result
}
