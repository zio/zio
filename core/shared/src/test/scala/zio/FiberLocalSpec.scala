package zio

@deprecated("use FiberRef", "1.0.0")
class FiberLocalSpec extends BaseCrossPlatformSpec {

  def is =
    "FiberLocalSpec".title ^ s2"""
    Create a new FiberLocal and
      retrieve fiber-local data that has been set          $e1
      empty fiber-local data                               $e2
      automatically sets and frees data                    $e3
      fiber-local data cannot be accessed by other fibers  $e4
      setting does not overwrite existing fiber-local data $e5
    """

  def e1 =
    for {
      local <- FiberLocal.make[Int]
      _     <- local.set(10)
      v     <- local.get
    } yield v must_=== Some(10)

  def e2 =
    for {
      local <- FiberLocal.make[Int]
      _     <- local.set(10)
      _     <- local.empty
      v     <- local.get
    } yield v must_=== None

  def e3 =
    for {
      local <- FiberLocal.make[Int]
      v1    <- local.locally(10)(local.get)
      v2    <- local.get
    } yield (v1 must_=== Some(10)) and (v2 must_=== None)

  def e4 =
    for {
      local <- FiberLocal.make[Int]
      p     <- Promise.make[Nothing, Unit]
      _     <- (local.set(10) *> p.succeed(())).fork
      _     <- p.await
      v     <- local.get
    } yield (v must_=== None)

  def e5 =
    for {
      local <- FiberLocal.make[Int]
      p     <- Promise.make[Nothing, Unit]
      f     <- (local.set(10) *> p.await *> local.get).fork
      _     <- local.set(20)
      _     <- p.succeed(())
      v1    <- f.join
      v2    <- local.get
    } yield (v1 must_=== Some(10)) and (v2 must_== Some(20))

}
