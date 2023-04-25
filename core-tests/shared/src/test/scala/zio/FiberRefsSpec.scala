package zio

import zio.test._

object FiberRefsSpec extends ZIOBaseSpec {

  def spec = suite("FiberRefSpec")(
    test("propagate FiberRef values across fiber boundaries") {
      for {
        fiberRef <- FiberRef.make(false)
        queue    <- Queue.unbounded[FiberRefs]
        producer <- (fiberRef.set(true) *> ZIO.getFiberRefs.flatMap(queue.offer)).fork
        consumer <- (queue.take.flatMap(ZIO.setFiberRefs(_)) *> fiberRef.get).fork
        _        <- producer.join
        value    <- consumer.join
      } yield assertTrue(value)
    } +
      test("interruptedCause") {
        val parent = Unsafe.unsafe(implicit unsafe => FiberId.make(Trace.empty))
        val child  = Unsafe.unsafe(implicit unsafe => FiberId.make(Trace.empty))

        val parentFiberRefs = FiberRefs.empty
        val childFiberRefs  = parentFiberRefs.updatedAs(child)(FiberRef.interruptedCause, Cause.interrupt(parent))

        val newParentFiberRefs = parentFiberRefs.joinAs(parent)(childFiberRefs)

        assertTrue(newParentFiberRefs.get(FiberRef.interruptedCause) == Some(Cause.empty))
      }
  )
}
