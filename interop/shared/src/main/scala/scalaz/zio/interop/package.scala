package scalaz.zio

package object interop {

  type Task[A] = IO[Throwable, A]

}
