package scalaz.zio

class FiberSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {
  def is =
    "FiberSpec".title ^ s2"""
    Create a new Fiber and
      lift it into Managed $e1
    """

  def e1 = {
    val managed = Fiber.succeed(true).toManaged

    val test =
      for {
        ref   <- Ref.make(false)
        setIO <- managed.use(f => UIO.fromFiber(f.map(ref.set)))
        _     <- setIO
        value <- ref.get
      } yield value must beTrue

    unsafeRun(test)
  }
}
