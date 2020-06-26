package zio

package object query {

  type RQuery[-R, +A]  = ZQuery[R, Throwable, A]
  type URQuery[-R, +A] = ZQuery[R, Nothing, A]
  type Query[+E, +A]   = ZQuery[Any, E, A]
  type UQuery[+A]      = ZQuery[Any, Nothing, A]
  type TaskQuery[+A]   = ZQuery[Any, Throwable, A]

  implicit class VectorSytanx[A](private val self: Vector[A]) extends AnyVal {
    def partitionMap[B, C](f: A => Either[B, C]): (Vector[B], Vector[C]) = {
      val bs = Vector.newBuilder[B]
      val cs = Vector.newBuilder[C]
      self.foreach { a =>
        f(a) match {
          case Left(b)  => bs += b
          case Right(c) => cs += c
        }
      }
      (bs.result(), cs.result())
    }
  }
}
