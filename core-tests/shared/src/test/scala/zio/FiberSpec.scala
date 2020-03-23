package zio

import zio.LatchOps._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object FiberSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("FiberSpec")(
    suite("Create a new Fiber and")(testM("lift it into Managed") {
      for {
        ref   <- Ref.make(false)
        fiber <- withLatch(release => (release *> IO.unit).bracket_(ref.set(true))(IO.never).fork)
        _     <- fiber.toManaged.use(_ => IO.unit)
        _     <- fiber.await
        value <- ref.get
      } yield assert(value)(isTrue)
    }),
    suite("`inheritLocals` works for Fiber created using:")(
      testM("`map`") {
        for {
          fiberRef <- FiberRef.make(initial)
          child    <- withLatch(release => (fiberRef.set(update) *> release).fork)
          _        <- child.map(_ => ()).inheritRefs
          value    <- fiberRef.get
        } yield assert(value)(equalTo(update))
      },
      testM("`orElse`") {
        for {
          fiberRef <- FiberRef.make(initial)
          latch1   <- Promise.make[Nothing, Unit]
          latch2   <- Promise.make[Nothing, Unit]
          child1   <- (fiberRef.set("child1") *> latch1.succeed(())).fork
          child2   <- (fiberRef.set("child2") *> latch2.succeed(())).fork
          _        <- latch1.await *> latch2.await
          _        <- child1.orElse(child2).inheritRefs
          value    <- fiberRef.get
        } yield assert(value)(equalTo("child1"))
      },
      testM("`zip`") {
        for {
          fiberRef <- FiberRef.make(initial)
          latch1   <- Promise.make[Nothing, Unit]
          latch2   <- Promise.make[Nothing, Unit]
          child1   <- (fiberRef.set("child1") *> latch1.succeed(())).fork
          child2   <- (fiberRef.set("child2") *> latch2.succeed(())).fork
          _        <- latch1.await *> latch2.await
          _        <- child1.zip(child2).inheritRefs
          value    <- fiberRef.get
        } yield assert(value)(equalTo("child1"))
      }
    ),
    suite("`Fiber.join` on interrupted Fiber")(
      testM("is inner interruption") {
        val fiberId = Fiber.Id(0L, 123L)

        for {
          exit <- Fiber.interruptAs(fiberId).join.run
        } yield assert(exit)(equalTo(Exit.interrupt(fiberId)))
      }
    ) @@ zioTag(interruption),
    suite("if one composed fiber fails then all must fail")(
      testM("`await`") {
        for {
          exit <- Fiber.fail("fail").zip(Fiber.never).await
        } yield assert(exit)(fails(equalTo("fail")))
      },
      testM("`join`") {
        for {
          exit <- Fiber.fail("fail").zip(Fiber.never).join.run
        } yield assert(exit)(fails(equalTo("fail")))
      },
      testM("`awaitAll`") {
        for {
          exit <- Fiber.awaitAll(Fiber.fail("fail") :: List.fill(100)(Fiber.never)).run
        } yield assert(exit)(succeeds(isUnit))
      },
      testM("`joinAll`") {
        for {
          exit <- Fiber.awaitAll(Fiber.fail("fail") :: List.fill(100)(Fiber.never)).run
        } yield assert(exit)(succeeds(isUnit))
      },
      testM("shard example") {
        def shard[R, E, A](queue: Queue[A], n: Int, worker: A => ZIO[R, E, Unit]): ZIO[R, E, Nothing] = {
          val worker1 = queue.take.flatMap(a => worker(a).uninterruptible).forever
          ZIO.forkAll(List.fill(n)(worker1)).flatMap(_.join) *> ZIO.never
        }
        for {
          queue  <- Queue.unbounded[Int]
          _      <- queue.offerAll(1 to 100)
          worker = (n: Int) => if (n == 100) ZIO.fail("fail") else queue.offer(n).unit
          exit   <- shard(queue, 4, worker).run
          _      <- queue.shutdown
        } yield assert(exit)(fails(equalTo("fail")))
      }
    ) @@ zioTag(errors),
    testM("grandparent interruption is propagated to grandchild despite parent termination") {
      for {
        latch1 <- Promise.make[Nothing, Unit]
        latch2 <- Promise.make[Nothing, Unit]
        c      = ZIO.never.interruptible.onInterrupt(latch2.succeed(()))
        a      = (latch1.succeed(()) *> c.fork.fork).uninterruptible *> ZIO.never
        fiber  <- a.fork
        _      <- latch1.await
        _      <- fiber.interrupt
        _      <- latch2.await
      } yield assertCompletes
    } @@ zioTag(interruption) @@ nonFlaky,
    suite("stack safety")(
      testM("awaitAll") {
        assertM(Fiber.awaitAll(fibers))(anything)
      },
      testM("joinAll") {
        assertM(Fiber.joinAll(fibers))(anything)
      },
      testM("collectAll") {
        assertM(Fiber.collectAll(fibers).join)(anything)
      }
    ) @@ sequential,
    suite("prettyPrint improvements")(
      testM("fiber with one child") {
        for {
          fiberRef <- FiberRef.make(0, math.max)
          child    <- fiberRef.update(_ + 1).fork
          dump     <- Fiber.dumpStr(child)
          childId  <- child.id
          _        <- child.join
        } yield {
          assert(dump.contains(s"+---#${childId.seqNumber} Status: Done"))(equalTo(true))
        }
      },
      testM("fiber with multiple children") {
        for {
          parent   <- (ZIO.infinity.forkAs("child2") *> ZIO.infinity).forkAs("child1")
          childId  <- parent.id
          childSeq = childId.seqNumber
          children <- parent.children
          dumpStr  <- Fiber.dumpStr(parent)
        } yield {
          assert(dumpStr.contains(s"#${childSeq} Status"))(equalTo(true))
          assert(children.size)(equalTo(1))
        }
      }
    )
  )

  val (initial, update) = ("initial", "update")
  val fibers            = List.fill(100000)(Fiber.unit)
}
