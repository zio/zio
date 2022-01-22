package zio.examples.test

import zio.mock.mockable
import zio.{IO, EnvironmentTag, Task, UIO, URIO}

object DifferentScopeExample {

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
    def poly1[A: EnvironmentTag](a: A): UIO[Unit]
    def poly2[A: EnvironmentTag]: IO[A, Unit]
    def poly3[A: EnvironmentTag]: UIO[A]
    def poly4[A: EnvironmentTag, B: EnvironmentTag](a: A): IO[B, Unit]
    def poly5[A: EnvironmentTag, B: EnvironmentTag](a: A): IO[Unit, B]
    def poly6[A: EnvironmentTag, B: EnvironmentTag]: IO[A, B]
    def poly7[A: EnvironmentTag, B: EnvironmentTag, C: EnvironmentTag](a: A): IO[B, C]
    def poly8[A: EnvironmentTag]: UIO[(A, String)]
    def poly9[A <: Foo: EnvironmentTag]: UIO[A]
    def poly10[A: EnvironmentTag](a: Wrapped[A]): UIO[A]
  }
}

@mockable[DifferentScopeExample.Service]
object MockableMacroExample
