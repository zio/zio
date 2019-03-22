package scalaz.zio

import scala.language.implicitConversions

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
      fiber <- (latch.succeed(()) *> IO.unit).bracket_(ref.set(true))(IO.never).fork
      _     <- latch.await
      _     <- fiber.toManaged.use(_ => IO.unit)
      _     <- fiber.await
      value <- ref.get
    } yield value must beTrue
  )
}
