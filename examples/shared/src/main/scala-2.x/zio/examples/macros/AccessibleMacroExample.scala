package zio.examples.macros

import zio.{ Has, UIO, URIO, ZLayer }
import zio.console.Console
import zio.macros.Accessible

@Accessible
object AccessibleMacroExample {

  type AccessibleMacroExample = Has[AccessibleMacroExample.Service]

  trait Service {

    val foo: UIO[Unit]
    def bar(n: Int): UIO[Unit]
    def baz(x: Int, y: Int): UIO[Int]
  }

  val live: ZLayer[Console, Nothing, Has[Service]] =
    ZLayer.fromService(console => new Service {
      val foo: UIO[Unit]                = console.putStrLn("foo")
      def bar(n: Int): UIO[Unit]        = console.putStrLn(s"bar $n")
      def baz(x: Int, y: Int): UIO[Int] = UIO.succeed(x + y)
    })

  // can use accessors even in the same compilation unit
  val program: URIO[AccessibleMacroExample, Int] =
    for {
      _ <- AccessibleMacroExample.foo
      _ <- AccessibleMacroExample.bar(1)
      v <- AccessibleMacroExample.baz(2, 3)
    } yield v

  // macro autogenerates accessors for `foo`, `bar` and `baz` below
}
