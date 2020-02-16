package zio.stream

package object experimental {
  type Stream[+E, +A] = ZStream[Any, E, Any, Unit, A]
  type UStream[A]     = ZStream[Any, Nothing, Any, Unit, A]
  type URStream[R, A] = ZStream[R, Nothing, Any, Unit, A]
}
