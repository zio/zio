package zio

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
      ref <- Ref.make(false)
      fiber <- withLatch { release =>
                (release *> IO.unit).bracket_(ref.set(true))(IO.never).fork
              }
      _     <- fiber.toManaged.use(_ => IO.unit)
      _     <- fiber.await
      value <- ref.get
    } yield value must beTrue

  def e2 =
    for {
      fiberRef <- FiberRef.make(initial)
      child <- withLatch { release =>
                (fiberRef.set(update) *> release).fork
              }
      _     <- child.map(_ => ()).inheritFiberRefs
      value <- fiberRef.get
    } yield value must beTheSameAs(update)

  def e3 =
    for {
      fiberRef <- FiberRef.make(initial)
      latch1   <- Promise.make[Nothing, Unit]
      latch2   <- Promise.make[Nothing, Unit]
      child1   <- (fiberRef.set("child1") *> latch1.succeed(())).fork
      child2   <- (fiberRef.set("child2") *> latch2.succeed(())).fork
      _        <- latch1.await *> latch2.await *> child1.orElse(child2).inheritFiberRefs
      value    <- fiberRef.get
    } yield value must beTheSameAs("child1")

  def e4 =
    for {
      fiberRef <- FiberRef.make(initial)
      latch1   <- Promise.make[Nothing, Unit]
      latch2   <- Promise.make[Nothing, Unit]
      child1   <- (fiberRef.set("child1") *> latch1.succeed(())).fork
      child2   <- (fiberRef.set("child2") *> latch2.succeed(())).fork
      _        <- latch1.await *> latch2.await *> child1.zip(child2).inheritFiberRefs
      value    <- fiberRef.get
    } yield value must beTheSameAs("child1")
}
