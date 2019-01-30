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

  val fmDefSwitch: TestIODefSwitch          = new FlatMapDefSwitch
  val pointDefSwitch: TestIODefSwitch       = new PointDefSwitch
  val strictDefSwitch: TestIODefSwitch      = new StrictDefSwitch
  val syncEfDefSwitch: TestIODefSwitch      = new SyncEffectDefSwitch
  val asyncEffectDefSwitch: TestIODefSwitch = new AsyncEffectDefSwitch

  val fmValSwitch: TestIOValSwitch          = new FlatMapValSwitch
  val pointValSwitch: TestIOValSwitch       = new PointValSwitch
  val strictValSwitch: TestIOValSwitch      = new StrictValSwitch
  val syncEfValSwitch: TestIOValSwitch      = new SyncEffectValSwitch
  val asyncEffectValSwitch: TestIOValSwitch = new AsyncEffectValSwitch

  val fmCl: TestIOAbstractClass          = new AbstractClassFlatMap
  val pointCl: TestIOAbstractClass       = new AbstractClassPoint
  val strictCl: TestIOAbstractClass      = new AbstractClassStrict
  val syncEfCl: TestIOAbstractClass      = new AbstractClassSyncEffect
  val asyncEffectCl: TestIOAbstractClass = new AbstractClassAsyncEffect

  val fmSub: TestIOSubclassing          = new FlatMapSubclassing
  val pointSub: TestIOSubclassing       = new PointSubclassing
  val strictSub: TestIOSubclassing      = new StrictSubclassing
  val syncEfSub: TestIOSubclassing      = new SyncEffectSubclassing
  val asyncEffectSub: TestIOSubclassing = new AsyncEffectSubclassing

  @Benchmark
  def allMapWithDefSwitch: Int =
    asyncEffectDefSwitch.mapWithSwitch() +
      fmDefSwitch.mapWithSwitch() +
      syncEfDefSwitch.mapWithSwitch() +
      strictDefSwitch.mapWithSwitch() +
      pointDefSwitch.mapWithSwitch()

  @Benchmark
  def allMapWithValSwitch: Int =
    asyncEffectDefSwitch.mapWithSwitch() +
      fmDefSwitch.mapWithSwitch() +
      syncEfDefSwitch.mapWithSwitch() +
      strictDefSwitch.mapWithSwitch() +
      pointDefSwitch.mapWithSwitch()

  @Benchmark
  def allSubclass(): Int =
    asyncEffectSub.mapSubclassing() +
      fmSub.mapSubclassing() +
      syncEfSub.mapSubclassing() +
      strictSub.mapSubclassing() +
      pointSub.mapSubclassing()

  @Benchmark
  def allMapWithSwitchClass: Int =
    asyncEffectCl.mapWithSwitch() +
      fmCl.mapWithSwitch() +
      syncEfCl.mapWithSwitch() +
      strictCl.mapWithSwitch() +
      pointCl.mapWithSwitch()

}

object MapDispatchBenchmark {

  sealed trait TestIODefSwitch { self =>
    def tag: Int

    final def mapWithSwitch(): Int = (self.tag: @switch) match {
      case IO.Tags.Point =>
        self.asInstanceOf[PointDefSwitch]
        1

      case IO.Tags.Strict =>
        self.asInstanceOf[StrictDefSwitch]
        2

      case IO.Tags.Fail =>
        self.asInstanceOf[TestIODefSwitch]
        3

      case _ => 4
    }
  }

