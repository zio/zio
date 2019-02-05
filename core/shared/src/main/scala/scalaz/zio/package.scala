// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object zio extends EitherCompat {
  private[zio] type Callback[E, A] = Exit[E, A] => Unit

  type Canceler = IO[Nothing, _]
  type FiberId  = Long
}
