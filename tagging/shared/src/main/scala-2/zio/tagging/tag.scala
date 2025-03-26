package zio.tagging

object tag {
  def apply[U]: TaggerPA[U] = TaggerPA.asInstanceOf[TaggerPA[U]]

  trait Tagged[U] extends Any
  type @@[+T, U] = ({ type R <: T with Tagged[U] })#R

  class TaggerPA[U] {
    def apply[T](t: T): T @@ U = t.asInstanceOf[T @@ U]
  }
  private object TaggerPA extends TaggerPA[Nothing]

  def untag[A, B](x: A @@ B): A = x

  implicit class Tagger[T](private val value: T) extends AnyVal {
    def taggedWith[U]: T @@ U = tag[U](value)
  }

  implicit class TaggingF[F[_], T](val fa: F[T]) extends AnyVal {
    @inline def taggedWithF[B]: F[T @@ B] = fa.asInstanceOf[F[T @@ B]]
  }
}
