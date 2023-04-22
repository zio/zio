package zio

import scala.annotation.experimental
import zio.test._

@experimental
object ProxySpec extends ZIOSpecDefault {

  val spec = suite("Proxy")(
    test("bypass") {
      trait Foo { 
        def bar: UIO[String] 
        def bar2(a: Int): Int
        def curried(b: Int)(c: String): Boolean
        def useImplicit(d: Int)(using e: String): Boolean
        def hasDefaultImpl(f: Int): Boolean = true
        def hasTypeParam[A](f: A): A
      }

      val service: Foo = new Foo { 
        def bar = ZIO.succeed("baz") 
        def bar2(x: Int): Int = 5
        def curried(x: Int)(y: String): Boolean = true
        def useImplicit(x: Int)(using y: String): Boolean = false
        def hasTypeParam[A](f: A): A = f
      }
      val proxy = Proxy.generate[Foo](service)
      for {
        res  <- proxy.bar
      } yield assertTrue(res == "baz")
    }
    // test("generates simple proxy") {
    //   trait Foo { def bar: UIO[String] }

    //   val service: Foo = new Foo { def bar = ZIO.succeed("baz") }
    //   for {
    //     ref  <- ScopedRef.make(service)
    //     proxy = Proxy.generate(ref)
    //     res  <- proxy.bar
    //   } yield assertTrue(res == "baz")
    // },
    // test("keeps default implementations") {
    //   trait Foo {
    //     def bar: UIO[String]

    //     def qux: String = "quux"
    //   }

    //   val service: Foo = new Foo {
    //     def bar = ZIO.succeed("baz")
    //   }
    //   for {
    //     ref  <- ScopedRef.make(service)
    //     proxy = Proxy.generate(ref)
    //   } yield assertTrue(proxy.qux == "quux")
    // },
    // test("fails to compile with non-ZIO abstract method") {

    //   for {
    //     res <- typeCheck(
    //              """
    //     trait Foo {
    //       def bar: UIO[String]
    //       def qux: String
    //     }

    //     val service: Foo = new Foo {
    //       def bar = ZIO.succeed("baz")
    //       def qux = "quux"
    //     }

    //     for {
    //       ref <- ScopedRef.make(service)
    //     } yield Proxy.generate(ref)
    //     """
    //            )
    //     errorMessage <- ZIO.fromEither(res.swap)
    //     _            <- ZIO.logInfo(errorMessage)
    //   } yield assertTrue(errorMessage.contains("non-ZIO method qux"))

    // }
  )

}
