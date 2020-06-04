package zio.examples.test

import zio.test.mock.mockable
import zio.{ IO, Tag, Task, UIO, URIO }

object DiffrentScopeExample {

  trait Foo { val value: String }
  case class Bar(value: String) extends Foo
  case class Wrapped[T](value: T)

  trait Service {
    def get(key: String): UIO[Int]
    def set(key: String, value: Int): UIO[Unit]
    def reset: UIO[Unit]
    def io: IO[String, Long]
    def task: Task[Long]
    def uio: UIO[Long]
    def urio: URIO[String, Long]
    def poly1[A: Tag](a: A): UIO[Unit]
    def poly2[A: Tag]: IO[A, Unit]
    def poly3[A: Tag]: UIO[A]
    def poly4[A: Tag, B: Tag](a: A): IO[B, Unit]
    def poly5[A: Tag, B: Tag](a: A): IO[Unit, B]
    def poly6[A: Tag, B: Tag]: IO[A, B]
    def poly7[A: Tag, B: Tag, C: Tag](a: A): IO[B, C]
    def poly8[A: Tag]: UIO[(A, String)]
    def poly9[A <: Foo: Tag]: UIO[A]
    def poly10[A: Tag](a: Wrapped[A]): UIO[A]
  }
}

@mockable[DiffrentScopeExample.Service]
object MockableMacroExample
