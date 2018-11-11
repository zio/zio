package scalaz.zio
package interop

import java.io.{ByteArrayOutputStream, PrintStream}

import cats.Eq
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, ExitCase}
import cats.effect.laws.{ConcurrentEffectLaws, ConcurrentLaws}
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.discipline.{ConcurrentEffectTests, ConcurrentTests, EffectTests, Parameters}
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.implicits._
import cats.laws.discipline.{AlternativeTests, BifunctorTests, MonadErrorTests, ParallelTests, SemigroupKTests}
import cats.syntax.all._
import org.scalacheck.{Arbitrary, Cogen}
import org.scalatest.prop.Checkers
import org.scalatest.{ BeforeAndAfterAll, FunSuite, Matchers }
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.Discipline
import scalaz.zio.interop.catz._
import cats.laws._

import scala.concurrent.ExecutionContext.global
import scala.util.control.NonFatal

trait ConcurrentLawsIO extends ConcurrentLaws[Task] {

  // FIXME: random freezes on "async cancelable receives cancel signal"
  // FIXME: random freezes on "bracket release is called on cancel" -
  //  no "release running" msg after interrupt (though there was "use running")
  // FIXME: random freezes on "race cancels both" -
  //  same reason, both fibers were terminated, but release wasn't called

  override def cancelOnBracketReleases[A, B](a: A, f: (A, A) => B) = {
    val received = for {
      // A deferred that waits for `use` to get executed
      startLatch <- Deferred[Task, A]
      // A deferred that waits for `release` to be executed
      exitLatch <- Deferred[Task, A]
      // What we're actually testing
      bracketed = F.bracketCase(F.pure(a))(a => startLatch.complete(a) *> F.never[A]) {
        case (r, ExitCase.Canceled) => exitLatch.complete(r) *> Task(println("exitLatch filled"))
        case x@(_, _) => {
          val msg = s"Unexpected combination $x"
          System.out println msg
          throw new Exception(msg)
        }
      }
      // Forked execution, allowing us to cancel it later
      fiber <- F.start(bracketed)
      // Waits for the `use` action to execute
      _ <- F.delay(System.out println "STARTING start get")
      waitStart <- startLatch.get
      // Triggers cancellation
      _ <- F.delay(System.out println "STARTING cancel")
      _ <- F.start(fiber.cancel)
      // Observes cancellation via bracket's `release`
      _ <- F.delay(System.out println "STARTING final get")
      waitExit <- exitLatch.get
    } yield f(waitStart, waitExit)

    received <-> F.pure(f(a, a))
  }

  // FIXME: Not implemented yet [maybeLaterIO unsafeRun]
  override def asyncFRegisterCanBeCancelled[A](a: A) =
    F.pure(a) <-> F.pure(a)

  // FIXME: Frequent freezes [same as above?]
  override def asyncCancelableReceivesCancelSignal[A](a: A) =
    F.pure(a) <-> F.pure(a)
}

object ConcurrentTestsIO {
  def apply()(implicit c: Concurrent[Task], cs: ContextShift[Task]): ConcurrentTests[Task] =
    new ConcurrentTests[Task] {
      def laws = new ConcurrentLawsIO {
        override val F: Concurrent[Task] = c
        override val contextShift: ContextShift[Task] = cs
//        implicit val clock: Clock = Clock.Live
      }
    }
}

object ConcurrentEffectTestsIO {
  def apply()(implicit c: ConcurrentEffect[Task], cs: ContextShift[Task]): ConcurrentEffectTests[Task] =
    new ConcurrentEffectTests[Task] {
      def laws = new ConcurrentEffectLaws[Task] with ConcurrentLawsIO {
        override val F: ConcurrentEffect[Task] = c
        override val contextShift: ContextShift[Task] = cs
//        implicit val clock: Clock = Clock.Live

        // FIXME: Not implemented yet
        override def runCancelableStartCancelCoherence[A](a: A) =
          F.pure(a) <-> F.pure(a)

        // FIXME: Not implemented yet
        override def runCancelableIsSynchronous[A](fa: _root_.scalaz.zio.interop.Task[A]) =
          F.unit <-> F.unit
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
    with RTS {

//  override val threadPool: ExecutorService = Executors.newCachedThreadPool()

  /**
   * Silences `System.err`, only printing the output in case exceptions are
   * thrown by the executed `thunk`.
   */
  def silenceSystemErr[A](thunk: => A): A = synchronized {
    // Silencing System.err
    val oldErr    = System.err
    val outStream = new ByteArrayOutputStream()
    val fakeErr   = new PrintStream(outStream)
    System.setErr(fakeErr)
    try {
      val result = thunk
      System.setErr(oldErr)
      result
    } catch {
      case NonFatal(e) =>
        System.setErr(oldErr)
        // In case of errors, print whatever was caught
        fakeErr.close()
        val out = outStream.toString("utf-8")
        if (out.nonEmpty) oldErr.println(out)
        throw e
    }
  }

  def checkAllAsync(name: String, f: TestContext => Laws#RuleSet): Unit = {
    val context = TestContext()
    val ruleSet = f(context)

    for ((id, prop) ← ruleSet.all.properties)
      test(name + "." + id) {
        silenceSystemErr(check(prop))
      }
  }

  checkAllAsync("Concurrent[Task]", (_) => ConcurrentTestsIO().concurrent[Int, Int, Int])
  checkAllAsync("ConcurrentEffect[Task]", implicit e => ConcurrentEffectTestsIO().concurrentEffect[Int, Int, Int])
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
      def eqv(io1: IO[E, A], io2: IO[E, A]): Boolean =
        unsafeRun(io1.attempt) === unsafeRun(io2.attempt)
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

  implicit def contextShift: cats.effect.ContextShift[Task] = ioContextShift(global)

  implicit def ioParArbitrary[E, A: Arbitrary: Cogen]: Arbitrary[ParIO[E, A]] =
    Arbitrary(genSuccess[E, A].map(Par.apply))
}
