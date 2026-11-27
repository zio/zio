package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.TestArrow.Span

import scala.annotation.tailrec

/**
 * The record of running a [[TestArrow]]: a tree of nodes, one per step, each
 * with its [[Result]], message and source code, joined by `>>>`, `&&`, `||` and
 * `!`. The test report reads it to explain a failure.
 */
sealed trait TestTrace[+A] { self =>

  /**
   * Returns the values of the nodes of this trace whose result is `Succeed`,
   * from left to right, without the nested children of a node.
   */
  def values: List[Any] =
    self.asInstanceOf[TestTrace[Any]] match {
      case TestTrace.Node(Result.Succeed(value), _, _, _, _, _, _, _, _, _, _) =>
        List(value)
      case TestTrace.Node(_, _, _, _, _, _, _, _, _, _, _) =>
        List()
      case TestTrace.AndThen(left, right) =>
        left.values ++ right.values
      case TestTrace.And(left, right) =>
        left.values ++ right.values
      case TestTrace.Or(left, right) =>
        left.values ++ right.values
      case TestTrace.Not(trace) =>
        trace.values
    }

  /**
   * Returns `true` when this Boolean trace fails.
   */
  def isFailure(implicit ev: A <:< Boolean): Boolean = !isSuccess

  /**
   * Returns `true` when this Boolean trace succeeds, that is when
   * `TestTrace.prune` finds nothing that explains a failure.
   */
  def isSuccess(implicit ev: A <:< Boolean): Boolean =
    TestTrace.prune(self.asInstanceOf[TestTrace[Boolean]], false).isEmpty

  /**
   * Returns `true` when a node of this trace died with an exception; the nested
   * children of a node are not checked.
   */
  def isDie: Boolean =
    self.asInstanceOf[TestTrace[?]] match {
      case TestTrace.Node(Result.Die(_), _, _, _, _, _, _, _, _, _, _) => true
      case TestTrace.Node(_, _, _, _, _, _, _, _, _, _, _)             => false
      case TestTrace.AndThen(left, right)                              => left.isDie || right.isDie
      case TestTrace.And(left, right)                                  => left.isDie || right.isDie
      case TestTrace.Or(left, right)                                   => left.isDie || right.isDie
      case TestTrace.Not(trace)                                        => trace.isDie
    }

  /**
   * Sets `span` on this node, or on the last node of a `>>>` chain. A trace
   * joined by `&&`, `||` or `!`, and a `None` argument, leave the trace
   * unchanged.
   */
  final def withSpan(span: Option[Span] = None): TestTrace[A] = if (span.isDefined) {
    self match {
      case node: TestTrace.Node[?]        => node.copy(span = span)
      case TestTrace.AndThen(left, right) => TestTrace.AndThen(left, right.withSpan(span))
      case zip                            => zip
    }
  } else {
    self
  }

  /**
   * Sets the parent span on every node of the tree that has none yet; the
   * nested children of a node are not changed. A `None` argument leaves the
   * trace unchanged.
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
   * Sets the location on every node in the tree, nested children included. A
   * `None` argument leaves the trace unchanged.
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
   * Sets `customLabel` on every node of the tree, nested children included. A
   * node that already has a label keeps it, and so do its children. A `None`
   * argument leaves the trace unchanged.
   */
  def withCustomLabel(customLabel: Option[String]): TestTrace[A] =
    if (customLabel.isDefined) {
      self match {
        case node: TestTrace.Node[A] =>
          if (node.customLabel.isDefined) node
          else node.copy(customLabel = customLabel, children = node.children.map(_.withCustomLabel(customLabel)))
        case TestTrace.AndThen(left, right) =>
          TestTrace.AndThen(left.withCustomLabel(customLabel), right.withCustomLabel(customLabel))
        case and: TestTrace.And =>
          TestTrace
            .And(and.left.withCustomLabel(customLabel), and.right.withCustomLabel(customLabel))
            .asInstanceOf[TestTrace[A]]
        case or: TestTrace.Or =>
          TestTrace
            .Or(or.left.withCustomLabel(customLabel), or.right.withCustomLabel(customLabel))
            .asInstanceOf[TestTrace[A]]
        case not: TestTrace.Not =>
          TestTrace.Not(not.trace.withCustomLabel(customLabel)).asInstanceOf[TestTrace[A]]
      }
    } else {
      self
    }

