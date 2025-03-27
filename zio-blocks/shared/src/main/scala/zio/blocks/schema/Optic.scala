package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._

import scala.collection.immutable.ArraySeq

sealed trait Optic[F[_, _], S, A] { self =>
  def structure: Reflect[F, S]

  def focus: Reflect[F, A]

  // Compose this optic with a lens:
  def apply[B](that: Lens[F, A, B]): Optic[F, S, B]

  // Compose this optic with a prism:
  def apply[B <: A](that: Prism[F, A, B]): Optic[F, S, B]

  // Compose this optic with an optional:
  def apply[B](that: Optional[F, A, B]): Optic[F, S, B]

  // Compose this optic with a traversal:
  def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B]

  def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optic[G, S, A]

  def noBinding: Optic[NoBinding, S, A]

  final def list[B](implicit ev: A <:< List[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.List

    val list = self.asSub[List[B]]
    list.focus match {
      case List(element) => list(Traversal.listValues(element))
      case _             => sys.error("FIXME - Not a list")
    }
  }

  final def vector[B](implicit ev: A <:< Vector[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.Vector

    val vector = self.asSub[Vector[B]]
    vector.focus match {
      case Vector(element) => vector(Traversal.vectorValues(element))
      case _               => sys.error("FIXME - Not a vector")
    }
  }

  final def set[B](implicit ev: A <:< Set[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.Set

    val set = self.asSub[Set[B]]
    set.focus match {
      case Set(element) => set(Traversal.setValues(element))
      case _            => sys.error("FIXME - Not a set")
    }
  }

  final def array[B](implicit ev: A <:< Array[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.Array

    val array = self.asSub[Array[B]]
    array.focus match {
      case Array(element) => array(Traversal.arrayValues(element))
      case _              => sys.error("FIXME - Not an array")
    }
  }

  final def asSub[B](implicit ev: A <:< B): Optic[F, S, B] = self.asInstanceOf[Optic[F, S, B]]

  override def hashCode: Int = linearized.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: Optic[F, _, _] => other.linearized.equals(linearized)
    case _                     => false
  }

  private[schema] def linearized: ArraySeq[Leaf[F, _, _]]
}

object Optic {
  type Bound[S, A] = Optic[Binding, S, A]
}

private[schema] sealed trait Leaf[F[_, _], S, A] extends Optic[F, S, A]

sealed trait Lens[F[_, _], S, A] extends Optic[F, S, A] {
  def get(s: S)(implicit F: HasBinding[F]): A

  def set(s: S, a: A)(implicit F: HasBinding[F]): S

  def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S

  // Compose this lens with a lens:
  override def apply[B](that: Lens[F, A, B]): Lens[F, S, B] = Lens(this, that)

  // Compose this lens with a prism:
  override def apply[B <: A](that: Prism[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this lens with an optional:
  override def apply[B](that: Optional[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this lens with a traversal:
  override def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Lens[G, S, A]

  override def noBinding: Lens[NoBinding, S, A] = refineBinding(RefineBinding.noBinding())
}

object Lens {
  type Bound[S, A] = Lens[Binding, S, A]

  def apply[F[_, _], S, A](parent: Reflect.Record[F, S], child: Term[F, S, A]): Lens[F, S, A] = {
    require((parent ne null) && (child ne null))
    new LensImpl(ArraySeq(parent), ArraySeq(child))
  }

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Lens[F, T, A]): Lens[F, S, A] = {
    val u1 = first.asInstanceOf[LensImpl[F, S, A]]
    val u2 = second.asInstanceOf[LensImpl[F, S, A]]
    new LensImpl(u1.parents ++ u2.parents, u1.childs ++ u2.childs)
  }

  private case class LensImpl[F[_, _], S, A](
    parents: ArraySeq[Reflect.Record[F, S]],
    childs: ArraySeq[Term[F, S, A]]
  ) extends Lens[F, S, A]
      with Leaf[F, S, A] {
    private[this] var bindings: Array[LensBinding]  = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    private[this] def init(implicit F: HasBinding[F]): Unit = {
      var offset   = RegisterOffset.Zero
      val len      = parents.length
      val bindings = new Array[LensBinding](len)
      var i        = 0
      while (i < len) {
        val parent = parents(i)
        bindings(i) = new LensBinding(
          deconstructor = F.deconstructor(parent.recordBinding).asInstanceOf[Deconstructor[Any]],
          constructor = F.constructor(parent.recordBinding).asInstanceOf[Constructor[Any]],
          register = parent
            .registers(parent.fields.indexWhere {
              val childName = childs(i).name
              x => x.name == childName
            })
            .asInstanceOf[Register[Any]],
          offset = offset
        )
        offset = RegisterOffset.add(offset, parent.usedRegisters)
        i += 1
      }
      this.bindings = bindings
      this.usedRegisters = offset
    }

    override def get(s: S)(implicit F: HasBinding[F]): A = {
      if (bindings eq null) init
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var i         = 0
      while (i < len) {
        val binding = bindings(i)
        val offset  = binding.offset
        binding.deconstructor.deconstruct(registers, offset, x)
        x = binding.register.get(registers, offset)
        i += 1
      }
      x.asInstanceOf[A]
    }

    override def set(s: S, a: A)(implicit F: HasBinding[F]): S = {
      if (bindings eq null) init
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var i         = 0
      while (i < len) {
        val binding = bindings(i)
        val offset  = binding.offset
        binding.deconstructor.deconstruct(registers, offset, x)
        if (i < len) x = binding.register.get(registers, offset)
        i += 1
      }
      x = a
      while (i > 0) {
        i -= 1
        val binding = bindings(i)
        val offset  = binding.offset
        binding.register.set(registers, offset, x)
        x = binding.constructor.construct(registers, offset)
      }
      x.asInstanceOf[S]
    }

    override def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = {
      if (bindings eq null) init
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var i         = 0
      while (i < len) {
        val binding = bindings(i)
        val offset  = binding.offset
        binding.deconstructor.deconstruct(registers, offset, x)
        x = binding.register.get(registers, offset)
        i += 1
      }
      x = f(x.asInstanceOf[A])
      while (i > 0) {
        i -= 1
        val binding = bindings(i)
        val offset  = binding.offset
        binding.register.set(registers, offset, x)
        x = binding.constructor.construct(registers, offset)
      }
      x.asInstanceOf[S]
    }

    override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Lens[G, S, A] =
      new LensImpl(parents.map(_.refineBinding(f)), childs.map(_.refineBinding(f)))

    override def structure: Reflect[F, S] = parents(0)

    override def focus: Reflect[F, A] = childs(childs.length - 1).value

    override def hashCode: Int = parents.hashCode ^ childs.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: LensImpl[F, _, _] => other.parents.equals(parents) && other.childs.equals(childs)
      case _                        => false
    }

    override private[schema] def linearized: ArraySeq[Leaf[F, S, A]] = ArraySeq(this)
  }

  private case class LensBinding(
    deconstructor: Deconstructor[Any],
    constructor: Constructor[Any],
    register: Register[Any],
    offset: RegisterOffset
  )
}

sealed trait Prism[F[_, _], S, A <: S] extends Optic[F, S, A] {
  def getOption(s: S)(implicit F: HasBinding[F]): Option[A]

  def reverseGet(a: A): S

  // Compose this prism with a prism:
  override def apply[B <: A](that: Prism[F, A, B]): Prism[F, S, B] = Prism(this, that)

  // Compose this prism with a lens:
  override def apply[B](that: Lens[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this prism with an optional:
  override def apply[B](that: Optional[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this prism with a traversal:
  override def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Prism[G, S, A]

  override def noBinding: Prism[NoBinding, S, A] = refineBinding(RefineBinding.noBinding())
}

object Prism {
  type Bound[S, A <: S] = Prism[Binding, S, A]

  def apply[F[_, _], S, A <: S](parent: Reflect.Variant[F, S], child: Term[F, S, A]): Prism[F, S, A] = {
    require((parent ne null) && (child ne null))
    new PrismImpl(parent, parent, child)
  }

  def apply[F[_, _], S, T <: S, A <: T](first: Prism[F, S, T], second: Prism[F, T, A]): Prism[F, S, A] = {
    val u1 = first.asInstanceOf[PrismImpl[F, _, _, _]]
    val u2 = second.asInstanceOf[PrismImpl[F, _, _, _]]
    new PrismImpl(
      u1.structure.asInstanceOf[Reflect.Variant[F, S]],
      u2.parent.asInstanceOf[Reflect.Variant[F, T]],
      u2.child.asInstanceOf[Term[F, T, A]]
    )
  }

  private case class PrismImpl[F[_, _], S, T <: S, A <: T](
    structure: Reflect.Variant[F, S],
    parent: Reflect.Variant[F, T],
    child: Term[F, T, A]
  ) extends Prism[F, S, A]
      with Leaf[F, S, A] {
    private[this] var matcher: Matcher[A] = null

    private def init(implicit F: HasBinding[F]): Unit =
      matcher =
        F.matchers(parent.variantBinding).apply(parent.cases.indexWhere(_.name == child.name)).asInstanceOf[Matcher[A]]

    def focus: Reflect[F, A] = child.value

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = {
      if (matcher eq null) init
      matcher.downcastOption(s)
    }

    def reverseGet(a: A): S = a

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Prism[G, S, A] =
      new PrismImpl(structure.refineBinding(f), parent.refineBinding(f), child.refineBinding(f))

    override def hashCode: Int = structure.hashCode ^ child.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: PrismImpl[F, _, _, _] => other.structure.equals(structure) && other.child.equals(child)
      case _                            => false
    }

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = ArraySeq(this)
  }
}

sealed trait Optional[F[_, _], S, A] extends Optic[F, S, A] {
  def getOption(s: S)(implicit F: HasBinding[F]): Option[A]

  def set(s: S, a: A)(implicit F: HasBinding[F]): S

  def setOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S]

  def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S

  // Compose this optional with a lens:
  override def apply[B](that: Lens[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this optional with a prism:
  override def apply[B <: A](that: Prism[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this optional with an optional:
  override def apply[B](that: Optional[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this optional with a traversal:
  override def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A]

  override def noBinding: Optional[NoBinding, S, A] = refineBinding(RefineBinding.noBinding())
}

object Optional {
  type Bound[S, A] = Optional[Binding, S, A]

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Lens[F, T, A]): Optional[F, S, A] =
    new Optional.OptionalLens(first, second)

  def apply[F[_, _], S, T, A <: T](first: Optional[F, S, T], second: Prism[F, T, A]): Optional[F, S, A] =
    new Optional.OptionalPrism(first, second)

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] =
    new Optional.OptionalOptional(first, second)

  def apply[F[_, _], S, T, A <: T](first: Lens[F, S, T], second: Prism[F, T, A]): Optional[F, S, A] =
    new Optional.LensPrism(first, second)

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] =
    new Optional.LensOptional(first, second)

  def apply[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Lens[F, T, A]): Optional[F, S, A] =
    new Optional.PrismLens(first, second)

  def apply[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] =
    new Optional.PrismOptional(first, second)

  private case class LensPrism[F[_, _], S, T, A <: T](first: Lens[F, S, T], second: Prism[F, T, A])
      extends Optional[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = second.getOption(first.get(s))

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.set(s, second.reverseGet(a))

    def setOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] = new Some(first.set(s, second.reverseGet(a)))

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(
      s,
      t =>
        second.getOption(t) match {
          case Some(a) => second.reverseGet(f(a))
          case _       => t
        }
    )

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new LensPrism(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class LensOptional[F[_, _], S, T, A](first: Lens[F, S, T], second: Optional[F, T, A])
      extends Optional[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = second.getOption(first.get(s))

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.modify(s, second.set(_, a))

    def setOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] = second.setOption(first.get(s), a) match {
      case Some(t) => new Some(first.set(s, t))
      case _       => None
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(s, second.modify(_, f))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new LensOptional(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class PrismLens[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Lens[F, T, A])
      extends Optional[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).map(second.get)

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.getOption(s) match {
      case Some(x) => first.reverseGet(second.set(x, a))
      case _       => s
    }

    def setOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] = first.getOption(s) match {
      case Some(x) => new Some(first.reverseGet(second.set(x, a)))
      case _       => None
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.getOption(s) match {
      case Some(x) => first.reverseGet(second.modify(x, f))
      case _       => s
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new PrismLens(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class PrismOptional[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Optional[F, T, A])
      extends Optional[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).flatMap(second.getOption)

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.getOption(s) match {
      case Some(x) => first.reverseGet(second.set(x, a))
      case _       => s
    }

    def setOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] = first.getOption(s) match {
      case Some(x) => new Some(first.reverseGet(second.set(x, a)))
      case _       => None
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.getOption(s) match {
      case Some(x) => first.reverseGet(second.modify(x, f))
      case _       => s
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new PrismOptional(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class OptionalLens[F[_, _], S, T, A](first: Optional[F, S, T], second: Lens[F, T, A])
      extends Optional[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).map(second.get)

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.getOption(s) match {
      case Some(x) => first.set(s, second.set(x, a))
      case _       => s
    }

    def setOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] = first.getOption(s) match {
      case Some(x) => new Some(first.set(s, second.set(x, a)))
      case _       => None
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.getOption(s) match {
      case Some(x) => first.set(s, second.modify(x, f))
      case _       => s
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new OptionalLens(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class OptionalPrism[F[_, _], S, T, A <: T](first: Optional[F, S, T], second: Prism[F, T, A])
      extends Optional[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).flatMap(second.getOption)

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.set(s, second.reverseGet(a))

    def setOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] =
      if (first.getOption(s) ne None) new Some(first.set(s, second.reverseGet(a)))
      else None

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(
      s,
      t =>
        second.getOption(t) match {
          case Some(a) => second.reverseGet(f(a))
          case _       => t
        }
    )

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new OptionalPrism(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class OptionalOptional[F[_, _], S, T, A](first: Optional[F, S, T], second: Optional[F, T, A])
      extends Optional[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).flatMap(second.getOption)

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.getOption(s) match {
      case Some(x) => first.set(s, second.set(x, a))
      case _       => s
    }

    def setOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] = first.getOption(s) match {
      case Some(x) => new Some(first.set(s, second.set(x, a)))
      case _       => None
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.getOption(s) match {
      case Some(x) => first.set(s, second.modify(x, f))
      case _       => s
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new OptionalOptional(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }
}

sealed trait Traversal[F[_, _], S, A] extends Optic[F, S, A] { self =>
  def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z

  // Core operation - modify all focuses
  def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S

  // Compose this traversal with a lens:
  override def apply[B](that: Lens[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  // Compose this traversal with a prism:
  override def apply[B <: A](that: Prism[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  // Compose this traversal with an optional:
  override def apply[B](that: Optional[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  // Compose this traversal with a traversal:
  override def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A]

  override def noBinding: Traversal[NoBinding, S, A] = refineBinding(RefineBinding.noBinding())
}

object Traversal {
  type Bound[S, A] = Traversal[Binding, S, A]

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    new TraversalTraversal(first, second)

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Lens[F, T, A]): Traversal[F, S, A] =
    new TraversalLens(first, second)

  def apply[F[_, _], S, T, A <: T](first: Traversal[F, S, T], second: Prism[F, T, A]): Traversal[F, S, A] =
    new TraversalPrism(first, second)

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Optional[F, T, A]): Traversal[F, S, A] =
    new TraversalOptional(first, second)

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    new LensTraversal(first, second)

  def apply[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    new PrismTraversal(first, second)

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    new OptionalTraversal(first, second)

  def arrayValues[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, Array[A], A] =
    new SeqValues(Reflect.array(reflect))

  def listValues[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, List[A], A] =
    new SeqValues(Reflect.list(reflect))

  def mapKeys[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M]): Traversal[F, M[Key, Value], Key] =
    new MapKeys(map)

  def mapValues[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M]): Traversal[F, M[Key, Value], Value] =
    new MapValues(map)

  def seqValues[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C]): Traversal[F, C[A], A] = new SeqValues(seq)

  def setValues[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, Set[A], A] =
    new SeqValues(Reflect.set(reflect))

  def vectorValues[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, Vector[A], A] =
    new SeqValues(Reflect.vector(reflect))

  private case class SeqValues[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C])
      extends Traversal[F, C[A], A]
      with Leaf[F, C[A], A] {
    require(seq ne null)

    def structure: Reflect[F, C[A]] = seq

    def focus: Reflect[F, A] = seq.element

    def fold[Z](s: C[A])(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = {
      val deconstructor = F.seqDeconstructor(seq.seqBinding)
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[C] =>
          val len = indexed.length(s)
          var idx = 0
          indexed.elementType(s) match {
            case _: RegisterType.Boolean.type =>
              var z  = zero
              val ss = s.asInstanceOf[C[Boolean]]
              val sf = f.asInstanceOf[(Z, Boolean) => Z]
              while (idx < len) {
                z = sf(z, indexed.booleanAt(ss, idx))
                idx = idx + 1
              }
              z
            case _: RegisterType.Byte.type =>
              var z  = zero
              val ss = s.asInstanceOf[C[Byte]]
              val sf = f.asInstanceOf[(Z, Byte) => Z]
              while (idx < len) {
                z = sf(z, indexed.byteAt(ss, idx))
                idx = idx + 1
              }
              z
            case _: RegisterType.Short.type =>
              var z  = zero
              val ss = s.asInstanceOf[C[Short]]
              val sf = f.asInstanceOf[(Z, Short) => Z]
              while (idx < len) {
                z = sf(z, indexed.shortAt(ss, idx))
                idx = idx + 1
              }
              z
            case _: RegisterType.Int.type =>
              zero match {
                case zi: Int =>
                  var z: Int = zi
                  val ss     = s.asInstanceOf[C[Int]]
                  val sf     = f.asInstanceOf[(Int, Int) => Int]
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx = idx + 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  var z: Long = zl
                  val ss      = s.asInstanceOf[C[Int]]
                  val sf      = f.asInstanceOf[(Long, Int) => Long]
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx = idx + 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  var z: Double = zd
                  val ss        = s.asInstanceOf[C[Int]]
                  val sf        = f.asInstanceOf[(Double, Int) => Double]
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx = idx + 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  var z  = zero
                  val ss = s.asInstanceOf[C[Int]]
                  val sf = f.asInstanceOf[(Z, Int) => Z]
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx = idx + 1
                  }
                  z
              }
            case _: RegisterType.Long.type =>
              zero match {
                case zi: Int =>
                  var z: Int = zi
                  val ss     = s.asInstanceOf[C[Long]]
                  val sf     = f.asInstanceOf[(Int, Long) => Int]
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx = idx + 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  var z: Long = zl
                  val ss      = s.asInstanceOf[C[Long]]
                  val sf      = f.asInstanceOf[(Long, Long) => Long]
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx = idx + 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  var z: Double = zd
                  val ss        = s.asInstanceOf[C[Long]]
                  val sf        = f.asInstanceOf[(Double, Long) => Double]
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx = idx + 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  var z  = zero
                  val ss = s.asInstanceOf[C[Long]]
                  val sf = f.asInstanceOf[(Z, Long) => Z]
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx = idx + 1
                  }
                  z
              }
            case _: RegisterType.Double.type =>
              zero match {
                case zi: Int =>
                  var z: Int = zi
                  val ss     = s.asInstanceOf[C[Double]]
                  val sf     = f.asInstanceOf[(Int, Double) => Int]
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx = idx + 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  var z: Long = zl
                  val ss      = s.asInstanceOf[C[Double]]
                  val sf      = f.asInstanceOf[(Long, Double) => Long]
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx = idx + 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  var z: Double = zd
                  val ss        = s.asInstanceOf[C[Double]]
                  val sf        = f.asInstanceOf[(Double, Double) => Double]
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx = idx + 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  var z  = zero
                  val ss = s.asInstanceOf[C[Double]]
                  val sf = f.asInstanceOf[(Z, Double) => Z]
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx = idx + 1
                  }
                  z
              }
            case _: RegisterType.Char.type =>
              var z  = zero
              val ss = s.asInstanceOf[C[Char]]
              val sf = f.asInstanceOf[(Z, Char) => Z]
              while (idx < len) {
                z = sf(z, indexed.charAt(ss, idx))
                idx = idx + 1
              }
              z
            case _ =>
              var z = zero
              while (idx < len) {
                z = f(z, indexed.objectAt(s, idx))
                idx = idx + 1
              }
              z
          }
        case _ =>
          var z  = zero
          val it = deconstructor.deconstruct(s)
          while (it.hasNext) {
            z = f(z, it.next())
          }
          z
      }
    }

    def modify(s: C[A], f: A => A)(implicit F: HasBinding[F]): C[A] = {
      val deconstructor = F.seqDeconstructor(seq.seqBinding)
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[C] =>
          val len         = indexed.length(s)
          val constructor = F.seqConstructor(seq.seqBinding)
          indexed.elementType(s) match {
            case _: RegisterType.Boolean.type =>
              val ss      = s.asInstanceOf[C[Boolean]]
              val sf      = f.asInstanceOf[Boolean => Boolean]
              val builder = constructor.newBooleanBuilder()
              var idx     = 0
              while (idx < len) {
                constructor.addBoolean(builder, sf(indexed.booleanAt(ss, idx)))
                idx = idx + 1
              }
              constructor.resultBoolean(builder).asInstanceOf[C[A]]
            case _: RegisterType.Byte.type =>
              val ss      = s.asInstanceOf[C[Byte]]
              val sf      = f.asInstanceOf[Byte => Byte]
              val builder = constructor.newByteBuilder()
              var idx     = 0
              while (idx < len) {
                constructor.addByte(builder, sf(indexed.byteAt(ss, idx)))
                idx = idx + 1
              }
              constructor.resultByte(builder).asInstanceOf[C[A]]
            case _: RegisterType.Short.type =>
              val ss      = s.asInstanceOf[C[Short]]
              val sf      = f.asInstanceOf[Short => Short]
              val builder = constructor.newShortBuilder()
              var idx     = 0
              while (idx < len) {
                constructor.addShort(builder, sf(indexed.shortAt(ss, idx)))
                idx = idx + 1
              }
              constructor.resultShort(builder).asInstanceOf[C[A]]
            case _: RegisterType.Int.type =>
              val ss      = s.asInstanceOf[C[Int]]
              val sf      = f.asInstanceOf[Int => Int]
              val builder = constructor.newIntBuilder()
              var idx     = 0
              while (idx < len) {
                constructor.addInt(builder, sf(indexed.intAt(ss, idx)))
                idx = idx + 1
              }
              constructor.resultInt(builder).asInstanceOf[C[A]]
            case _: RegisterType.Long.type =>
              val ss      = s.asInstanceOf[C[Long]]
              val sf      = f.asInstanceOf[Long => Long]
              val builder = constructor.newLongBuilder()
              var idx     = 0
              while (idx < len) {
                constructor.addLong(builder, sf(indexed.longAt(ss, idx)))
                idx = idx + 1
              }
              constructor.resultLong(builder).asInstanceOf[C[A]]
            case _: RegisterType.Float.type =>
              val ss      = s.asInstanceOf[C[Float]]
              val sf      = f.asInstanceOf[Float => Float]
              val builder = constructor.newFloatBuilder()
              var idx     = 0
              while (idx < len) {
                constructor.addFloat(builder, sf(indexed.floatAt(ss, idx)))
                idx = idx + 1
              }
              constructor.resultFloat(builder).asInstanceOf[C[A]]
            case _: RegisterType.Double.type =>
              val ss      = s.asInstanceOf[C[Double]]
              val sf      = f.asInstanceOf[Double => Double]
              val builder = constructor.newDoubleBuilder()
              var idx     = 0
              while (idx < len) {
                constructor.addDouble(builder, sf(indexed.doubleAt(ss, idx)))
                idx = idx + 1
              }
              constructor.resultDouble(builder).asInstanceOf[C[A]]
            case _: RegisterType.Char.type =>
              val ss      = s.asInstanceOf[C[Char]]
              val sf      = f.asInstanceOf[Char => Char]
              val builder = constructor.newCharBuilder()
              var idx     = 0
              while (idx < len) {
                constructor.addChar(builder, sf(indexed.charAt(ss, idx)))
                idx = idx + 1
              }
              constructor.resultChar(builder).asInstanceOf[C[A]]
            case _ =>
              val builder = constructor.newObjectBuilder[A]()
              var idx     = 0
              while (idx < len) {
                constructor.addObject(builder, f(indexed.objectAt(s, idx)))
                idx = idx + 1
              }
              constructor.resultObject(builder)
          }
        case _ =>
          val constructor = F.seqConstructor(seq.seqBinding)
          val builder     = constructor.newObjectBuilder[A]()
          val it          = deconstructor.deconstruct(s)
          while (it.hasNext) {
            constructor.addObject(builder, f(it.next()))
          }
          constructor.resultObject(builder)
      }
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): SeqValues[G, A, C] = new SeqValues(seq.refineBinding(f))

    override def hashCode: Int = seq.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: SeqValues[F, _, _] => other.seq.equals(seq)
      case _                         => false
    }

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = ArraySeq(this)
  }

  private case class MapKeys[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M])
      extends Traversal[F, M[Key, Value], Key]
      with Leaf[F, M[Key, Value], Key] {
    require(map ne null)

    def structure: Reflect[F, M[Key, Value]] = map

    def focus: Reflect[F, Key] = map.key

    def fold[Z](s: M[Key, Value])(zero: Z, f: (Z, Key) => Z)(implicit F: HasBinding[F]): Z = {
      val deconstructor = map.mapDeconstructor
      var z             = zero
      val it            = deconstructor.deconstruct(s)
      while (it.hasNext) {
        val next = it.next()
        z = f(z, deconstructor.getKey(next))
      }
      z
    }

    def modify(s: M[Key, Value], f: Key => Key)(implicit F: HasBinding[F]): M[Key, Value] = {
      val deconstructor = map.mapDeconstructor
      val constructor   = map.mapConstructor
      val builder       = constructor.newObjectBuilder[Key, Value]()
      val it            = deconstructor.deconstruct(s)
      while (it.hasNext) {
        val next  = it.next()
        val key   = deconstructor.getKey(next)
        val value = deconstructor.getValue(next)
        constructor.addObject(builder, f(key), value)
      }
      constructor.resultObject(builder)
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): MapKeys[G, Key, Value, M] = new MapKeys(map.refineBinding(f))

    override def hashCode: Int = map.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: MapKeys[F, _, _, M] => other.map.equals(map)
      case _                          => false
    }

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = ArraySeq(this)
  }

  private case class MapValues[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M])
      extends Traversal[F, M[Key, Value], Value]
      with Leaf[F, M[Key, Value], Value] {
    require(map ne null)

    def structure: Reflect[F, M[Key, Value]] = map

    def focus: Reflect[F, Value] = map.value

    def fold[Z](s: M[Key, Value])(zero: Z, f: (Z, Value) => Z)(implicit F: HasBinding[F]): Z = {
      val deconstructor = map.mapDeconstructor
      var z             = zero
      val it            = deconstructor.deconstruct(s)
      while (it.hasNext) {
        val next = it.next()
        z = f(z, deconstructor.getValue(next))
      }
      z
    }

    def modify(s: M[Key, Value], f: Value => Value)(implicit F: HasBinding[F]): M[Key, Value] = {
      val deconstructor = map.mapDeconstructor
      val constructor   = F.mapConstructor(map.mapBinding)
      val builder       = constructor.newObjectBuilder[Key, Value]()
      val it            = deconstructor.deconstruct(s)
      while (it.hasNext) {
        val next  = it.next()
        val key   = deconstructor.getKey(next)
        val value = deconstructor.getValue(next)
        constructor.addObject(builder, key, f(value))
      }
      constructor.resultObject(builder)
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): MapValues[G, Key, Value, M] =
      new MapValues(map.refineBinding(f))

    override def hashCode: Int = map.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: MapValues[F, _, _, M] => other.map.equals(map)
      case _                            => false
    }

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = ArraySeq(this)
  }

  // All compositions that yield Traversal:
  private case class TraversalTraversal[F[_, _], S, T, A](first: Traversal[F, S, T], second: Traversal[F, T, A])
      extends Traversal[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z =
      first.fold[Z](s)(zero, (z, t) => second.fold(t)(z, f))

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(s, second.modify(_, f))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new TraversalTraversal(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class TraversalLens[F[_, _], S, T, A](first: Traversal[F, S, T], second: Lens[F, T, A])
      extends Traversal[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z =
      first.fold(s)(zero, (z, t) => f(z, second.get(t)))

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(s, second.modify(_, f))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new TraversalLens(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class TraversalPrism[F[_, _], S, T, A <: T](first: Traversal[F, S, T], second: Prism[F, T, A])
      extends Traversal[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = first.fold[Z](s)(
      zero,
      (z, t) =>
        second.getOption(t) match {
          case Some(a) => f(z, a)
          case _       => z
        }
    )

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(
      s,
      t =>
        second.getOption(t) match {
          case Some(a) => second.reverseGet(f(a))
          case _       => t
        }
    )

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new TraversalPrism(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class TraversalOptional[F[_, _], S, T, A](first: Traversal[F, S, T], second: Optional[F, T, A])
      extends Traversal[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = first.fold[Z](s)(
      zero,
      (z, t) =>
        second.getOption(t) match {
          case Some(a) => f(z, a)
          case _       => z
        }
    )

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(s, second.modify(_, f))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new TraversalOptional(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class LensTraversal[F[_, _], S, T, A](first: Lens[F, S, T], second: Traversal[F, T, A])
      extends Traversal[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = second.fold(first.get(s))(zero, f)

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(s, second.modify(_, f))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new LensTraversal(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class PrismTraversal[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Traversal[F, T, A])
      extends Traversal[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = first.getOption(s) match {
      case Some(t) => second.fold(t)(zero, f)
      case _       => zero
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.getOption(s) match {
      case Some(t) => first.reverseGet(second.modify(t, f))
      case _       => s
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new PrismTraversal(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class OptionalTraversal[F[_, _], S, T, A](first: Optional[F, S, T], second: Traversal[F, T, A])
      extends Traversal[F, S, A] {
    require((first ne null) && (second ne null))

    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = first.getOption(s) match {
      case Some(t) => second.fold(t)(zero, f)
      case _       => zero
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(s, second.modify(_, f))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new OptionalTraversal(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }
}