  final class FlatMapDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.FlatMap
  }

  final class PointDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Point
  }

  final class StrictDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Strict
  }

  final class SyncEffectDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.SyncEffect
  }

  final class AsyncEffectDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.AsyncEffect
  }

  final class RedeemDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Redeem
  }

  final class ForkDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Fork
  }

  final class UninterruptibleDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Uninterruptible
  }

  final class SuperviseDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Supervise
  }

  final class FailDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Fail
  }

  final class EnsuringDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Ensuring
  }

  final object DescriptorDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Descriptor
  }

  final class LockDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Lock
  }

  final object YieldDefSwitch extends TestIODefSwitch {
    override def tag: Int = Tags.Yield
  }

  sealed trait TestIOValSwitch { self =>
    val tag: Int

    final def mapWithSwitch(): Int = (self.tag: @switch) match {
      case IO.Tags.Point =>
        self.asInstanceOf[PointDefSwitch]
        1

      case IO.Tags.Strict =>
        self.asInstanceOf[StrictDefSwitch]
        2

      case IO.Tags.Fail =>
        self.asInstanceOf[TestIODefSwitch]
        3

      case _ => 4
    }
  }

  final class FlatMapValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.FlatMap
  }

  final class PointValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Point
  }

  final class StrictValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Strict
  }

  final class SyncEffectValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.SyncEffect
  }

  final class AsyncEffectValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.AsyncEffect
  }

  final class RedeemValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Redeem
  }

  final class ForkValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Fork
  }

  final class UninterruptibleValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Uninterruptible
  }

  final class SuperviseValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Supervise
  }

  final class FailValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Fail
  }

  final class EnsuringValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Ensuring
  }

  final object DescriptorValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Descriptor
  }

  final class LockValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Lock
  }

  final object YieldValSwitch extends TestIOValSwitch {
    override final val tag: Int = Tags.Yield
  }

  sealed trait TestIOSubclassing {
    def mapSubclassing(): Int
  }

  final class FlatMapSubclassing extends TestIOSubclassing {
    override def mapSubclassing(): Int = 1
  }

  final class PointSubclassing extends TestIOSubclassing {
    override def mapSubclassing(): Int = 2
  }

  final class StrictSubclassing extends TestIOSubclassing {
    override def mapSubclassing(): Int = 3
  }

  final class SyncEffectSubclassing extends TestIOSubclassing {
    override def mapSubclassing(): Int = 4
  }

  final class AsyncEffectSubclassing extends TestIOSubclassing {

    override def mapSubclassing(): Int = 4

  }

  final class RedeemSubclassing extends TestIOSubclassing {

    override def mapSubclassing(): Int = 4

  }

  final class ForkSubclassing extends TestIOSubclassing {

    override def mapSubclassing(): Int = 4

  }

  final class UninterruptibleSubclassing extends TestIOSubclassing {

    override def mapSubclassing(): Int = 4

  }

  final class SuperviseSubclassing extends TestIOSubclassing {

    override def mapSubclassing(): Int = 4

  }

  final class FailSubclassing extends TestIOSubclassing {

    override def mapSubclassing(): Int = 4

  }

  final class EnsuringSubclassing extends TestIOSubclassing {

    override def mapSubclassing(): Int = 4

  }

  final object DescriptorSubclassing extends TestIOSubclassing {

    override def mapSubclassing(): Int = 4

  }

  final class LockSubclassing extends TestIOSubclassing {

    override def mapSubclassing(): Int = 4

  }

  final object YieldSubclassing extends TestIOSubclassing {

    override def mapSubclassing(): Int = 4

  }

  abstract class TestIOAbstractClass(storedTag0: Int) {
    final val storedTag = storedTag0

    final def mapWithSwitch(): Int = (storedTag: @switch) match {
      case IO.Tags.Point =>
        this.asInstanceOf[AbstractClassPoint]
        1

      case IO.Tags.Strict =>
        this.asInstanceOf[AbstractClassStrict]
        2

      case IO.Tags.Fail =>
        this.asInstanceOf[TestIOAbstractClass]
        3

      case _ => 4
    }
  }

  final class AbstractClassFlatMap extends TestIOAbstractClass(Tags.FlatMap)

  final class AbstractClassPoint extends TestIOAbstractClass(Tags.Point)

  final class AbstractClassStrict extends TestIOAbstractClass(Tags.Strict)

  final class AbstractClassSyncEffect extends TestIOAbstractClass(Tags.SyncEffect)

  final class AbstractClassAsyncEffect extends TestIOAbstractClass(Tags.AsyncEffect)

  final class AbstractClassRedeem extends TestIOAbstractClass(Tags.Redeem)

  final class AbstractClassFork extends TestIOAbstractClass(Tags.Fork)

  final class AbstractClassUninterruptible extends TestIOAbstractClass(Tags.Uninterruptible)

  final class AbstractClassSupervise extends TestIOAbstractClass(Tags.Supervise)

  final class AbstractClassFail extends TestIOAbstractClass(Tags.Fail)

  final class AbstractClassEnsuring extends TestIOAbstractClass(Tags.Ensuring)

  final class Descriptor extends TestIOAbstractClass(Tags.Descriptor)

  final class AbstractClassLock extends TestIOAbstractClass(Tags.Lock)

  final class Yield extends TestIOAbstractClass(Tags.Yield)

}
