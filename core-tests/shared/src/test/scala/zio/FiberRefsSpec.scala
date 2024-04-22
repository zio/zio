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
        val parent = Unsafe.unsafe(implicit unsafe => FiberId.Gen.Random.make(Trace.empty))
        val child  = Unsafe.unsafe(implicit unsafe => FiberId.Gen.Random.make(Trace.empty))

        val parentFiberRefs = FiberRefs.empty
        val childFiberRefs  = parentFiberRefs.updatedAs(child)(FiberRef.interruptedCause, Cause.interrupt(parent))

        val newParentFiberRefs = parentFiberRefs.joinAs(parent)(childFiberRefs)

        assertTrue(newParentFiberRefs.get(FiberRef.interruptedCause) == Some(Cause.empty))
      } +
      /*
        If any of the following tests fail, it is likely that the optimizations in FiberRefs are broken.
        Either ensure that the optimizations are no longer needed (and delete the tests) or fix the issue that
        is causing the underlying maps to return a different instance of `FiberRefs`
       */
      suite("optimizations") {
        implicit val unsafe: Unsafe = Unsafe.unsafe
        val fiberId                 = FiberId.Gen.Random.make(implicitly)

        val fiberRefs = List(
          FiberRef.unsafe.make[Int](0, join = (a, b) => a + b),
          FiberRef.unsafe.make(1),
          FiberRef.unsafe.make(3),
          FiberRef.unsafe.make(4),
          FiberRef.unsafe.make[Int](5, fork = _ + 1)
        )

        def makeFiberRefs(f: List[FiberRef[Int]]) =
          f.foldLeft(FiberRefs.empty)((refs, ref) => refs.updatedAs(fiberId)(ref, 5))

        test("`delete` returns the same instance if the map was unchanged") {
          val fr   = makeFiberRefs(fiberRefs)
          val isEq = fr.delete(FiberRef.unsafe.make(6)) eq fr
          assertTrue(isEq)
        } +
          test("forkAs returns the same map if no fibers are modified during fork") {
            val fr   = makeFiberRefs(fiberRefs.take(4))
            val isEq = fr.forkAs(FiberId.Gen.Random.make(implicitly)) eq fr
            assertTrue(isEq)
          } +
          test("joinAs returns the same map when fiber refs are unchanged after joining") {
            val fr1  = makeFiberRefs(fiberRefs.drop(1))
            val fr2  = makeFiberRefs(fiberRefs.drop(2))
            val isEq = fr1.joinAs(FiberId.Gen.Random.make(implicitly))(fr2) eq fr1
            assertTrue(isEq)
          } +
          // Sanity checks
          test("forkAs returns a different map if forked fibers are modified") {
            val fr   = makeFiberRefs(fiberRefs)
            val isEq = fr.forkAs(FiberId.Gen.Random.make(implicitly)) ne fr
            assertTrue(isEq)
          } +
          test("joinAs returns a different map when fiber refs are changed after joining") {
            val fr1, fr2 = makeFiberRefs(fiberRefs)
            val isEq     = fr1.joinAs(FiberId.Gen.Random.make(implicitly))(fr2) ne fr1
            assertTrue(isEq)
          }
      }
  )
}
