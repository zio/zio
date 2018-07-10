// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import scala.concurrent.Await

import IOBenchmarks._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class IOShallowAttemptBenchmark {
  case class ScalazError(message: String)

  @Param(Array("1000"))
  var depth: Int = _

  @Benchmark
  def thunkShallowAttempt(): BigInt = {
    def throwup(n: Int): Thunk[BigInt] =
      if (n == 0) throwup(n + 1).attempt.map(_.fold(_ => 0, a => a))
      else if (n == depth) Thunk(1)
      else throwup(n + 1).attempt.map(_.fold(_ => 0, a => a)).flatMap(_ => Thunk.fail(new Error("Oh noes!")))

    throwup(0).unsafePerformIO()
  }

  @Benchmark
  def futureShallowAttempt(): BigInt = {
    import scala.concurrent.Future
    import scala.concurrent.duration.Duration.Inf

    def throwup(n: Int): Future[BigInt] =
      if (n == 0) throwup(n + 1) recover { case _ => 0 } else if (n == depth) Future(1)
      else
        throwup(n + 1).recover { case _ => 0 }
          .flatMap(_ => Future.failed(new Exception("Oh noes!")))

    Await.result(throwup(0), Inf)
  }

  @Benchmark
  def monixShallowAttempt(): BigInt = {
    import monix.eval.Task

    def throwup(n: Int): Task[BigInt] =
      if (n == 0) throwup(n + 1).attempt.map(_.fold(_ => 0, a => a))
      else if (n == depth) Task(1)
      else
        throwup(n + 1).attempt.flatMap {
          case Left(_)  => Task.now(0)
          case Right(_) => Task.raiseError(new Error("Oh noes!"))
        }

    throwup(0).runSyncMaybe.right.get
  }

  @Benchmark
  def scalazShallowAttempt(): BigInt = {
    def throwup(n: Int): IO[ScalazError, BigInt] =
      if (n == 0) throwup(n + 1).redeemPure[ScalazError, BigInt](_ => 50, identity)
      else if (n == depth) IO.point(1)
      else throwup(n + 1).redeem[ScalazError, BigInt](_ => IO.now(0), _ => IO.fail(ScalazError("Oh noes!")))

    unsafePerformIO(throwup(0))
  }

  @Benchmark
  def catsShallowAttempt(): BigInt = {
    import cats.effect._

    def throwup(n: Int): IO[BigInt] =
      if (n == 0) throwup(n + 1).attempt.map(_.fold(_ => 0, a => a))
      else if (n == depth) IO(1)
      else
        throwup(n + 1).attempt.flatMap {
          case Left(_)  => IO(0)
          case Right(_) => IO.raiseError(new Error("Oh noes!"))
        }

    throwup(0).unsafeRunSync()
  }
}
