// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object zio {
  type Canceler     = Throwable => Unit
  type PureCanceler = Throwable => IO[Nothing, Unit]
}
