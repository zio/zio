package scalaz.zio

package object stream {

  type Stream[+E, +A] = Stream[E, A]
  val Stream = StreamR

}
