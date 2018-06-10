// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

import scala.AnyRef

package object effect {

  implicit class IONothingSyntax[A](val io: IO[Nothing, A]) extends AnyRef {
    def apply[E]: IO[E, A] = io.asInstanceOf[IO[E, A]]
  }

  type Task[A] = IO[Throwable, A]

  type Unexceptional[A] = IO[Nothing, A]

  type Canceler     = Throwable => Unit
  type PureCanceler = Throwable => IO[Nothing, Unit]
}
