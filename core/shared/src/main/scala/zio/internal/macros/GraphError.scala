package zio.internal.macros

import zio.Chunk

sealed trait GraphError[+Key, +A]

object GraphError {
  def missingTransitiveDependency[Key, A](node: Node[Key, A], dependency: Key): GraphError[Key, A] =
    MissingTransitiveDependencies(node, Chunk(dependency))

  case class MissingTransitiveDependencies[+Key, +A](node: Node[Key, A], dependency: Chunk[Key])
      extends GraphError[Key, A]

  case class MissingTopLevelDependency[+Key](requirement: Key) extends GraphError[Key, Nothing]

  case class CircularDependency[+Key, +A](node: Node[Key, A], dependency: Node[Key, A], depth: Int = 0)
      extends GraphError[Key, A]
}
