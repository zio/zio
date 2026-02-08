package zio.internal

import zio.test._
import zio.test.Assertion._

import java.util.concurrent.atomic.AtomicInteger

object ZSchedulerSpecJVM extends zio.ZIOBaseSpec {

  private def schedulerState(scheduler: ZScheduler): AtomicInteger = {
    val f = scheduler.getClass.getDeclaredFields
      .find(_.getType == classOf[AtomicInteger])
      .getOrElse(throw new NoSuchFieldException("state"))
    f.setAccessible(true)
    f.get(scheduler).asInstanceOf[AtomicInteger]
  }

  private def stopScheduler(scheduler: ZScheduler): Unit = {
    val f = scheduler.getClass.getDeclaredFields
      .find(field => field.getType.isArray && classOf[Thread].isAssignableFrom(field.getType.getComponentType))
      .getOrElse(throw new NoSuchFieldException("workers"))

    f.setAccessible(true)
    val workers = f.get(scheduler).asInstanceOf[Array[Thread]]
    workers.foreach(_.interrupt())
  }

  def spec = suite("ZSchedulerSpecJVM")(
    test("idle workers do not incorrectly enter searching state") {
      val scheduler = new ZScheduler(autoBlocking = false)

      try {
        // Give workers time to start and observe an empty queue.
        Thread.sleep(50L)

        val current   = schedulerState(scheduler).get
        val searching = current & 0xffff

        assert(searching)(equalTo(0))
      } finally {
        stopScheduler(scheduler)
      }
    }
  )
}
