package scalaz
package zio
package interop

import Tags.Parallel
import scalaz72.ParIO

object scalaz72 extends IOInstances with Scalaz72Platform {
  type ParIO[E, A] = IO[E, A] @@ Parallel
}

abstract class IOInstances extends IOInstances1 {

  // cached for efficiency
  implicit val taskInstances: MonadError[Task, Throwable] with BindRec[Task] with Plus[Task] =
    new IOMonadError[Throwable] with IOPlus[Throwable]

  implicit val taskParAp: Applicative[ParIO[Throwable, ?]] = new IOParApplicative[Throwable]
}

sealed abstract class IOInstances1 extends IOInstances2 {
  implicit def ioMonoidInstances[E: Monoid]
    : MonadError[IO[E, ?], E] with BindRec[IO[E, ?]] with Bifunctor[IO] with MonadPlus[IO[E, ?]] =
    new IOMonadPlus[E] with IOBifunctor

  implicit def ioParAp[E]: Applicative[ParIO[E, ?]] = new IOParApplicative[E]
}

sealed abstract class IOInstances2 {
  implicit def ioInstances[E]: MonadError[IO[E, ?], E] with BindRec[IO[E, ?]] with Bifunctor[IO] with Plus[IO[E, ?]] =
    new IOMonadError[E] with IOPlus[E] with IOBifunctor
}

private class IOMonad[E] extends Monad[IO[E, ?]] with BindRec[IO[E, ?]] {
  override def map[A, B](fa: IO[E, A])(f: A => B): IO[E, B]         = fa.map(f)
  override def point[A](a: => A): IO[E, A]                          = IO.point(a)
  override def bind[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] = fa.flatMap(f)
  override def tailrecM[A, B](f: A => IO[E, A \/ B])(a: A): IO[E, B] =
    f(a).flatMap(_.fold(tailrecM(f), point(_)))
}

private class IOMonadError[E] extends IOMonad[E] with MonadError[IO[E, ?], E] {
  override def handleError[A](fa: IO[E, A])(f: E => IO[E, A]): IO[E, A] = fa.catchAll(f)
  override def raiseError[A](e: E): IO[E, A]                            = IO.fail(e)
}

// lossy, throws away errors using the "first success" interpretation of Plus
private trait IOPlus[E] extends Plus[IO[E, ?]] {
  override def plus[A](a: IO[E, A], b: => IO[E, A]): IO[E, A] = a.orElse(b)
}

private class IOMonadPlus[E: Monoid] extends IOMonadError[E] with MonadPlus[IO[E, ?]] {
  override def plus[A](a: IO[E, A], b: => IO[E, A]): IO[E, A] =
    a.catchAll { e1 =>
      b.catchAll { e2 =>
        IO.fail(Monoid[E].append(e1, e2))
      }
    }
  override def empty[A]: IO[E, A] = raiseError(Monoid[E].zero)
}

private trait IOBifunctor extends Bifunctor[IO] {
  override def bimap[A, B, C, D](fab: IO[A, B])(f: A => C, g: B => D): IO[C, D] =
    fab.bimap(f, g)
}

private class IOParApplicative[E] extends Applicative[ParIO[E, ?]] {
  override def point[A](a: => A): ParIO[E, A] = Tag(IO.point(a))
  override def ap[A, B](fa: => ParIO[E, A])(f: => ParIO[E, A => B]): ParIO[E, B] = {
    lazy val fa0: IO[E, A] = Tag.unwrap(fa)
    Tag(Tag.unwrap(f).flatMap(x => fa0.map(x)))
  }

  override def map[A, B](fa: ParIO[E, A])(f: A => B): ParIO[E, B] =
    Tag(Tag.unwrap(fa).map(f))

  override def apply2[A, B, C](
    fa: => ParIO[E, A],
    fb: => ParIO[E, B]
  )(f: (A, B) => C): ParIO[E, C] =
    Tag(Tag.unwrap(fa).par(Tag.unwrap(fb)).map(f.tupled))
}
