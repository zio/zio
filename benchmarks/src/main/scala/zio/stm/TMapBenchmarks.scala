package zio.stm

import java.util.UUID
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio._
import scala.util.Random

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TMapBenchmarks {

  @Param(Array("10", "100", "1000", "10000", "100000"))
  private var size: Int = _

  @Param(Array("20"))
  private var depth: Int = _

  @Benchmark
  def intInsertion(): List[Int] = insertion(ints)

  @Benchmark
  def intRemoval(): Unit = removal(ints)

  @Benchmark
  def intLookup(): List[Int] = lookup(ints)

  @Benchmark
  def stringInsertion(): List[String] = insertion(strings)

  @Benchmark
  def stringRemoval(): Unit = removal(strings)

  @Benchmark
  def stringLookup(): List[String] = lookup(strings)

  @Benchmark
  def cachedFibonacci(): BigInt = {
    def fib(n: Int)(cache: TMap[Int, BigInt]): STM[Nothing, BigInt] =
      if (n <= 1)
        STM.succeed[BigInt](n)
      else
        readThrough(cache, n - 1).flatMap { a =>
          readThrough(cache, n - 2).map(b => a + b)
        }

    def readThrough(cache: TMap[Int, BigInt], key: Int): STM[Nothing, BigInt] =
      cache.get(key).flatMap {
        case None        => fib(key)(cache).flatMap(value => cache.put(key, value).as(value))
        case Some(value) => STM.succeed(value)
      }

    val tx = TMap.empty[Int, BigInt].flatMap(fib(depth))

    IOBenchmarks.unsafeRun(tx.commit)
  }

  private def insertion[A](list: Int => List[A]): List[A] = {
    val tx =
      for {
        map   <- TMap.empty[A, A]
        items = list(size)
        _     <- STM.foreach(items)(i => map.put(i, i))
        res   <- map.keys
      } yield res

    IOBenchmarks.unsafeRun(tx.commit)
  }

  private def removal[A](list: Int => List[A]): Unit = {
    val tx =
      for {
        map   <- TMap.empty[A, A]
        items = list(size)
        _     <- STM.foreach(items)(i => map.put(i, i))
        _     <- STM.foreach(items)(map.delete)
      } yield ()

    IOBenchmarks.unsafeRun(tx.commit)
  }

  private def lookup[A](list: Int => List[A]): List[A] = {
    val tx =
      for {
        map   <- TMap.empty[A, A]
        items = list(size)
        _     <- STM.foreach(items)(i => map.put(i, i))
        res   <- STM.foreach(items)(map.get)
      } yield res.flatten

    IOBenchmarks.unsafeRun(tx.commit)
  }

  private def ints(n: Int): List[Int] = Random.shuffle((1 to n).toList)

  private def strings(n: Int): List[String] = List.fill(n)(UUID.randomUUID().toString())
}
