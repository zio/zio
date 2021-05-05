package zio.test.internal

import zio.test.internal.CrossVersionSmartAssertionMacroUtils

import scala.reflect.macros.blackbox

trait Scala2MacroUtils {
  val c: blackbox.Context
  import c.universe._

  object Cross extends CrossVersionSmartAssertionMacroUtils[c.Tree, c.Type] {
    override val AnyType: c.Type    = weakTypeOf[Any]
    override val EitherType: c.Type = weakTypeOf[Either[_, _]]

    override def Assertion: c.Tree = q"zio.test.Assertion"

    override def getTreeType(tree: c.Tree): c.Type = tree.tpe

    override def isSubtype(t1: c.Type, t2: c.Type): Boolean = t1 <:< t2

    case class UnapplyF[A, B](f: A => Option[B]) {
      def unapply(a: A): Option[B] = f(a)
    }

    override def unapplySelect[A](desc: c.Tree => Option[A], name: String, tree: c.Tree): Option[A] = {
      val unApplyDesc = UnapplyF(desc)

      tree match {
        case q"${unApplyDesc(lhs)}.$name0" if name0.toString == name => Some(lhs)
        case _                                                       => None
      }
    }

    override def applySelect[A](desc: A => c.Tree, a: A, name: String): c.Tree = {
      val lhs = desc.apply(a)
      q"$lhs.${TermName(name)}"
    }

    override def unapplyApply[A](desc: c.Tree => Option[A], tree: c.Tree): Option[(A, Seq[c.Tree])] = {
      val unApplyDesc = UnapplyF(desc)
      tree match {
        case q"${unApplyDesc(a)}(..$args)"       => Some(a, args)
        case q"${unApplyDesc(a)}[..$_](..$args)" => Some(a, args)
        case _                                   => None
      }
    }

    override def applyApply[A](desc: A => Tree, a: (A, Seq[Tree])): Tree =
      a match {
        case (a, args) =>
          val lhs = desc.apply(a)
          q"$lhs(..$args)"
      }
  }
}
