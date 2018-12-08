package scalaz.zio

class FiberLocalSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec {

  def is =
    "FiberLocalSpec".title ^ s2"""
    Create a new FiberLocal and
      retrieve fiber-local data that has been set          $e1
      empty fiber-local data                               $e2
      automatically sets and frees data                    $e3
      fiber-local data cannot be accessed by other fibers  $e4
      setting does not overwrite existing fiber-local data $e5
    """

  def e1 = unsafeRun(
    for {
      local <- FiberLocal.make[Int]
      _     <- local.set(10)
      v     <- local.get
    } yield v must_=== Some(10)
  )

  def e2 = unsafeRun(
    for {
      local <- FiberLocal.make[Int]
      _     <- local.set(10)
      _     <- local.empty
      v     <- local.get
    } yield v must_=== None
  )

  def e3 = unsafeRun(
    for {
      local <- FiberLocal.make[Int]
      v1    <- local.locally(10)(local.get)
      v2    <- local.get
    } yield (v1 must_=== Some(10)) and (v2 must_=== None)
  )

  def e4 = unsafeRun(
    for {
      local <- FiberLocal.make[Int]
      p     <- Promise.make[Nothing, Unit]
      _     <- (local.set(10) *> p.complete(())).fork
      _     <- p.get
      v     <- local.get
    } yield (v must_=== None)
  )

  def e5 = unsafeRun(
    for {
      local <- FiberLocal.make[Int]
      p     <- Promise.make[Nothing, Unit]
      f     <- (local.set(10) *> p.get *> local.get).fork
      _     <- local.set(20)
      _     <- p.complete(())
      v1    <- f.join
      v2    <- local.get
    } yield (v1 must_=== Some(10)) and (v2 must_== Some(20))
  )

}
