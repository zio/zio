package scalaz.zio
package interop

import cats.Eq
import cats.effect.concurrent.Deferred
import cats.effect.{ ConcurrentEffect, ContextShift }
import cats.effect.laws.ConcurrentEffectLaws
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.discipline.{ ConcurrentEffectTests, ConcurrentTests, EffectTests, Parameters }
import cats.effect.laws.util.{ TestContext, TestInstances }
import cats.laws.discipline.{ AlternativeTests, BifunctorTests, MonadErrorTests, ParallelTests, SemigroupKTests }
import cats.implicits._
import org.scalacheck.{ Arbitrary, Cogen }
import org.scalatest.prop.Checkers
import org.scalatest.{ BeforeAndAfterAll, FunSuite, Matchers }
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.Discipline
import scalaz.zio.interop.catz._
import cats.laws._
import scalaz.zio.internal.PlatformLive

trait ConcurrentEffectLawsOverrides[F[_]] extends ConcurrentEffectLaws[F] {

  import cats.effect.IO
  import scala.concurrent.Promise

  override final def runCancelableIsSynchronous[A](fa: F[A]) = {
    val lh = Deferred.uncancelable[F, Unit].flatMap { release =>
      val latch = Promise[Unit]()
      // Never ending task
      val ff = F.cancelable[A] { _ =>
//          F.runAsync(started.complete(()))(_ => IO.unit).unsafeRunSync()
        latch.success(()); release.complete(())
      }
      // Execute, then cancel after the effect has started
      val token = for {
        canceler <- F.delay(F.runCancelable(ff)(_ => IO.unit).unsafeRunSync())
        _        <- F.liftIO(IO.fromFuture(IO.pure(latch.future)))
        _        <- canceler
      } yield ()

      F.liftIO(F.runAsync(token)(_ => IO.unit).toIO) *> release.get
    }
    lh <-> F.unit
  }
}

object IOConcurrentEffectTests {
  def apply()(implicit ce: ConcurrentEffect[Task], cs: ContextShift[Task]): ConcurrentEffectTests[Task] =
    new ConcurrentEffectTests[Task] {
      def laws =
        new ConcurrentEffectLaws[Task] with ConcurrentEffectLawsOverrides[Task] {
          override val F: ConcurrentEffect[Task]        = ce
          override val contextShift: ContextShift[Task] = cs
        }
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
    with DefaultRuntime {

  override val Platform = PlatformLive.makeDefault().withReportFailure(_ => ())

  implicit val runtime: Runtime[Any] = this

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
        val duration = 20.seconds
        val leftM    = x.attempt.unsafeRunTimed(duration)
        val rightM   = y.attempt.unsafeRunTimed(duration)

        val res = (leftM, rightM).mapN {
          (_, _) match {
            case (Right(l), Right(r)) => A.eqv(l, r)
            case (Left(l), Left(r))   => l == r
            case _                    => false
          }
        }

        res.getOrElse {
          println(s"One of actions timed out, results are: leftM = $leftM, rightM = $rightM")
          false
        }
      }
    }

  // TODO: reintroduce repeated ConcurrentTests as they're removed due to the hanging CI builds (see https://github.com/scalaz/scalaz-zio/pull/482)
  checkAllAsync(s"ConcurrentEffect[Task]", implicit tctx => IOConcurrentEffectTests().concurrentEffect[Int, Int, Int])
  checkAllAsync("Effect[Task]", implicit tctx => EffectTests[Task].effect[Int, Int, Int])
  checkAllAsync("Concurrent[TaskR[Int, ?]]", (_) => ConcurrentTests[TaskR[Int, ?]].concurrent[Int, Int, Int])
  checkAllAsync("Concurrent[TaskR[String, ?]]", (_) => ConcurrentTests[TaskR[String, ?]].concurrent[Int, Int, Int])
  checkAllAsync("Concurrent[Task]", (_) => ConcurrentTests[Task].concurrent[Int, Int, Int])
  checkAllAsync(
    "MonadError[ZIO[String, Int, ?]]",
    (_) => MonadErrorTests[ZIO[String, Int, ?], Int].monadError[Int, Int, Int]
  )
  checkAllAsync("MonadError[IO[Int, ?]]", (_) => MonadErrorTests[IO[Int, ?], Int].monadError[Int, Int, Int])
  checkAllAsync(
    "Alternative[ZIO[String, Int, ?]]",
    (_) => AlternativeTests[ZIO[String, Int, ?]].alternative[Int, Int, Int]
  )
  checkAllAsync("Alternative[IO[Int, ?]]", (_) => AlternativeTests[IO[Int, ?]].alternative[Int, Int, Int])
  checkAllAsync(
    "Alternative[IO[Option[Unit], ?]]",
    (_) => AlternativeTests[IO[Option[Unit], ?]].alternative[Int, Int, Int]
  )
  checkAllAsync("SemigroupK[TaskR[String, ?]]", (_) => SemigroupKTests[TaskR[String, ?]].semigroupK[Int])
  checkAllAsync("SemigroupK[Task]", (_) => SemigroupKTests[Task].semigroupK[Int])
  checkAllAsync(
    "Bifunctor[ZIO[String, ?, ?]]",
    (_) => BifunctorTests[ZIO[String, ?, ?]].bifunctor[Int, Int, Int, Int, Int, Int]
  )
  checkAllAsync("Bifunctor[IO]", (_) => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task, Task.Par]", (_) => ParallelTests[Task, Util.Par].parallel[Int, Int])
  checkAllAsync(
    "Parallel[TaskR[Int, ?], Task.ParIO[IO, Throwable, ?]]",
    (_) => ParallelTests[TaskR[Int, ?], ParIO[Int, Throwable, ?]].parallel[Int, Int]
  )

  implicit def catsEQ[R, E, A: Eq]: Eq[ZIO[R, E, A]] =
    new Eq[ZIO[R, E, A]] {
      import scalaz.zio.duration._

      def eqv(io1: ZIO[R, E, A], io2: ZIO[R, E, A]): Boolean = {
        val v1  = unsafeRunSync(io1.asInstanceOf[IO[E, A]].timeout(20.seconds)).map(_.get)
        val v2  = unsafeRunSync(io2.asInstanceOf[IO[E, A]].timeout(20.seconds)).map(_.get)
        val res = v1 === v2
        if (!res) {
          println(s"Mismatch: $v1 != $v2")
        }
        res
      }
    }

  implicit def catsParEQ[R, E: Eq, A: Eq]: Eq[ParIO[R, E, A]] =
    new Eq[ParIO[R, E, A]] {
      def eqv(io1: ParIO[R, E, A], io2: ParIO[R, E, A]): Boolean =
        unsafeRun(Par.unwrap(io1.asInstanceOf[ParIO[Any, E, A]]).either) === unsafeRun(
          Par.unwrap(io2.asInstanceOf[ParIO[Any, E, A]]).either
        )
    }

  implicit def params: Parameters =
    Parameters.default.copy(allowNonTerminationLaws = false)

  implicit def zioArbitrary[E, A: Arbitrary: Cogen, R: Arbitrary: Cogen]: Arbitrary[ZIO[R, E, A]] =
    Arbitrary(genSuccess[E, A])

  implicit def ioArbitrary[E, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(genSuccess[E, A])

  implicit def ioParArbitrary[E, A: Arbitrary: Cogen, R <: Any]: Arbitrary[ParIO[R, E, A]] =
    Arbitrary(genSuccess[E, A].map(Par.apply))
}
