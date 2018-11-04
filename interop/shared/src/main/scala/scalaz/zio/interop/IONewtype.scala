package scalaz.zio
package interop

private[interop] trait IONewtype {
  type Base
  trait Tag extends Any

  type T[+E, +A] <: Base with Tag

  def apply[E, A](io: IO[E, A]): T[E, A] =
    io.asInstanceOf[T[E, A]]

  def unwrap[E, A](t: T[E, A]): IO[E, A] =
    t.asInstanceOf[IO[E, A]]
}

private[interop] object Par extends IONewtype
