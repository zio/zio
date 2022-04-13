package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.TestArrow.Span

import scala.annotation.tailrec

sealed trait TestTrace[+A] { self =>

  def isFailure: Boolean = !isSuccess

  def isSuccess: Boolean = self.result match {
    case Result.Succeed(true) => true
    case _                    => false
  }

  /**
   * Apply the metadata to the rightmost node in the trace.
   */
  final def withSpan(span: Option[Span] = None): TestTrace[A] = if (span.isDefined) {
    self match {
      case node: TestTrace.Node[_]        => node.copy(span = span)
      case TestTrace.AndThen(left, right) => TestTrace.AndThen(left, right.withSpan(span))
      case zip                            => zip
    }
  } else {
    self
  }

  /**
   * Apply the parent span to every node in the tree.
   */
  def withParentSpan(span: Option[Span]): TestTrace[A] = if (span.isDefined) {
    self match {
      case node: TestTrace.Node[_] =>
        node.copy(parentSpan = node.parentSpan.orElse(span))
      case TestTrace.AndThen(left, right) =>
        TestTrace.AndThen(left.withParentSpan(span), right.withParentSpan(span))
      case and: TestTrace.And =>
        TestTrace.And(and.left.withParentSpan(span), and.right.withParentSpan(span)).asInstanceOf[TestTrace[A]]
      case or: TestTrace.Or =>
        TestTrace.Or(or.left.withParentSpan(span), or.right.withParentSpan(span)).asInstanceOf[TestTrace[A]]
      case not: TestTrace.Not =>
        TestTrace.Not(not.trace.withParentSpan(span)).asInstanceOf[TestTrace[A]]
    }
  } else {
    self
  }

  /**
   * Apply the location to every node in the tree.
   */
  def withLocation(location: Option[String]): TestTrace[A] = if (location.isDefined) {
    self match {
      case node: TestTrace.Node[_] =>
        node.copy(location = location, children = node.children.map(_.withLocation(location)))
      case TestTrace.AndThen(left, right) =>
        TestTrace.AndThen(left.withLocation(location), right.withLocation(location))
      case and: TestTrace.And =>
        TestTrace.And(and.left.withLocation(location), and.right.withLocation(location)).asInstanceOf[TestTrace[A]]
      case or: TestTrace.Or =>
        TestTrace.Or(or.left.withLocation(location), or.right.withLocation(location)).asInstanceOf[TestTrace[A]]
      case not: TestTrace.Not =>
        TestTrace.Not(not.trace.withLocation(location)).asInstanceOf[TestTrace[A]]
    }
  } else {
    self
  }

  /**
   * Apply the code to every node in the tree.
   */
  final def withCode(code: Option[String]): TestTrace[A] =
    self match {
      case node: TestTrace.Node[_] =>
        node.copy(fullCode = code, children = node.children.map(_.withCode(code)))
      case TestTrace.AndThen(left, right) =>
        TestTrace.AndThen(left.withCode(code), right.withCode(code))
      case and: TestTrace.And =>
        TestTrace.And(and.left.withCode(code), and.right.withCode(code)).asInstanceOf[TestTrace[A]]
      case or: TestTrace.Or =>
        TestTrace.Or(or.left.withCode(code), or.right.withCode(code)).asInstanceOf[TestTrace[A]]
      case not: TestTrace.Not =>
        TestTrace.Not(not.trace.withCode(code)).asInstanceOf[TestTrace[A]]
    }

  @tailrec
  final def annotate(annotation: TestTrace.Annotation*): TestTrace[A] =
    self match {
      case node: TestTrace.Node[_]     => node.copy(annotations = node.annotations ++ annotation.toSet)
      case TestTrace.AndThen(_, right) => right.annotate(annotation: _*)
      case zip                         => zip
    }

