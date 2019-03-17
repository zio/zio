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
      fiber <- IO.unit.bracket(_ => ref.set(true) *> latch.succeed(()))(_ => IO.never).fork
      _     <- fiber.toManaged.use(_ => latch.await)
      value <- ref.get
    } yield value must beTrue
  )
}
