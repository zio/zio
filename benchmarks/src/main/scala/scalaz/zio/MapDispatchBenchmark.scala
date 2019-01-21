package scalaz.zio
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import scalaz.zio.IO.Tags
import scalaz.zio.MapDispatchBenchmark._

import scala.annotation.switch

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class MapDispatchBenchmark {

  val fm: TestIO          = new FlatMap
  val point: TestIO       = new Point
  val strict: TestIO      = new Strict
  val syncEf: TestIO      = new SyncEffect
  val asyncEffect: TestIO = new AsyncEffect
  val v                   = new MapVisitor
  val a: Int              = 42

  @Benchmark
  def fmMapWithSwitch: Int =
    fm.mapWithSwitch()

  @Benchmark
  def fmSubclass(): Int =
    fm.mapSubclassing()

  @Benchmark
  def fmVisitor(): Int =
    fm.acceptMapVisitor(v)

  @Benchmark
  def asyncEffectMapWithSwitch: Int =
    asyncEffect.mapWithSwitch()

  @Benchmark
  def asyncEffectSubclass(): Int =
    asyncEffect.mapSubclassing()

  @Benchmark
  def asyncEffectVisitor(): Int =
    asyncEffect.acceptMapVisitor(v)

  @Benchmark
  def allMapWithSwitch: Int =
    asyncEffect.mapWithSwitch() +
      fm.mapWithSwitch() +
      syncEf.mapWithSwitch() +
      strict.mapWithSwitch() +
      point.mapWithSwitch()

  @Benchmark
  def allSubclass(): Int =
    asyncEffect.mapSubclassing() +
      fm.mapSubclassing() +
      syncEf.mapSubclassing() +
      strict.mapSubclassing() +
      point.mapSubclassing()

  @Benchmark
  def allVisitor(): Int =
    asyncEffect.acceptMapVisitor(v) +
      fm.acceptMapVisitor(v) +
      syncEf.acceptMapVisitor(v) +
      strict.acceptMapVisitor(v) +
      point.acceptMapVisitor(v)

}

object MapDispatchBenchmark {

  sealed trait TestIO { self =>
    def tag: Int

    def mapSubclassing(): Int

    final def mapWithSwitch(): Int = (self.tag: @switch) match {
      case IO.Tags.Point =>
        self.asInstanceOf[Point]
        1

      case IO.Tags.Strict =>
        self.asInstanceOf[Strict]
        2

      case IO.Tags.Fail =>
        self.asInstanceOf[TestIO]
        3

      case _ => 4
    }

    def acceptMapVisitor(v: MapVisitor): Int
  }

  final class FlatMap extends TestIO {
    override def tag: Int                             = Tags.FlatMap
    override def mapSubclassing(): Int          = 1
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitFlatMap
  }

  final class Point extends TestIO {
    override def tag: Int                             = Tags.Point
    override def mapSubclassing(): Int          = 2
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitPoint
  }

  final class Strict extends TestIO {
    override def tag: Int                             = Tags.Strict
    override def mapSubclassing(): Int          = 3
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitStrict
  }

  final class SyncEffect extends TestIO {
    override def tag: Int                             = Tags.SyncEffect
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitSyncEffect
  }

  final class AsyncEffect extends TestIO {
    override def tag: Int                             = Tags.AsyncEffect
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitAsyncEffect
  }

  final class Redeem extends TestIO {
    override def tag: Int                             = Tags.Redeem
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitOther()
  }

  final class Fork extends TestIO {
    override def tag: Int                             = Tags.Fork
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitOther()
  }

  final class Uninterruptible extends TestIO {
    override def tag: Int                             = Tags.Uninterruptible
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitOther()
  }

  final class Supervise extends TestIO {
    override def tag: Int                             = Tags.Supervise
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitOther()
  }

  final class Fail extends TestIO {
    override def tag: Int                             = Tags.Fail
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitOther()
  }

  final class Ensuring extends TestIO {
    override def tag: Int                             = Tags.Ensuring
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitOther()
  }

  final object Descriptor extends TestIO {
    override def tag: Int                             = Tags.Descriptor
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitOther()
  }

  final class Lock extends TestIO {
    override def tag: Int                             = Tags.Lock
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitOther()
  }

  final object Yield extends TestIO {
    override def tag: Int                             = Tags.Yield
    override def mapSubclassing(): Int          = 4
    override def acceptMapVisitor(v: MapVisitor): Int = v.visitOther()
  }

  class MapVisitor {
    def visitFlatMap()     = 1
    def visitPoint()       = 2
    def visitStrict()      = 3
    def visitSyncEffect()  = 4
    def visitAsyncEffect() = 4
    def visitOther()       = 4
  }
}
