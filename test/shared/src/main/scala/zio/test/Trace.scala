package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.TestArrow.Span

import scala.annotation.tailrec

sealed trait Trace[+A] { self =>

  def values: List[Any] =
    self.asInstanceOf[Trace[Any]] match {
      case Trace.Node(Result.Succeed(value), _, _, _, _, _, _, _, _, _) =>
        List(value)
      case Trace.Node(_, _, _, _, _, _, _, _, _, _) =>
        List()
      case Trace.AndThen(left, right) =>
        left.values ++ right.values
      case Trace.And(left, right) =>
        left.values ++ right.values
      case Trace.Or(left, right) =>
        left.values ++ right.values
      case Trace.Not(trace) =>
        trace.values
    }

  def isFailure(implicit ev: A <:< Boolean): Boolean = !isSuccess

  def isSuccess(implicit ev: A <:< Boolean): Boolean =
    Trace.prune(self.asInstanceOf[Trace[Boolean]], false).isEmpty

  def isDie: Boolean =
    self.asInstanceOf[Trace[_]] match {
      case Trace.Node(Result.Die(_), _, _, _, _, _, _, _, _, _) => true
      case Trace.Node(_, _, _, _, _, _, _, _, _, _)             => false
      case Trace.AndThen(left, right)                           => left.isDie || right.isDie
      case Trace.And(left, right)                               => left.isDie || right.isDie
      case Trace.Or(left, right)                                => left.isDie || right.isDie
      case Trace.Not(trace)                                     => trace.isDie
    }

  /**
   * Apply the metadata to the rightmost node in the trace.
   */
  final def withSpan(span: Option[Span] = None): Trace[A] = if (span.isDefined) {
    self match {
      case node: Trace.Node[_]        => node.copy(span = span)
      case Trace.AndThen(left, right) => Trace.AndThen(left, right.withSpan(span))
      case zip                        => zip
    }
  } else {
    self
  }

  /**
   * Apply the parent span to every node in the tree.
   */
  def withParentSpan(span: Option[Span]): Trace[A] = if (span.isDefined) {
    self match {
      case node: Trace.Node[_] =>
        node.copy(parentSpan = node.parentSpan.orElse(span))
      case Trace.AndThen(left, right) =>
        Trace.AndThen(left.withParentSpan(span), right.withParentSpan(span))
      case and: Trace.And =>
        Trace.And(and.left.withParentSpan(span), and.right.withParentSpan(span)).asInstanceOf[Trace[A]]
      case or: Trace.Or =>
        Trace.Or(or.left.withParentSpan(span), or.right.withParentSpan(span)).asInstanceOf[Trace[A]]
      case not: Trace.Not =>
        Trace.Not(not.trace.withParentSpan(span)).asInstanceOf[Trace[A]]
    }
  } else {
    self
  }

  /**
   * Apply the location to every node in the tree.
   */
  def withLocation(location: Option[String]): Trace[A] = if (location.isDefined) {
    self match {
      case node: Trace.Node[_] =>
        node.copy(location = location, children = node.children.map(_.withLocation(location)))
      case Trace.AndThen(left, right) =>
        Trace.AndThen(left.withLocation(location), right.withLocation(location))
      case and: Trace.And =>
        Trace.And(and.left.withLocation(location), and.right.withLocation(location)).asInstanceOf[Trace[A]]
      case or: Trace.Or =>
        Trace.Or(or.left.withLocation(location), or.right.withLocation(location)).asInstanceOf[Trace[A]]
      case not: Trace.Not =>
        Trace.Not(not.trace.withLocation(location)).asInstanceOf[Trace[A]]
    }
  } else {
    self
  }

