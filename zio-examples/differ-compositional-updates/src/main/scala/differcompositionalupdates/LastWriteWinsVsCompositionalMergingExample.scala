package differcompositionalupdates

import zio._

/** Title: Last-Write-Wins vs. Compositional Merging
  * Description: Demonstrates that Differ.update (last-write-wins) loses one fiber's update
  * when two fibers concurrently modify a FiberRef, then contrasts it with a custom addDiffer
  * that merges both changes faithfully.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.LastWriteWinsVsCompositionalMergingExample"
  */
object LastWriteWinsVsCompositionalMergingExample extends ZIOAppDefault {

  // A custom Differ that records numeric deltas: diff = subtraction, patch = addition.
  // Because both operations are inverse (and combine is addition), concurrent fiber
  // patches are always merged: delta1 + delta2 = the sum of both updates.
  val addDiffer: Differ[Int, Int] = new Differ[Int, Int] {
    def combine(first: Int, second: Int): Int   = first + second
    def diff(oldValue: Int, newValue: Int): Int = newValue - oldValue
    def empty: Int                              = 0
    def patch(patch: Int)(oldValue: Int): Int   = oldValue + patch
  }

  // ===== Demo 1: Differ.update — last-write-wins =====
  // Two fibers both update the ref; the last one to join wins.
  // The result is either 10 or 5 — never 15.
  val lastWriteWinsDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- Differ.update (last-write-wins) ---").orDie
      ref    <- FiberRef.makePatch(0, Differ.update[Int])
      left   <- ref.update(_ + 10).fork  // wants to add 10
      right  <- ref.update(_ + 5).fork   // wants to add 5
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"Last-write-wins result: $result  (10 or 5, never 15)").orDie
    } yield ()
  }

  // ===== Demo 2: addDiffer — compositional merge =====
  // Two fibers both update the ref; both deltas (10 and 5) are merged.
  // The result is always 15 — deterministic and correct.
  val compositionalMergeDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- addDiffer (compositional merge) ---").orDie
      ref    <- FiberRef.makePatch(0, addDiffer, 0)  // initial = 0, fork patch = 0
      left   <- ref.update(_ + 10).fork              // fiber 1 adds 10 → delta 10
      right  <- ref.update(_ + 5).fork               // fiber 2 adds 5  → delta 5
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"Compositional merge result: $result  (always 15)").orDie
    } yield ()
  }

  override def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== Concept 1: Last-Write-Wins vs. Compositional Merging ===").orDie
      _ <- lastWriteWinsDemo
      _ <- Console.printLine("").orDie
      _ <- compositionalMergeDemo
    } yield ()
}
