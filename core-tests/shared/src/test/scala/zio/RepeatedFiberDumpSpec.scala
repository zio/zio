package zio

import zio._
import zio.clock._
import zio.console._
import zio.duration._
import zio.test._
import zio.test.TestAspect._
import zio.Supervisor.Propagation
import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.SortedSet
import java.util.function.UnaryOperator
import zio.test.environment.Live

object RepeatedFiberDumpSpec extends ZIOBaseSpec {

  override def runner: TestRunner[zio.test.environment.TestEnvironment, Any] =
    defaultTestRunner.withPlatform(_.withSupervisor(simpleSupervisor))

  override def spec = (suite("The Fiber context should")(
    pollDumpForever
  )) @@ timed @@ timeout(90.seconds)

  private val pollDumpForever = testM("allow to regularly poll the dump of all current fibers")(for {
    _ <- putStrLn("Starting test")
    _ <- timeWarp.fork
    // Start a number of busy Fibers
    _  <- ZIO.foreach(0.to(10))(i => recurringWork(('A' + i).toChar).fork)
    f  <- ZIO.unit.schedule(Schedule.duration(300.seconds)).fork
    fd <- (dumpLoop.onError{ t =>
      putStrLn(t.failures.map(_.getMessage()).mkString("\n")) *> f.interrupt
    }).schedule(Schedule.spaced(1.second)).fork
    _  <- f.join
    _  <- putStrLn("Stopping test")
    _  <- fd.interrupt
  } yield assertCompletes)

  // Advance the test clock every 10 millis by a second
  private def timeWarp = for {
    _ <-
      Live.withLive(environment.TestClock.adjust(java.time.Duration.ofSeconds(1)))(_.repeat(Schedule.spaced(10.millis)))
  } yield ()

  // Create a fiber that does something in a loop, regularly calling itself .....
  private def recurringWork(c : Char): ZIO[Clock with Console, Nothing, Unit] = {

    def go: ZIO[Clock with Console, Nothing, Unit] =
      putStr(s"$c") *> go.schedule(Schedule.duration(1.second)).flatMap(_ => ZIO.unit)
    go
  }

  // Access the pretty Printed dump that should eventually yield the exception of #4384
  private def dumpLoop : ZIO[Console, Throwable, String] =
    printDumps(simpleSupervisor) <* putStrLn("")

  private def getDumps(sv: Supervisor[SortedSet[Fiber.Runtime[Any, Any]]]): UIO[Iterable[Fiber.Dump]] = {
    putStr(".")
    sv.value.flatMap(fibers => Fiber.dump(fibers.toSeq: _*))
  }

  private def printDumps(sv: Supervisor[SortedSet[Fiber.Runtime[Any, Any]]]): ZIO[Any, Throwable, String] = for {
    dumps <- getDumps(sv)
    text  <- IO.foreach(dumps)(_.prettyPrintM)
  } yield (text.mkString("\n"))

  // A simple supervisor that
  private lazy val simpleSupervisor: Supervisor[SortedSet[Fiber.Runtime[Any, Any]]] =
    new Supervisor[SortedSet[Fiber.Runtime[Any, Any]]] {

      private[this] val fibers: AtomicReference[Map[Fiber.Id, Fiber.Runtime[Any, Any]]] = new AtomicReference(
        Map.empty
      )

      override def value: zio.UIO[SortedSet[Fiber.Runtime[Any, Any]]] =
        UIO(SortedSet(fibers.get.view.values.toSeq: _*))

      override private[zio] def unsafeOnStart[R, E, A](
        environment: R,
        effect: ZIO[R, E, A],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E, A]
      ): Supervisor.Propagation = {

        fibers.updateAndGet(new UnaryOperator[Map[Fiber.Id, Fiber.Runtime[Any, Any]]] {
          override def apply(t: Map[Fiber.Id, Fiber.Runtime[Any, Any]]): Map[Fiber.Id, Fiber.Runtime[Any, Any]] =
            t ++ Map(fiber.id -> fiber)
        })

        Propagation.Continue
      }

      override private[zio] def unsafeOnEnd[R, E, A](
        value: Exit[E, A],
        fiber: Fiber.Runtime[E, A]
      ): Propagation = {

        fibers.updateAndGet(new UnaryOperator[Map[Fiber.Id, Fiber.Runtime[Any, Any]]] {
          override def apply(t: Map[Fiber.Id, Fiber.Runtime[Any, Any]]): Map[Fiber.Id, Fiber.Runtime[Any, Any]] =
            t - fiber.id
        })

        Propagation.Continue
      }
    }
}