  /**
   * Sets the code on every node in the tree, nested children included. A `None`
   * argument leaves the trace unchanged.
   */
  final def withCode(fullCode: Option[String]): TestTrace[A] = if (fullCode.isDefined) {
    self match {
      case node: TestTrace.Node[_] =>
        node.copy(fullCode = fullCode, children = node.children.map(_.withCode(fullCode)))
      case TestTrace.AndThen(left, right) =>
        TestTrace.AndThen(left.withCode(fullCode), right.withCode(fullCode))
      case and: TestTrace.And =>
        TestTrace.And(and.left.withCode(fullCode), and.right.withCode(fullCode)).asInstanceOf[TestTrace[A]]
      case or: TestTrace.Or =>
        TestTrace.Or(or.left.withCode(fullCode), or.right.withCode(fullCode)).asInstanceOf[TestTrace[A]]
      case not: TestTrace.Not =>
        TestTrace.Not(not.trace.withCode(fullCode)).asInstanceOf[TestTrace[A]]
    }
  } else {
    self
  }

  /**
   * Sets the source code of the whole assertion on every node in the tree,
   * nested children included. A `None` argument clears it.
   */
  final def withCompleteCode(completeCode: Option[String]): TestTrace[A] =
    self match {
      case node: TestTrace.Node[_] =>
        node.copy(completeCode = completeCode, children = node.children.map(_.withCompleteCode(completeCode)))
      case TestTrace.AndThen(left, right) =>
        TestTrace.AndThen(left.withCompleteCode(completeCode), right.withCompleteCode(completeCode))
      case and: TestTrace.And =>
        TestTrace
          .And(and.left.withCompleteCode(completeCode), and.right.withCompleteCode(completeCode))
          .asInstanceOf[TestTrace[A]]
      case or: TestTrace.Or =>
        TestTrace
          .Or(or.left.withCompleteCode(completeCode), or.right.withCompleteCode(completeCode))
          .asInstanceOf[TestTrace[A]]
      case not: TestTrace.Not =>
        TestTrace.Not(not.trace.withCompleteCode(completeCode)).asInstanceOf[TestTrace[A]]
    }

  /**
   * Sets `genFailureDetails` on every node of the tree, nested children
   * included, that has none yet.
   */
  final def withGenFailureDetails(genFailureDetails: Option[GenFailureDetails]): TestTrace[A] =
    self match {
      case node: TestTrace.Node[_] =>
        node.copy(
          genFailureDetails = node.genFailureDetails.orElse(genFailureDetails),
          children = node.children.map(_.withGenFailureDetails(genFailureDetails))
        )
      case TestTrace.AndThen(left, right) =>
        TestTrace.AndThen(left.withGenFailureDetails(genFailureDetails), right.withGenFailureDetails(genFailureDetails))
      case and: TestTrace.And =>
        TestTrace
          .And(and.left.withGenFailureDetails(genFailureDetails), and.right.withGenFailureDetails(genFailureDetails))
          .asInstanceOf[TestTrace[A]]
      case or: TestTrace.Or =>
        TestTrace
          .Or(or.left.withGenFailureDetails(genFailureDetails), or.right.withGenFailureDetails(genFailureDetails))
          .asInstanceOf[TestTrace[A]]
      case not: TestTrace.Not =>
        TestTrace.Not(not.trace.withGenFailureDetails(genFailureDetails)).asInstanceOf[TestTrace[A]]
    }

  /**
   * Returns the first generator failure details set on a node, from left to
   * right; the nested children of a node are not searched.
   */
  def getGenFailureDetails: Option[GenFailureDetails] =
    self match {
      case node: TestTrace.Node[_]        => node.genFailureDetails
      case TestTrace.AndThen(left, right) => left.getGenFailureDetails.orElse(right.getGenFailureDetails)
      case and: TestTrace.And             => and.left.getGenFailureDetails.orElse(and.right.getGenFailureDetails)
      case or: TestTrace.Or               => or.left.getGenFailureDetails.orElse(or.right.getGenFailureDetails)
      case not: TestTrace.Not             => not.trace.getGenFailureDetails
    }

  /**
   * Adds the annotations to this node, or to the last node of a `>>>` chain. A
   * trace joined by `&&`, `||` or `!` is returned unchanged.
   */
  @tailrec
  final def annotate(annotation: TestTrace.Annotation*): TestTrace[A] =
    self match {
      case node: TestTrace.Node[_]     => node.copy(annotations = node.annotations ++ annotation.toSet)
      case TestTrace.AndThen(_, right) => right.annotate(annotation: _*)
      case zip                         => zip
    }

