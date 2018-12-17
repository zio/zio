package scalaz.zio.syntax
import scalaz.zio.Exit.Cause
import scalaz.zio.{ Fiber, IO }

object IOSyntax {
  final class IOCreationLazySyntax[A](val a: () => A) extends AnyVal {
    def succeedLazy: IO[Nothing, A]                             = IO.succeedLazy(a())
    def sync: IO[Nothing, A]                                    = IO.sync(a())
    def syncException: IO[Exception, A]                         = IO.syncException(a())
    def syncThrowable: IO[Throwable, A]                         = IO.syncThrowable(a())
    def syncCatch[E]: PartialFunction[Throwable, E] => IO[E, A] = IO.syncCatch(a())
  }

  final class IOCreationEagerSyntax[A](val a: A) extends AnyVal {
    def succeed: IO[Nothing, A]                     = IO.succeed(a)
    def fail: IO[A, Nothing]                        = IO.fail(a)
    def require[AA]: IO[A, Option[AA]] => IO[A, AA] = IO.require(a)
  }

  final class IOFlattenSyntax[E, A](val io: IO[E, IO[E, A]]) extends AnyVal {
    def flatten: IO[E, A] = IO.flatten(io)
  }

  final class IOAbsolveSyntax[E, A](val io: IO[E, Either[E, A]]) extends AnyVal {
    def absolve: IO[E, A] = IO.absolve(io)
  }

  final class IOUnsandboxSyntax[E, A](val io: IO[Cause[E], A]) extends AnyVal {
    def unsandbox: IO[E, A] = IO.unsandbox(io)
  }

  final class IOUnitSyntax[E](val io: IO[E, Unit]) extends AnyVal {
    def when(pred: Boolean): IO[E, Unit]               = IO.when(pred)(io)
    def whenM(pred: IO[Nothing, Boolean]): IO[E, Unit] = IO.whenM(pred)(io)
  }

  final class IOIterableSyntax[E, A](val ios: Iterable[IO[E, A]]) extends AnyVal {
    def mergeAll[B](zero: B, f: (B, A) => B): IO[E, B] = IO.mergeAll(ios)(zero, f)
    def collectAllPar: IO[E, List[A]]                  = IO.collectAllPar(ios)
    def forkAll: IO[Nothing, Fiber[E, List[A]]]        = IO.forkAll(ios)
    def collectAll: IO[E, List[A]]                     = IO.collectAll(ios)
  }

  final class IOGetSyntax[A](val io: IO[Nothing, Option[A]]) extends AnyVal {
    def get: IO[Unit, A] = io.map(_.toRight(())).absolve
  }

  final class IOOptionSyntax[A](val io: IO[Unit, A]) extends AnyVal {
    def option: IO[Nothing, Option[A]] = io.attempt.map {
      case Right(value) => Some(value)
      case _            => None
    }
  }

  final class IOSyntax[E, A](val io: IO[E, A]) extends AnyVal {
    def raceAll(ios: Iterable[IO[E, A]]): IO[E, A] = IO.raceAll(io, ios)
    def supervise: IO[E, A]                        = IO.supervise(io)
  }

  final class IOTuple2[E, A, B](val ios2: (IO[E, A], IO[E, B])) extends AnyVal {
    def map2[C](f: (A, B) => C): IO[E, C] = ios2._1.flatMap(a => ios2._2.map(f(a, _)))
  }

  final class IOTuple3[E, A, B, C](val ios3: (IO[E, A], IO[E, B], IO[E, C])) extends AnyVal {
    def map3[D](f: (A, B, C) => D): IO[E, D] =
      for {
        a <- ios3._1
        b <- ios3._2
        c <- ios3._3
      } yield f(a, b, c)
  }

  final class IOTuple4[E, A, B, C, D](val ios4: (IO[E, A], IO[E, B], IO[E, C], IO[E, D])) extends AnyVal {
    def map4[F](f: (A, B, C, D) => F): IO[E, F] =
      for {
        a <- ios4._1
        b <- ios4._2
        c <- ios4._3
        d <- ios4._4
      } yield f(a, b, c, d)
  }
}
