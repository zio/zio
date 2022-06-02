package zio.test

import zio.test.TestArrow.Span

import scala.annotation.tailrec

sealed trait Trace[+A] { self =>

  def isFailure: Boolean = !isSuccess

  def isSuccess: Boolean = self.result match {
    case Result.Succeed(true) => true
    case _                    => false
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

  /**
   * Apply the code to every node in the tree.
   */
  final def withCode(code: Option[String]): Trace[A] =
    self match {
      case node: Trace.Node[_] =>
        node.copy(fullCode = code, children = node.children.map(_.withCode(code)))
      case Trace.AndThen(left, right) =>
        Trace.AndThen(left.withCode(code), right.withCode(code))
      case and: Trace.And =>
        Trace.And(and.left.withCode(code), and.right.withCode(code)).asInstanceOf[Trace[A]]
      case or: Trace.Or =>
        Trace.Or(or.left.withCode(code), or.right.withCode(code)).asInstanceOf[Trace[A]]
      case not: Trace.Not =>
        Trace.Not(not.trace.withCode(code)).asInstanceOf[Trace[A]]
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
      case node @ Trace.Node(Result.Succeed(bool), _, _, _, _, _, _, _) if bool == negated =>
        Some(node.copy(children = node.children.flatMap(prune(_, negated))))

      case Trace.Node(Result.Succeed(_), _, _, _, _, _, _, _) =>
        None

      case Trace.Node(Result.Die(_) | Result.Fail, _, _, _, _, _, _, _) =>
        Some(trace)

      case Trace.AndThen(left, node: Trace.Node[_]) if node.annotations.contains(Trace.Annotation.Rethrow) =>
        prune(left.asInstanceOf[Trace[Boolean]], negated)

      case Trace.AndThen(left, right) =>
        prune(right, negated).map { next =>
          Trace.AndThen(left, next)
        }

      case and: Trace.And =>
        (prune(and.left, negated), prune(and.right, negated)) match {
          case (None, right)             => right
          case (left, None)              => left
          case (Some(left), Some(right)) => Some(Trace.And(left, right))
        }

      case or: Trace.Or =>
        (prune(or.left, negated), prune(or.right, negated)) match {
          case (Some(left), Some(right))                               => Some(Trace.Or(left, right))
          case (Some(left), _) if negated || left.result.isFailOrDie   => Some(left)
          case (_, Some(right)) if negated || right.result.isFailOrDie => Some(right)
          case (_, _)                                                  => None
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
    message: ErrorMessage = ErrorMessage.choice("Succeeded", "Failed"),
    children: Option[Trace[Boolean]] = None,
    span: Option[Span] = None,
    parentSpan: Option[Span] = None,
    fullCode: Option[String] = None,
    location: Option[String] = None,
    annotations: Set[Annotation] = Set.empty
  ) extends Trace[A] {

    def renderResult: Any =
      result match {
        case Result.Fail           => "<FAIL>"
        case Result.Die(err)       => err
        case Result.Succeed(value) => value
      }

    def code: String =
      span.getOrElse(Span(0, 0)).substring(fullCode.getOrElse(""))
  }

  private[test] case class AndThen[A, +B](left: Trace[A], right: Trace[B]) extends Trace[B] {
    override def result: Result[B] = right.result
  }

  private[test] case class And(left: Trace[Boolean], right: Trace[Boolean]) extends Trace[Boolean] {
    override def result: Result[Boolean] = left.result.zipWith(right.result)(_ && _)
  }

  private[test] case class Or(left: Trace[Boolean], right: Trace[Boolean]) extends Trace[Boolean] {
    override def result: Result[Boolean] =
      (left.result, right.result) match {
        case (Result.Succeed(true), _) => Result.succeed(true)
        case (_, Result.Succeed(true)) => Result.succeed(true)
        case (a, b)                    => a.zipWith(b)(_ || _)
      }
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

  def boolean(value: Boolean)(message: ErrorMessage): Trace[Boolean] = Node(Result.succeed(value), message = message)

  def die(throwable: Throwable): Trace[Nothing] =
    Node(Result.die(throwable), message = ErrorMessage.throwable(throwable))

  object Halt {
    def unapply[A](trace: Trace[A]): Boolean =
      trace.result match {
        case Result.Fail => true
        case _           => false
      }
  }

  object Fail {
    def unapply[A](trace: Trace[A]): Option[Throwable] =
      trace.result match {
        case Result.Die(err) => Some(err)
        case _               => None
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
