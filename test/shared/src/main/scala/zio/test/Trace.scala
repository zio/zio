package zio.test

import zio.Chunk
import zio.test.Assert.Span
import zio.test.ConsoleUtils.blue

import scala.annotation.tailrec

sealed trait Trace[+A] { self =>

  def isSuccess = self.result match {
    case Result.Succeed(true) => true
    case _                    => false
  }

  /**
   * Apply the metadata to the rightmost node in the trace.
   */
  final def withSpan(span: Option[Span] = None): Trace[A] =
    self match {
      case node: Trace.Node[_]        => node.copy(span = span)
      case Trace.AndThen(left, right) => Trace.AndThen(left, right.withSpan(span))
      case zip                        => zip
    }

  /**
   * Apply the code to every node in the tree.
   */
  final def withCode(code: Option[String]): Trace[A] =
    self match {
      case node: Trace.Node[_] => node.copy(fullCode = code)
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

  final def &&[B](that: Trace[Boolean])(implicit ev: A <:< Boolean): Trace[Boolean] =
    Trace.And(ev.liftCo(self), that)

  final def ||[B](that: Trace[Boolean])(implicit ev: A <:< Boolean): Trace[Boolean] =
    Trace.Or(ev.liftCo(self), that)

  final def unary_![B](implicit ev: A <:< Boolean): Trace[Boolean] =
    Trace.Not(ev.liftCo(self))

  final def >>>[B](that: Trace[B]): Trace[B] =
    Trace.AndThen(self, that)

  def result: Result[A]
}

object Trace {

  def prune(trace: Trace[Boolean], negated: Boolean): Option[Trace[Boolean]] =
    trace match {
      case Trace.Node(Result.Succeed(bool), _, _, _, _) if bool == negated =>
        Some(trace)

      case Trace.Node(Result.Succeed(_), _, _, _, _) =>
        None

      case Trace.Node(Result.Die(_) | Result.Fail, _, _, _, _) =>
        Some(trace)

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
          case (Some(left), Some(right))   => Some(Trace.Or(left, right))
          case (Some(left), _) if negated  => Some(left)
          case (_, Some(right)) if negated => Some(right)
          case (_, _)                      => None
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
    // child: Option[Node] = None,
    span: Option[Span] = None,
    fullCode: Option[String] = None,
    annotations: Set[Annotation] = Set.empty
  ) extends Trace[A] {

    def renderResult: String =
      result match {
        case Result.Fail           => "<FAIL>"
        case Result.Die(err)       => err.toString
        case Result.Succeed(value) => value.toString
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
    override def result: Result[Boolean] = left.result.zipWith(right.result)(_ || _)
  }

  private[test] case class Not(trace: Trace[Boolean]) extends Trace[Boolean] {
    override def result: Result[Boolean] = trace.result match {
      case Result.Succeed(value) => Result.Succeed(!value)
      case other                 => other
    }
  }

  def halt: Trace[Nothing]                                           = Node(Result.Fail)
  def halt(message: String): Trace[Nothing]                          = Node(Result.Fail, message = ErrorMessage.text(message))
  def halt(message: ErrorMessage): Trace[Nothing]                    = Node(Result.Fail, message = message)
  def succeed[A](value: A): Trace[A]                                 = Node(Result.succeed(value))
  def boolean(value: Boolean)(message: ErrorMessage): Trace[Boolean] = Node(Result.succeed(value), message = message)
  def fail(throwable: Throwable): Trace[Nothing]                     = Node(Result.die(throwable))

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
