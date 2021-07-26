package zio

import cats._
import cats.effect.{Fiber => CFiber, IO => CIO}
import zio.internal._

import scala.concurrent.ExecutionContext

object BenchmarkUtil extends BootstrapRuntime {

  override val platform: Platform = Platform.benchmark

  val TracedRuntime: BootstrapRuntime = new BootstrapRuntime {
    override val platform = Platform.benchmark.withTracing(Tracing.enabled)
  }

  implicit val futureExecutionContext: ExecutionContext =
    ExecutionContext.global

  def repeat[R, E, A](n: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    if (n <= 1) zio
    else zio *> repeat(n - 1)(zio)

  def verify(cond: Boolean)(message: => String): IO[AssertionError, Unit] =
    ZIO.when(!cond)(IO.fail(new AssertionError(message)))

  def catsForeach[A, B](as: List[A])(f: A => CIO[B]): CIO[List[B]] =
    Traverse[List].traverse(as)(f)

  def catsForkAll[A](as: Iterable[CIO[A]]): CIO[CFiber[CIO, Throwable, List[A]]] = ???

  def catsRepeat[A](n: Int)(io: CIO[A]): CIO[A] =
    if (n <= 1) io
    else io.flatMap(_ => catsRepeat(n - 1)(io))
}
