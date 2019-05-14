package scalaz.zio

import java.util.concurrent.TimeUnit

import scalaz.zio.duration.Duration

class FiberLocalSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  //retrieve fiber-local data that has been set          $e1
  //empty fiber-local data                               $e2
  //automatically sets and frees data                    $e3
  //fiber-local data cannot be accessed by other fibers  $e4
  //setting does not overwrite existing fiber-local data $e5

  def is =
    "FiberLocalSpec".title ^ s2"""
    Create a new FiberLocal and

      child sees data written by parent                    $e6
      parent doesn't see writes by child                   $e7
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
      _     <- (local.set(10) *> p.succeed(())).fork
      _     <- p.await
      v     <- local.get
    } yield (v must_=== None)
  )

  def e5 = unsafeRun(
    for {
      local <- FiberLocal.make[Int]
      p     <- Promise.make[Nothing, Unit]
      f     <- (local.set(10) *> p.await *> local.get).fork
      _     <- local.set(20)
      _     <- p.succeed(())
      v1    <- f.join
      v2    <- local.get
    } yield (v1 must_=== Some(10)) and (v2 must_== Some(20))
  )

  def e6 = unsafeRun(
    for {
      local <- FiberLocal.make[Int]
      _     <- local.set(10)
      f     <- local.get.fork
      v1    <- f.join
    } yield v1 must_=== Some(10)
  )

  def e7 = unsafeRun(
    for {
      local <- FiberLocal.make[Int]
      _     <- local.set(42)
      f     <- (local.set(10) *> local.get).fork
      v1    <- local.get
      v2    <- f.join
    } yield (v1 must_=== Some(42)) and (v2 must_=== Some(10))
  )

  def e8 = unsafeRun(
    for {
      local <- FiberLocal.make[Int]
      f1    <- local.set(10).fork
      f2    <- local.set(20).fork
      f     = f1.zip(f2)
      _     <- ZIO.inheritLocals(f)
      v     <- local.get
    } yield v must_=== ???
  )

}
