// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object zio {

  implicit class IOVoidSyntax[A](val io: IO[Nothing, A]) extends AnyRef {
    def apply[E]: IO[E, A]      = io.asInstanceOf[IO[E, A]]
    def widenError[E]: IO[E, A] = apply
  }

  implicit class IOSyntax[E, A](val io: IO[E, A]) extends AnyRef {

    /**
     * Widens the error type to any supertype. While `leftMap` suffices for this
     * purpose, this method is significantly faster for this purpose.
     */
    def widenError[E2 >: E]: IO[E2, A] = io.asInstanceOf[IO[E2, A]]
  }

  type Canceler     = Throwable => Unit
  type PureCanceler = Throwable => IO[Nothing, Unit]
}
