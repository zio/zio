package zio.stream

package object experimental {
  type Stream[+E, +A] = ZStream[Any, E, Any, Nothing, A]
  type UStream[A]     = ZStream[Any, Nothing, Any, Nothing, A]
  type URStream[R, A] = ZStream[R, Nothing, Any, Nothing, A]
}
