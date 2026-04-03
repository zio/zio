package zio

import cats.effect.kernel.Deferred
import cats.syntax.traverse._
import cats.instances.list._
import cats.effect.unsafe.implicits.global
import cats.effect.{IO => CIO}
import cats.syntax.foldable._
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(value = 3)
class PromiseBenchmarks {

  val n            = 100000
  val waiters: Int = 8

  def createWaitersZIO(promise: Promise[Nothing, Unit]): ZIO[Any, Nothing, Seq[Fiber[Nothing, Unit]]] =
    ZIO.foreach(Vector.range(0, waiters))(_ => promise.await.forkDaemon)

  def createWaitersCats(promise: Deferred[CIO, Unit]) =
    List.range(0, waiters).traverse(_ => promise.get.start)

  @Benchmark
  def zioPromiseDoneAwait(): Unit = {

    val io =
      Promise
        .make[Nothing, Unit]
        .flatMap { promise =>
          promise.done(Exit.unit) *> promise.await
        }
        .repeatN(n)

    unsafeRun(io)
  }

  @Benchmark
  def catsPromiseDoneAwait(): Unit = {

    val io =
      Deferred[CIO, Unit].flatMap { promise =>
        promise.complete(()).flatMap(_ => promise.get)
      }.replicateA_(n)

    io.unsafeRunSync()
  }

  @Benchmark
  def zioPromiseMultiAwaitDone(): Unit = {
    val io = Promise
      .make[Nothing, Unit]
      .flatMap { promise =>
        for {
          fibers <- createWaitersZIO(promise)
          _      <- promise.done(Exit.unit)
          _      <- ZIO.foreachDiscard(fibers)(_.await)
        } yield ()
      }
      .repeatN(1023)

    unsafeRun(io)
  }

  @Benchmark
  def catsPromiseMultiAwaitDone(): Unit = {
    val io =
      Deferred[CIO, Unit].flatMap { promise =>
        for {
          fibers <- createWaitersCats(promise)
          _      <- promise.complete(())
          _      <- fibers.traverse_(_.join)
        } yield ()
      }.replicateA_(1023)

    io.unsafeRunSync()
  }

  @Benchmark
  def zioPromiseMultiAwaitMultiDone(): Unit = {
    def createCompleters(promise: Promise[Nothing, Unit], latch: Promise[Nothing, Unit]) =
      ZIO.foreach(Vector.range(0, waiters))(_ => (latch.await *> promise.done(Exit.unit)).forkDaemon)

    val io = {
      for {
        latch   <- Promise.make[Nothing, Unit]
        promise <- Promise.make[Nothing, Unit]
        waiters <- createWaitersZIO(promise)
        fibers  <- createCompleters(promise, latch)
        _       <- latch.done(Exit.unit)
        result  <- promise.await
      } yield result
    }.repeatN(1023)

    unsafeRun(io)
  }

  @Benchmark
  def catsPromiseMultiAwaitMultiDone(): Unit = {
    def createCompleters(promise: Deferred[CIO, Unit], latch: Deferred[CIO, Unit]) =
      List.range(0, waiters).traverse(_ => (latch.get *> promise.complete(())).start)

    val io = {
      for {
        latch   <- Deferred[CIO, Unit]
        promise <- Deferred[CIO, Unit]
        waiters <- createWaitersCats(promise)
        fibers  <- createCompleters(promise, latch)
        _       <- latch.complete(())
        result  <- promise.get
      } yield result
    }.replicateA_(1023)

    io.unsafeRunSync()
  }
}
