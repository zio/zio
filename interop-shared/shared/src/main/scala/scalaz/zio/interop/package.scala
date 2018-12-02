package scalaz.zio

package object interop {

  type Task[+A]      = IO[Throwable, A]
  type ParIO[+E, +A] = Par.T[E, A]

  implicit final class AutoCloseableOps(private val a: AutoCloseable) extends AnyVal {

    /**
     * Returns an `IO` action which closes this `AutoCloseable` resource.
     */
    def closeIO(): IO[Nothing, Unit] = IO.sync(a.close())
  }

  implicit final class IOAutocloseableOps[E, A <: AutoCloseable](private val io: IO[E, A]) extends AnyVal {

    /**
     * Like `bracket`, safely wraps a use and release of a resource.
     * This resource will get automatically closed, because it implements `AutoCloseable`.
     */
    def bracketAuto[E1 >: E, B](use: A => IO[E1, B]): IO[E1, B] =
      io.bracket[E1, B](_.closeIO())(use)
  }
}
