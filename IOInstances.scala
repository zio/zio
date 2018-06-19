// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz
package ioeffect

trait IOInstances extends IOInstances1 {
  // cached for efficiency
  implicit val taskInstances: MonadError[Task, Throwable] with BindRec[Task] = new IOMonadError[Throwable]
}

sealed trait IOInstances1 {
  implicit def ioInstances[E]: MonadError[IO[E, ?], E] with BindRec[IO[E, ?]] with Bifunctor[IO] =
    new IOMonadError[E] with IOBifunctor
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

private trait IOBifunctor extends Bifunctor[IO] {
  def bimap[A, B, C, D](fab: IO[A, B])(f: A => C, g: B => D): IO[C, D] =
    IO.absolve(fab.attempt.map(_.bimap(f, g)))
}