  def withCustomLabel(customLabel: Option[String]): Trace[A] =
    if (customLabel.isDefined) {
      self match {
        case node: Trace.Node[_] =>
          node.copy(customLabel = customLabel, children = node.children.map(_.withCustomLabel(customLabel)))
        case Trace.AndThen(left, right) =>
          Trace.AndThen(left.withCustomLabel(customLabel), right.withCustomLabel(customLabel))
        case and: Trace.And =>
          Trace
            .And(and.left.withCustomLabel(customLabel), and.right.withCustomLabel(customLabel))
            .asInstanceOf[Trace[A]]
        case or: Trace.Or =>
          Trace.Or(or.left.withCustomLabel(customLabel), or.right.withCustomLabel(customLabel)).asInstanceOf[Trace[A]]
        case not: Trace.Not =>
          Trace.Not(not.trace.withCustomLabel(customLabel)).asInstanceOf[Trace[A]]
      }
    } else {
      self
    }

  /**
   * Apply the code to every node in the tree.
   */
  final def withCode(fullCode: Option[String]): Trace[A] =
    self match {
      case node: Trace.Node[_] =>
        node.copy(fullCode = fullCode.orElse(node.fullCode), children = node.children.map(_.withCode(fullCode)))
      case Trace.AndThen(left, right) =>
        Trace.AndThen(left.withCode(fullCode), right.withCode(fullCode))
      case and: Trace.And =>
        Trace.And(and.left.withCode(fullCode), and.right.withCode(fullCode)).asInstanceOf[Trace[A]]
      case or: Trace.Or =>
        Trace.Or(or.left.withCode(fullCode), or.right.withCode(fullCode)).asInstanceOf[Trace[A]]
      case not: Trace.Not =>
        Trace.Not(not.trace.withCode(fullCode)).asInstanceOf[Trace[A]]
    }

  /**
   * Apply the code to every node in the tree.
   */
  final def withCompleteCode(completeCode: Option[String]): Trace[A] =
    self match {
      case node: Trace.Node[_] =>
        node.copy(completeCode = completeCode, children = node.children.map(_.withCompleteCode(completeCode)))
      case Trace.AndThen(left, right) =>
        Trace.AndThen(left.withCompleteCode(completeCode), right.withCompleteCode(completeCode))
      case and: Trace.And =>
        Trace
          .And(and.left.withCompleteCode(completeCode), and.right.withCompleteCode(completeCode))
          .asInstanceOf[Trace[A]]
      case or: Trace.Or =>
        Trace.Or(or.left.withCompleteCode(completeCode), or.right.withCompleteCode(completeCode)).asInstanceOf[Trace[A]]
      case not: Trace.Not =>
        Trace.Not(not.trace.withCompleteCode(completeCode)).asInstanceOf[Trace[A]]
    }

  @tailrec
  final def annotate(annotation: Trace.Annotation*): Trace[A] =
    self match {
      case node: Trace.Node[_]     => node.copy(annotations = node.annotations ++ annotation.toSet)
      case Trace.AndThen(_, right) => right.annotate(annotation: _*)
      case zip                     => zip
    }

  final def implies(that: Trace[Boolean])(implicit ev: A <:< Boolean): Trace[Boolean] =
    !self || that

  final def ==>(that: Trace[Boolean])(implicit ev: A <:< Boolean): Trace[Boolean] =
    implies(that)

  final def <==>(that: Trace[Boolean])(implicit ev: A <:< Boolean): Trace[Boolean] =
    self ==> that && that ==> self.asInstanceOf[Trace[Boolean]]

  final def &&(that: Trace[Boolean])(implicit ev: A <:< Boolean): Trace[Boolean] =
    Trace.And(self.asInstanceOf[Trace[Boolean]], that)

  final def ||(that: Trace[Boolean])(implicit ev: A <:< Boolean): Trace[Boolean] =
    Trace.Or(self.asInstanceOf[Trace[Boolean]], that)

  final def unary_!(implicit ev: A <:< Boolean): Trace[Boolean] =
    Trace.Not(self.asInstanceOf[Trace[Boolean]])

  final def >>>[B](that: Trace[B]): Trace[B] =
    Trace.AndThen(self, that)

  def result: Result[A]
}

object Trace {

