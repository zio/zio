package zio

trait EitherCompat {
  implicit final class EitherOps[E, A](self: Either[E, A]) {
    def flatMap[E1 >: E, B](f: A => Either[E1, B]): Either[E1, B] =
      self.fold(e => Left(e), a => f(a))
    def map[B](f: A => B): Either[E, B] =
      self.fold(e => Left(e), a => Right(f(a)))
  }
}
