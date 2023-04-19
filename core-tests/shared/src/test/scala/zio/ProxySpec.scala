package zio

import zio.test._

object ProxySpec extends ZIOSpecDefault {

  val spec = suite("Proxy")(
    test("generates simple proxy") {
      trait Foo { def bar: UIO[String] }

      val service: Foo = new Foo { def bar = ZIO.succeed("baz") }
      for {
        ref  <- ScopedRef.make(service)
        proxy = Proxy.generate(ref)
        res  <- proxy.bar
      } yield assertTrue(res == "baz")
    },
    test("keeps default implementations") {
      trait Foo {
        def bar: UIO[String]

        def qux: String = "quux"
      }

      val service: Foo = new Foo {
        def bar = ZIO.succeed("baz")
      }
      for {
        ref  <- ScopedRef.make(service)
        proxy = Proxy.generate(ref)
      } yield assertTrue(proxy.qux == "quux")
    },
    test("fails to compile with non-ZIO abstract method") {

      for {
        res <- typeCheck(
                 """
        trait Foo {
          def bar: UIO[String]
          def qux: String
        }

        val service: Foo = new Foo {
          def bar = ZIO.succeed("baz")
          def qux = "quux"
        }

        for {
          ref <- ScopedRef.make(service)
        } yield Proxy.generate(ref)
        """
               )
        errorMessage <- ZIO.fromEither(res.swap)
        _            <- ZIO.logInfo(errorMessage)
      } yield assertTrue(errorMessage.contains("non-ZIO method qux"))

    }
  )

}
