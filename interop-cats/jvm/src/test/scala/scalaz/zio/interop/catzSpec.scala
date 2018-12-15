package scalaz.zio
package interop

import cats.Eq
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.discipline.{ ConcurrentTests, EffectTests, Parameters }
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
import scalaz.zio.internal.Env

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

    for ((id, prop) ← ruleSet.all.properties)
      test(name + "." + id) {
        check(prop)
      }
  }

  override implicit def eqIO[A](implicit A: Eq[A], ec: TestContext): Eq[cats.effect.IO[A]] =
    new Eq[cats.effect.IO[A]] {
      def eqv(x: cats.effect.IO[A], y: cats.effect.IO[A]): Boolean = {
        val l = x.attempt.unsafeRunSync
        val r = y.attempt.unsafeRunSync

        (l, r) match {
          case (Right(l), Right(r)) => A.eqv(l, r)
          case (Left(l), Left(r))   => Eq[Throwable].eqv(l, r)
          case _                    => false
        }
      }
    }

  (1 to 50).foreach { s =>
    checkAllAsync(s"Concurrent[Task] $s", (_) => ConcurrentTests[Task].concurrent[Int, Int, Int])
  }
  checkAllAsync("Effect[Task]", implicit e => EffectTests[Task].effect[Int, Int, Int])
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
