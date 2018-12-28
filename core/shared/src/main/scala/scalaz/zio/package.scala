// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object zio {
  private[zio] type Callback[E, A] = ExitResult[E, A] => Unit

  type Canceler = ZIO[Any, Nothing, _]
  type FiberId  = Long

  type IO[E, A] = ZIO[Any, E, A]
  type Task[A]  = ZIO[Any, Throwable, A]
  type UIO[A]   = ZIO[Any, Nothing, A]

  object IO extends ZIO_A_Any with ZIO_E_Any {
    type UpperE = Any
    type UpperA = Any
    type LowerR = Any
  }
  object Task extends ZIO_A_Any with ZIO_E_Throwable {
    type UpperE = Throwable
    type UpperA = Any
    type LowerR = Any
  }
  object UIO extends ZIO_A_Any {
    type UpperE = Nothing
    type UpperA = Any
    type LowerR = Any
  }
}
