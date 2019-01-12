package scalaz.zio

/**
 * A `Managed[E, A]` is a managed resource of type `A`, which may be used by
 * invoking the `use` method of the resource. The resource will be automatically
 * acquired before the resource is used, and automatically released after the
 * resource is used.
 *
 * Resources do not survive the scope of `use`, meaning that if you attempt to
 * capture the resource, leak it from `use`, and then use it after the resource
 * has been consumed, the resource will not be valid anymore and may fail with
 * some checked error, as per the type of the functions provided by the resource.
 */
sealed abstract class Managed[-R, +E, +A] extends Serializable { self =>
  type A0 <: A

  protected def acquire: ZIO[R, E, A0]

  protected def release: A0 => UIO[_]

  def use[R1 <: R, E1 >: E, A1](f: A => ZIO[R1, E1, A1]): ZIO[R1, E1, A1]

  final def use_[R1 <: R, E1 >: E, A](f: ZIO[R1, E1, A]): ZIO[R1, E1, A] =
    use(_ => f)

  final def map[A1](f0: A => A1): Managed[R, E, A1] =
    new Managed[R, E, A1] {
      type A0 = A1

      protected def acquire: IO[E, A1] = IO.never

      protected def release: A1 => IO[Nothing, Unit] = _ => IO.unit

      def use[R1 <: R, E1 >: E, A](f: A1 => ZIO[R1, E1, A]): ZIO[R1, E1, A] =
        self.use(r => f(f0(r)))
    }

  final def flatMap[R1 <: R, E1 >: E, A1](f0: A => Managed[R1, E1, A1]): Managed[R1, E1, A1] =
    new Managed[R1, E1, A1] {
      type A0 = A1

      protected def acquire: ZIO[R1, E1, A1] = IO.never

      protected def release: A1 => UIO[Unit] = _ => IO.unit

      def use[R2 <: R1, E2 >: E1, A](f: A1 => ZIO[R2, E2, A]): ZIO[R2, E2, A] =
        self.use { r =>
          f0(r).use { r1 =>
            f(r1)
          }
        }
    }

  final def *>[R1 <: R, E1 >: E, A1](that: Managed[R1, E1, A1]): Managed[R1, E1, A1] =
    flatMap(_ => that)

  final def <*[R1 <: R, E1 >: E, A1](that: Managed[R1, E1, A1]): Managed[R1, E1, A] =
    flatMap(r => that.map(_ => r))

  final def seqWith[R1 <: R, E1 >: E, A1, A2](that: Managed[R1, E1, A1])(f: (A, A1) => A2): Managed[R1, E1, A2] =
    flatMap(r => that.map(r1 => f(r, r1)))

  final def seq[R1 <: R, E1 >: E, A1](that: Managed[R1, E1, A1]): Managed[R1, E1, (A, A1)] =
    seqWith(that)((_, _))

  final def parWith[R1 <: R, E1 >: E, A1, A2](that: Managed[R1, E1, A1])(f0: (A, A1) => A2): Managed[R1, E1, A2] =
    new Managed[R1, E1, A2] {
      type A0 = A2

      protected def acquire: ZIO[R1, E1, A2] = IO.never

      protected def release: A2 => UIO[Unit] = _ => IO.unit

      def use[R2 <: R1, E2 >: E1, A](f: A2 => ZIO[R2, E2, A]): ZIO[R2, E2, A] = {
        val acquireBoth = self.acquire.par(that.acquire)

        def releaseBoth(pair: (self.A0, that.A0)): UIO[Unit] =
          self.release(pair._1).par(that.release(pair._2)) *> IO.unit

        acquireBoth.bracket[R2, E2, A](releaseBoth) {
          case (r, r1) => f(f0(r, r1))
        }
      }
    }

  final def par[R1 <: R, E1 >: E, A1](that: Managed[R1, E1, A1]): Managed[R1, E1, (A, A1)] =
    parWith(that)((_, _))
}

object Managed {

  /**
   * Lifts an `IO[E, A]`` into `Managed[E, A]`` with a release action.
   */
  final def apply[R, E, A](a: ZIO[R, E, A])(r: A => UIO[_]): Managed[R, E, A] =
    new Managed[R, E, A] {
      type A0 = A

      protected def acquire: ZIO[R, E, A] = a

      protected def release: A => UIO[_] = r

      def use[R1 <: R, E1 >: E, A1](f: A => ZIO[R1, E1, A1]): ZIO[R1, E1, A1] =
        acquire.bracket[R1, E1, A1](release)(f)
    }

  /**
   * Lifts an IO[E, A] into Managed[E, A] with no release action. Use
   * with care.
   */
  final def liftIO[R, E, A](fa: ZIO[R, E, A]): Managed[R, E, A] =
    new Managed[R, E, A] {
      type A0 = A

      protected def acquire: ZIO[R, E, A] = fa

      protected def release: A => UIO[_] = _ => IO.unit

      def use[R1 <: R, E1 >: E, A1](f: A => ZIO[R1, E1, A1]): ZIO[R1, E1, A1] =
        fa flatMap f
    }

  /**
   * Unwraps a `Managed` that is inside an `IO`.
   */
  final def unwrap[R, E, A](fa: ZIO[R, E, Managed[R, E, A]]): Managed[R, E, A] =
    new Managed[R, E, A] {
      type A0 = A

      protected def acquire: ZIO[R, E, A] = IO.never

      protected def release: A => UIO[Unit] = _ => IO.unit

      def use[R1 <: R, E1 >: E, A1](f: A => ZIO[R1, E1, A1]): ZIO[R1, E1, A1] =
        fa flatMap (_ use f)
    }

  /**
   * Lifts a strict, pure value into a Managed.
   */
  final def now[A](r: A): Managed[Any, Nothing, A] =
    new Managed[Any, Nothing, A] {
      type A0 = A

      protected def acquire: UIO[A] = IO.now(r)

      protected def release: A => UIO[_] = _ => IO.unit

      def use[R <: Any, E >: Nothing, A1](f: A => ZIO[R, E, A1]): ZIO[R, E, A1] = f(r)
    }

  /**
   * Lifts a by-name, pure value into a Managed.
   */
  final def point[A](r: => A): Managed[Any, Nothing, A] =
    new Managed[Any, Nothing, A] {
      type A0 = A

      protected def acquire: UIO[A] = IO.point(r)

      protected def release: A => UIO[_] = _ => IO.unit

      def use[R <: Any, E >: Nothing, A1](f: A => ZIO[R, E, A1]): ZIO[R, E, A1] = f(r)
    }

  final def traverse[R, E, A, A1](as: Iterable[A])(f: A => Managed[R, E, A1]): Managed[R, E, List[A1]] =
    as.foldRight[Managed[R, E, List[A1]]](now(Nil)) { (a, m) =>
      f(a).seqWith(m)(_ :: _)
    }

  final def sequence[R, E, A](ms: Iterable[Managed[R, E, A]]): Managed[R, E, List[A]] =
    traverse(ms)(identity)
}
