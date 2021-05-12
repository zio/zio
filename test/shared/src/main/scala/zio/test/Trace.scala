package zio.test

import zio.test.Assert.Span

import scala.annotation.tailrec

sealed trait Trace[+A] { self =>

  /**
   * Apply the metadata to the rightmost node in the trace.
   */
  final def meta(label: Option[String] = None, span: Option[Span] = None): Trace[A] =
    self match {
      case node: Trace.Node[_]        => node.copy(label = label, span = span)
      case Trace.AndThen(left, right) => Trace.AndThen(left, right.meta(label, span))
      case zip                        => zip
    }

  /**
   * Apply the code to every node in the tree.
   */
  final def withCode(code: Option[String]): Trace[A] =
    self match {
      case node: Trace.Node[_] => node.copy(code = code)
      case Trace.AndThen(left, right) =>
        Trace.AndThen(left.withCode(code), right.withCode(code))
      case zip: Trace.Zip[_, _] =>
        Trace.Zip(zip.left.withCode(code), zip.right.withCode(code)).asInstanceOf[Trace[A]]
    }

  @tailrec
  final def annotate(annotation: Trace.Annotation*): Trace[A] =
    self match {
      case node: Trace.Node[_]     => node.copy(annotations = node.annotations ++ annotation.toSet)
      case Trace.AndThen(_, right) => right.annotate(annotation: _*)
      case zip                     => zip
    }

  final def zip[B](that: Trace[B]): Trace[(A, B)] =
    Trace.Zip(self, that)

  final def >>>[B](that: Trace[B]): Trace[B] =
    Trace.AndThen(self, that)

  def result: Result[A]
}

object Trace {
  def markFailures[A](trace: Trace[A], negated: Boolean): Trace[A] = trace match {
    case node @ Node(Result.Succeed(bool: Boolean), _, _, _, _, _) if bool == negated =>
      node.annotate(Annotation.Failure)
    case AndThen(left, right @ Node(_, _, _, _, _, Annotation.Not())) =>
      AndThen(markFailures(left, !negated), right)
    case AndThen(left, right) =>
      AndThen(markFailures(left, negated), markFailures(right, negated))
    case Zip(left, right) =>
      Zip(markFailures(left, negated), markFailures(right, negated))
    case other => other
  }

  sealed trait Annotation

  object Annotation {

    case object And extends Annotation {
      def unapply(annotations: Set[Annotation]): Boolean =
        annotations.contains(And)
    }

    case object Or extends Annotation {
      def unapply(annotations: Set[Annotation]): Boolean =
        annotations.contains(Or)
    }

    case object Not extends Annotation {
      def unapply(annotations: Set[Annotation]): Boolean =
        annotations.contains(Not)
    }

    object BooleanLogic {
      def unapply(annotations: Set[Annotation]): Boolean =
        Set[Annotation](And, Or, Not).intersect(annotations).nonEmpty
    }

    case object Rethrow extends Annotation {
      def unapply(value: Set[Annotation]): Boolean = value.contains(Rethrow)
    }

    case object Failure extends Annotation {
      def unapply(value: Set[Annotation]): Boolean = value.contains(Failure)
    }
  }

  private[test] case class Node[+A](
    result: Result[A],
    label: Option[String] = None,
    message: Option[String] = None,
    span: Option[Span] = None,
    code: Option[String] = None,
    annotations: Set[Annotation] = Set.empty
  ) extends Trace[A]

  private[test] case class Zip[+A, +B](left: Trace[A], right: Trace[B]) extends Trace[(A, B)] {
    override def result: Result[(A, B)] = left.result zip right.result
  }

  private[test] case class AndThen[A, +B](left: Trace[A], right: Trace[B]) extends Trace[B] {
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
