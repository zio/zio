package scalaz.zio

sealed abstract class Managed[+E, +R] { self =>
  def use[E1 >: E, A](f: R => IO[E1, A]): IO[E1, A]

  final def use_[E1 >: E, A](f: IO[E1, A]): IO[E1, A] =
    use(_ => f)

  final def map[R1](f0: R => R1): Managed[E, R1] =
    new Managed[E, R1] {
      def use[E1 >: E, A](f: R1 => IO[E1, A]): IO[E1, A] =
        self.use(r => f(f0(r)))
    }

  final def flatMap[E1 >: E, R1](f0: R => Managed[E1, R1]): Managed[E1, R1] =
    new Managed[E1, R1] {
      def use[E2 >: E1, A](f: R1 => IO[E2, A]): IO[E2, A] =
        self.use { r =>
          f0(r).use { r1 =>
            f(r1)
          }
        }
    }

  final def *>[E1 >: E, R1](ff: Managed[E1, R1]): Managed[E1, R1] =
    flatMap(_ => ff)

  final def <*[E1 >: E, R1](ff: Managed[E1, R1]): Managed[E1, R] =
    flatMap(r => ff.map(_ => r))

  final def seqWith[E1 >: E, R1, R2](ff: Managed[E1, R1])(f: (R, R1) => R2): Managed[E1, R2] =
    flatMap(r => ff.map(r1 => f(r, r1)))

  final def seq[E1 >: E, R1](ff: Managed[E1, R1]): Managed[E1, (R, R1)] =
    seqWith(ff)((_, _))

  final def parWith[E1 >: E, R1, R2](that: Managed[E1, R1])(f0: (R, R1) => R2): Managed[E1, R2] =
    new Managed[E1, R2] {
      override def use[E2 >: E1, A](f: R2 => IO[E2, A]): IO[E2, A] = {
        val x = self.use(IO.now)
        val y = that.use(IO.now)

        x.parWith(y)(f0).flatMap(f)
      }
    }

  final def par[E1 >: E, R1](that: Managed[E1, R1]): Managed[E1, (R, R1)] =
    self.parWith(that)((a, b) => (a, b))
}

object Managed {

  /**
   * Lifts an IO[E, R] into Managed[E, R] with a release action.
   */
  def apply[E, R](acquire: IO[E, R])(release: R => IO[Nothing, Unit]): Managed[E, R] =
    new Managed[E, R] {
      def use[E1 >: E, A](f: R => IO[E1, A]): IO[E1, A] =
        acquire.bracket[E1, A](release)(f)
    }

  /**
   * Lifts an IO[E, R] into Managed[E, R] with no release action. Use
   * with care.
   */
  def liftIO[E, R](fa: IO[E, R]): Managed[E, R] =
    new Managed[E, R] {
      def use[E1 >: E, A](f: R => IO[E1, A]): IO[E1, A] =
        fa.flatMap(f)
    }

  /**
   * Lifts a strict, pure value into a Managed.
   */
  def now[R](r: R): Managed[Nothing, R] =
    new Managed[Nothing, R] {
      def use[E, A](f: R => IO[E, A]): IO[E, A] = f(r)
    }

  /**
   * Lifts a by-name, pure value into a Managed.
   */
  def point[R](r: => R): Managed[Nothing, R] =
    new Managed[Nothing, R] {
      def use[E, A](f: R => IO[E, A]): IO[E, A] = f(r)
    }

  def traverse[E, R, A](as: Iterable[A])(f: A => Managed[E, R]): Managed[E, List[R]] =
    as.foldRight[Managed[E, List[R]]](now(Nil)) { (a, m) =>
      f(a).seqWith(m)(_ :: _)
    }

  def sequence[E, R, A](ms: Iterable[Managed[E, R]]): Managed[E, List[R]] =
    traverse(ms)(identity)
}
