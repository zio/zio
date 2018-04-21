// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz
package ioeffect

trait IOInstances {
  implicit def IOMonad[E]: Monad[IO[E, ?]] = new Monad[IO[E, ?]] {
    override def point[A](a: =>A): IO[E, A] =
      IO.point(a)
    override def bind[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] =
      fa.flatMap(f)
  }
}
