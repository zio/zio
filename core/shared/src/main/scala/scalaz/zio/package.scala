// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object zio {
  private[zio] type Callback[E, A] = ExitResult[E, A] => Unit

  type Canceler = IO[Nothing, Unit]
  type FiberId  = Long
}
