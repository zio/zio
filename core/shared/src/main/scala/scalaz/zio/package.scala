// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object zio extends EitherCompat {
  private[zio] type Callback[E, A] = Exit[E, A] => Unit

  type Canceler = ZIO[Any, Nothing, _]
  type FiberId  = Long

  type IO[+E, +A] = ZIO[Any, E, A]
  type Task[+A]   = ZIO[Any, Throwable, A]
  type UIO[+A]    = ZIO[Any, Nothing, A]
}
