package differcompositionalupdates

import zio._

/** Title: The Differ Data Type — Compositional FiberRef Updates End-to-End
  * Description: A comprehensive example showing how Differ[Value, Patch] enables
  * concurrent fiber updates to a FiberRef to be merged faithfully rather than
  * overwritten. Covers a custom Differ, built-in Differs (set, map), composed
  * Differs (zip), and the internal ZEnvironment Differ used by ZIO itself.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.CompleteExample"
  */
object CompleteExample extends ZIOAppDefault {

  // ===== Custom Differ: additive numeric deltas =====
  // diff records the delta (newValue - oldValue), patch applies it (oldValue + delta),
  // combine merges two deltas (addition is associative and commutative), empty = 0.
  val addDiffer: Differ[Int, Int] = new Differ[Int, Int] {
    def combine(first: Int, second: Int): Int   = first + second
    def diff(oldValue: Int, newValue: Int): Int = newValue - oldValue
    def empty: Int                              = 0
    def patch(patch: Int)(oldValue: Int): Int   = oldValue + patch
  }

  // Two fibers each add a number; because addDiffer.combine merges deltas,
  // the result is always the sum — never one or the other.
  val addDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("[addDiffer] Starting concurrent fiber updates...").orDie
      ref    <- FiberRef.makePatch(0, addDiffer, 0)
      left   <- ref.update(_ + 10).fork   // fiber 1 contributes delta +10
      right  <- ref.update(_ + 5).fork    // fiber 2 contributes delta +5
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"[addDiffer] Result after join: $result").orDie
    } yield ()
  }

  // ===== Built-in Differ.set — membership merging =====
  // Both fibers' Add patches are combined: the final Set always contains both elements.
  val setDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("[Differ.set] Starting concurrent fiber updates...").orDie
      ref    <- FiberRef.makePatch(Set.empty[String], Differ.set[String])
      left   <- ref.update(_ + "fiber-left").fork
      right  <- ref.update(_ + "fiber-right").fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"[Differ.set] Members after join: $result").orDie
    } yield ()
  }

  // ===== Built-in Differ.map — key-level merging =====
  // Each fiber adds a different key; MapPatch.combine keeps both Add operations.
  val mapDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("[Differ.map] Starting concurrent fiber updates...").orDie
      differ  = Differ.map[String, Int, Int => Int](Differ.update[Int])
      ref    <- FiberRef.makePatch(Map.empty[String, Int], differ)
      left   <- ref.update(_ + ("score" -> 100)).fork
      right  <- ref.update(_ + ("level" -> 5)).fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"[Differ.map] Entries after join: $result").orDie
    } yield ()
  }

  // ===== Composed Differ: zip (<*>) — field-by-field merging =====
  // Differ.set tracks the Set field; Differ.update tracks the Int field independently.
  // Each fiber modifies a different field; both changes survive the join.
  val zipDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("[zip (<*>)] Starting concurrent fiber updates...").orDie
      differ  = Differ.set[String] <*> Differ.update[Int]
      ref    <- FiberRef.makePatch((Set.empty[String], 0), differ)
      left   <- ref.update { case (s, n) => (s + "tag", n) }.fork   // adds "tag" to set
      right  <- ref.update { case (s, n) => (s, 99) }.fork          // sets int to 99
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"[zip (<*>)] Pair after join: $result").orDie
    } yield ()
  }

  // ===== Internal ZIO: ZEnvironment Differ =====
  // ZIO uses Differ.environment internally for currentEnvironment so that two fibers
  // running withEnvironment with different services both keep their service after join.
  // ZEnvironment[+R] is covariant; get[A >: R] retrieves a service that is a supertype
  // of the environment type R. Starting from ServiceA with ServiceB lets us get either.
  case class ServiceA(name: String)
  case class ServiceB(port: Int)

  val environmentDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    // ZEnvironment.apply[A, B] creates ZEnvironment[A with B] — correctly typed
    val initial = ZEnvironment(ServiceA("default"), ServiceB(0))
    for {
      _      <- Console.printLine("[ZEnvironment] Starting concurrent fiber updates...").orDie
      ref    <- FiberRef.makeEnvironment[ServiceA with ServiceB](initial)
      left   <- ref.update(_.add(ServiceA("auth"))).fork
      right  <- ref.update(_.add(ServiceB(8080))).fork
      _      <- left.join
      _      <- right.join
      env    <- ref.get
      _      <- Console.printLine(s"[ZEnvironment] ServiceA: ${env.get[ServiceA].name}").orDie
      _      <- Console.printLine(s"[ZEnvironment] ServiceB port: ${env.get[ServiceB].port}").orDie
    } yield ()
  }

  override def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== The Differ Data Type: Compositional FiberRef Updates ===").orDie
      _ <- Console.printLine("").orDie
      _ <- addDifferDemo
      _ <- Console.printLine("").orDie
      _ <- setDifferDemo
      _ <- Console.printLine("").orDie
      _ <- mapDifferDemo
      _ <- Console.printLine("").orDie
      _ <- zipDifferDemo
      _ <- Console.printLine("").orDie
      _ <- environmentDemo
      _ <- Console.printLine("").orDie
      _ <- Console.printLine("All updates from concurrent fibers were merged, not overwritten.").orDie
    } yield ()
}
