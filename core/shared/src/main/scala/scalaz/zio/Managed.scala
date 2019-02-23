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
final case class Managed[+E, +R](reserve: IO[E, Managed.Reservation[E, R]]) { self =>
  import Managed.Reservation

  def use[E1 >: E, A](f: R => IO[E1, A]): IO[E1, A] =
    reserve.bracket[E1, A](_.release)(_.acquire.flatMap(f))

  final def use_[E1 >: E, A](f: IO[E1, A]): IO[E1, A] =
    use(_ => f)

  final def map[R1](f0: R => R1): Managed[E, R1] =
    Managed[E, R1] {
      self.reserve.map(token => token.copy(acquire = token.acquire.map(f0)))
    }

  final def flatMap[E1 >: E, R1](f0: R => Managed[E1, R1]): Managed[E1, R1] =
    Managed[E1, R1] {
      Ref.make[IO[Nothing, Any]](IO.unit).map { finalizers =>
        Reservation(
          acquire = for {
            resR <- self.reserve.interruptible
                     .flatMap(res => finalizers.update(fs => res.release *> fs).const(res))
                     .uninterruptible
            r <- resR.acquire
            resR1 <- f0(r).reserve.interruptible
                      .flatMap(res => finalizers.update(fs => res.release *> fs).const(res))
                      .uninterruptible
            r1 <- resR1.acquire
          } yield r1,
          release = IO.flatten(finalizers.get)
        )
      }
    }

  final def *>[E1 >: E, R1](that: Managed[E1, R1]): Managed[E1, R1] =
    flatMap(_ => that)

  final def <*[E1 >: E, R1](that: Managed[E1, R1]): Managed[E1, R] =
    flatMap(r => that.map(_ => r))

  final def zipWith[E1 >: E, R1, R2](that: Managed[E1, R1])(f: (R, R1) => R2): Managed[E1, R2] =
    flatMap(r => that.map(r1 => f(r, r1)))

  final def zip[E1 >: E, R1](that: Managed[E1, R1]): Managed[E1, (R, R1)] =
    zipWith(that)((_, _))

  final def zipWithPar[E1 >: E, R1, R2](that: Managed[E1, R1])(f0: (R, R1) => R2): Managed[E1, R2] =
    Managed[E1, R2] {
      Ref.make[IO[Nothing, Any]](IO.unit).map { finalizers =>
        Reservation(
          acquire = {
            val left =
              self.reserve.interruptible
                .flatMap(res => finalizers.update(fs => res.release *> fs).const(res))
                .uninterruptible
            val right =
              that.reserve.interruptible
                .flatMap(res => finalizers.update(fs => res.release *> fs).const(res))
                .uninterruptible

            left.flatMap(_.acquire).zipWithPar(right.flatMap(_.acquire))(f0)
          },
          release = IO.flatten(finalizers.get)
        )
      }
    }

  final def zipPar[E1 >: E, R1](that: Managed[E1, R1]): Managed[E1, (R, R1)] =
    zipWithPar(that)((_, _))
}

object Managed {
  final case class Reservation[+E, +A](acquire: IO[E, A], release: IO[Nothing, _])

  /**
   * Lifts an `IO[E, R]`` into `Managed[E, R]`` with a release action.
   */
  final def make[E, R](acquire: IO[E, R])(release: R => IO[Nothing, _]): Managed[E, R] =
    Managed(acquire.map(r => Reservation(IO.succeed(r), release(r))))

  /**
   * Lifts an IO[E, R] into Managed[E, R] with no release action. Use
   * with care.
   */
  final def liftIO[E, R](fa: IO[E, R]): Managed[E, R] =
    Managed(IO.succeed(Reservation(fa, IO.unit)))

  /**
   * Unwraps a `Managed` that is inside an `IO`.
   */
  final def unwrap[E, R](fa: IO[E, Managed[E, R]]): Managed[E, R] =
    Managed(fa.flatMap(_.reserve))

  /**
   * Lifts a strict, pure value into a Managed.
   */
  final def succeed[R](r: R): Managed[Nothing, R] =
    Managed(IO.succeed(Reservation(IO.succeed(r), IO.unit)))

  /**
   * Lifts a by-name, pure value into a Managed.
   */
  final def succeedLazy[R](r: => R): Managed[Nothing, R] =
    Managed(IO.succeed(Reservation(IO.succeedLazy(r), IO.unit)))

  final def foreach[E, R, A](as: Iterable[A])(f: A => Managed[E, R]): Managed[E, List[R]] =
    as.foldRight[Managed[E, List[R]]](succeed(Nil)) { (a, m) =>
      f(a).zipWith(m)(_ :: _)
    }

  final def collectAll[E, R, A](ms: Iterable[Managed[E, R]]): Managed[E, List[R]] =
    foreach(ms)(identity)
}
