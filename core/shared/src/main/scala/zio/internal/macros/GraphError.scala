package zio.internal.macros

sealed trait GraphError[+A]

object GraphError {
  case class MissingDependency[+A](node: Node[A], dependency: String)                   extends GraphError[A]
  case class MissingTopLevelDependency(requirement: String)                             extends GraphError[Nothing]
  case class CircularDependency[+A](node: Node[A], dependency: Node[A], depth: Int = 0) extends GraphError[A]
}
