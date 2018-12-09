package scalaz.zio
package interop

import cats.Eq
import cats.effect.{ ConcurrentEffect, ContextShift }
import cats.effect.laws.ConcurrentEffectLaws
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.discipline.{ ConcurrentEffectTests, ConcurrentTests, EffectTests, Parameters }
import cats.effect.laws.util.{ TestContext, TestInstances }
import cats.implicits._
import cats.laws.discipline.{ AlternativeTests, BifunctorTests, MonadErrorTests, ParallelTests, SemigroupKTests }
import cats.syntax.all._
import org.scalacheck.{ Arbitrary, Cogen }
import org.scalatest.prop.Checkers
import org.scalatest.{ BeforeAndAfterAll, FunSuite, Matchers }
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.Discipline
import scalaz.zio.interop.catz._
import cats.laws._
import scalaz.zio.internal.Env

class IOConcurrentEffectLaws(override val F: ConcurrentEffect[Task], override val contextShift: ContextShift[Task])
    extends ConcurrentEffectLaws[Task] {
//  override def runCancelableIsSynchronous[A](fa: Task[A]) = {
//    F.unit <-> F.unit
//  }

  override def runCancelableIsSynchronous[A](fa: Task[A]) = {
    val lh = Promise.make[Nothing, Unit].flatMap { latch =>
      // Never ending task
      val ff = IO.async0[Nothing, A](_ => Async.maybeLater(latch.complete(()).void))
      // Execute, then cancel
      val token = IO.suspend(ff.fork.flatMap(_.interrupt))
      token *> latch.get
    }
    Task.unit <-> lh
  }

//  override def runAsyncRunCancelableCoherence[A](fa: Task[A]) = {
//    fa <-> fa
//  }

//  override def runAsyncRunCancelableCoherence[A](fa: Task[A]) = {
//    val fa1 = cats.effect.IO.async[A] { cb => F.runAsync(fa)(r => cats.effect.IO(cb(r))).unsafeRunSync() }
//    val fa2 = cats.effect.IO.cancelable[A] { cb => F.toIO(F.runCancelable(fa)(r => cats.effect.IO(cb(r))).unsafeRunSync()) }
//    fa1 <-> fa2
//  }

//  override def toIORunCancelableConsistency[A](fa: Task[A]) =
////    ConcurrentEffect.toIOFromRunCancelable(fa) <-> F.toIO(fa)
//    F.toIO(fa) <-> F.toIO(fa)
}

object IOConcurrentEffectTests {
  def apply()(implicit ce: ConcurrentEffect[Task], cs: ContextShift[Task]): ConcurrentEffectTests[Task] =
    new ConcurrentEffectTests[Task] {
      def laws = new IOConcurrentEffectLaws(ce, cs)
    }
}

class catzSpec
    extends FunSuite
    with BeforeAndAfterAll
    with Matchers
    with Checkers
    with Discipline
    with TestInstances
    with GenIO
    with RTS {

  override lazy val env = Env.newDefaultEnv(_ => IO.unit)

  def checkAllAsync(name: String, f: TestContext => Laws#RuleSet): Unit = {
    val context = TestContext()
    val ruleSet = f(context)

    checkAll(name, ruleSet)
  }

  override implicit def eqIO[A](implicit A: Eq[A], ec: TestContext): Eq[cats.effect.IO[A]] =
    new Eq[cats.effect.IO[A]] {
      import cats.syntax.apply._
      import scala.concurrent.duration._

      def eqv(x: cats.effect.IO[A], y: cats.effect.IO[A]): Boolean = {
        val duration = 10.seconds
        val leftM    = x.attempt.unsafeRunTimed(duration)
        val rightM   = y.attempt.unsafeRunTimed(duration)

        val res = (leftM, rightM).mapN {
          (_, _) match {
            case (Right(l), Right(r)) => A.eqv(l, r)
            case (Left(l), Left(r))   => l == r
            case _                    => false
          }
        }

        res.getOrElse(false)
      }
    }

  (1 to 50).foreach { s =>
    checkAllAsync(
      s"ConcurrentEffect[Task] $s",
      implicit tctx => {
        val _ = tctx
//      implicit def eqIO[A: Eq]: Eq[cats.effect.IO[A]] = new Eq[cats.effect.IO[A]] {
//        override def eqv(x: cats.effect.IO[A], y: cats.effect.IO[A]): Boolean = Eq[A].eqv(x.unsafeRunSync(), y.unsafeRunSync())
//      }

        IOConcurrentEffectTests().concurrentEffect[Int, Int, Int]
      }
    )
  }
  // TODO: reintroduce repeated ConcurrentTests as they're removed due to the hanging CI builds (see https://github.com/scalaz/scalaz-zio/pull/482)
  checkAllAsync("Concurrent[Task]", (_) => ConcurrentTests[Task].concurrent[Int, Int, Int])
  checkAllAsync("Effect[Task]", implicit tctx => EffectTests[Task].effect[Int, Int, Int])
  checkAllAsync("MonadError[IO[Int, ?]]", (_) => MonadErrorTests[IO[Int, ?], Int].monadError[Int, Int, Int])
  checkAllAsync("Alternative[IO[Int, ?]]", (_) => AlternativeTests[IO[Int, ?]].alternative[Int, Int, Int])
  checkAllAsync(
    "Alternative[IO[Option[Unit], ?]]",
    (_) => AlternativeTests[IO[Option[Unit], ?]].alternative[Int, Int, Int]
  )
  checkAllAsync("SemigroupK[IO[Nothing, ?]]", (_) => SemigroupKTests[IO[Nothing, ?]].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", (_) => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task, Task.Par]", (_) => ParallelTests[Task, Task.Par].parallel[Int, Int])

  implicit def catsEQ[E, A: Eq]: Eq[IO[E, A]] =
    new Eq[IO[E, A]] {
      import scalaz.zio.duration._

      def eqv(io1: IO[E, A], io2: IO[E, A]): Boolean = {
        val v1  = unsafeRunSync(io1.timeout(20.seconds)).map(_.get)
        val v2  = unsafeRunSync(io2.timeout(20.seconds)).map(_.get)
        val res = v1 === v2
        if (!res) {
          println(s"Mismatch: $v1 != $v2")
        }
        res
      }
    }

  implicit def catsParEQ[E, A: Eq]: Eq[ParIO[E, A]] =
    new Eq[ParIO[E, A]] {
      def eqv(io1: ParIO[E, A], io2: ParIO[E, A]): Boolean =
        unsafeRun(Par.unwrap(io1).attempt) === unsafeRun(Par.unwrap(io2).attempt)
    }

  implicit def params: Parameters =
    Parameters.default.copy(allowNonTerminationLaws = false)

  implicit def ioArbitrary[E, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(genSuccess[E, A])

  implicit def ioParArbitrary[E, A: Arbitrary: Cogen]: Arbitrary[ParIO[E, A]] =
    Arbitrary(genSuccess[E, A].map(Par.apply))
}
