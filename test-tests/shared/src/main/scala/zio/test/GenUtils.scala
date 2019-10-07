package zio.test

import zio.Exit.{ Failure, Success }
import zio.random.Random
import zio.test.environment.TestRandom
import zio._

import scala.concurrent.Future

object GenUtils extends DefaultRuntime {

  def checkEqual[A](left: Gen[Random, A], right: Gen[Random, A]): Future[Boolean] =
    unsafeRunToFuture(equal(left, right))

  def checkSample[A](gen: Gen[Random with Sized, A], size: Int = 100)(f: List[A] => Boolean): Future[Boolean] =
    unsafeRunToFuture(provideSize(sample(gen).map(f))(size))

  def checkFinite[A](gen: Gen[Random, A])(f: List[A] => Boolean): Future[Boolean] =
    unsafeRunToFuture(gen.sample.map(_.value).runCollect.map(f))

  def checkShrink[A](gen: Gen[Random with Sized, A])(a: A): Future[Boolean] =
    unsafeRunToFuture(provideSize(alwaysShrinksTo(gen)(a: A))(100))

  def sample[R, A](gen: Gen[R, A]): ZIO[R, Nothing, List[A]] =
    gen.sample.map(_.value).forever.take(100).runCollect

  def alwaysShrinksTo[R, A](gen: Gen[R, A])(a: A): ZIO[R, Nothing, Boolean] = {
    val shrinks = if (TestPlatform.isJS) 1 else 100
    ZIO.collectAll(List.fill(shrinks)(shrinksTo(gen))).map(_.forall(_ == a))
  }

  def shrinksTo[R, A](gen: Gen[R, A]): ZIO[R, Nothing, A] =
    shrinks(gen).map(_.reverse.head)

  def shrinks[R, A](gen: Gen[R, A]): ZIO[R, Nothing, List[A]] =
    gen.sample.forever.take(1).flatMap(_.shrinkSearch(_ => true)).take(1000).runCollect

  def equal[A](left: Gen[Random, A], right: Gen[Random, A]): UIO[Boolean] =
    equalSample(left, right).zipWith(equalShrink(left, right))(_ && _)

  def forAll[E <: Throwable, A](zio: ZIO[Random, E, Boolean]): Future[Boolean] =
    unsafeRunToFuture(ZIO.collectAll(List.fill(100)(zio)).map(_.forall(identity)))

  def equalSample[A](left: Gen[Random, A], right: Gen[Random, A]): UIO[Boolean] = {
    val testRandom = Managed.fromEffect(TestRandom.make(TestRandom.DefaultData))
    for {
      leftSample  <- sample(left).provideManaged(testRandom)
      rightSample <- sample(right).provideManaged(testRandom)
    } yield leftSample == rightSample
  }

  def equalShrink[A](left: Gen[Random, A], right: Gen[Random, A]): UIO[Boolean] = {
    val testRandom = Managed.fromEffect(TestRandom.make(TestRandom.DefaultData))
    for {
      leftShrinks  <- ZIO.collectAll(List.fill(100)(shrinks(left))).provideManaged(testRandom)
      rightShrinks <- ZIO.collectAll(List.fill(100)(shrinks(right))).provideManaged(testRandom)
    } yield leftShrinks == rightShrinks
  }

  def showTree[R, A](sample: Sample[R, A], offset: Int = 0): ZIO[R, Nothing, String] = {
    val head = " " * offset + sample.value + "\n"
    val tail = sample.shrink.mapM(showTree(_, offset + 2)).runCollect.map(_.mkString("\n"))
    tail.map(head + _)
  }

  def provideSize[A](zio: ZIO[Random with Sized, Nothing, A])(n: Int): ZIO[Random, Nothing, A] =
    Sized.makeService(n).flatMap { service =>
      zio.provideSome[Random] { r =>
        new Random with Sized {
          val random = r.random
          val sized  = service
        }
      }
    }

  def sampleEffect[E, A](
    gen: Gen[Random with Sized, ZIO[Random with Sized, E, A]],
    size: Int = 100
  ): ZIO[Random, Nothing, List[Exit[E, A]]] =
    provideSize(sample(gen).flatMap(effects => ZIO.collectAll(effects.map(_.run))))(size)

  def partitionExit[E, A](eas: List[Exit[E, A]]): (List[Failure[E]], List[A]) =
    eas.foldRight((List.empty[Failure[E]], List.empty[A])) {
      case (Failure(e), (es, as)) => (Failure(e) :: es, as)
      case (Success(a), (es, as)) => (es, a :: as)
    }
}
