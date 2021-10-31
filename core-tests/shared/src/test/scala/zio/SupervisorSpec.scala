package zio
import zio.Supervisor.Propagation
import zio.duration.durationInt
import zio.test._

import java.util.concurrent.atomic.AtomicReference

object SupervisorSpec extends ZIOBaseSpec {
  override def spec: ZSpec[Environment, Failure] = suite("SupervisorSpec")(
    suite("supervise resume / suspend")(
      testM("track resume and suspend (based on superviseOperations)") {
        checkM(Gen.boolean) { superviseOperations =>
          val supervisor = new RecordingSupervisor
          val runtime =
            Runtime.default.mapPlatform(_.withSuperviseOperations(superviseOperations).withSupervisor(supervisor))
          runIn(runtime) {
            for {
              fiber1 <- currentFiberId
              forked <- (currentFiberId <* ZIO.sleep(20.millis)).fork
              fiber2 <- forked.join
            } yield {
              val recorded = supervisor.logged
              assertTrue(
                recorded.toSet == (Set(
                  s"unsafeOnResume($fiber1)",
                  s"unsafeOnResume($fiber2)"
                ).filter(_ => superviseOperations) ++
                  Set(
                    s"unsafeOnSuspend($fiber1)",
                    s"unsafeOnSuspend($fiber2)"
                  ))
              )
            }
          }
        }
      },
      testM("combined supervisors are both notified on resume / suspend") {
        checkM(combineSupervisorsGen) { combine =>
          val supervisor1 = new RecordingSupervisor
          val supervisor2 = new RecordingSupervisor
          val runtime =
            Runtime.default.mapPlatform(
              _.withSuperviseOperations(true)
                .withSupervisor(combine.combine(supervisor1, supervisor2))
            )
          runIn(runtime) {
            for {
              fiber1 <- currentFiberId
            } yield {
              assertTrue(
                supervisor1.logged.toSet == Set(s"unsafeOnSuspend($fiber1)", s"unsafeOnResume($fiber1)") &&
                  supervisor2.logged.toSet == Set(s"unsafeOnSuspend($fiber1)", s"unsafeOnResume($fiber1)")
              )
            }
          }
        }
      }
    )
  )

  case class Combine(tag: String, combine: (Supervisor[Unit], Supervisor[Unit]) => Supervisor[_]) {
    override def toString: String = tag
  }

  private val combineSupervisorsGen = Gen.boolean.map { and =>
    if (and) Combine("combining with &&", _ && _)
    else Combine("combining with ||", _ || _)
  }

  private def currentFiberId = Task(
    Fiber.unsafeCurrentFiber().fold("None")(_.asInstanceOf[Fiber.Runtime[_, _]].id.toString)
  )

  private class RecordingSupervisor extends Supervisor[Unit] {
    private val logRef = new AtomicReference(Seq.empty[String])

    override def value: UIO[Unit] = UIO.unit

    override private[zio] def unsafeOnStart[R, E, A](
      environment: R,
      effect: ZIO[R, E, A],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A]
    ) = Propagation.Continue

    override private[zio] def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]) = Propagation.Continue

    override private[zio] def unsafeOnSuspend[E, A1](fiber: Fiber.Runtime[E, A1]): Unit =
      log(s"unsafeOnSuspend(${fiber.id})")

    override private[zio] def unsafeOnResume[E, A1](fiber: Fiber.Runtime[E, A1]): Unit =
      log(s"unsafeOnResume(${fiber.id})")

    def logged = logRef.get

    def log(message: String): Unit = {
      logRef.updateAndGet(_ :+ message)
      ()
    }
  }

  private def runIn[R, E, A](rt: Runtime[R])(a: ZIO[R, E, A]) =
    ZIO.effectAsync[Any, E, A](callback => rt.unsafeRunAsync(a)(exit => callback(exit.fold(ZIO.halt(_), UIO(_)))))
}
