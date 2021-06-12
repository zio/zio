package zio.test

import zio.Chunk
import zio.test.render.LogLine._
import zio.test.render._

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

    deleted.map(error("- ") +: _) ++ inserted.map(info("+ ") +: _)
  }

  private def renderColor(actions: Chunk[Action]) =
    actions
      .foldLeft(Line.empty) { (acc, action) =>
        action match {
          case Action.Delete(s) =>
            acc :+ error(s).underlined
          case Action.Insert(s) =>
            acc :+ info(s).underlined
          case Action.Keep(s) =>
            acc :+ fr(s)
        }
      }
      .toMessage
}
