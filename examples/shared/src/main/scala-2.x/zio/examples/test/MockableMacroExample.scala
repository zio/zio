package zio.examples.test

import zio.test.mock.mockable
import zio.{Tagged, UIO}
import zio.{IO, Task, Tagged, UIO, URIO, ZIO}

object DiffrentScopeExample {

  trait Foo { val value: String }
  case class Bar(value: String) extends Foo
  case class Wrapped[T](value: T)

  trait Service {
    def get(key: String): ZIO[Any, Nothing, Int]
    def set(key: String, value: Int): ZIO[Any, Nothing, Unit]
    def reset: UIO[Unit]
    def io: IO[String, Long]
    def task: Task[Long]
    def uio: UIO[Long]
    def urio: URIO[String, Long]
    def poly1[A: Tagged](a: A): UIO[Unit]
    def poly2[A: Tagged]: IO[A, Unit]
    def poly3[A: Tagged]: UIO[A]
    def poly4[A: Tagged, B: Tagged](a: A): IO[B, Unit]
    def poly5[A: Tagged, B: Tagged](a: A): IO[Unit, B]
    def poly6[A: Tagged, B: Tagged]: IO[A, B]
    def poly7[A: Tagged, B: Tagged, C: Tagged](a: A): IO[B, C]
    def poly8[A: Tagged]: UIO[(A, String)]
    def poly9[A <: Foo: Tagged]: UIO[A]
    def poly10[A: Tagged](a: Wrapped[A]): UIO[A]
  }
}

@mockable[DiffrentScopeExample.Service]
object MockableMacroExample