  final def implies(that: TestTrace[Boolean])(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    !self || that

  final def ==>(that: TestTrace[Boolean])(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    implies(that)

  final def <==>(that: TestTrace[Boolean])(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    self ==> that && that ==> self.asInstanceOf[TestTrace[Boolean]]

  final def &&(that: TestTrace[Boolean])(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    TestTrace.And(self.asInstanceOf[TestTrace[Boolean]], that)

  final def ||(that: TestTrace[Boolean])(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    TestTrace.Or(self.asInstanceOf[TestTrace[Boolean]], that)

  final def unary_!(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    TestTrace.Not(self.asInstanceOf[TestTrace[Boolean]])

  final def >>>[B](that: TestTrace[B]): TestTrace[B] =
    TestTrace.AndThen(self, that)

  def result: Result[A]
}

object TestTrace {

  /**
   * Prune all non-failures from the trace.
   */
  def prune(trace: TestTrace[Boolean], negated: Boolean): Option[TestTrace[Boolean]] =
    trace match {
      case node @ TestTrace.Node(Result.Succeed(bool), _, _, _, _, _, _, _) if bool == negated =>
        Some(node.copy(children = node.children.flatMap(prune(_, negated))))

      case TestTrace.Node(Result.Succeed(_), _, _, _, _, _, _, _) =>
        None

      case TestTrace.Node(Result.Die(_) | Result.Fail, _, _, _, _, _, _, _) =>
        Some(trace)

      case TestTrace.AndThen(left, node: TestTrace.Node[_])
          if node.annotations.contains(TestTrace.Annotation.Rethrow) =>
        prune(left.asInstanceOf[TestTrace[Boolean]], negated)

      case TestTrace.AndThen(left, right) =>
        prune(right, negated).map { next =>
          TestTrace.AndThen(left, next)
        }

      case and: TestTrace.And =>
        (prune(and.left, negated), prune(and.right, negated)) match {
          case (None, right)             => right
          case (left, None)              => left
          case (Some(left), Some(right)) => Some(TestTrace.And(left, right))
        }

      case or: TestTrace.Or =>
        (prune(or.left, negated), prune(or.right, negated)) match {
          case (Some(left), Some(right))                               => Some(TestTrace.Or(left, right))
          case (Some(left), _) if negated || left.result.isFailOrDie   => Some(left)
          case (_, Some(right)) if negated || right.result.isFailOrDie => Some(right)
          case (_, _)                                                  => None
        }

      case not: TestTrace.Not =>
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
    children: Option[TestTrace[Boolean]] = None,
    span: Option[Span] = None,
    parentSpan: Option[Span] = None,
    fullCode: Option[String] = None,
    location: Option[String] = None,
    annotations: Set[Annotation] = Set.empty
  ) extends TestTrace[A] {

    def renderResult: Any =
      result match {
        case Result.Fail           => "<FAIL>"
        case Result.Die(err)       => err
        case Result.Succeed(value) => value
      }

    def code: String =
      span.getOrElse(Span(0, 0)).substring(fullCode.getOrElse(""))
  }

  private[test] case class AndThen[A, +B](left: TestTrace[A], right: TestTrace[B]) extends TestTrace[B] {
    override def result: Result[B] = right.result
  }

  private[test] case class And(left: TestTrace[Boolean], right: TestTrace[Boolean]) extends TestTrace[Boolean] {
    override def result: Result[Boolean] = left.result.zipWith(right.result)(_ && _)
  }

  private[test] case class Or(left: TestTrace[Boolean], right: TestTrace[Boolean]) extends TestTrace[Boolean] {
    override def result: Result[Boolean] = left.result.zipWith(right.result)(_ || _)
  }

  private[test] case class Not(trace: TestTrace[Boolean]) extends TestTrace[Boolean] {
    override def result: Result[Boolean] = trace.result match {
      case Result.Succeed(value) => Result.Succeed(!value)
      case other                 => other
    }
  }

  def fail: TestTrace[Nothing]                        = Node(Result.Fail)
  def fail(message: String): TestTrace[Nothing]       = Node(Result.Fail, message = ErrorMessage.text(message))
  def fail(message: ErrorMessage): TestTrace[Nothing] = Node(Result.Fail, message = message)
  def succeed[A](value: A): TestTrace[A]              = Node(Result.succeed(value))

  def boolean(value: Boolean)(message: ErrorMessage): TestTrace[Boolean] =
    Node(Result.succeed(value), message = message)

  def die(throwable: Throwable): TestTrace[Nothing] =
    Node(Result.die(throwable), message = ErrorMessage.throwable(throwable))

  object Halt {
    def unapply[A](trace: TestTrace[A]): Boolean =
      trace.result match {
        case Result.Fail => true
        case _           => false
      }
  }

  object Fail {
    def unapply[A](trace: TestTrace[A]): Option[Throwable] =
      trace.result match {
        case Result.Die(err) => Some(err)
        case _               => None
      }
  }

  object Succeed {
    def unapply[A](trace: TestTrace[A]): Option[A] =
      trace.result match {
        case Result.Succeed(value) => Some(value)
        case _                     => None
      }
  }
}
