package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit
import scala.collection.immutable.Range

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ArrayFillBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  def createTestArray: Array[Int] = Range.inclusive(1, size).toArray.reverse

  @Benchmark
  def zioArrayFill(): Unit = {
    import BenchmarkUtil.unsafeRun

    def arrayFill(array: Array[Int])(i: Int): UIO[Unit] =
      if (i >= array.length) ZIO.unit
      else ZIO.succeed(array.update(i, i)).flatMap(_ => arrayFill(array)(i + 1))

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(
        for {
          array <- ZIO.succeed[Array[Int]](createTestArray)
          _     <- arrayFill(array)(0)
        } yield ()
      )
    }
  }

  @Benchmark
  def monoArrayFill(): Unit = {
    import reactor.core.publisher.Mono

    def arrayFill(array: Array[Int])(i: Int): Mono[Unit] =
      if (i >= array.length) Mono.fromSupplier(() => ())
      else
        Mono
          .fromSupplier(() => array.update(i, i))
          .flatMap(_ => arrayFill(array)(i + 1))

    val _ = (for {
      array <- Mono.fromSupplier(() => createTestArray)
      _     <- arrayFill(array)(0)
    } yield ())
      .block()
  }

  @Benchmark
  def catsArrayFill(): Unit = {
    import cats.effect.IO

    def arrayFill(array: Array[Int])(i: Int): IO[Unit] =
      if (i >= array.length) IO.unit
      else IO(array.update(i, i)).flatMap(_ => arrayFill(array)(i + 1))

    (for {
      array <- IO(createTestArray)
      _     <- arrayFill(array)(0)
    } yield ()).unsafeRunSync()
  }
}
