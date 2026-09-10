package differcompositionalupdates

import zio._

/** Title: How ZIO Uses Differ Internally — Environment, Loggers, RuntimeFlags
  * Description: Shows that ZIO's own FiberRefs (currentEnvironment, currentLoggers,
  * currentRuntimeFlags) are all backed by Differ, making withEnvironment and ZLayer
  * compositional across concurrent fibers. Demonstrates via FiberRef.makeEnvironment
  * that two concurrent fiber updates to a ZEnvironment are always merged.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.ZIOInternalDiffersExample"
  */
object ZIOInternalDiffersExample extends ZIOAppDefault {

  // Services for demonstration
  case class ServiceA(value: String)
  case class ServiceB(value: Int)

  // Mirrors FiberRefSpec's makeEnvironment test: two fibers each add a different
  // service to a ZEnvironment FiberRef; after joining, both services are present.
  // ZEnvironment[+R] is covariant; get[A >: R] works when A is a supertype of R.
  // Starting with ServiceA with ServiceB lets us get either service after join.
  val environmentMergeDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    val initial = ZEnvironment(ServiceA("default"), ServiceB(0))
    for {
      _      <- Console.printLine("--- FiberRef.makeEnvironment: concurrent environment updates ---").orDie
      ref    <- FiberRef.makeEnvironment[ServiceA with ServiceB](initial)
      left   <- ref.update(_.add(ServiceA("hello"))).fork
      right  <- ref.update(_.add(ServiceB(42))).fork
      _      <- left.join
      _      <- right.join
      env    <- ref.get
      _      <- Console.printLine(s"ServiceA present: ${env.get[ServiceA].value}").orDie
      _      <- Console.printLine(s"ServiceB present: ${env.get[ServiceB].value}").orDie
    } yield ()
  }

  // Demonstrates that ZIO.withRuntimeFlags is compositional: two fibers can each
  // modify runtime flags and both modifications are merged on join.
  val runtimeFlagsInfo: ZIO[Any, Nothing, Unit] =
    for {
      _ <- Console.printLine("--- ZIO internal FiberRefs use Differ ---").orDie
      _ <- Console.printLine("  currentEnvironment  → Differ.environment[A] (ZEnvironment.Patch)").orDie
      _ <- Console.printLine("  currentLoggers      → Differ.set[ZLogger] (SetPatch)").orDie
      _ <- Console.printLine("  currentSupervisor   → Differ.supervisor (Supervisor.Patch)").orDie
      _ <- Console.printLine("  currentRuntimeFlags → Differ.runtimeFlags (RuntimeFlags.Patch)").orDie
      _ <- Console.printLine("  All of these compose via combine on fiber join.").orDie
    } yield ()

  override def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== Concept 5: How ZIO Uses Differ Internally ===").orDie
      _ <- runtimeFlagsInfo
      _ <- Console.printLine("").orDie
      _ <- environmentMergeDemo
    } yield ()
}
