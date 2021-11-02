package zio

import zio.test._

object SupervisorSpec extends ZIOBaseSpec {

  private val threadLocal = new ThreadLocal[Option[String]] {
    override def initialValue() = None
  }

  private val initialValue = "initial-value"

  def spec: ZSpec[Environment, Failure] = suite("SupervisorSpec")(
    suite("fiberRefTrackingSupervisor")(
      fiberRefTrackingSupervisorSuite(InstrumentRuntime) ++
        fiberRefTrackingSupervisorSuite(InstrumentEffect): _*
    )
  )

  private def fiberRefTrackingSupervisorSuite[In](
    runInstrumented: RunInstrumented
  )(implicit testConstructor: TestConstructor[Nothing, In]): Seq[ZSpec[Environment, Failure]] = {
    val run = runInstrumented.runIn[Throwable, Assert](initialValue, a => threadLocal.set(Some(a))) _
    Seq(
      test(s"[${runInstrumented.show}] track initial value") {
        run { _ =>
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
      test(s"[${runInstrumented.show}] track FiberRef.set / modify") {
        val newValue1 = "new-value1"
        val newValue2 = "new-value2"
        run { fiberRef =>
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
      test(s"[${runInstrumented.show}] track in FiberRef.locally") {
        val newValue1 = "new-value1"
        val newValue2 = "new-value2"
        run { fiberRef =>
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
      test(s"[${runInstrumented.show}] track in -> FiberRef.locallyManaged") {
        val newValue1 = "new-value1"
        val newValue2 = "new-value2"
        run { fiberRef =>
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
  }

  private sealed trait RunInstrumented {
    def runIn[E, A](initialValue: String, link: String => Unit)(f: FiberRef[String] => IO[E, A]): IO[E, A]
    def show: String
  }

  private case object InstrumentRuntime extends RunInstrumented {
    override def runIn[E, A](initialValue: String, link: String => Unit)(f: FiberRef[String] => IO[E, A]): IO[E, A] = {
      val (aspect, fiberRef) = RuntimeConfigAspect.trackFiberRef(initialValue)(link)
      val runtime            = Runtime.default.mapRuntimeConfig(_ @@ aspect)
      ZIO.async[Any, E, A](callback =>
        runtime.unsafeRunAsyncWith(f(fiberRef))(exit => callback(exit.fold(ZIO.failCause(_), UIO(_))))
      )
    }

    override def show: String = "Instrument Runtime"
  }

  private case object InstrumentEffect extends RunInstrumented {
    override def runIn[E, A](initialValue: String, link: String => Unit)(f: FiberRef[String] => IO[E, A]): IO[E, A] = {
      val (aspect, fiberRef) = RuntimeConfigAspect.trackFiberRef(initialValue)(link)
      (
        //force suspend and resume
        ZIO.sleep(1.milli).provideLayer(Clock.live) *>
          f(fiberRef)
      ).withRuntimeConfig(Runtime.default.runtimeConfig @@ aspect)
    }

    override def show: String = "Instrumenting effect"
  }

  def threadLocalGet =
    Task(threadLocal.get)
}
