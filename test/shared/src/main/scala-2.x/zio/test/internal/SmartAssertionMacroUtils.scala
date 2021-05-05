//package zio.test.internal
//
//import scala.reflect.macros.blackbox
//
//trait SmartAssertionMacroUtils {
//  val c: blackbox.Context
//  import c.universe._
//
//  private val Assertion = q"zio.test.Assertion"
//
//  case class Transformer[A, B](from: MethodDesc[A], f: A => B, to: MethodDesc[B]) {
//    def unapply(tree: c.Tree): Option[c.Tree] =
//      from.unapply(tree).map { a =>
//        val b = f(a)
//        to.apply(b)
//      }
//  }
//
//  object Matchers {
//    val rightGet = MethodDesc.select[Either[_, _]]("right", "get") orElse
//      MethodDesc.select[Either[_, _]]("toOption", "get")
//
//    val leftGet = MethodDesc.select[Either[_, _]]("left", "get") orElse
//      MethodDesc.select[Either[_, _]]("swap", "toOption", "get")
//
//    def isLeft(assertion: c.Tree): c.universe.Tree =
//      MethodDesc.select("isLeft").call.apply((Assertion, List(assertion)))
//
//    def isRight(assertion: c.Tree): c.universe.Tree =
//      MethodDesc.select("isRight").call.apply((Assertion, List(assertion)))
//
//    val isRight0 = MethodDesc
//      .select("isRight")
//      .call
//      .map[c.Tree](_._2.head)(t => (Assertion, List(t)))
//  }
//
//  sealed trait MethodDesc[A] { self =>
//    def unapply(tree: Tree): Option[A]
//    def apply(a: A): Tree
//    def map[B](to: A => B)(from: B => A): MethodDesc.XMap[A, B] =
//      MethodDesc.XMap[A, B](self, to, from)
//    def orElse(that: MethodDesc[A]): MethodDesc[A] =
//      MethodDesc.XOrElse[A](self, that)
//
//    def transform(that: MethodDesc[A]): Transformer[A, A]               = Transformer[A, A](self, identity, that)
//    def transform[B](that: MethodDesc[B])(f: A => B): Transformer[A, B] = Transformer(self, f, that)
//  }
//
//  object MethodDesc {
//    def any[T](implicit tpe: c.WeakTypeTag[T]): MethodDesc[Tree] = XWildcard[T](tpe)
//    def select[A](desc: MethodDesc[A], name: String): XSelect[A] = XSelect(desc, name)
//    def select[T: WeakTypeTag](name: String): XSelect[c.Tree]    = select(any[T], name)
//    def select[T: WeakTypeTag](name1: String, name2: String): XSelect[c.Tree] =
//      select(select(any[T], name1), name2)
//    def select[T: WeakTypeTag](name1: String, name2: String, name3: String): XSelect[c.Tree] =
//      select(select(select(any[T], name1), name2), name3)
//
//    case class XOrElse[A](lhs: MethodDesc[A], rhs: MethodDesc[A]) extends MethodDesc[A] {
//      override def unapply(tree: c.universe.Tree): Option[A] =
//        lhs.unapply(tree).orElse(rhs.unapply(tree))
//
//      override def apply(a: A): c.universe.Tree = lhs.apply(a)
//    }
//
//    case class XMap[A, B](desc: MethodDesc[A], to: A => B, from: B => A) extends MethodDesc[B] {
//      override def unapply(tree: c.universe.Tree): Option[B] =
//        desc.unapply(tree).map(to)
//      override def apply(a: B): c.universe.Tree =
//        desc.apply(from(a))
//    }
//
//    case class XWildcard[T](tpe: c.WeakTypeTag[T]) extends MethodDesc[Tree] {
//      override def unapply(tree: c.Tree): Option[c.Tree] =
//        Option.when(tree.tpe <:< tpe.tpe)(tree)
//
//      override def apply(a: c.Tree): c.Tree = a
//    }
//
//    case class XSelect[A](desc: MethodDesc[A], name: String) extends MethodDesc[A] {
//      def call: MethodDesc[(A, Seq[Tree])] = XApply(this)
//
//      override def unapply(tree: c.Tree): Option[A] =
//        tree match {
//          case q"${desc(lhs)}.$name0" if name0.toString == name => Some(lhs)
//          case _                                                => None
//        }
//
//      override def apply(a: A): c.Tree = a match {
//        case a =>
//          val lhs = desc.apply(a)
//          q"$lhs.${TermName(name)}"
//      }
//    }
//
//    case class XApply[A](desc: MethodDesc[A]) extends MethodDesc[(A, Seq[Tree])] {
//      override def unapply(tree: c.Tree): Option[(A, Seq[Tree])] = tree match {
//        case q"${desc(a)}(..$args)"       => Some(a, args)
//        case q"${desc(a)}[..$_](..$args)" => Some(a, args)
//        case _                            => None
//      }
//
//      override def apply(a: (A, Seq[c.Tree])): c.Tree = a match {
//        case (a, args) =>
//          val lhs = desc.apply(a)
//          q"$lhs(..$args)"
//      }
//    }
//  }
//}
