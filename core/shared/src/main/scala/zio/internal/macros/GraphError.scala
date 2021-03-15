package zio.internal.macros

sealed trait GraphError[+Key, +A]

object GraphError {
  case class MissingDependency[+Key, +A](node: Node[Key, A], dependency: Key) extends GraphError[Key, A]
  case class MissingTopLevelDependency[+Key](requirement: Key)                extends GraphError[Key, Nothing]
  case class CircularDependency[+Key, +A](node: Node[Key, A], dependency: Node[Key, A], depth: Int = 0)
      extends GraphError[Key, A]
}
