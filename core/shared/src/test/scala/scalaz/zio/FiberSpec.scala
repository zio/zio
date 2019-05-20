package scalaz.zio

class FiberSpec extends BaseCrossPlatformSpec {
  def is =
    "FiberSpec".title ^ s2"""
    Create a new Fiber and
      lift it into Managed                          $e1
    `inheritLocals` works for Fiber created using:
      `map`                                         $e2
      `orElse`                                      $e3
      `zip`                                         $e4
    """

  val (initial, update) = ("initial", "update")

  def e1 =
    for {
      ref   <- Ref.make(false)
      latch <- Promise.make[Nothing, Unit]
      fiber <- (latch.succeed(()) *> IO.unit)
                .bracket_[Any, Nothing]
                .apply[Any](ref.set(true))(IO.never)
                .fork //    TODO: Dotty doesn't infer this properly
      _     <- latch.await
      _     <- fiber.toManaged.use(_ => IO.unit)
      _     <- fiber.await
      value <- ref.get
    } yield value must beTrue

  def e2 =
    for {
      fiberRef <- FiberRef.make(initial)
      latch    <- Promise.make[Nothing, Unit]
      child    <- (fiberRef.set(update) *> latch.succeed(())).fork
      _        <- latch.await
      _        <- child.map(_ => ()).inheritFiberRefs
      value    <- fiberRef.get
    } yield value must beTheSameAs(update)

  def e3 =
    for {
      fiberRef  <- FiberRef.make(initial)
      semaphore <- Semaphore.make(2)
      _         <- semaphore.acquireN(2)
      child1    <- (fiberRef.set("child1") *> semaphore.release).fork
      child2    <- (fiberRef.set("child2") *> semaphore.release).fork
      _         <- semaphore.acquireN(2)
      _         <- child1.orElse(child2).inheritFiberRefs
      value     <- fiberRef.get
    } yield value must beTheSameAs("child1")

  def e4 =
    for {
      fiberRef  <- FiberRef.make(initial)
      semaphore <- Semaphore.make(2)
      _         <- semaphore.acquireN(2)
      child1    <- (fiberRef.set("child1") *> semaphore.release).fork
      child2    <- (fiberRef.set("child2") *> semaphore.release).fork
      _         <- semaphore.acquireN(2)
      _         <- child1.zip(child2).inheritFiberRefs
      value     <- fiberRef.get
    } yield value must beTheSameAs("child1")
}
