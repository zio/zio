package zio

import zio.test._

object ThreadLocalBridgeSpec extends ZIOBaseSpec {

  def spec = suite("ThreadLocalBridgeSpec")(
    suite("fiberRefTrackingSupervisor")(
      test("track initial value") {
        val tag          = "tiv"
        val initialValue = s"initial-value-$tag"
        tracking(initialValue) { (_, threadLocalGet) =>
          for {
            _     <- FiberRef.currentSupervisor.get
            ab    <- threadLocalGet zipPar threadLocalGet
            (a, b) = ab
          } yield {
            assertTrue(
              a.contains(initialValue),
              b.contains(initialValue)
            )
          }
        }
      },
      test("track FiberRef.set / modify") {
        val tag          = "modify"
        val initialValue = s"initial-value-$tag"
        val newValue1    = s"new-value1-$tag"
        val newValue2    = s"new-value2-$tag"
        tracking(initialValue) { (fiberRef, threadLocalGet) =>
          for {
            beforeModify <- threadLocalGet
            _            <- fiberRef.modify(_ => () -> newValue1)
            afterModify  <- threadLocalGet
            ab <-
              (fiberRef.set(newValue2) *> threadLocalGet) zipPar
                threadLocalGet
            (a, b) = ab
          } yield {
            assertTrue(
              beforeModify.contains(initialValue),
              afterModify.contains(newValue1),
              a.contains(newValue2),
              b.contains(newValue1)
            )
          }
        }
      },
      test("track in FiberRef.locally") {
        val tag          = "locally"
        val initialValue = s"initial-value-$tag"
        val newValue1    = s"new-value1-$tag"
        val newValue2    = s"new-value2-$tag"
        tracking(initialValue) { (fiberRef, threadLocalGet) =>
          for {
            a <- threadLocalGet
            bc <- fiberRef.locally(newValue1) {
                    threadLocalGet zipPar
                      fiberRef.locally(newValue2)(threadLocalGet)
                  }
            (b, c) = bc
            d     <- threadLocalGet
          } yield assertTrue(
            a.contains(initialValue),
            b.contains(newValue1),
            c.contains(newValue2),
            d.contains(initialValue)
          )
        }
      },
      test("reset to initial value at FiberRef Scope boundary") {
        val tag            = "scope"
        val initialValue   = s"initial-value-$tag"
        val newValue1      = s"new-value1-$tag"
        val threadLocal    = aThreadLocal()
        val threadLocalGet = ZIO.succeed(threadLocal.get)
        for {
          pair <- ZIO.scoped {
                    for {
                      fiberRef  <- ThreadLocalBridge.makeFiberRef(initialValue)(a => threadLocal.set(Some(a)))
                      beforeSet <- threadLocalGet
                      _         <- fiberRef.set(newValue1)
                      afterSet  <- threadLocalGet
                    } yield (beforeSet, afterSet)
                  }
          (beforeSet, afterSet) = pair
          outOfScope           <- threadLocalGet
        } yield {
          assertTrue(
            beforeSet.contains(initialValue),
            afterSet.contains(newValue1),
            outOfScope.contains(initialValue)
          )
        }
      }
    )
  ).provideSomeLayer[zio.test.TestEnvironment with zio.Scope](ThreadLocalBridge.live) @@ TestAspect.exceptNative

  def tracking[R, E, A](
    initialValue: String
  )(effect: (FiberRef[String], UIO[Option[String]]) => ZIO[R with ThreadLocalBridge, E, A]) = {
    val threadLocal    = aThreadLocal()
    val threadLocalGet = ZIO.succeed(threadLocal.get)
    ThreadLocalBridge
      .makeFiberRef[String](initialValue)(a => threadLocal.set(Some(a)))
      .flatMap(effect(_, threadLocalGet))
  }

  private def aThreadLocal() =
    new ThreadLocal[Option[String]] {
      override def initialValue() = None
    }
}
