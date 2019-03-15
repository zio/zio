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
      fiber <- IO.never.ensuring(ref.set(true)).fork
      _     <- fiber.toManaged.use(_ => IO.unit)
      value <- ref.get
    } yield value must beTrue
  )
}
