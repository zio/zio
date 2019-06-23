package zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class IOPromiseBenchmark {

  var length: Int = 500

  @Benchmark
  def listOfPromises(): Int = {
    val task: UIO[Int] = for {
      promises <- UIO.foreach(1 to length)(_ => Promise.make[Nothing, Int])
      _        <- UIO.foreach_(promises)(_.succeed(1))
      sum      <- UIO.foldLeft(promises)(0)((s, p) => p.await.map(_ + s))
    } yield sum

    IOBenchmarks.unsafeRun(task)
  }

  @Benchmark
  def twoPromises(): Int = {
    def task: UIO[Int] =
      for {
        one <- Promise.make[Nothing, Int]
        two <- Promise.make[Nothing, Int]
        _   <- one.succeed(1)
        _   <- two.succeed(2)
        o   <- one.await
        t   <- two.await
      } yield o + t

    IOBenchmarks.unsafeRun(task)
  }
}
