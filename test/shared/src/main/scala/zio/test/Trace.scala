package zio.test

import zio.Chunk

import scala.annotation.tailrec

sealed trait Trace[+A] { self =>
  def removingConsecutiveErrors(err: Throwable): Option[Trace[A]] = self match {
    case Trace.Node(Result.Fail(e), _, _) if e == err => None
    case node: Trace.Node[_]                          => Some(node)
    case Trace.Then(left, right) =>
      (left.removingConsecutiveErrors(err), right.removingConsecutiveErrors(err)) match {
        case (Some(a), Some(b)) => Some(Trace.Then(a, b))
        case (_, b)             => b
      }
    case both: Trace.Both[_, _] => Some(both)
  }

  def lastNode: Option[Trace.Node[_]] = self match {
    case node: Trace.Node[_]  => Some(node)
    case Trace.Then(_, right) => right.lastNode
    case _: Trace.Both[_, _]  => None
  }

  @tailrec
  final def label(label0: String): Trace[A] =
    self match {
      case node: Trace.Node[_]  => node.copy(label = Some(label0))
      case Trace.Then(_, right) => right.label(label0)
      case both                 => both
    }

  final def <*>[B](that: Trace[B]): Trace[(A, B)] =
    Trace.Both(self, that)

  final def >>>[B](that: Trace[B]): Trace[B] =
    Trace.Then(self, that)

  def result: Result[A]

  final def debug: Unit = self match {
    case Trace.Node(result, label, message) =>
      println {
        s"""label: ${label.getOrElse("N/A")}
           |result: $result
           |message: $message
           |""".stripMargin.trim
      }
    case Trace.Then(left, right) =>
      left.debug
      println("-->")
      right.debug
    case _ =>
  }
}

object Trace {

  private[test] case class Node[+A](result: Result[A], label: Option[String] = None, message: Option[String] = None)
      extends Trace[A]

  private[test] case class Both[+A, +B](left: Trace[A], right: Trace[B]) extends Trace[(A, B)] {
    override def result: Result[(A, B)] = left.result <*> right.result
  }

  private[test] case class Then[A, +B](left: Trace[A], right: Trace[B]) extends Trace[B] {
    override def result: Result[B] = right.result
  }

  def halt: Trace[Nothing]                       = Node(Result.Halt)
  def halt(message: String): Trace[Nothing]      = Node(Result.Halt, message = Some(message))
  def succeed[A](value: A): Trace[A]             = Node(Result.succeed(value))
  def fail(throwable: Throwable): Trace[Nothing] = Node(Result.fail(throwable))

  object Halt {
    def unapply[A](trace: Trace[A]): Boolean =
      trace.result match {
        case Result.Halt => true
        case _           => false
      }
  }

  object Fail {
    def unapply[A](trace: Trace[A]): Option[Throwable] =
      trace.result match {
        case Result.Fail(err) => Some(err)
        case _                => None
      }
  }

  object Succeed {
    def unapply[A](trace: Trace[A]): Option[A] =
      trace.result match {
        case Result.Succeed(value) => Some(value)
        case _                     => None
      }
  }
}

sealed trait TraceTree { self =>
  final def >>>(that: TraceTree): TraceTree =
    (self, that) match {
      case (TraceTree.Empty, that)      => that
      case (self, TraceTree.Empty)      => self
      case (TraceTree.Next(n1, n2), n3) => TraceTree.Next(n1, n2 >>> n3)
    }

  final def ++(that: TraceTree): TraceTree =
    (self, that) match {
      case (TraceTree.Empty, that)                          => that
      case (self, TraceTree.Empty)                          => self
      case (TraceTree.Many(nodes1), TraceTree.Many(nodes2)) => TraceTree.Many(nodes1 ++ nodes2)
      case (TraceTree.Many(nodes1), n2: TraceTree.Next)     => TraceTree.Many(nodes1 :+ n2)
      case (n1: TraceTree.Next, TraceTree.Many(nodes2))     => TraceTree.Many(n1 +: nodes2)
    }
}

object TraceTree {
  case object Empty                                     extends TraceTree
  case class Next(node: Trace.Node[_], next: TraceTree) extends TraceTree
  case class Many(nodes: Chunk[TraceTree])              extends TraceTree

  def fromTrace(trace: Trace[_]): TraceTree = trace match {
    case node: Trace.Node[_] => TraceTree.Next(node, TraceTree.Empty)
    case Trace.Both(left, right) =>
      fromTrace(left) ++ fromTrace(right)
    case Trace.Then(left, right) =>
      fromTrace(left) >>> fromTrace(right)
  }
}
