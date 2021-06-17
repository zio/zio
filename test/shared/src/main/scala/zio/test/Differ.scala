package zio.test

import zio.Chunk
import zio.test.FailureRenderer.FailureMessage.{Fragment, Line, Message}
import zio.test.FailureRenderer._

trait OptionalImplicit[A] {
  def value: Option[A]
}

object OptionalImplicit extends LowPriOptionalImplicit {
  def apply[A: OptionalImplicit]: Option[A] = implicitly[OptionalImplicit[A]].value

  implicit def some[A](implicit instance: A): OptionalImplicit[A] = new OptionalImplicit[A] {
    val value: Option[A] = Some(instance)
  }
}

trait LowPriOptionalImplicit {
  implicit def none[A]: OptionalImplicit[A] = new OptionalImplicit[A] {
    val value: Option[A] = None
  }
}

trait Diff[A] {
  def diff(lhs: A, rhs: A): Message
}

object Diff {
  implicit def stringDiff: Diff[String] = (lhs: String, rhs: String) => {
    val actions = MyersDiff.diff(lhs, rhs).actions

    val deleted = renderColor(actions.filter {
      case _: Action.Insert => false
      case _                => true
    })

    val inserted = renderColor(actions.filter {
      case _: Action.Delete => false
      case _                => true
    })

    deleted.map(red("- ") +: _) ++ inserted.map(green("+ ") +: _)
  }

  private def renderColor(actions: Chunk[Action]) =
    actions
      .foldLeft(Line(Vector(Fragment(scala.Console.RESET)))) { (acc, action) =>
        action match {
          case Action.Delete(s) =>
            acc :+ redUnderlined(s)
          case Action.Insert(s) =>
            acc :+ greenUnderlined(s)
          case Action.Keep(s) =>
            acc :+ Fragment(s)
        }
      }
      .toMessage
}
