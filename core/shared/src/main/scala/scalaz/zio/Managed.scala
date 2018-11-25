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
  def use[E1 >: E, A](f: R => IO[E1, A]): IO[E1, A]

  final def use_[E1 >: E, A](f: IO[E1, A]): IO[E1, A] =
    use(_ => f)

  final def map[R1](f0: R => R1): Managed[E, R1] =
    new Managed[E, R1] {
      def use[E1 >: E, A](f: R1 => IO[E1, A]): IO[E1, A] =
        self.use(r => f(f0(r)))
    }

  final def flatMap[E1 >: E, R1](f0: R => Managed[E1, R1]) =
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
}

object Managed {

  /**
   * Lifts an `IO[E, R]`` into `Managed[E, R]`` with a release action.
   */
  final def apply[E, R](acquire: IO[E, R])(release: R => IO[Nothing, Unit]) =
    new Managed[E, R] {
      final def use[E1 >: E, A](f: R => IO[E1, A]): IO[E1, A] =
        acquire.bracket[E1, A](release)(f)
    }

  /**
   * Lifts an IO[E, R] into Managed[E, R] with no release action. Use
   * with care.
   */
  final def liftIO[E, R](fa: IO[E, R]) =
    new Managed[E, R] {
      final def use[E1 >: E, A](f: R => IO[E1, A]): IO[E1, A] =
        fa.flatMap(f)
    }

  /**
   * Lifts a strict, pure value into a Managed.
   */
  final def now[R](r: R): Managed[Nothing, R] =
    new Managed[Nothing, R] {
      final def use[E, A](f: R => IO[E, A]): IO[E, A] = f(r)
    }

  /**
   * Lifts a by-name, pure value into a Managed.
   */
  final def point[R](r: => R): Managed[Nothing, R] =
    new Managed[Nothing, R] {
      final def use[E, A](f: R => IO[E, A]): IO[E, A] = f(r)
    }

  final def traverse[E, R, A](as: Iterable[A])(f: A => Managed[E, R]): Managed[E, List[R]] =
    as.foldRight[Managed[E, List[R]]](now(Nil)) { (a, m) =>
      f(a).seqWith(m)(_ :: _)
    }

  final def sequence[E, R, A](ms: Iterable[Managed[E, R]]): Managed[E, List[R]] =
    traverse(ms)(identity)
}
