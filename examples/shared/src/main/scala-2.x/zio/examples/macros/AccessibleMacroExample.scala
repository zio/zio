package zio.examples.macros

import zio.{Chunk, Has, IO, UIO, URIO, ZIO, ZLayer, random}
import zio.console.Console
import zio.stream.{ZSink, ZStream}
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
    val value: String
    def function(n: Int): String
    def stream(n: Int): ZStream[Any, String, Int]
    def sink(n: Int): ZSink[Any, Nothing, Int, Chunk[Int]]
  }

  val live: ZLayer[Console, Nothing, Has[Service]] =
    ZLayer.fromService(console => new Service {
      val foo: UIO[Unit]                                             = UIO.unit
      def bar(n: Int): UIO[Unit]                                     = console.putStrLn(s"bar $n")
      def baz(x: Int, y: Int): IO[String, Int]                       = UIO.succeed(x + y)
      def poly[A](a: A): IO[Long, A]                                 = UIO.succeed(a)
      def poly2[A <: Foo](a: Wrapped[A]): IO[String, List[A]]        = UIO.succeed(List(a.value))
      def dependent(n: Int): ZIO[Random, Long, Int]                  = random.nextIntBounded(n)
      val value: String                                              = "foo"
      def function(n: Int): String                                   = s"foo $n"
      def stream(n: Int): ZStream[Any, String, Int]                  = ZStream.fromIterable(List(1, 2, 3))
      def sink(n: Int): ZSink[Any, Nothing, Int, Chunk[Int]] = ZSink.collectAll
    })

  // can use accessors even in the same compilation unit
  val program: URIO[AccessibleMacroExample with Random, (Int, String, Long, List[Foo], Int, String, String, ZStream[Any, String, Int], ZSink[Any, Nothing, Int, Chunk[Int]])] =
    for {
      _  <- AccessibleMacroExample.foo
      _  <- AccessibleMacroExample.bar(1)
      v1 <- AccessibleMacroExample.baz(2, 3).orDieWith(_ => new Exception)
      v2 <- AccessibleMacroExample.poly("foo").orDieWith(_ => new Exception)
      v3 <- AccessibleMacroExample.poly(4L).orDieWith(_ => new Exception)
      v4 <- AccessibleMacroExample.poly2(Wrapped(Bar("bar"))).orDieWith(_ => new Exception)
      v5 <- AccessibleMacroExample.dependent(5).orDieWith(_ => new Exception)
      v6 <- AccessibleMacroExample.value.orDie
      v7 <- AccessibleMacroExample.function(6).orDie
      v8 <- AccessibleMacroExample.stream(7)
      v9 <- AccessibleMacroExample.sink(8)
    } yield (v1, v2, v3, v4, v5, v6, v7, v8, v9)

  // sanity check
  val _foo                            : URIO[AccessibleMacroExample, Unit]                                         = AccessibleMacroExample.foo
  def _bar(n: Int)                    : URIO[AccessibleMacroExample, Unit]                                         = AccessibleMacroExample.bar(n)
  def _baz(x: Int, y: Int)            : ZIO[AccessibleMacroExample, String, Int]                                           = AccessibleMacroExample.baz(x, y)
  def _poly[A](a: A)                  : ZIO[AccessibleMacroExample, Long, A]                                               = AccessibleMacroExample.poly(a)
  def _poly2[A <: Foo](a: Wrapped[A]) : ZIO[AccessibleMacroExample, String, List[A]]                                       = AccessibleMacroExample.poly2(a)
  def _dependent(n: Int)              : ZIO[AccessibleMacroExample with Random, Long, Int]                                 = AccessibleMacroExample.dependent(n)
  def _value                          : RIO[AccessibleMacroExample, String]                                     = AccessibleMacroExample.value
  def _function(n: Int)               : RIO[AccessibleMacroExample, String]                                     = AccessibleMacroExample.function(n)
  def _stream(n: Int)                 : ZIO[AccessibleMacroExample, Nothing, ZStream[Any, String, Int]]                    = AccessibleMacroExample.stream(n)
  def _sink(n: Int)                   : ZIO[AccessibleMacroExample, Nothing, ZSink[Any, Nothing, Int, Chunk[Int]]] = AccessibleMacroExample.sink(n)

  // macro autogenerates accessors for `foo`, `bar`, `baz`, `poly`, `poly2`, `value` and `function` below
}
