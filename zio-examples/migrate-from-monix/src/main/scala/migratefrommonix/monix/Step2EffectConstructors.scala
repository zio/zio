package migratefrommonix.monix

import cats.effect.ExitCode
import monix.eval.{Coeval, Task, TaskApp}

import scala.concurrent.duration._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Translating Effect Constructors + Coeval
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.monix.Step2EffectConstructors"
 */
object Step2EffectConstructors extends TaskApp {
  def run(args: List[String]): Task[ExitCode] = {
    val fetched    = Task.eval("result from database")
    val constant   = Task.pure(42)
    val unit       = Task.unit
    val raiseErr   = Task.raiseError(new RuntimeException("oops"))
    val fromEither = Task.fromEither(Right(1): Either[Throwable, Int])
    val fromTry    = Task.fromTry(scala.util.Try(1))
    val slept      = Task.sleep(10.millis)
    val deferred   = Task.defer(Task.eval("deferred"))

    // Coeval: synchronous computation
    val coevalValue: Int = Coeval.eval(42 * 2).value()

    for {
      f <- fetched
      c <- constant
      _ <- unit
      _ <- fromEither
      _ <- fromTry
      _ <- slept
      d <- deferred
      _ <- Task.eval(println(s"fetched=$f constant=$c coeval=$coevalValue deferred=$d"))
    } yield ExitCode.Success
  }
}
