package zio.test.internal

trait CrossVersionSmartAssertionMacroUtils[Tree, Tpe] {
  // q"zio.test.Assertion"
  def Assertion: Tree
  val AnyType: Tpe
  val EitherType: Tpe

  def getTreeType(tree: Tree): Tpe
  def isSubtype(t1: Tpe, t2: Tpe): Boolean

  def unapplySelect[A](desc: Tree => Option[A], name: String, tree: Tree): Option[A]
  def applySelect[A](desc: A => Tree, a: A, name: String): Tree

  def unapplyApply[A](desc: Tree => Option[A], tree: Tree): Option[(A, Seq[Tree])]
  def applyApply[A](desc: A => Tree, a: (A, Seq[Tree])): Tree

  object Matchers {
    def rightGet: MethodDesc[Tree] =
      MethodDesc.select(EitherType, "right", "get") orElse
        MethodDesc.select(EitherType, "toOption", "get")

    def leftGet: MethodDesc[Tree] =
      MethodDesc.select(EitherType, "left", "get") orElse
        MethodDesc.select(EitherType, "swap", "toOption", "get")

    def isLeft(assertion: Tree): Tree =
      MethodDesc.select(EitherType, "isLeft").call.apply((Assertion, List(assertion)))

    def isRight(assertion: Tree): Tree =
      MethodDesc.select(EitherType, "isRight").call.apply((Assertion, List(assertion)))
  }

  sealed trait MethodDesc[A] { self =>
    def unapply(tree: Tree): Option[A]
    def apply(a: A): Tree
    def map[B](to: A => B)(from: B => A): MethodDesc.XMap[A, B] =
      MethodDesc.XMap[A, B](self, to, from)
    def orElse(that: MethodDesc[A]): MethodDesc[A] =
      MethodDesc.XOrElse[A](self, that)
  }

  object MethodDesc {
    def any(tpe: Tpe = AnyType): MethodDesc[Tree] = XWildcard(tpe)

    def select[A](desc: MethodDesc[A], name: String): XSelect[A] = XSelect(desc, name)

    def select(tpe: Tpe, name: String): XSelect[Tree] = select(any(tpe), name)

    def select(tpe: Tpe, name1: String, name2: String): XSelect[Tree] =
      select(select(any(tpe), name1), name2)

    def select[T](tpe: Tpe, name1: String, name2: String, name3: String): XSelect[Tree] =
      select(select(select(any(tpe), name1), name2), name3)

    case class XOrElse[A](lhs: MethodDesc[A], rhs: MethodDesc[A]) extends MethodDesc[A] {
      override def unapply(tree: Tree): Option[A] =
        lhs.unapply(tree).orElse(rhs.unapply(tree))

      override def apply(a: A): Tree = lhs.apply(a)
    }

    case class XMap[A, B](desc: MethodDesc[A], to: A => B, from: B => A) extends MethodDesc[B] {
      override def unapply(tree: Tree): Option[B] =
        desc.unapply(tree).map(to)
      override def apply(a: B): Tree =
        desc.apply(from(a))
    }

    case class XWildcard(tpe: Tpe) extends MethodDesc[Tree] {
      override def unapply(tree: Tree): Option[Tree] =
        Option.when(isSubtype(getTreeType(tree), tpe))(tree)

      override def apply(a: Tree): Tree = a
    }

    case class XSelect[A](desc: MethodDesc[A], name: String) extends MethodDesc[A] {
      def call: MethodDesc[(A, Seq[Tree])] = XApply(this)

      override def unapply(tree: Tree): Option[A] =
        unapplySelect(desc.unapply, name, tree)

      override def apply(a: A): Tree =
        applySelect(desc.apply, a, name)
    }

    case class XApply[A](desc: MethodDesc[A]) extends MethodDesc[(A, Seq[Tree])] {
      override def unapply(tree: Tree): Option[(A, Seq[Tree])] =
        unapplyApply(desc.unapply, tree)

      override def apply(a: (A, Seq[Tree])): Tree =
        applyApply(desc.apply, a)
    }
  }
}
