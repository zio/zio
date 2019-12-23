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

  @Benchmark
  def intInsertion(): List[Int] = insertion(ints)

  @Benchmark
  def intUpdate(): List[Int] = update(ints)(v => v + v)

  @Benchmark
  def intTransform(): List[Int] = transform(ints)(v => v + v)

  @Benchmark
  def intTransformM(): List[Int] = transformM(ints)(v => STM.succeed(v + v))

  @Benchmark
  def intRemoval(): Unit = removal(ints)

  @Benchmark
  def intLookup(): List[Int] = lookup(ints)

  @Benchmark
  def stringInsertion(): List[String] = insertion(strings)

  @Benchmark
  def stringUpdate(): List[String] = update(strings)(v => v + v)

  @Benchmark
  def stringTransform(): List[String] = transform(strings)(v => v + v)

  @Benchmark
  def stringTransformM(): List[String] = transformM(strings)(v => STM.succeed(v + v))

  @Benchmark
  def stringRemoval(): Unit = removal(strings)

  @Benchmark
  def stringLookup(): List[String] = lookup(strings)

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

  private def update[A](list: Int => List[A])(f: A => A): List[A] = {
    val items = list(size)

    val tx =
      for {
        map   <- TMap.fromIterable(items.map(i => (i, i)))
        _     <- STM.foreach(items)(i => map.put(i, f(i)))
        vals  <- map.values
      } yield vals

    IOBenchmarks.unsafeRun(tx.commit)
  }

  private def transform[A](list: Int => List[A])(f: A => A): List[A] = {
    val items = list(size)

    val tx =
      for {
        map   <- TMap.fromIterable(items.map(i => (i, i)))
        _     <- map.transform((k, v) => (k, f(v)))
        vals  <- map.values
      } yield vals

    IOBenchmarks.unsafeRun(tx.commit)
  }

  private def transformM[A](list: Int => List[A])(f: A => STM[Nothing, A]): List[A] = {
    val items = list(size)

    val tx =
      for {
        map   <- TMap.fromIterable(items.map(i => (i, i)))
        _     <- map.transformM((k, v) => f(v).map(k -> _))
        vals  <- map.values
      } yield vals

    IOBenchmarks.unsafeRun(tx.commit)
  }

  private def removal[A](list: Int => List[A]): Unit = {
    val items = list(size)

    val tx =
      for {
        map   <- TMap.fromIterable(items.map(i => (i, i)))
        _     <- STM.foreach(items)(map.delete)
      } yield ()

    IOBenchmarks.unsafeRun(tx.commit)
  }

  private def lookup[A](list: Int => List[A]): List[A] = {
    val items = list(size)

    val tx =
      for {
        map   <- TMap.fromIterable(items.map(i => (i, i)))
        res   <- STM.foreach(items)(map.get)
      } yield res.flatten

    IOBenchmarks.unsafeRun(tx.commit)
  }

  private def ints(n: Int): List[Int] = Random.shuffle((1 to n).toList)

  private def strings(n: Int): List[String] = List.fill(n)(UUID.randomUUID().toString())
}
