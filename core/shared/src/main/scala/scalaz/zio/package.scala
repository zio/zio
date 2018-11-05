// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object zio {
  type Callback[E, A] = ExitResult[E, A] => Unit
  type Canceler       = () => Unit
  type PureCanceler   = () => IO[Nothing, Unit]
  type FiberId        = Long
}
