package zio

package object query {

  type RQuery[-R, +A]  = ZQuery[R, Throwable, A]
  type URQuery[-R, +A] = ZQuery[R, Nothing, A]
  type Query[+E, +A]   = ZQuery[Any, E, A]
  type UQuery[+A]      = ZQuery[Any, Nothing, A]
  type TaskQuery[+A]   = ZQuery[Any, Throwable, A]
}
