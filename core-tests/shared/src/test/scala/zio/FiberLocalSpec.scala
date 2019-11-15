package zio

import zio.test._
import zio.test.Assertion._

@deprecated("use FiberRef", "1.0.0")
object FiberLocalSpec extends ZIOBaseSpec {

  def spec = suite("FiberLocalSpec")(
    suite("Create a new FiberLocal and")(
      testM("retrieve fiber-local data that has been set") {
        for {
          local <- FiberLocal.make[Int]
          _     <- local.set(10)
          v     <- local.get
        } yield assert(v, isSome(equalTo(10)))
      },
      testM("empty fiber-local data") {
        for {
          local <- FiberLocal.make[Int]
          _     <- local.set(10)
          _     <- local.empty
          v     <- local.get
        } yield assert(v, isNone)
      },
      testM("automatically sets and frees data") {
        for {
          local <- FiberLocal.make[Int]
          v1    <- local.locally(10)(local.get)
          v2    <- local.get
        } yield assert(v1, isSome(equalTo(10))) && assert(v2, isNone)
      },
      testM("fiber-local data cannot be accessed by other fibers") {
        for {
          local <- FiberLocal.make[Int]
          p     <- Promise.make[Nothing, Unit]
          _     <- (local.set(10) *> p.succeed(())).fork
          _     <- p.await
          v     <- local.get
        } yield assert(v, isNone)
      },
      testM("setting does not overwrite existing fiber-local data") {
        for {
          local <- FiberLocal.make[Int]
          p     <- Promise.make[Nothing, Unit]
          f     <- (local.set(10) *> p.await *> local.get).fork
          _     <- local.set(20)
          _     <- p.succeed(())
          v1    <- f.join
          v2    <- local.get
        } yield assert(v1, isSome(equalTo(10))) && assert(v2, isSome(equalTo(20)))
      }
    )
  )
}
