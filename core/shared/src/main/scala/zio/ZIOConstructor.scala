package zio

import scala.util.Try

sealed trait ZIOConstructor[Input] {
  type Out

  def make(input: Input): Out
}
object ZIOConstructor extends ZIOConstructorLowPriority {

  implicit def EitherConstructor[E, A]: WithOut[Either[E, A], ZIO[Any, E, A]] = new ZIOConstructor[Either[E, A]] {
    type Out = ZIO[Any, E, A]
    def make(input: Either[E, A]): ZIO[Any, E, A] =
      ZIO.fromEither(input)
  }

  implicit def FunctionConstructor[A, B]: WithOut[A => B, ZIO[A, Nothing, B]] = new ZIOConstructor[A => B] {
    type Out = ZIO[A, Nothing, B]
    def make(input: (A => B)): ZIO[A, Nothing, B] =
      ZIO.fromFunction(input)
  }

  // implicit def LeftConstructor[E, A]: WithOut[Left[E, A], ZIO[Any, E, A]] = new ZIOConstructor[Left[E, A]] {
  //   type Out = ZIO[Any, E, A]
  //   def make(input: Left[E, A]): ZIO[Any, E, A] =
  //     ZIO.fromEither(input)
  // }

  // implicit def RightConstructor[E, A]: WithOut[Right[E, A], ZIO[Any, E, A]] = new ZIOConstructor[Right[E, A]] {
  //   type Out = ZIO[Any, E, A]
  //   def make(input: Right[E, A]): ZIO[Any, E, A] =
  //     ZIO.fromEither(input)
  // }

  implicit def TryConstructor[A]: WithOut[Try[A], ZIO[Any, Throwable, A]] = new ZIOConstructor[Try[A]] {
    type Out = ZIO[Any, Throwable, A]
    def make(input: Try[A]): ZIO[Any, Throwable, A] =
      ZIO.fromTry(input)
  }
}

trait ZIOConstructorLowPriority {
  type WithOut[In, Out0] = ZIOConstructor[In] { type Out = Out0 }

  implicit def AttemptConstructor[A]: WithOut[A, ZIO[Any, Throwable, A]] = new ZIOConstructor[A] {
    type Out = ZIO[Any, Throwable, A]
    def make(input: A): ZIO[Any, Throwable, A] = ZIO.attempt(input)
  }
}
