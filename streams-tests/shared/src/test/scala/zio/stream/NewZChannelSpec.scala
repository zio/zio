package zio.stream

import zio._
import zio.test._

object NewZChannelSpec extends ZIOBaseSpec {

  def spec = suite("NewZChannelSpec")(
    suite("ConcatAll")(
      test("child reads from upstream of parent") {
        val upstream = ZChannel.writeAll(1, 2, 3)
        val parent   = ZChannel.writeAll(7, 8, 9) >>> ZChannel.identity[Nothing, Int, Any]
        val child    = (parent: Int) => ZChannel.read[Int].flatMap(upstream => ZChannel.write(parent + upstream))
        val channel  = upstream >>> parent.concatMap(child)
        for {
          tuple    <- channel.runCollect
          (elem, _) = tuple
        } yield assertTrue(elem == Chunk(8, 10, 12))
      }
    ),
    suite("Ensuring")(
      test("finalizers are executed before running next channel") {
        for {
          ref    <- Ref.make(false)
          channel = ZChannel.unit.ensuring(ref.set(true)) *> ZChannel.unit
          _      <- channel.runScoped
          value  <- ref.get
        } yield assertTrue(value)
      },
      test("finalizers from last channel are deferred to scope if it succeeds") {
        for {
          ref    <- Ref.make(false)
          scope  <- Scope.make
          channel = ZChannel.unit.ensuring(ref.set(true))
          _      <- scope.extend(channel.runScoped)
          open   <- ref.get
          _      <- scope.close(Exit.unit)
          closed <- ref.get
        } yield assertTrue(!open) && assertTrue(closed)
      },
      test("finalizers from last channel are deferred to scope if it fails") {
        for {
          ref    <- Ref.make(false)
          scope  <- Scope.make
          channel = ZChannel.fail("fail").ensuring(ref.set(true))
          _      <- scope.extend(channel.runScoped).ignore
          open   <- ref.get
          _      <- scope.close(Exit.unit)
          closed <- ref.get
        } yield assertTrue(!open) && assertTrue(closed)
      },
      test("finalizers from last channel are deferred to scope if it is interrupted") {
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(false)
          scope   <- Scope.make
          channel  = (ZChannel.fromZIO(promise.succeed(())) *> ZChannel.never).ensuring(ref.set(true))
          fiber   <- scope.extend(channel.runScoped).fork
          _       <- promise.await
          _       <- fiber.interrupt
          open    <- ref.get
          _       <- scope.close(Exit.unit)
          closed  <- ref.get
        } yield assertTrue(!open) && assertTrue(closed)
      },
      test("finalizers from last channel are deferred to scope if it never completes") {
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(false)
          scope   <- Scope.make
          channel  = (ZChannel.fromZIO(promise.succeed(())) *> ZChannel.never).ensuring(ref.set(true))
          _       <- scope.extend(channel.runScoped).fork
          _       <- promise.await
          open    <- ref.get
          _       <- scope.close(Exit.unit)
          closed  <- ref.get
        } yield assertTrue(!open) && assertTrue(closed)
      },
      test("finalizers from last upstream channel are deferred to scope") {
        for {
          ref       <- Ref.make(false)
          scope     <- Scope.make
          upstream   = ZChannel.write(1).ensuring(ref.set(true))
          downstream = ZChannel.read[Int]
          channel    = upstream >>> downstream
          _         <- scope.extend(channel.runScoped)
          open      <- ref.get
          _         <- scope.close(Exit.unit)
          closed    <- ref.get
        } yield assertTrue(!open) && assertTrue(closed)
      },
      test("finalizers from last downstream channel are deferred to scope") {
        for {
          ref       <- Ref.make(false)
          scope     <- Scope.make
          upstream   = ZChannel.write(1)
          downstream = ZChannel.read[Int].ensuring(ref.set(true))
          channel    = upstream >>> downstream
          _         <- scope.extend(channel.runScoped)
          open      <- ref.get
          _         <- scope.close(Exit.unit)
          closed    <- ref.get
        } yield assertTrue(!open) && assertTrue(closed)
      },
      test("finalizers from upstream are deferred to scope if downstream never reads") {
        for {
          ref       <- Ref.make(false)
          scope     <- Scope.make
          upstream   = ZChannel.write(1).ensuring(ref.set(true)) *> ZChannel.unit
          downstream = ZChannel.read[Int]
          channel    = upstream >>> downstream
          _         <- scope.extend(channel.runScoped)
          open      <- ref.get
          _         <- scope.close(Exit.unit)
          closed    <- ref.get
        } yield assertTrue(!open) && assertTrue(closed)
      },
      test("deferred upstream finalizers are executed before running next channel") {
        for {
          ref       <- Ref.make(false)
          scope     <- Scope.make
          upstream   = ZChannel.write(1).ensuring(ref.set(true))
          downstream = ZChannel.read[Int]
          channel    = (upstream >>> downstream) *> ZChannel.unit
          _         <- scope.extend(channel.runScoped)
          open      <- ref.get
          _         <- scope.close(Exit.unit)
          closed    <- ref.get
        } yield assertTrue(open) && assertTrue(closed)
      },
      test("deferred downstream finalizers are executed before running next channel") {
        for {
          ref       <- Ref.make(false)
          scope     <- Scope.make
          upstream   = ZChannel.write(1)
          downstream = ZChannel.read[Int].ensuring(ref.set(true))
          channel    = (upstream >>> downstream) *> ZChannel.unit
          _         <- scope.extend(channel.runScoped)
          open      <- ref.get
          _         <- scope.close(Exit.unit)
          closed    <- ref.get
        } yield assertTrue(open) && assertTrue(closed)
      },
      test("deferred downstream finalizers are run before deferred upstream finalizers") {
        for {
          ref       <- Ref.make(Chunk.empty[String])
          scope     <- Scope.make
          upstream   = ZChannel.write(1).ensuring(ref.update(_ :+ "upstream"))
          downstream = ZChannel.read[Int].ensuring(ref.update(_ :+ "downstream"))
          channel    = upstream >>> downstream
          _         <- scope.extend(channel.runScoped)
          open      <- ref.get
          _         <- scope.close(Exit.unit)
          closed    <- ref.get
        } yield assertTrue(open.isEmpty) && assertTrue(closed == Chunk("downstream", "upstream"))
      },
      test("finalizers from last parent channel are deferred to scope") {
        for {
          ref    <- Ref.make(false)
          scope  <- Scope.make
          parent  = ZChannel.write(1).ensuring(ref.set(true))
          child   = ZChannel.unit
          channel = parent.concatMap(_ => child)
          _      <- scope.extend(channel.runScoped)
          open   <- ref.get
          _      <- scope.close(Exit.unit)
          closed <- ref.get
        } yield assertTrue(!open) && assertTrue(closed)
      },
      test("finalizers from child of last parent channel are deferred to scope") {
        for {
          ref    <- Ref.make(false)
          scope  <- Scope.make
          parent  = ZChannel.write(1)
          child   = ZChannel.unit.ensuring(ref.set(true))
          channel = parent.concatMap(_ => child)
          _      <- scope.extend(channel.runScoped)
          open   <- ref.get
          _      <- scope.close(Exit.unit)
          closed <- ref.get
        } yield assertTrue(!open) && assertTrue(closed)
      },
      test("deferred parent finalizers are executed before running next channel") {
        for {
          ref    <- Ref.make(false)
          scope  <- Scope.make
          parent  = ZChannel.write(1).ensuring(ref.set(true))
          child   = ZChannel.unit
          channel = parent.concatMap(_ => child) *> ZChannel.unit
          _      <- scope.extend(channel.runScoped)
          open   <- ref.get
          _      <- scope.close(Exit.unit)
          closed <- ref.get
        } yield assertTrue(open) && assertTrue(closed)
      },
      test("child finalizers are run before parent finalizers") {
        for {
          ref    <- Ref.make(Chunk.empty[String])
          scope  <- Scope.make
          parent  = (ZChannel.write(1).ensuring(ref.update(_ :+ "parent")) *> ZChannel.unit)
          child   = ZChannel.unit.ensuring(ref.update(_ :+ "child"))
          channel = parent.concatMap(_ => child)
          _      <- scope.extend(channel.runScoped)
          open   <- ref.get
          _      <- scope.close(Exit.unit)
          closed <- ref.get
        } yield assertTrue(open == Chunk("child", "parent")) && assertTrue(closed == Chunk("child", "parent"))
      },
      test("deferred child finalizers are run before deferred parent finalizers") {
        for {
          ref    <- Ref.make(Chunk.empty[String])
          scope  <- Scope.make
          parent  = ZChannel.write(1).ensuring(ref.update(_ :+ "parent"))
          child   = ZChannel.unit.ensuring(ref.update(_ :+ "child"))
          channel = parent.concatMap(_ => child)
          _      <- scope.extend(channel.runScoped)
          open   <- ref.get
          _      <- scope.close(Exit.unit)
          closed <- ref.get
        } yield assertTrue(open.isEmpty) && assertTrue(closed == Chunk("child", "parent"))
      }
    ),
    suite("Fold")(
      test("upstream cannot recover from downstream failure") {
        for {
          ref       <- Ref.make(true)
          upstream   = ZChannel.write(1).catchAll(_ => ZChannel.fromZIO(ref.set(false)))
          downstream = ZChannel.read[Int] *> ZChannel.fail("fail")
          channel    = upstream >>> downstream
          _         <- channel.run.flip
          value     <- ref.get
        } yield assertTrue(value)
      }
    ),
    suite("FromZIO")(
      test("FiberRef values are propagated from ZChannel to ZIO") {
        for {
          fiberRef <- FiberRef.make(false)
          channel   = ZChannel.setFiberRef(fiberRef)(true) *> ZChannel.fromZIO(fiberRef.get)
          value    <- channel.run
        } yield assertTrue(value)
      },
      test("FiberRef values are propagated from successful ZIO to ZChannel") {
        for {
          fiberRef <- FiberRef.make(false)
          channel   = ZChannel.fromZIO(fiberRef.set(true)) *> ZChannel.getFiberRef(fiberRef)
          value    <- channel.run
        } yield assertTrue(value)
      },
      test("FiberRef values are propagated from failed ZIO to ZChannel") {
        for {
          fiberRef <- FiberRef.make(true)
          channel   = ZChannel.fromZIO(fiberRef.set(false) *> ZIO.fail("fail")).orElse(ZChannel.getFiberRef(fiberRef))
          value    <- channel.run
        } yield assertTrue(!value)
      },
      test("RuntimeFlags are propagated from ZChannel to ZIO") {
        for {
          runtimeFlags <- ZIO.runtimeFlags
          isEnabled     = RuntimeFlags.isEnabled(runtimeFlags)(RuntimeFlag.FiberRoots)
          channel = ZChannel.updateRuntimeFlags(RuntimeFlags.disable(RuntimeFlag.FiberRoots)) *>
                      ZChannel.fromZIO(ZIO.runtimeFlags)
          runtimeFlags <- channel.run
          isDisabled    = RuntimeFlags.isDisabled(runtimeFlags)(RuntimeFlag.FiberRoots)
        } yield assertTrue(isEnabled) && assertTrue(isDisabled)
      },
      test("RuntimeFlags are propagated from successful ZIO to ZChannel") {
        for {
          runtimeFlags <- ZIO.runtimeFlags
          isEnabled     = RuntimeFlags.isEnabled(runtimeFlags)(RuntimeFlag.FiberRoots)
          channel = ZChannel.fromZIO(ZIO.updateRuntimeFlags(RuntimeFlags.disable(RuntimeFlag.FiberRoots))) *>
                      ZChannel.runtimeFlags
          runtimeFlags <- channel.run
          isDisabled    = RuntimeFlags.isDisabled(runtimeFlags)(RuntimeFlag.FiberRoots)
        } yield assertTrue(isEnabled) && assertTrue(isDisabled)
      },
      test("RuntimeFlags are propagated from failed ZIO to ZChannel") {
        for {
          runtimeFlags <- ZIO.runtimeFlags
          isEnabled     = RuntimeFlags.isEnabled(runtimeFlags)(RuntimeFlag.FiberRoots)
          channel = ZChannel
                      .fromZIO(ZIO.updateRuntimeFlags(RuntimeFlags.disable(RuntimeFlag.FiberRoots)) *> ZIO.fail("fail"))
                      .orElse(ZChannel.runtimeFlags)
          runtimeFlags <- channel.run
          isDisabled    = RuntimeFlags.isDisabled(runtimeFlags)(RuntimeFlag.FiberRoots)
        } yield assertTrue(isEnabled) && assertTrue(isDisabled)
      },
      test("interruption is propagated from ZChannel to ZIO") {
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(false)
          zio      = (promise.succeed(()) *> ZIO.never).ensuring(ref.set(true))
          channel  = ZChannel.fromZIO(zio)
          fiber   <- channel.run.fork
          _       <- promise.await
          _       <- fiber.interrupt
          value   <- ref.get
        } yield assertTrue(value)
      },
      test("Interruption is propagated from ZChannel to ZIO in uninterruptible region") {
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(false)
          zio      = (promise.succeed(()) *> ZIO.never).ensuring(ref.set(true)).interruptible
          channel  = ZChannel.fromZIO(zio).uninterruptible
          fiber   <- channel.run.fork
          _       <- promise.await
          _       <- fiber.interrupt
          value   <- ref.get
        } yield assertTrue(value)
      }
    ),
    suite("MergeAllWith") {
      test("finalizers from merged channel are run when it completes execution") {
        for {
          promise <- Promise.make[Nothing, Unit]
          left     = ZChannel.fromZIO(promise.await)
          right    = ZChannel.unit.ensuring(promise.succeed(()))
          channel  = ZChannel.mergeAll(ZChannel.writeAll(left, right), 2)
          _       <- channel.run
        } yield assertCompletes
      }
    },
    suite("MergeWith") {
      test("finalizers from merged channel are run when downstream completes execution") {
        for {
          promise <- Promise.make[Nothing, Unit]
          left     = ZChannel.unit
          right    = (ZChannel.write(1) *> ZChannel.write(2)).ensuring(promise.succeed(()))
          upstream = left.mergeWith(right)(
                       _ => ZChannel.MergeDecision.awaitConst(ZIO.unit),
                       _ => ZChannel.MergeDecision.awaitConst(ZIO.unit)
                     )
          downstream = ZChannel.read[Int]
          channel    = upstream >>> downstream
          _         <- channel.run
          _         <- promise.await
        } yield assertCompletes
      }
    },
    suite("Stateful")(
      test("FiberRef values can be modified") {
        for {
          fiberRef <- FiberRef.make(false)
          channel   = ZChannel.setFiberRef(fiberRef)(true) *> ZChannel.getFiberRef(fiberRef)
          value    <- channel.run
        } yield assertTrue(value)
      },
      test("RuntimeFlags can be modified") {
        for {
          runtimeFlags <- ZIO.runtimeFlags
          isEnabled     = RuntimeFlags.isEnabled(runtimeFlags)(RuntimeFlag.FiberRoots)
          channel       = ZChannel.updateRuntimeFlags(RuntimeFlags.disable(RuntimeFlag.FiberRoots)) *> ZChannel.runtimeFlags
          runtimeFlags <- channel.run
          isDisabled    = RuntimeFlags.isDisabled(runtimeFlags)(RuntimeFlag.FiberRoots)
        } yield assertTrue(isEnabled) && assertTrue(isDisabled)
      },
      test("changes are propagated from upstream to downstream") {
        for {
          promise   <- Promise.make[Nothing, Unit]
          upstream   = ZChannel.uninterruptibleMask(restore => restore(ZChannel.write(1)))
          downstream = ZChannel.read[Int] *> ZChannel.fromZIO(promise.succeed(()) *> ZIO.never)
          channel    = upstream >>> downstream
          fiber     <- channel.run.fork
          _         <- promise.await
          _         <- fiber.interrupt
        } yield assertCompletes
      }
    ),
    suite("YieldNow")(
      test("channel fiber yields to interruption") {
        for {
          fiber <- ZChannel.unit.repeated.run.fork
          _     <- ZIO.yieldNow
          _     <- fiber.interrupt
        } yield assertCompletes
      }
    )
  )
}
