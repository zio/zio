package scalaz.zio

class FiberSpec extends BaseCrossPlatformSpec {
  def is =
    "FiberSpec".title ^ s2"""
    Create a new Fiber and
      lift it into Managed $e1
    """

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
}
