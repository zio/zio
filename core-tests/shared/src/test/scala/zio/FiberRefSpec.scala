package zio

import zio.FiberRefSpecUtil._
import zio.test.Assertion._
import zio.test._

object FiberRefSpec
    extends ZIOBaseSpec(
      suite("FiberRefSpec")(
        suite("Create a new FiberRef with a specified value and check if:")(
          testM("`get` returns the current value") {
            for {
              fiberRef <- FiberRef.make(initial)
              value    <- fiberRef.get
            } yield assert(value, equalTo(initial))
          },
          testM("`get` returns the current value for a child") {
            for {
              fiberRef <- FiberRef.make(initial)
              child    <- fiberRef.get.fork
              value    <- child.join
            } yield assert(value, equalTo(initial))
          },
          testM("`set` updates the current value") {
            for {
              fiberRef <- FiberRef.make(initial)
              _        <- fiberRef.set(update)
              value    <- fiberRef.get
            } yield assert(value, equalTo(update))
          },
          testM("`set` by a child doesn't update parent's value") {
            for {
              fiberRef <- FiberRef.make(initial)
              promise  <- Promise.make[Nothing, Unit]
              _        <- (fiberRef.set(update) *> promise.succeed(())).fork
              _        <- promise.await
              value    <- fiberRef.get
            } yield assert(value, equalTo(initial))
          },
          testM("`locally` restores original value") {
            for {
              fiberRef <- FiberRef.make(initial)
              local    <- fiberRef.locally(update)(fiberRef.get)
              value    <- fiberRef.get
            } yield assert(local, equalTo(update)) && assert(value, equalTo(initial))
          },
          testM("`locally` restores parent's value") {
            for {
              fiberRef <- FiberRef.make(initial)
              child    <- fiberRef.locally(update)(fiberRef.get).fork
              local    <- child.join
              value    <- fiberRef.get
            } yield assert(local, equalTo(update)) && assert(value, equalTo(initial))
          },
          testM("`locally` restores undefined value") {
            for {
              child <- FiberRef.make(initial).fork
              // Don't use join as it inherits values from child.
              fiberRef   <- child.await.flatMap(ZIO.done)
              localValue <- fiberRef.locally(update)(fiberRef.get)
              value      <- fiberRef.get
            } yield assert(localValue, equalTo(update)) && assert(value, equalTo(initial))
          },
          testM("its value is inherited on join") {
            for {
              fiberRef <- FiberRef.make(initial)
              child    <- fiberRef.set(update).fork
              _        <- child.join
              value    <- fiberRef.get
            } yield assert(value, equalTo(update))
          },
          testM("initial value is always available") {
            for {
              child    <- FiberRef.make(initial).fork
              fiberRef <- child.await.flatMap(ZIO.done)
              value    <- fiberRef.get
            } yield assert(value, equalTo(initial))
          },
          testM("`update` changes value") {
            for {
              fiberRef <- FiberRef.make(initial)
              value1   <- fiberRef.update(_ => update)
              value2   <- fiberRef.get
            } yield assert(value1, equalTo(update)) && assert(value2, equalTo(update))
          },
          testM("`updateSome` changes value") {
            for {
              fiberRef <- FiberRef.make(initial)
              value1 <- fiberRef.updateSome {
                         case _ => update
                       }
              value2 <- fiberRef.get
            } yield assert(value1, equalTo(update)) && assert(value2, equalTo(update))
          },
          testM("`updateSome` changes value") {
            for {
              fiberRef <- FiberRef.make(initial)
              value1 <- fiberRef.updateSome {
                         case _ => update
                       }
              value2 <- fiberRef.get
            } yield assert(value1, equalTo(update)) && assert(value2, equalTo(update))
          },
          testM("`updateSome` not changes value") {
            for {
              fiberRef <- FiberRef.make(initial)
              value1 <- fiberRef.updateSome {
                         case _ if false => update
                       }
              value2 <- fiberRef.get
            } yield assert(value1, equalTo(initial)) && assert(value2, equalTo(initial))
          },
          testM("`modify` changes value") {
            for {
              fiberRef <- FiberRef.make(initial)
              value1 <- fiberRef.modify {
                         case _ => (1, update)
                       }
              value2 <- fiberRef.get
            } yield assert(value1, equalTo(1)) && assert(value2, equalTo(update))
          },
          testM("`modifySome` not changes value") {
            for {
              fiberRef <- FiberRef.make(initial)
              value1 <- fiberRef.modifySome(2) {
                         case _ if false => (1, update)
                       }
              value2 <- fiberRef.get
            } yield assert(value1, equalTo(2)) && assert(value2, equalTo(initial))
          },
          testM("its value is inherited after simple race") {
            for {
              fiberRef <- FiberRef.make(initial)
              _        <- fiberRef.set(update1).race(fiberRef.set(update2))
              value    <- fiberRef.get
            } yield assert(value, equalTo(update1)) || assert(value, equalTo(update2))
          },
          testM("its value is inherited after a race with a bad winner") {
            for {
              fiberRef   <- FiberRef.make(initial)
              badWinner  = fiberRef.set(update1) *> ZIO.fail("ups")
              goodLooser = fiberRef.set(update2)
              _          <- badWinner.race(goodLooser)
              value      <- fiberRef.get
            } yield assert(value, equalTo(update2))
          },
          testM("its value is not inherited after a race of losers") {
            for {
              fiberRef <- FiberRef.make(initial)
              looser1  = fiberRef.set(update1) *> ZIO.fail("ups1")
              looser2  = fiberRef.set(update2) *> ZIO.fail("ups2")
              _        <- looser1.race(looser2).catchAll(_ => ZIO.unit)
              value    <- fiberRef.get
            } yield assert(value, equalTo(initial))
          },
          testM("the value of the looser is inherited in zipPar") {
            for {
              fiberRef <- FiberRef.make(initial)
              latch    <- Promise.make[Nothing, Unit]
              winner   = fiberRef.set(update1) *> latch.succeed(()).unit
              looser   = latch.await *> fiberRef.set(update2)
              _        <- winner.zipPar(looser)
              value    <- fiberRef.get
            } yield assert(value, equalTo(update2))
          },
          testM("nothing gets inherited with a failure in zipPar") {
            for {
              fiberRef <- FiberRef.make(initial)
              success  = fiberRef.set(update)
              failure1 = fiberRef.set(update1) *> ZIO.fail(":-(")
              failure2 = fiberRef.set(update2) *> ZIO.fail(":-O")
              _        <- success.zipPar(failure1.zipPar(failure2)).orElse(ZIO.unit)
              value    <- fiberRef.get
            } yield assert(value, equalTo(initial))
          }
        )
      )
    )

object FiberRefSpecUtil {
  val (initial, update, update1, update2) = ("initial", "update", "update1", "update2")
}
