package zio.interop

import cats.effect.Resource
import org.specs2.Specification
import org.specs2.specification.AroundTimeout
import zio.{ DefaultRuntime, Task, ZIO, ZManaged }
import zio.interop.catz._

import scala.collection.mutable

class CatsZManagedSyntaxSpec extends Specification with AroundTimeout with DefaultRuntime {

  def is = s2"""

      toManaged
        calls finalizers correctly when use is interrupted $toManagedFinalizersWhenInterrupted
        calls finalizers correctly when use has failed $toManagedFinalizersWhenFailed
        calls finalizers correctly when use has died $toManagedFinalizersWhenDied
        calls finalizers correctly when using the resource $toManagedFinalizers
        calls finalizers should not run if exception is thrown in acquisition $toManagedFinalizersExceptionAcquisition
        composing with other managed should calls finalizers in correct order $toManagedComposition

      toResource
        calls finalizers when using resource $toResourceFinalizers
        calls finalizers when using resource fails $toResourceFinalizersWhenFailed
        calls finalizers when using resource is canceled $toResourceFinalizersWhenCanceled
      """

  def toManagedFinalizersWhenInterrupted = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.make(ZIO.effect { effects += x; () })(_ => ZIO.effect { effects += x; () })

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.interrupt.unit)
    }

    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 1))
  }

  def toManagedFinalizersWhenFailed = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.make(ZIO.effect { effects += x; () })(_ => ZIO.effect { effects += x; () })

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.fail(new RuntimeException()).unit)
    }

    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 1))
  }

  def toManagedFinalizersWhenDied = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.make(ZIO.effect { effects += x; () })(_ => ZIO.effect { effects += x; () })

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.die(new RuntimeException()).unit)
    }

    unsafeRun(testCase.sandbox.orElse(ZIO.unit))
    effects must be_===(List(1, 1))
  }

  def toManagedFinalizersExceptionAcquisition = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.make(ZIO.effect(effects += x) *> ZIO.effect(throw new RuntimeException()).unit)(
        _ => ZIO.effect { effects += x; () }
      )

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.unit)
    }

    unsafeRun(testCase.sandbox.orElse(ZIO.unit))
    effects must be_===(List(1))
  }

  def toManagedFinalizers = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.make(ZIO.effect { effects += x; () })(_ => ZIO.effect { effects += x; () })

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.unit)
    }

    unsafeRun(testCase)
    effects must be_===(List(1, 1))
  }

  def toManagedComposition = {

    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.make(ZIO.effect { effects += x; () })(_ => ZIO.effect { effects += x; () })

    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed1: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      val managed2: ZManaged[Any, Throwable, Unit] = man(2)
      (managed1 *> managed2).use(_ => ZIO.unit)
    }

    unsafeRun(testCase)
    effects must be_===(List(1, 2, 2, 1))

  }

  def toResourceFinalizers = {

    val effects = new mutable.ListBuffer[Int]
    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      man(1).toResource.use(_ => ZIO.unit)
    }
    unsafeRun(testCase)
    effects must be_===(List(1, 1))
  }

  def toResourceFinalizersWhenFailed = {

    val effects = new mutable.ListBuffer[Int]
    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      man(1).toResource.use(_ => ZIO.fail(new RuntimeException()).unit)
    }
    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 1))
  }

  def toResourceFinalizersWhenCanceled = {

    val effects = new mutable.ListBuffer[Int]
    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      man(1).toResource.use(_ => ZIO.interrupt)
    }
    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 1))
  }

}
