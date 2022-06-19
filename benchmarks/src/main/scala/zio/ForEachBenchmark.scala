package zio

import cats.effect.unsafe.implicits.global
import cats.effect.{IO => CIO}
import cats.syntax.all._
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(1)
class ForEachBenchmark {

  val size = 100000

  var ints: List[Int] = _

  @Setup(Level.Trial)
  def createList(): Unit =
    ints = List.range(0, size)

  @Benchmark
  def catsForEachFork(): Int = {

    val io = for {
      _ <- ints.traverse_(CIO.pure(_).start)
    } yield 0

    io.unsafeRunSync()
  }

  @Benchmark
  def catsForEachIncrementRef(): Int = {

    val io = for {
      ref   <- cats.effect.Ref[cats.effect.IO].of(0)
      _     <- ints.traverse(v => ref.update(_ + v))
      value <- ref.get
    } yield value

    io.unsafeRunSync()
  }

  @Benchmark
  def catsForEachForkAwait: Int = {

    val io = for {
      fibers <- ints.traverse(CIO.pure(_).start)
      _      <- fibers.traverse(_.join)
    } yield 0

    io.unsafeRunSync()
  }

  @Benchmark
  def catsForkEachUnit(): Int = {

    val io = for {
      _ <- ints.traverse_(_ => CIO.unit)
    } yield 0

    io.unsafeRunSync()
  }

  @Benchmark
  def zioForEachFork(): Int = {

    val io = for {
      _ <- ZIO.foreachDiscard(ints)(ZIO.succeedNow(_).forkDaemon)
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def zioForEachForkAwait(): Int = {

    val io = for {
      fibers <- ZIO.foreach(ints)(ZIO.succeedNow(_).forkDaemon)
      _      <- ZIO.foreach(fibers)(_.await)
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def zioForEachIncrementRef(): Int = {

    val io = for {
      ref   <- Ref.make[Int](0)
      _     <- ZIO.foreach(ints)(v => ref.update(_ + v))
      value <- ref.get
    } yield value

    unsafeRun(io)
  }

  @Benchmark
  def zioForEachUnit(): Int = {

    val io = for {
      _ <- ZIO.foreachDiscard(ints)(_ => ZIO.unit)
    } yield 0

    unsafeRun(io)
  }
}
