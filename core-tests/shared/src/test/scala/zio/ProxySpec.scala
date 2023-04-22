package zio

import scala.annotation.experimental
import zio.test._

@experimental
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
    test("generate proxy with type parameter") {
      trait Foo[A] { def bar: UIO[A] }

      val service: Foo[String] = new Foo[String] { def bar = ZIO.succeed("baz") }
      for {
        ref  <- ScopedRef.make(service)
        proxy = Proxy.generate(ref)
        res  <- proxy.bar
      } yield assertTrue(res == "baz")
    },
    test("forwards generic methods") {
      trait Foo { def bar[A](a: A): UIO[A] }

      val service: Foo = new Foo { def bar[A](a: A) = ZIO.succeed(a) }
      for {
        ref  <- ScopedRef.make(service)
        proxy = Proxy.generate(ref)
        res  <- proxy.bar[String]("zio")
      } yield assertTrue(res == "zio")
    },
    test("forwards curried methods") {
      trait Foo { def bar(a: Int)(b: String): UIO[String] }

      val service: Foo = new Foo { def bar(a: Int)(b: String) = ZIO.succeed(b * a) }
      for {
        ref  <- ScopedRef.make(service)
        proxy = Proxy.generate(ref)
        res  <- proxy.bar(3)("zio")
      } yield assertTrue(res == "zioziozio")
    },
    test("forwards implicit clauses") {
      trait Foo { def bar(a: Int)(implicit b: String): UIO[String] }

      val service: Foo = new Foo { def bar(a: Int)(implicit b: String) = ZIO.succeed(b * a) }
      implicit val b: String = "zio"
      for {
        ref  <- ScopedRef.make(service)
        proxy = Proxy.generate(ref)
        res  <- proxy.bar(3)
      } yield assertTrue(res == "zioziozio")
    },
    test("keeps default implementations") {
      trait Foo {
        def bar: UIO[String]
        def qux: String = "quux"
      }

      val service: Foo = new Foo { def bar = ZIO.succeed("baz") }
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
      } yield assertTrue(res.isLeft)

    }
  )

}