  /**
   * Logical implication: the same as `!this || that`.
   */
  final def implies(that: TestTrace[Boolean])(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    !self || that

  /**
   * Logical implication; the same as `implies`.
   */
  final def ==>(that: TestTrace[Boolean])(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    implies(that)

  /**
   * Logical equivalence: both traces succeed or both fail. When either trace is
   * a single node that died, the result fails.
   */
  final def <==>(that: TestTrace[Boolean])(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    self ==> that && that ==> self.asInstanceOf[TestTrace[Boolean]]

  /**
   * Joins two Boolean traces by logical and.
   */
  final def &&(that: TestTrace[Boolean])(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    TestTrace.And(self.asInstanceOf[TestTrace[Boolean]], that)

  /**
   * Joins two Boolean traces by logical or.
   */
  final def ||(that: TestTrace[Boolean])(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    TestTrace.Or(self.asInstanceOf[TestTrace[Boolean]], that)

  /**
   * Negates a Boolean trace by logical not.
   */
  final def unary_!(implicit ev: A <:< Boolean): TestTrace[Boolean] =
    TestTrace.Not(self.asInstanceOf[TestTrace[Boolean]])

  /**
   * Joins this trace and `that`, the trace of the next step, in sequence. The
   * result of the joined trace is the result of `that`.
   */
  final def >>>[B](that: TestTrace[B]): TestTrace[B] =
    TestTrace.AndThen(self, that)

  /**
   * The result of this trace: the result of its node, of the last step of a
   * `>>>` chain, the two sides of `&&` and `||` combined, or for `!` the
   * negated result of the inner trace.
   */
  def result: Result[A]
}

/**
 * Constructors of [[TestTrace]] values.
 */
object TestTrace {

  /**
   * Returns the part of `trace` that explains why it failed, or `None` when it
   * succeeded. With `negated = true` the trace is read as negated, so the nodes
   * that succeeded are kept instead, and a negated `&&` is kept only when both
   * of its sides are kept. A node that died is kept, but inside a negated `&&`
   * whose other side is not kept it is dropped together with that `&&`, so `!(a
   * && b)` can succeed even though `a` threw.
   */
  def prune(trace: TestTrace[Boolean], negated: Boolean): Option[TestTrace[Boolean]] =
    trace match {
      case node @ TestTrace.Node(Result.Succeed(bool), _, _, _, _, _, _, _, _, _, _) =>
        if (bool == negated) {
          Some(node.copy(children = node.children.flatMap(prune(_, negated))))
        } else
          None

      case TestTrace.Node(Result.Fail, _, _, _, _, _, _, _, _, _, _) =>
        if (negated) None else Some(trace)

      case TestTrace.Node(Result.Die(_), _, _, _, _, _, _, _, _, _, _) =>
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
          case (None, Some(right)) if !negated => Some(right)
          case (Some(left), None) if !negated  => Some(left)
          case (Some(left), Some(right))       => Some(TestTrace.And(left, right))
          case _                               => None
        }

      case or: TestTrace.Or =>
        (prune(or.left, negated), prune(or.right, negated)) match {
          case (Some(left), Some(right))                  => Some(TestTrace.Or(left, right))
          case (Some(left), _) if negated || left.isDie   => Some(left)
          case (_, Some(right)) if negated || right.isDie => Some(right)
          case (_, _)                                     => None
        }

      case not: TestTrace.Not =>
        prune(not.trace, !negated)
    }

  /**
   * A marker on a node of a trace.
   */
  sealed trait Annotation

  /**
   * The annotations of a trace node.
   */
  object Annotation {

    /**
     * Marks a node that passes on an exception from an earlier step; `prune`
     * reports the earlier step instead.
     */
    case object Rethrow extends Annotation {

      /**
       * Returns `true` when `value` contains `Rethrow`.
       */
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
    annotations: Set[Annotation] = Set.empty,
    completeCode: Option[String] = None,
    customLabel: Option[String] = None,
    genFailureDetails: Option[GenFailureDetails] = None
  ) extends TestTrace[A] {

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

  /**
   * A failed trace with the default message.
   */
  val fail: TestTrace[Nothing] = Node(Result.Fail)

  /**
   * A failed trace with `message`.
   */
  def fail(message: String): TestTrace[Nothing] = Node(Result.Fail, message = ErrorMessage.text(message))

  /**
   * A failed trace with `message`.
   */
  def fail(message: ErrorMessage): TestTrace[Nothing] = Node(Result.Fail, message = message)

  /**
   * A successful trace holding `value`.
   */
  def succeed[A](value: A): TestTrace[A] = Node(Result.succeed(value))

  /**
   * A successful trace holding the content of `value`, or a failed one with
   * `message` when `value` is `None`.
   */
  def option[A](value: Option[A])(message: ErrorMessage): TestTrace[A] = {
    val result = value.fold[Result[A]](Result.Fail)(a => Result.succeed(a))
    Node[A](result, message = message)
  }

  /**
   * A trace holding `value` with `message`. It counts as a failure when `value`
   * is `false`.
   */
  def boolean(value: Boolean)(message: ErrorMessage): TestTrace[Boolean] =
    Node(Result.succeed(value), message = message)

  /**
   * A trace that died with `throwable`.
   */
  def die(throwable: Throwable): TestTrace[Nothing] =
    Node(Result.die(throwable), message = ErrorMessage.throwable(throwable))

}
