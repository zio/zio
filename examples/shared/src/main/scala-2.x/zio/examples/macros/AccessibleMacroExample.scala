package zio.examples.macros

import zio.{ Has, UIO, URIO, ZLayer }
import zio.console.Console
import zio.macros.accessible

@accessible
object AccessibleMacroExample {

  type AccessibleMacroExample = Has[AccessibleMacroExample.Service]

  trait Service {

    val foo: UIO[Unit]
    def bar(n: Int): UIO[Unit]
    def baz(x: Int, y: Int): UIO[Int]
    def poly[A](a: A): UIO[A]
  }

  val live: ZLayer[Console, Nothing, Has[Service]] =
    ZLayer.fromService(console => new Service {
      val foo: UIO[Unit]                = console.putStrLn("foo")
      def bar(n: Int): UIO[Unit]        = console.putStrLn(s"bar $n")
      def baz(x: Int, y: Int): UIO[Int] = UIO.succeed(x + y)
      def poly[A](a: A): UIO[A]         = UIO.succeed(a)
    })

  // can use accessors even in the same compilation unit
  val program: URIO[AccessibleMacroExample, (Int, String, Long)] =
    for {
      _  <- AccessibleMacroExample.foo
      _  <- AccessibleMacroExample.bar(1)
      v1 <- AccessibleMacroExample.baz(2, 3)
      v2 <- AccessibleMacroExample.poly("foo")
      v3 <- AccessibleMacroExample.poly(4L)
    } yield (v1, v2, v3)

  // macro autogenerates accessors for `foo`, `bar`, `baz` and `poly` below
}