  /**
   * Prune all non-failures from the trace.
   */
  def prune(trace: Trace[Boolean], negated: Boolean): Option[Trace[Boolean]] =
    trace match {
      case node @ Trace.Node(Result.Succeed(bool), _, _, _, _, _, _, _, _, _) =>
        if (bool == negated) {
          Some(node.copy(children = node.children.flatMap(prune(_, negated))))
        } else
          None

      case Trace.Node(Result.Fail, _, _, _, _, _, _, _, _, _) =>
        if (negated) None else Some(trace)

      case Trace.Node(Result.Die(_), _, _, _, _, _, _, _, _, _) =>
        Some(trace)

      case Trace.AndThen(left, node: Trace.Node[_]) if node.annotations.contains(Trace.Annotation.Rethrow) =>
        prune(left.asInstanceOf[Trace[Boolean]], negated)

      case Trace.AndThen(left, right) =>
        prune(right, negated).map { next =>
          Trace.AndThen(left, next)
        }

      case and: Trace.And =>
        (prune(and.left, negated), prune(and.right, negated)) match {
          case (None, Some(right)) if !negated => Some(right)
          case (Some(left), None) if !negated  => Some(left)
          case (Some(left), Some(right))       => Some(Trace.And(left, right))
          case _                               => None
        }

      case or: Trace.Or =>
        (prune(or.left, negated), prune(or.right, negated)) match {
          case (Some(left), Some(right))                  => Some(Trace.Or(left, right))
          case (Some(left), _) if negated || left.isDie   => Some(left)
          case (_, Some(right)) if negated || right.isDie => Some(right)
          case (_, _)                                     => None
        }

      case not: Trace.Not =>
        prune(not.trace, !negated)
    }

  sealed trait Annotation

  object Annotation {
    case object Rethrow extends Annotation {
      def unapply(value: Set[Annotation]): Boolean = value.contains(Rethrow)
    }
  }

  private[test] case class Node[+A](
    result: Result[A],
    message: ErrorMessage = ErrorMessage.choice("Result was true", "Result was false"),
    children: Option[Trace[Boolean]] = None,
    span: Option[Span] = None,
    parentSpan: Option[Span] = None,
    fullCode: Option[String] = None,
    location: Option[String] = None,
    annotations: Set[Annotation] = Set.empty,
    completeCode: Option[String] = None,
    customLabel: Option[String] = None
  ) extends Trace[A] {

    def renderResult: Any =
      result match {
        case Result.Fail           => "<FAIL>"
        case Result.Die(err)       => err
        case Result.Succeed(value) => value
      }

    def code: String =
      span match {
        case Some(span) => span.substring(fullCode.getOrElse(""))
        case None       => fullCode.getOrElse("")
      }
  }

  private[test] case class AndThen[A, +B](left: Trace[A], right: Trace[B]) extends Trace[B] {
    override def result: Result[B] = right.result
  }

  private[test] case class And(left: Trace[Boolean], right: Trace[Boolean]) extends Trace[Boolean] {
    override def result: Result[Boolean] = left.result.zipWith(right.result)(_ && _)
  }

  private[test] case class Or(left: Trace[Boolean], right: Trace[Boolean]) extends Trace[Boolean] {
    override def result: Result[Boolean] = left.result.zipWith(right.result)(_ || _)
  }

  private[test] case class Not(trace: Trace[Boolean]) extends Trace[Boolean] {
    override def result: Result[Boolean] = trace.result match {
      case Result.Succeed(value) => Result.Succeed(!value)
      case other                 => other
    }
  }

  def fail: Trace[Nothing]                        = Node(Result.Fail)
  def fail(message: String): Trace[Nothing]       = Node(Result.Fail, message = ErrorMessage.text(message))
  def fail(message: ErrorMessage): Trace[Nothing] = Node(Result.Fail, message = message)
  def succeed[A](value: A): Trace[A]              = Node(Result.succeed(value))
  def option[A](value: Option[A])(message: ErrorMessage): Trace[A] = {
    val result = value.fold[Result[A]](Result.Fail)(a => Result.succeed(a))
    Node[A](result, message = message)
  }

  def boolean(value: Boolean)(message: ErrorMessage): Trace[Boolean] =
    Node(Result.succeed(value), message = message)

  def die(throwable: Throwable): Trace[Nothing] =
    Node(Result.die(throwable), message = ErrorMessage.throwable(throwable))

}
