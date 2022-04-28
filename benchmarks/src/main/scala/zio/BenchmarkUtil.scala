package zio

import cats._
import cats.effect.{Fiber => CFiber, IO => CIO}

import scala.concurrent.ExecutionContext

object BenchmarkUtil extends Runtime[Any] {
  val environment   = Runtime.default.environment
  val runtimeConfig = RuntimeConfig.benchmark

  implicit val futureExecutionContext: ExecutionContext =
    ExecutionContext.global

  def repeat[R, E, A](n: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    if (n <= 1) zio
    else zio *> repeat(n - 1)(zio)

  def verify(cond: Boolean)(message: => String): IO[AssertionError, Unit] =
    ZIO.when(!cond)(ZIO.fail(new AssertionError(message))).unit

  def catsForeach[A, B](as: List[A])(f: A => CIO[B]): CIO[List[B]] =
    Traverse[List].traverse(as)(f)

  def catsForkAll[A](as: Iterable[CIO[A]]): CIO[CFiber[CIO, Throwable, List[A]]] = ???

  def catsRepeat[A](n: Int)(io: CIO[A]): CIO[A] =
    if (n <= 1) io
    else io.flatMap(_ => catsRepeat(n - 1)(io))
}
