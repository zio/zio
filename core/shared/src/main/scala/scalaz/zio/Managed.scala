package scalaz.zio

/**
 * A `Managed[E, R]` is a managed resource of type `R`, which may be used by
 * invoking the `use` method of the resource. The resource will be automatically
 * acquired before the resource is used, and automatically released after the
 * resource is used.
 *
 * Resources do not survive the scope of `use`, meaning that if you attempt to
 * capture the resource, leak it from `use`, and then use it after the resource
 * has been consumed, the resource will not be valid anymore and may fail with
 * some checked error, as per the type of the functions provided by the resource.
 */
sealed abstract class Managed[+E, +R] extends Serializable { self =>
  type R0 <: R

  def acquire: IO[E, R0]

  def release: R0 => IO[Nothing, Unit]

  def use[E1 >: E, A](f: R => IO[E1, A]): IO[E1, A] =
    acquire.bracket[E1, A](release)(f)

  final def use_[E1 >: E, A](f: IO[E1, A]): IO[E1, A] =
    use(_ => f)

  final def map[R1](f0: R => R1): Managed[E, R1] =
    new Managed[E, R1] {
      type R0 = R1

      def acquire: IO[E, R1] = IO.never

      def release: R1 => IO[Nothing, Unit] = _ => IO.unit

      override def use[E1 >: E, A](f: R1 => IO[E1, A]): IO[E1, A] =
        self.use(r => f(f0(r)))
    }

  final def flatMap[E1 >: E, R1](f0: R => Managed[E1, R1]): Managed[E1, R1] =
    new Managed[E1, R1] {
      type R0 = R1
      def acquire: IO[E, R1] = IO.never
      def release: R1 => IO[Nothing, Unit] = _ => IO.unit

      override def use[E2 >: E1, A](f: R1 => IO[E2, A]): IO[E2, A] =
        self.use { r =>
          f0(r).use { r1 =>
            f(r1)
          }
        }
    }

  final def *>[E1 >: E, R1](that: Managed[E1, R1]): Managed[E1, R1] =
    flatMap(_ => that)

  final def <*[E1 >: E, R1](that: Managed[E1, R1]): Managed[E1, R] =
    flatMap(r => that.map(_ => r))

  final def seqWith[E1 >: E, R1, R2](that: Managed[E1, R1])(f: (R, R1) => R2): Managed[E1, R2] =
    flatMap(r => that.map(r1 => f(r, r1)))

  final def seq[E1 >: E, R1](that: Managed[E1, R1]): Managed[E1, (R, R1)] =
    seqWith(that)((_, _))

  final def parWith[E1 >: E, R1, R2](that: Managed[E1, R1])(f0: (R0, that.R0) => R2): Managed[E1, R2] = {
    val acquireBoth = acquire.par(that.acquire)

    def releaseBoth(pair: (R0, that.R0)): IO[Nothing, Unit] =
      release(pair._1).par(that.release(pair._2)) *> IO.unit

    new Managed[E1, R2] {
      type R0 = R2

      def acquire: IO[E1, R2] = IO.never

      def release: R2 => IO[Nothing, Unit] = _ => IO.unit

      override def use[E2 >: E1, A](f: R2 => IO[E2, A]): IO[E2, A] =
        acquireBoth.bracket[E2, A](releaseBoth) {
          case (r, r1) => f(f0(r, r1))
        }
    }
  }

  final def par[E1 >: E, R1](that: Managed[E1, R1]): Managed[E1, (R0, that.R0)] =
    parWith(that)((_, _))
}

object Managed {

  /**
   * Lifts an `IO[E, R]`` into `Managed[E, R]`` with a release action.
   */
  final def apply[E, R](a: IO[E, R])(r: R => IO[Nothing, Unit]): Managed[E, R] =
    new Managed[E, R] {
      type R0 = R

      def acquire: IO[E, R] = a

      def release: R => IO[Nothing, Unit] = r
    }

  /**
   * Lifts an IO[E, R] into Managed[E, R] with no-op release. Use with care.
   */
  final def liftIO[E, R](fa: IO[E, R]): Managed[E, R] =
    new Managed[E, R] {
      type R0 = R

      def acquire: IO[E, R] = fa

      def release: R => IO[Nothing, Unit] = _ => IO.unit
    }

  /**
   * Lifts a strict, pure value into a Managed.
   */
  final def now[R](r: R): Managed[Nothing, R] =
    new Managed[Nothing, R] {
      type R0 = R

      def acquire: IO[Nothing, R] = IO.now(r)

      def release: R => IO[Nothing, Unit] = _ => IO.unit
    }

  /**
   * Lifts a by-name, pure value into a Managed.
   */
  final def point[R](r: => R): Managed[Nothing, R] =
    new Managed[Nothing, R] {
      type R0 = R

      def acquire: IO[Nothing, R] = IO.point(r)

      def release: R => IO[Nothing, Unit] = _ => IO.unit
    }

  final def traverse[E, R, A](as: Iterable[A])(f: A => Managed[E, R]): Managed[E, List[R]] =
    as.foldRight[Managed[E, List[R]]](now(Nil)) { (a, m) =>
      f(a).seqWith(m)(_ :: _)
    }

  final def sequence[E, R, A](ms: Iterable[Managed[E, R]]): Managed[E, List[R]] =
    traverse(ms)(identity)
}
