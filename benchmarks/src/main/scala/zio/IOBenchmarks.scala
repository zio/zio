package zio

import scala.concurrent.ExecutionContext

import cats._
import cats.effect.{ ContextShift, Fiber => CFiber, IO => CIO }
import monix.eval.{ Task => MTask }

import zio.internal._

object IOBenchmarks extends BootstrapRuntime {

  override val platform: Platform = Platform.benchmark

  val TracedRuntime: BootstrapRuntime = new BootstrapRuntime {
    override val platform = Platform.benchmark.withTracing(Tracing.enabled)
  }

  import monix.execution.Scheduler
  implicit val contextShift: ContextShift[CIO] = CIO.contextShift(ExecutionContext.global)

  implicit val monixScheduler: Scheduler = {
    import monix.execution.ExecutionModel.SynchronousExecution
    Scheduler.global.withExecutionModel(SynchronousExecution)
  }

  def repeat[R, E, A](n: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    if (n <= 1) zio
    else zio *> repeat(n - 1)(zio)

  def verify(cond: Boolean)(message: => String): IO[AssertionError, Unit] =
    ZIO.when(!cond)(IO.fail(new AssertionError(message)))

  def catsForkAll[A](as: Iterable[CIO[A]]): CIO[CFiber[CIO, List[A]]] = {
    type Fiber[A] = CFiber[CIO, A]

    as.foldRight[CIO[CFiber[CIO, List[A]]]](CIO(Applicative[Fiber].pure(Nil))) { (io, listFiber) =>
      Applicative[CIO].map2(listFiber, io.start)((f1, f2) => Applicative[Fiber].map2(f1, f2)((as, a) => a :: as))
    }
  }

  def catsRepeat[A](n: Int)(io: CIO[A]): CIO[A] =
    if (n <= 1) io
    else io.flatMap(_ => catsRepeat(n - 1)(io))

  def monixForkAll[A](as: Iterable[MTask[A]]): MTask[CFiber[MTask, List[A]]] = {
    type Fiber[A] = CFiber[MTask, A]

    as.foldRight[MTask[CFiber[MTask, List[A]]]](MTask(Applicative[Fiber].pure(Nil))) { (io, listFiber) =>
      MTask.map2(listFiber, io.start)((f1, f2) => Applicative[Fiber].map2(f1, f2)((as, a) => a :: as))
    }
  }

  def monixRepeat[A](n: Int)(mio: MTask[A]): MTask[A] =
    if (n <= 1) mio
    else mio.flatMap(_ => monixRepeat(n - 1)(mio))

  class Thunk[A](val unsafeRun: () => A) {
    def map[B](ab: A => B): Thunk[B] =
      new Thunk(() => ab(unsafeRun()))
    def flatMap[B](afb: A => Thunk[B]): Thunk[B] =
      new Thunk(() => afb(unsafeRun()).unsafeRun())
    def attempt: Thunk[Either[Throwable, A]] =
      new Thunk(() =>
        try Right(unsafeRun())
        catch {
          case t: Throwable => Left(t)
        }
      )
  }
  object Thunk {
    def apply[A](a: => A): Thunk[A] = new Thunk(() => a)

    def fail[A](t: Throwable): Thunk[A] = new Thunk(() => throw t)
  }
}
