package zio
import zio.Supervisor.Propagation
import zio.duration.durationInt
import zio.test._

object SupervisorSpec extends ZIOBaseSpec {
  override def spec: ZSpec[Environment, Failure] = suite("SupervisorSpec")(
    suite("supervise resume / suspend")(
      testM("track resume and suspend (based on superviseOperations)") {
        checkAllM(superviseOperationsGen) { case (_, superviseOperations) =>
          val supervisor = new RecordingSupervisor
          val runtime =
            Runtime.default.mapPlatform(_.withSuperviseOperations(superviseOperations).withSupervisor(supervisor))
          runIn(runtime) {
            for {
              fiber1 <- currentFiberId
              forked <- (currentFiberId <* ZIO.sleep(20.millis)).fork
              fiber2 <- forked.join
            } yield (fiber1, fiber2)
          } map { case (fiber1, fiber2) =>
            val recorded = supervisor.logged
            assertTrue(
              recorded.toSet == (Set(
                s"unsafeOnResume($fiber1)",
                s"unsafeOnResume($fiber2)",
                s"unsafeOnSuspend($fiber1)",
                s"unsafeOnSuspend($fiber2)"
              ))
            )
          }
        }
      },
      testM("combined supervisors are both notified on resume / suspend") {
        checkAllM(combineSupervisorsGen) { combine =>
          println(s"combine: $combine")
          val supervisor1 = new RecordingSupervisor
          val supervisor2 = new RecordingSupervisor
          val runtime =
            Runtime.default.mapPlatform(
              _.withSuperviseOperations(true)
                .withSupervisor(combine.combine(supervisor1, supervisor2))
            )
          runIn(runtime) {
            currentFiberId
          } map { fiber1 =>
            assertTrue(
              supervisor1.logged.toSet == Set(s"unsafeOnSuspend($fiber1)", s"unsafeOnResume($fiber1)") &&
                supervisor2.logged.toSet == Set(s"unsafeOnSuspend($fiber1)", s"unsafeOnResume($fiber1)")
            )
          }
        }
      }
    )
  )

  private case class Combine(tag: String, combine: (Supervisor[Unit], Supervisor[Unit]) => Supervisor[_]) {
    override def toString: String = tag
  }

  private val combineSupervisorsGen = Gen.fromIterable(
    Seq(
      Combine("combining with &&", _ && _),
      Combine("combining with ||", _ || _)
    )
  )

  private val superviseOperationsGen =
    Gen.fromIterable(Seq("superviseOperations" -> true, "superviseOperations" -> false))

  private def currentFiberId = Task(
    Fiber.unsafeCurrentFiber().fold("None")(_.asInstanceOf[Fiber.Runtime[_, _]].id.toString)
  )

  private class RecordingSupervisor extends Supervisor[Unit] {
    @volatile
    private var logRef = Seq.empty[String]

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

    def logged = logRef

    def log(message: String): Unit =
      logRef = logRef :+ message
  }

  private def runIn[R, E, A](rt: Runtime[R])(a: ZIO[R, E, A]) =
    ZIO.effectAsync[Any, E, A](callback => rt.unsafeRunAsync(a)(exit => callback(exit.fold(ZIO.halt(_), UIO(_)))))
}
