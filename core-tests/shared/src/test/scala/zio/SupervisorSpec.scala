package zio

import zio.test._

object SupervisorSpec extends ZIOBaseSpec {

  private val threadLocal = new ThreadLocal[Option[String]] {
    override def initialValue() = None
  }

  private val initialValue = "initial-value"

  private val (traceFiberRefAspect, fiberRef) =
    RuntimeConfigAspect.trackFiberRef[String](initialValue)(a => threadLocal.set(Some(a)))

  val runtime = Runtime.default.mapRuntimeConfig(_ @@ traceFiberRefAspect)

  def spec: ZSpec[Environment, Failure] = suite("SupervisorSpec")(
    suite("fiberRefTrackingSupervisor")(
      test("track initial value") {
        runIn(runtime) {
          for {
            (a, b) <- threadLocalGet zipPar threadLocalGet
          } yield {
            assertTrue(
              a.contains(initialValue),
              b.contains(initialValue)
            )
          }
        }
      },
      test("track FiberRef.set / modify") {
        val newValue1 = "new-value1"
        val newValue2 = "new-value2"
        runIn(runtime) {
          for {
            _ <- fiberRef.modify(_ => () -> newValue1)
            (a, b) <-
              (fiberRef.set(newValue2) *> threadLocalGet) zipPar
                threadLocalGet
          } yield {
            assertTrue(
              a.contains(newValue2),
              b.contains(newValue1)
            )
          }
        }
      },
      test("track in FiberRef.locally") {
        val newValue1 = "new-value1"
        val newValue2 = "new-value2"
        runIn(runtime) {
          for {
            a <- threadLocalGet
            (b, c) <- fiberRef.locally(newValue1) {
                        threadLocalGet zipPar
                          fiberRef.locally(newValue2)(threadLocalGet)
                      }
            d <- threadLocalGet
          } yield assertTrue(
            a.contains(initialValue),
            b.contains(newValue1),
            c.contains(newValue2),
            d.contains(initialValue)
          )
        }
      },
      test("track in FiberRef.locallyManaged") {
        val newValue1 = "new-value1"
        val newValue2 = "new-value2"
        runIn(runtime) {
          for {
            a <- threadLocalGet
            (b, c) <- fiberRef.locallyManaged(newValue1).useDiscard {
                        threadLocalGet zipPar
                          fiberRef.locallyManaged(newValue2).useDiscard(threadLocalGet)
                      }
            d <- threadLocalGet
          } yield assertTrue(
            a.contains(initialValue),
            b.contains(newValue1),
            c.contains(newValue2),
            d.contains(initialValue)
          )
        }
      }
    )
  )

  def threadLocalGet =
    Task(threadLocal.get)

  private def runIn[E, A](rt: Runtime[Any])(a: IO[E, A]) =
    ZIO.async[Any, E, A](callback => rt.unsafeRunAsyncWith(a)(exit => callback(exit.fold(ZIO.failCause(_), UIO(_)))))
}
