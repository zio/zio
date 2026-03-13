package zio.internal

import zio._
import zio.test._

object ZSchedulerSpec extends ZIOBaseSpec {

  def spec = suite("ZSchedulerSpec")(
    test("handleFullWorkerQueue: no lost runnables when local queue overflows") {
      for {
        ref <- Ref.make(0)
        _   <- ZIO.foreachDiscard(1 to 300)(_ => ref.update(_ + 1) *> ZIO.yieldNow)
        n   <- ref.get
      } yield assert(n)(Assertion.equalTo(300))
    },
    test("blocking work runs on blocking executor when autoBlocking enabled") {
      ZIO.succeed {
        Unsafe.unsafe { implicit u =>
          val rt = Runtime.unsafe.fromLayer(Runtime.setExecutor(Executor.makeDefault(true)))
          try {
            val name =
              rt.unsafe.run(ZIO.blocking(ZIO.succeed(Thread.currentThread().getName))).getOrThrowFiberFailure()
            assert(name)(Assertion.not(Assertion.containsString("ZScheduler-Worker"))) &&
            assert(name)(Assertion.containsString("blocking"))
          } finally {
            rt.unsafe.shutdown()
          }
        }
      }
    }
  )
}
