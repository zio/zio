package zio.examples.macros

import zio.{ random, Has, IO, UIO, URIO, ZIO, ZLayer }
import zio.console.Console
import zio.random.Random
import zio.macros.accessible

@accessible
object AccessibleMacroExample {

  type AccessibleMacroExample = Has[AccessibleMacroExample.Service]

  trait Foo { val value: String }
  case class Bar(value: String) extends Foo
  case class Wrapped[T](value: T)

  trait Service {

    val foo: UIO[Unit]
    def bar(n: Int): UIO[Unit]
    def baz(x: Int, y: Int): IO[String, Int]
    def poly[A](a: A): IO[Long, A]
    def poly2[A <: Foo](a: Wrapped[A]): IO[String, List[A]]
    def dependent(n: Int): ZIO[Random, Long, Int]
  }

  val live: ZLayer[Console, Nothing, Has[Service]] =
    ZLayer.fromService(console => new Service {
      val foo: UIO[Unit]                                      = UIO.unit
      def bar(n: Int): UIO[Unit]                              = console.putStrLn(s"bar $n")
      def baz(x: Int, y: Int): IO[String, Int]                = UIO.succeed(x + y)
      def poly[A](a: A): IO[Long, A]                          = UIO.succeed(a)
      def poly2[A <: Foo](a: Wrapped[A]): IO[String, List[A]] = UIO.succeed(List(a.value))
      def dependent(n: Int): ZIO[Random, Long, Int]           = random.nextInt(n)
    })

  // can use accessors even in the same compilation unit
  val program: URIO[AccessibleMacroExample with Random, (Int, String, Long, List[Foo], Int)] =
    for {
      _  <- AccessibleMacroExample.foo
      _  <- AccessibleMacroExample.bar(1)
      v1 <- AccessibleMacroExample.baz(2, 3).orDieWith(_ => new Exception)
      v2 <- AccessibleMacroExample.poly("foo").orDieWith(_ => new Exception)
      v3 <- AccessibleMacroExample.poly(4L).orDieWith(_ => new Exception)
      v4 <- AccessibleMacroExample.poly2(Wrapped(Bar("bar"))).orDieWith(_ => new Exception)
      v5 <- AccessibleMacroExample.dependent(5).orDieWith(_ => new Exception)
    } yield (v1, v2, v3, v4, v5)

  // sanity check
  val _foo                            : ZIO[AccessibleMacroExample, Nothing, Unit]         = AccessibleMacroExample.foo
  def _bar(n: Int)                    : ZIO[AccessibleMacroExample, Nothing, Unit]         = AccessibleMacroExample.bar(n)
  def _baz(x: Int, y: Int)            : ZIO[AccessibleMacroExample, String, Int]           = AccessibleMacroExample.baz(x, y)
  def _poly[A](a: A)                  : ZIO[AccessibleMacroExample, Long, A]               = AccessibleMacroExample.poly(a)
  def _poly2[A <: Foo](a: Wrapped[A]) : ZIO[AccessibleMacroExample, String, List[A]]       = AccessibleMacroExample.poly2(a)
  def _dependent(n: Int)              : ZIO[AccessibleMacroExample with Random, Long, Int] = AccessibleMacroExample.dependent(n)

  // macro autogenerates accessors for `foo`, `bar`, `baz`, `poly` and `poly2` below
}
