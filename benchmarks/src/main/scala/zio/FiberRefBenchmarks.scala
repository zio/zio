package zio

import org.openjdk.jmh.annotations.{
  Benchmark,
  BenchmarkMode,
  Fork,
  Measurement,
  Mode,
  OutputTimeUnit,
  Param,
  Scope => JScope,
  State,
  Threads,
  Warmup
}
import zio.BenchmarkUtil.verify

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
class FiberRefBenchmarks {
  @Param(Array("32"))
  var n: Int = _

  @Param(Array("10000"))
  var m: Int = _

  @Benchmark
  def createUpdateAndRead(): Unit =
    createUpdateAndRead(BenchmarkUtil)

  @Benchmark
  def justYield(): Unit =
    justYield(BenchmarkUtil)

  @Benchmark
  def createFiberRefsAndYield(): Unit =
    createFiberRefsAndYield(BenchmarkUtil)

  @Benchmark
  def createAndJoin(): Unit =
    createAndJoin(BenchmarkUtil)

  @Benchmark
  def createAndJoinInitialValue(): Unit =
    createAndJoinInitialValue(BenchmarkUtil)

  @Benchmark
  def createAndJoinUpdatesWide(): Unit =
    createAndJoinUpdatesWide(BenchmarkUtil)

  @Benchmark
  def createAndJoinUpdatesWideExpensive(): Unit =
    createAndJoinUpdatesWideExpensive(BenchmarkUtil)

  @Benchmark
  def createAndJoinUpdatesDeep(): Unit =
    createAndJoinUpdatesDeep(BenchmarkUtil)

  private def justYield(runtime: Runtime[Any]) = runtime.unsafeRun {
    for {
      _ <- ZIO.foreachDiscard(1.to(n))(_ => ZIO.yieldNow)
    } yield ()
  }

  private def createFiberRefsAndYield(runtime: Runtime[Any]) = runtime.unsafeRun {
    ZIO.scoped {
      for {
        fiberRefs <- ZIO.foreach(1.to(n))(i => FiberRef.make(i))
        _         <- ZIO.foreachDiscard(1.to(n))(_ => ZIO.yieldNow)
        values    <- ZIO.foreachPar(fiberRefs)(_.get)
        _         <- verify(values == 1.to(n))(s"Got $values")
      } yield ()
    }
  }

  private def createUpdateAndRead(runtime: Runtime[Any]) = runtime.unsafeRun {
    ZIO.scoped {
      for {
        fiberRefs <- ZIO.foreach(1.to(n))(i => FiberRef.make(i))
        values1   <- ZIO.foreachPar(fiberRefs)(ref => ref.update(-_) *> ref.get)
        values2   <- ZIO.foreachPar(fiberRefs)(_.get)
        _ <- verify(values1.forall(_ < 0) && values1.size == values2.size)(
               s"Got \nvalues1: $values1, \nvalues2: $values2"
             )
      } yield ()
    }
  }

  private def createAndJoin(runtime: Runtime[Any]) = runtime.unsafeRun {
    ZIO.scoped {
      for {
        fiberRefs <- ZIO.foreach(1.to(n))(i => FiberRef.makePatch(i, addDiffer, 0))
        _         <- ZIO.foreachDiscard(fiberRefs)(_.update(_ + 1))
        _         <- ZIO.collectAllParDiscard(List.fill(m)(ZIO.unit))
      } yield ()
    }
  }

  private def createAndJoinInitialValue(runtime: Runtime[Any]) = runtime.unsafeRun {
    ZIO.scoped {
      for {
        _ <- ZIO.foreach(1.to(n))(i => FiberRef.makePatch(i, addDiffer, 0))
        _ <- ZIO.collectAllParDiscard(List.fill(m)(ZIO.unit))
      } yield ()
    }
  }

  private def createAndJoinUpdatesWide(runtime: Runtime[Any]) = runtime.unsafeRun {
    ZIO.scoped {
      for {
        fiberRefs <- ZIO.foreach(1.to(n))(i => FiberRef.makePatch(i, addDiffer, 0))
        _         <- ZIO.foreachDiscard(fiberRefs)(_.update(_ + 1))
        _         <- ZIO.collectAllParDiscard(List.fill(m)(ZIO.foreachDiscard(fiberRefs)(_.update(_ + 1))))
      } yield ()
    }
  }

  private def createAndJoinUpdatesWideExpensive(runtime: Runtime[Any]) = runtime.unsafeRun {
    ZIO.scoped {
      for {
        fiberRefs <- ZIO.foreach(1.to(n))(i => FiberRef.makeSet(1.to(i).toSet))
        _         <- ZIO.foreachDiscard(fiberRefs)(_.update(_.map(_ + 1)))
        _         <- ZIO.collectAllParDiscard(List.fill(m)(ZIO.foreachDiscard(fiberRefs)(_.update(_.map(_ + 1)))))
      } yield ()
    }
  }

  private def createAndJoinUpdatesDeep(runtime: Runtime[Any]) = runtime.unsafeRun {
    ZIO.scoped {
      ZIO.foreach(1.to(n))(i => FiberRef.makePatch(i, addDiffer, 0)).flatMap { fiberRefs =>
        def go(depth: Int): UIO[Unit] =
          if (depth <= 0) ZIO.unit
          else
            for {
              _  <- ZIO.foreachDiscard(fiberRefs)(_.update(_ + 1))
              f1 <- go(depth - 1).fork
              _  <- f1.join
            } yield ()

        go(m)
      }
    }
  }

  private val addDiffer = new Differ[Int, Int] {
    def combine(first: Int, second: Int): Int   = first + second
    def diff(oldValue: Int, newValue: Int): Int = newValue - oldValue
    def empty: Int                              = 0
    def patch(patch: Int)(oldValue: Int): Int   = oldValue + patch
  }
}
