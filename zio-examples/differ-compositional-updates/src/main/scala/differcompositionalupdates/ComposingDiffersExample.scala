package differcompositionalupdates

import zio._

/** Title: Composing Differs — zip, orElseEither, and transform
  * Description: Demonstrates how to compose Differs with <*> (zip) for product types,
  * <+> (orElseEither) for Either values, and transform for isomorphic types.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.ComposingDiffersExample"
  */
object ComposingDiffersExample extends ZIOAppDefault {

  // ===== zip (<*>) — product type merging =====
  // One fiber adds an element to the Set; another updates the Int.
  // Both changes are merged independently by their respective Differs.
  val zipDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- zip (<*>): (Set[String], Int) ---").orDie
      differ  = Differ.set[String] <*> Differ.update[Int]
      ref    <- FiberRef.makePatch((Set.empty[String], 0), differ)
      left   <- ref.update { case (s, n) => (s + "item", n) }.fork   // adds to set
      right  <- ref.update { case (s, n) => (s, 42) }.fork           // overwrites int
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"zip result: $result").orDie  // (Set(item), 42)
    } yield ()
  }

  // ===== orElseEither (<+>) — Either type merging =====
  // When two fibers both update the same side, their patches are combined.
  // When one switches sides, SetLeft / SetRight describes the transition.
  val orElseEitherDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- orElseEither (<+>): Either[String, Int] ---").orDie
      differ  = Differ.update[String] <+> Differ.update[Int]
      ref    <- FiberRef.makePatch(Left("hello"): Either[String, Int], differ)
      left   <- ref.update {
                  case Left(s)  => Left(s + " world")   // same side: updates the string
                  case Right(n) => Right(n)
                }.fork
      right  <- ref.update {
                  case Left(_)  => Right(99)             // switches to Right side
                  case Right(n) => Right(n)
                }.fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"orElseEither result: $result").orDie
    } yield ()
  }

  // ===== transform — isomorphic type adaptation =====
  // Lift a Differ[String, String => String] to Differ[Option[String], String => String]
  // using the isomorphism Option[String] <-> String (with a default).
  val transformDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- transform: Option[String] via Differ.update[String] ---").orDie
      // f: String → Option[String], g: Option[String] → String
      differ  = Differ.update[String].transform[Option[String]](
                  f = s    => if (s.isEmpty) None else Some(s),
                  g = opt  => opt.getOrElse("")
                )
      ref    <- FiberRef.makePatch(None: Option[String], differ)
      left   <- ref.update(_ => Some("hello")).fork
      _      <- left.join
      result <- ref.get
      _      <- Console.printLine(s"transform result: $result").orDie  // Some(hello)
    } yield ()
  }

  override def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== Concept 3: Composing Differs ===").orDie
      _ <- zipDemo
      _ <- Console.printLine("").orDie
      _ <- orElseEitherDemo
      _ <- Console.printLine("").orDie
      _ <- transformDemo
    } yield ()
}
