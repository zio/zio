// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object ioeffect {

  implicit class IOVoidSyntax[A](val io: IO[Void, A]) extends AnyRef {
    def apply[E]: IO[E, A] = io.asInstanceOf[IO[E, A]]
  }

  type Task[A] = IO[Throwable, A]

  type Unexceptional[A] = IO[Void, A]

  type Void = Void.Void // required at this level for working Void

  val Void: VoidModule = VoidImpl
}
