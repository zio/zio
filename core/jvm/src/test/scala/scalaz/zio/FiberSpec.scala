package scalaz.zio

class FiberSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {
  def is =
    "FiberSpec".title ^ s2"""
    Create a new Fiber and
      lift it into Managed $e1
    """

  def e1 = unsafeRun(
    for {
      ref   <- Ref.make(false)
      latch <- Promise.make[Nothing, Unit]
      fiber <- (latch.succeed(()) *> BIO.unit)
                .bracket_[Any, Nothing]
                .apply[Any](ref.set(true))(BIO.never)
                .fork //    TODO: Dotty doesn't infer this properly
      _     <- latch.await
      _     <- fiber.toManaged.use(_ => BIO.unit)
      _     <- fiber.await
      value <- ref.get
    } yield value must beTrue
  )
}
