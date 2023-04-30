package zio

import zio.test._
import com.github.ghik.silencer.silent

@scala.annotation.experimental
object ProxySpec extends ZIOSpecDefault {

  val spec = suite("Proxy")(
    suite("generates a proxy")(
      test("switches underlying service in runtime") {
        trait Foo { def bar: UIO[String] }

        val service1: Foo = new Foo { def bar = ZIO.succeed("zio1") }
        val service2: Foo = new Foo { def bar = ZIO.succeed("zio2") }
        for {
          ref  <- ScopedRef.make(service1)
          proxy = Proxy.generate(ref)
          res1 <- proxy.bar
          _    <- ref.set(ZIO.succeed(service2))
          res2 <- proxy.bar
        } yield assertTrue(res1 == "zio1" && res2 == "zio2")
      },
      test("the type out of the current lexical scope") {
        object foo {
          trait Foo { def bar: UIO[String] }
        }
        val service: foo.Foo = new foo.Foo { def bar = ZIO.succeed("zio") }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res1 <- proxy.bar
        } yield assertTrue(res1 == "zio")
      },
      test("multiple methods") {
        trait Foo {
          def bar: UIO[String]
          def baz: UIO[Int]
        }
        val service: Foo = new Foo {
          override def bar: UIO[String] = ZIO.succeed("zio")
          override def baz: UIO[Int]    = ZIO.succeed(1)
        }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res1 <- proxy.bar
          res2 <- proxy.baz
        } yield assertTrue(res1 == "zio" && res2 == 1)
      },
      test("trait with type parameter") {
        trait Foo[A] { def bar: UIO[A] }
        val service: Foo[String] = new Foo[String] { def bar = ZIO.succeed("baz") }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res  <- proxy.bar
        } yield assertTrue(res == "baz")
      },
      test("abstract class") {
        abstract class Foo { def bar: UIO[String] }
        val service: Foo = new Foo { def bar = ZIO.succeed("zio") }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res  <- proxy.bar
        } yield assertTrue(res == "zio")
      },
      test("class") {
        class Foo { def bar: UIO[String] = ZIO.succeed("zio1") }
        val service: Foo = new Foo {
          override def bar = ZIO.succeed("zio2")
        }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res  <- proxy.bar
        } yield assertTrue(res == "zio2")
      }
    ),
    suite("forwards")(
      test("methods with params") {
        trait Foo { def bar(a: String): UIO[Int] }
        val service: Foo = new Foo { def bar(a: String) = ZIO.succeed(a.length) }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res  <- proxy.bar("zio")
        } yield assertTrue(res == 3)
      },
      test("generic methods") {
        trait Foo { def bar[A](a: A): UIO[A] }
        val service: Foo = new Foo { def bar[A](a: A) = ZIO.succeed(a) }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res  <- proxy.bar[String]("zio")
        } yield assertTrue(res == "zio")
      },
      test("curried methods") {
        trait Foo { def bar(a: Int)(b: String): UIO[String] }
        val service: Foo = new Foo { def bar(a: Int)(b: String) = ZIO.succeed(b * a) }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res  <- proxy.bar(3)("zio")
        } yield assertTrue(res == "zioziozio")
      },
      test("implicit clauses") {
        trait Foo { def bar(a: Int)(implicit b: String): UIO[String] }
        val service: Foo       = new Foo { def bar(a: Int)(implicit b: String) = ZIO.succeed(b * a) }
        implicit val b: String = "zio"
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res  <- proxy.bar(3)
        } yield assertTrue(res == "zioziozio")
      },
      test("inherited abstract methods") {
        trait Foo0 { def bar(a: Int): UIO[String] }
        trait Foo extends Foo0

        val service: Foo = new Foo { def bar(a: Int) = ZIO.succeed("zio" * a) }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res  <- proxy.bar(3)
        } yield assertTrue(res == "zioziozio")
      },
      test("overridden methods with default implementation") {
        trait Foo { def bar: UIO[String] = ZIO.succeed("zio1") }
        val service: Foo = new Foo { override def bar = ZIO.succeed("zio2") }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res  <- proxy.bar
        } yield assertTrue(res == "zio2")
      },
      test("package private methods") {
        trait Foo { private[zio] def bar: UIO[String] }
        val service: Foo = new Foo { private[zio] def bar: UIO[String] = ZIO.succeed("zio") }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res  <- proxy.bar
        } yield assertTrue(res == "zio")
      },
      test("ZIO vals") {
        trait Foo { val bar: UIO[String] }
        val service1: Foo = new Foo { val bar: UIO[String] = ZIO.succeed("zio1") }
        val service2: Foo = new Foo { val bar: UIO[String] = ZIO.succeed("zio2") }
        for {
          ref  <- ScopedRef.make(service1)
          proxy = Proxy.generate(ref)
          res1 <- proxy.bar
          _    <- ref.set(ZIO.succeed(service2))
          res2 <- proxy.bar
        } yield assertTrue(res1 == "zio1" && res2 == "zio2")
      },
      test("ZIO vals with default implementation") {
        trait Foo { val bar: UIO[String] = ZIO.succeed("zio") }
        val service: Foo = new Foo { override val bar: UIO[String] = ZIO.succeed("zio1") }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
          res1 <- proxy.bar
        } yield assertTrue(res1 == "zio1")
      },
      test("keeps non-ZIO default implementations") {
        trait Foo {
          def bar: UIO[String]
          def qux: String = "quux"
        }
        val service: Foo = new Foo { def bar = ZIO.succeed("baz") }
        for {
          ref  <- ScopedRef.make(service)
          proxy = Proxy.generate(ref)
        } yield assertTrue(proxy.qux == "quux")
      }
    ),
    suite("fails to compile")(
      test("non-ZIO abstract method") {
        for {
          res <- typeCheck(
                   """
        trait Foo { def qux: String }
        val service: Foo = new Foo { def qux = "quux" }
        for {
          ref <- ScopedRef.make(service)
        } yield Proxy.generate(ref)
        """
                 )
        } yield
          if (TestVersion.isScala2)
            assertTrue(res.swap.exists(_.contains("non-ZIO")))
          else
            assertTrue(res.isLeft)
      },
      test("classes/traits requiring constructor parameter") {
        for {
          res <- typeCheck(
                   """
          class Foo(s: String) { def bar: UIO[String] = ZIO.succeed(s) }
          val service: Foo = new Foo("zio")
          for {
            ref  <- ScopedRef.make(service)
            proxy = Proxy.generate(ref)
            res  <- proxy.bar
          } yield Proxy.generate(ref)
          """
                 )
        } yield
          if (TestVersion.isScala2)
            assertTrue(res.swap.exists(_.contains("requiring constructor")))
          else
            assertTrue(res.isLeft)
      },
      test("abstract type members") {
        @silent("never used")
        implicit val debug: Boolean = true
        for {
          res <- typeCheck(
                   """
            trait Foo {
              type T
              def bar: UIO[T]
            }

            val service: Foo = new Foo {
              type T = String
              def bar: UIO[T] = ZIO.succeed("zio")
            }
            for {
              ref  <- ScopedRef.make(service)
              proxy = Proxy.generate(ref)
              res  <- proxy.bar
            } yield assertTrue(res == "zio")
            """
                 )
        } yield
          if (TestVersion.isScala2)
            assertTrue(res.swap.exists(_.contains("Abstract type members")))
          else
            assertTrue(res.isLeft)
      }
    )
  )
}
