package zio

import zio.test._

object ThreadLocalBridgeSpec extends ZIOBaseSpec {

  def spec = suite("SupervisorSpec")(
    suite("fiberRefTrackingSupervisor")(
      test("track initial value") {
        val tag          = "tiv"
        val initialValue = s"initial-value-$tag"
        tracking(initialValue) { (_, threadLocalGet) =>
          for {
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
      }
    )
  ).provideSomeLayer[zio.test.TestEnvironment with zio.Scope](ThreadLocalBridge.live)

  def tracking[R, E, A](
    initialValue: String
  )(effect: (FiberRef[String], UIO[Option[String]]) => ZIO[R with ThreadLocalBridge, E, A]) = {
    val threadLocal = new ThreadLocal[Option[String]] {
      override def initialValue() = None
    }
    val threadLocalGet = ZIO.succeed(threadLocal.get)
    ThreadLocalBridge
      .makeFiberRef[String](initialValue, a => threadLocal.set(Some(a)))
      .flatMap(effect(_, threadLocalGet))
  }
}
