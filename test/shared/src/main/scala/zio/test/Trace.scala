package zio.test

import scala.annotation.tailrec

sealed trait Trace[+A] { self =>

  // TODO: Beautify
  final def removingConsecutiveErrors(err: Throwable): Option[Trace[A]] = self match {
    case Trace.Node(Result.Fail(e), _, _, _) if e == err => None
    case node: Trace.Node[_]                             => Some(node)
    case Trace.Then(left, right) =>
      (left.removingConsecutiveErrors(err), right.removingConsecutiveErrors(err)) match {
        case (Some(a), Some(b)) => Some(Trace.Then(a, b))
        case (_, b)             => b
      }
    case both: Trace.Both[_, _] => Some(both)
  }

  @tailrec
  final def label(label0: String): Trace[A] =
    self match {
      case node: Trace.Node[_]  => node.copy(label = Some(label0))
      case Trace.Then(_, right) => right.label(label0)
      case both                 => both
    }

  @tailrec
  final def annotate(annotation: Trace.Annotation*): Trace[A] =
    self match {
      case node: Trace.Node[_]  => node.copy(annotations = node.annotations ++ annotation.toSet)
      case Trace.Then(_, right) => right.annotate(annotation: _*)
      case both                 => both
    }

  final def <*>[B](that: Trace[B]): Trace[(A, B)] =
    Trace.Both(self, that)

  final def >>>[B](that: Trace[B]): Trace[B] =
    Trace.Then(self, that)

  def result: Result[A]
}

object Trace {

  sealed trait Annotation

  object Annotation {
    case object BooleanLogic extends Annotation {
      def unapply(value: Set[Annotation]): Boolean = value.contains(BooleanLogic)
    }
  }

  private[test] case class Node[+A](
    result: Result[A],
    label: Option[String] = None,
    message: Option[String] = None,
    annotations: Set[Annotation] = Set.empty
  ) extends Trace[A]

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
