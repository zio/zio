package zio.interop

import cats.Eq
import cats.effect.laws.util.{ TestContext, TestInstances }
import cats.implicits._
import cats.mtl.laws.discipline._
import cats.mtl.{ ApplicativeAsk, ApplicativeHandle, ApplicativeLocal, FunctorRaise }
import org.scalacheck.{ Arbitrary, Cogen, Gen }
import org.scalatest.prop.Checkers
import org.scalatest.{ BeforeAndAfterAll, FunSuite, Matchers }
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.Discipline
import zio.Exit.Cause
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.internal.PlatformLive
import zio.interop.catz._
import zio.interop.catz.mtl._
import zio.random.Random
import zio.system.System

class catzMtlSpec
    extends FunSuite
    with BeforeAndAfterAll
    with Matchers
    with Checkers
    with Discipline
    with TestInstances
    with GenIO {

  type Env   = Clock with Console with System with Random
  type Ctx   = String
  type Error = String

  implicit def rts(implicit tc: TestContext): Runtime[Env] = new DefaultRuntime {
    override val Platform = PlatformLive.fromExecutionContext(tc).withReportFailure(_ => ())
  }

  implicit def zioEqCause[E: Eq]: Eq[Cause[E]] =
    new Eq[Cause[E]] {
      def eqv(c1: Cause[E], c2: Cause[E]): Boolean = (c1, c2) match {
        case (Cause.Interrupt, Cause.Interrupt)       => true
        case (Cause.Die(x), Cause.Die(y))             => x eqv y
        case (Cause.Fail(x), Cause.Fail(y))           => x eqv y
        case (Cause.Traced(x, _), y)                  => eqv(x, y)
        case (x, Cause.Traced(y, _))                  => eqv(x, y)
        case (Cause.Then(lx, rx), Cause.Then(ly, ry)) => eqv(lx, ly) && eqv(rx, ry)
        case (Cause.Both(lx, rx), Cause.Both(ly, ry)) => eqv(lx, ly) && eqv(rx, ry)
        case _                                        => false
      }
    }

  implicit def zioEq[R: Arbitrary, E: Eq, A: Eq](implicit tc: TestContext): Eq[ZIO[R, E, A]] = {
    def run(r: R, zio: ZIO[R, E, A]) = taskEffectInstance.toIO(zio.provide(r).sandbox.either)
    Eq.instance((io1, io2) => Arbitrary.arbitrary[R].sample.fold(false)(r => run(r, io1) eqv run(r, io2)))
  }

  implicit def zioArbitrary[R: Cogen, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZIO[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[R => IO[E, A]].map(ZIO.environment[R].flatMap(_)))

  implicit def ioArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(Gen.oneOf(genIO[E, A], genLikeTrans(genIO[E, A]), genIdentityTrans(genIO[E, A])))

  def checkAllAsync(name: String, f: TestContext => Laws#RuleSet): Unit =
    checkAll(name, f(TestContext()))

  checkAllAsync(
    "ApplicativeAsk[ZIO[Ctx, Error, ?]]",
    implicit tc => ApplicativeAskTests[ZIO[Ctx, Error, ?], Ctx].applicativeAsk[Ctx]
  )

  checkAllAsync(
    "ApplicativeLocal[ZIO[Ctx, Error, ?]]",
    implicit tc => ApplicativeLocalTests[ZIO[Ctx, Error, ?], Ctx].applicativeLocal[Ctx, Int]
  )

  checkAllAsync(
    "FunctorRaise[ZIO[Ctx, Error, ?]]",
    implicit tc => FunctorRaiseTests[ZIO[Ctx, Error, ?], Error].functorRaise[Int]
  )

  checkAllAsync(
    "ApplicativeHandle[ZIO[Ctx, Error, ?]]",
    implicit tc => ApplicativeHandleTests[ZIO[Ctx, Error, ?], Error].applicativeHandle[Int]
  )

  def askSummoner[R, E]    = ApplicativeAsk[ZIO[R, E, ?], R]
  def localSummoner[R, E]  = ApplicativeLocal[ZIO[R, E, ?], R]
  def raiseSummoner[R, E]  = FunctorRaise[ZIO[R, E, ?], E]
  def handleSummoner[R, E] = ApplicativeHandle[ZIO[R, E, ?], E]
}
