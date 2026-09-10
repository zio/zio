package differcompositionalupdates

import zio._

/** Title: Built-in Differs — set, map, chunk, and update
  * Description: Shows how to wire Differ.set, Differ.map, and Differ.chunk into
  * FiberRef.makePatch and observe that concurrent fiber additions are always merged,
  * not overwritten.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.BuiltInDiffersExample"
  */
object BuiltInDiffersExample extends ZIOAppDefault {

  // ===== Differ.set — membership merging =====
  // Two fibers each add a different element; both appear in the final Set.
  val setDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- Differ.set ---").orDie
      ref    <- FiberRef.makePatch(Set.empty[String], Differ.set[String])
      left   <- ref.update(_ + "left").fork
      right  <- ref.update(_ + "right").fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"Set result: $result").orDie  // Set(left, right)
    } yield ()
  }

  // ===== Differ.map — key-level merging =====
  // Two fibers add different keys; both entries appear in the final Map.
  val mapDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- Differ.map ---").orDie
      differ  = Differ.map[String, Int, Int => Int](Differ.update[Int])
      ref    <- FiberRef.makePatch(Map.empty[String, Int], differ)
      left   <- ref.update(_ + ("a" -> 1)).fork
      right  <- ref.update(_ + ("b" -> 2)).fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"Map result: $result").orDie  // Map(a -> 1, b -> 2)
    } yield ()
  }

  // ===== Differ.chunk — append merging =====
  // Two fibers each append different elements; both appear in the final Chunk.
  val chunkDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- Differ.chunk ---").orDie
      differ  = Differ.chunk[String, String => String](Differ.update[String])
      ref    <- FiberRef.makePatch(Chunk.empty[String], differ)
      left   <- ref.update(_ :+ "alpha").fork
      right  <- ref.update(_ :+ "beta").fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"Chunk result: $result").orDie  // Chunk(alpha, beta) or Chunk(beta, alpha)
    } yield ()
  }

  override def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== Concept 2: Built-in Differs ===").orDie
      _ <- setDifferDemo
      _ <- Console.printLine("").orDie
      _ <- mapDifferDemo
      _ <- Console.printLine("").orDie
      _ <- chunkDifferDemo
    } yield ()
}
