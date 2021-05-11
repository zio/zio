package zio.test

import zio.Chunk

sealed trait Tree { self =>
  def render: String = debugImpl.reverse.mkString("\n")

  def debugImpl: Chunk[String] = self match {
    case Tree.Value(node) =>
      Chunk(node.toString)
    case Tree.Branch(nodes) =>
      nodes.flatMap(_.debugImpl ++ Chunk("&&")) //.dropRight(1)
    case Tree.Linear(nodes) =>
      nodes.flatMap(_.debugImpl).map("- " + _)
  }
}

object Tree {
  case class Value(node: Trace.Node[_]) extends Tree
  case class Branch(nodes: Chunk[Tree]) extends Tree
  case class Linear(nodes: Chunk[Tree]) extends Tree

  def fromTrace(trace: Trace[_]): Tree = trace match {
    case node: Trace.Node[_] => Value(node)

    // Flatten AND
    case Trace.Then(left, Trace.Node(_, _, _, Trace.Annotation.BooleanLogic())) =>
      fromTrace(left)

    case Trace.Both(left, right) =>
      (fromTrace(left), fromTrace(right)) match {
        case (Branch(ts1), Branch(ts2)) => Branch(ts1 ++ ts2)
        case (Branch(ts1), t2)          => Branch(ts1 :+ t2)
        case (t1, Branch(ts2))          => Branch(t1 +: ts2)
        case (t1, t2)                   => Branch(Chunk(t1, t2))
      }
    case Trace.Then(left, right) =>
      (fromTrace(left), fromTrace(right)) match {
        case (Linear(ts1), Linear(ts2)) => Linear(ts1 ++ ts2)
        case (Linear(ts1), t2)          => Linear(ts1 :+ t2)
        case (t1, Linear(ts2))          => Linear(t1 +: ts2)
        case (t1, t2)                   => Linear(Chunk(t1, t2))
      }
  }

}
