package scalaz.zio
package interop

import java.io.{ByteArrayOutputStream, PrintStream}

import scala.util.control.NonFatal
import cats.Eq
import cats.implicits._
import cats.syntax.all._
import cats.effect.laws.discipline.EffectTests
import org.typelevel.discipline.scalatest.Discipline
import cats.effect.laws.util.{TestContext, TestInstances}
import org.typelevel.discipline.Laws
import org.scalatest.prop.Checkers
import org.scalatest.{FunSuite, Matchers}
import org.scalacheck.{Arbitrary, Cogen}
import cats.laws.discipline.{BifunctorTests, MonadErrorTests, AlternativeTests, SemigroupKTests}

import catz._

class catzSpec extends FunSuite with Matchers with Checkers with Discipline with TestInstances with GenIO with RTS {

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

  checkAllAsync("Effect[Task]", implicit e => EffectTests[Task].effect[Int, Int, Int])
  checkAllAsync("MonadError[IO[Int, ?]]", implicit e => MonadErrorTests[IO[Int, ?], Int].monadError[Int, Int, Int])
  checkAllAsync("Alternative[IO[Int, ?]]", implicit e => AlternativeTests[IO[Int, ?]].alternative[Int, Int, Int])
  checkAllAsync("SemigroupK[IO[Nothing, ?]]", implicit e => SemigroupKTests[IO[Nothing, ?]].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", implicit e => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])

  implicit def catsEQ[E, A: Eq]: Eq[IO[E, A]] =
    new Eq[IO[E, A]] {
      def eqv(io1: IO[E, A], io2: IO[E, A]): Boolean =
        unsafeRun(io1.attempt) === unsafeRun(io2.attempt)
    }

  implicit def ioArbitrary[E, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(genSuccess[E, A])

}
