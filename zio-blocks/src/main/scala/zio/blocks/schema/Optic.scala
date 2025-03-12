package zio.blocks.schema

import zio.blocks.schema.binding._

import scala.collection.immutable.ArraySeq

sealed trait Optic[F[_, _], S, A] { self =>
  def structure: Reflect[F, S]

  def focus: Reflect[F, A]

  // Compose this optic with a lens:
  def apply[B](that: Lens[F, A, B]): Optic[F, S, B]

  // Compose this optic with a prism:
  def apply[B](that: Prism[F, A, B]): Optic[F, S, B]

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
      case List(element) => list(Traversal.list(element))
      case _             => sys.error("FIXME - Not a list")
    }
  }

  final def vector[B](implicit ev: A <:< Vector[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.Vector

    val vector = self.asSub[Vector[B]]
    vector.focus match {
      case Vector(element) => vector(Traversal.vector(element))
      case _               => sys.error("FIXME - Not a vector")
    }
  }

  final def set[B](implicit ev: A <:< Set[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.Set

    val set = self.asSub[Set[B]]
    set.focus match {
      case Set(element) => set(Traversal.set(element))
      case _            => sys.error("FIXME - Not a set")
    }
  }

  final def array[B](implicit ev: A <:< Array[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.Array

    val array = self.asSub[Array[B]]
    array.focus match {
      case Array(element) => array(Traversal.array(element))
      case _              => sys.error("FIXME - Not an array")
    }
  }

  final def asSub[B](implicit ev: A <:< B): Optic[F, S, B] = self.asInstanceOf[Optic[F, S, B]]

  override def hashCode: Int = this.linearized.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: Optic[F, _, _] => other.linearized.equals(this.linearized)
    case _                     => false
  }

  private[schema] def linearized: ArraySeq[Leaf[F, _, _]]
}

object Optic {
  type Bound[S, A] = Optic[Binding, S, A]
}

sealed trait Leaf[F[_, _], S, A] extends Optic[F, S, A]

sealed trait Lens[F[_, _], S, A] extends Optic[F, S, A] {
  def get(s: S)(implicit F: HasBinding[F]): A

  // FIXME: Introduce modify(s: S, f: A => A) for performance reasons, implement set in terms of that.
  def set(s: S, a: A)(implicit F: HasBinding[F]): S

  // Compose this lens with a lens:
  override def apply[B](that: Lens[F, A, B]): Lens[F, S, B] = Lens(this, that)

  // Compose this lens with a prism:
  override def apply[B](that: Prism[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this lens with an optional:
  override def apply[B](that: Optional[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this lens with a traversal:
  override def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Lens[G, S, A]

  override def noBinding: Lens[NoBinding, S, A] = refineBinding(RefineBinding.noBinding())
}

object Lens {
  type Bound[S, A] = Lens[Binding, S, A]

  def apply[F[_, _], S, A](parent: Reflect.Record[F, S], child: Term[F, S, A]): Lens[F, S, A] = new Root(parent, child)

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Lens[F, T, A]): Lens[F, S, A] = new LensLens(first, second)

  private case class Root[F[_, _], S, A](parent: Reflect.Record[F, S], child: Term[F, S, A])
      extends Lens[F, S, A]
      with Leaf[F, S, A] {
    def structure: Reflect[F, S] = parent

    def focus: Reflect[F, A] = child.value

    private val register: Register[A] =
      parent.registers(parent.fields.indexWhere(_.name == child.name)).asInstanceOf[Register[A]]

    def get(s: S)(implicit F: HasBinding[F]): A = {
      val registers = Registers()
      F.deconstructor(parent.recordBinding).deconstruct(registers, RegisterOffset.Zero, s)
      register.get(registers, RegisterOffset.Zero)
    }

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = {
      val registers = Registers()
      F.deconstructor(parent.recordBinding).deconstruct(registers, RegisterOffset.Zero, s)
      register.set(registers, RegisterOffset.Zero, a)
      F.constructor(parent.recordBinding).construct(registers, RegisterOffset.Zero)
    }

    override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Lens[G, S, A] =
      new Root(parent.refineBinding(f), child.refineBinding(f))

    override def hashCode: Int = parent.hashCode ^ child.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: Root[F, _, _] => other.parent.equals(this.parent) && other.child.equals(this.child)
      case _                    => false
    }

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = ArraySeq(this)
  }

  private case class LensLens[F[_, _], S, T, A](first: Lens[F, S, T], second: Lens[F, T, A]) extends Lens[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def get(s: S)(implicit F: HasBinding[F]): A = second.get(first.get(s))

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.set(s, second.set(first.get(s), a))

    override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Lens[G, S, A] =
      new LensLens(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }
}

sealed trait Prism[F[_, _], S, A] extends Optic[F, S, A] {
  def getOption(s: S)(implicit F: HasBinding[F]): Option[A]

  def reverseGet(a: A): S

  // Compose this prism with a prism:
  override def apply[B](that: Prism[F, A, B]): Prism[F, S, B] = Prism(this, that)

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
  type Bound[S, A] = Prism[Binding, S, A]

  def apply[F[_, _], S, A <: S](parent: Reflect.Variant[F, S], child: Term[F, S, A]): Prism[F, S, A] =
    new Root(parent, child)

  def apply[F[_, _], S, T, A](first: Prism[F, S, T], second: Prism[F, T, A]): Prism[F, S, A] =
    new PrismPrism(first, second)

  private case class Root[F[_, _], S, A <: S](parent: Reflect.Variant[F, S], child: Term[F, S, A])
      extends Prism[F, S, A]
      with Leaf[F, S, A] {
    private var matcher: Matcher[A] = null

    private def init(F: HasBinding[F]): Unit =
      if (matcher eq null) {
        val matchers = F.matchers(parent.variantBinding)
        matcher = matchers(parent.cases.indexWhere(_.name == child.name)).asInstanceOf[Matcher[A]]
      }

    def structure: Reflect[F, S] = parent

    def focus: Reflect[F, A] = child.value

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = {
      init(F)
      matcher.downcastOption(s)
    }

    def reverseGet(a: A): S = a

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Prism[G, S, A] =
      new Root(parent.refineBinding(f), child.refineBinding(f))

    override def hashCode: Int = parent.hashCode ^ child.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: Root[F, _, _] => other.parent.equals(this.parent) && other.child.equals(this.child)
      case _                    => false
    }

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = ArraySeq(this)
  }

  private case class PrismPrism[F[_, _], S, T, A](first: Prism[F, S, T], second: Prism[F, T, A])
      extends Prism[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).flatMap(second.getOption)

    def reverseGet(a: A): S = first.reverseGet(second.reverseGet(a))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Prism[G, S, A] =
      new PrismPrism(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }
}

sealed trait Optional[F[_, _], S, A] extends Optic[F, S, A] {
  def getOption(s: S)(implicit F: HasBinding[F]): Option[A]

  def set(s: S, a: A)(implicit F: HasBinding[F]): S

  // Compose this optional with a lens:
  override def apply[B](that: Lens[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this optional with a prism:
  override def apply[B](that: Prism[F, A, B]): Optional[F, S, B] = Optional(this, that)

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

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Prism[F, T, A]): Optional[F, S, A] =
    new Optional.OptionalPrism(first, second)

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] =
    new Optional.OptionalOptional(first, second)

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Prism[F, T, A]): Optional[F, S, A] =
    new Optional.LensPrism(first, second)

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] =
    new Optional.LensOptional(first, second)

  def apply[F[_, _], S, T, A](first: Prism[F, S, T], second: Lens[F, T, A]): Optional[F, S, A] =
    new Optional.PrismLens(first, second)

  def apply[F[_, _], S, T, A](first: Prism[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] =
    new Optional.PrismOptional(first, second)

  private case class LensPrism[F[_, _], S, T, A](first: Lens[F, S, T], second: Prism[F, T, A])
      extends Optional[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = second.getOption(first.get(s))

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.set(s, second.reverseGet(a))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new LensPrism(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class LensOptional[F[_, _], S, T, A](first: Lens[F, S, T], second: Optional[F, T, A])
      extends Optional[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = second.getOption(first.get(s))

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.set(s, second.set(first.get(s), a))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new LensOptional(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class PrismLens[F[_, _], S, T, A](first: Prism[F, S, T], second: Lens[F, T, A])
      extends Optional[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).map(second.get)

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.reverseGet(second.set(first.getOption(s).get, a))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new PrismLens(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class PrismOptional[F[_, _], S, T, A](first: Prism[F, S, T], second: Optional[F, T, A])
      extends Optional[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).flatMap(second.getOption)

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.reverseGet(second.set(first.getOption(s).get, a))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new PrismOptional(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class OptionalLens[F[_, _], S, T, A](first: Optional[F, S, T], second: Lens[F, T, A])
      extends Optional[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).map(second.get)

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.set(s, second.set(first.getOption(s).get, a))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new OptionalLens(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class OptionalPrism[F[_, _], S, T, A](first: Optional[F, S, T], second: Prism[F, T, A])
      extends Optional[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).flatMap(second.getOption)

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.set(s, second.reverseGet(a))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new OptionalPrism(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class OptionalOptional[F[_, _], S, T, A](first: Optional[F, S, T], second: Optional[F, T, A])
      extends Optional[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = first.getOption(s).flatMap(second.getOption)

    def set(s: S, a: A)(implicit F: HasBinding[F]): S = first.set(s, second.set(first.getOption(s).get, a))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new OptionalOptional(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }
}

sealed trait Traversal[F[_, _], S, A] extends Optic[F, S, A] { self =>
  def focus: Reflect[F, A]

  def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z

  // Core operation - modify all focuses
  def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S

  // Compose this traversal with a lens:
  override def apply[B](that: Lens[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  // Compose this traversal with a prism:
  override def apply[B](that: Prism[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  // Compose this traversal with an optional:
  override def apply[B](that: Optional[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  // Compose this traversal with a traversal:
  override def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A]

  override def noBinding: Traversal[NoBinding, S, A] = refineBinding(RefineBinding.noBinding())
}

object Traversal {
  type Bound[S, A] = Traversal[Binding, S, A]

  def apply[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C]): Traversal[F, C[A], A] = new Seq(seq)

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    new TraversalTraversal(first, second)

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Lens[F, T, A]): Traversal[F, S, A] =
    new TraversalLens(first, second)

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Prism[F, T, A]): Traversal[F, S, A] =
    new TraversalPrism(first, second)

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Optional[F, T, A]): Traversal[F, S, A] =
    new TraversalOptional(first, second)

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    new LensTraversal(first, second)

  def apply[F[_, _], S, T, A](first: Prism[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    new PrismTraversal(first, second)

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    new OptionalTraversal(first, second)

  def array[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, Array[A], A] =
    new Seq(Reflect.array(reflect))

  def list[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, List[A], A] =
    new Seq(Reflect.list(reflect))

  def mapKeys[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M]): Traversal[F, M[Key, Value], Key] =
    new MapKeys(map)

  def mapValues[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M]): Traversal[F, M[Key, Value], Value] =
    new MapValues(map)

  def set[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, Set[A], A] =
    new Seq(Reflect.set(reflect))

  def vector[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, Vector[A], A] =
    new Seq(Reflect.vector(reflect))

  private case class Seq[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C])
      extends Traversal[F, C[A], A]
      with Leaf[F, C[A], A] {
    def structure: Reflect[F, C[A]] = seq

    def focus: Reflect[F, A] = seq.element

    def fold[Z](s: C[A])(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = {
      val deconstructor = F.seqDeconstructor(seq.seqBinding)
      deconstructor match {
        case indexed: SeqDeconstructor.Indexed[c] =>
          var idx = 0
          val len = indexed.length(s)
          var z   = zero
          while (idx < len) {
            z = f(z, indexed.objectAt(s, idx)) // TODO: Specialize
            idx = idx + 1
          }
          z
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
        case indexed: SeqDeconstructor.Indexed[c] =>
          val len         = indexed.length(s)
          val constructor = F.seqConstructor(seq.seqBinding)
          val builder     = constructor.newObjectBuilder[A]() // TODO: Specialize
          var idx         = 0
          while (idx < len) {
            constructor.addObject(builder, f(indexed.objectAt(s, idx))) // TODO: Specialize
            idx = idx + 1
          }
          constructor.resultObject(builder)
        case _ =>
          val constructor = F.seqConstructor(seq.seqBinding)
          val builder     = constructor.newObjectBuilder[A]() // TODO: Specialize
          val it          = deconstructor.deconstruct(s)
          while (it.hasNext) {
            constructor.addObject(builder, f(it.next()))
          }
          constructor.resultObject(builder)
      }
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Seq[G, A, C] = new Seq(seq.refineBinding(f))

    override def hashCode: Int = seq.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: Seq[F, _, _] => other.seq.equals(this.seq)
      case _                   => false
    }

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = ArraySeq(this)
  }

  private case class MapKeys[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M])
      extends Traversal[F, M[Key, Value], Key]
      with Leaf[F, M[Key, Value], Key] {
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
      case other: MapKeys[F, _, _, M] => other.map.equals(this.map)
      case _                          => false
    }

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = ArraySeq(this)
  }

  private case class MapValues[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M])
      extends Traversal[F, M[Key, Value], Value]
      with Leaf[F, M[Key, Value], Value] {
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
      case other: MapValues[F, _, _, M] => other.map.equals(this.map)
      case _                            => false
    }

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = ArraySeq(this)
  }

  // All compositions that yield Traversal:
  private case class TraversalTraversal[F[_, _], S, T, A](first: Traversal[F, S, T], second: Traversal[F, T, A])
      extends Traversal[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z =
      first.fold[Z](s)(zero, (z, t) => second.fold(t)(z, f))

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(s, t => second.modify(t, f))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new TraversalTraversal(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class TraversalLens[F[_, _], S, T, A](first: Traversal[F, S, T], second: Lens[F, T, A])
      extends Traversal[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z =
      first.fold(s)(zero, (z, t) => f(z, second.get(t)))

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.modify(s, t => second.set(t, f(second.get(t))))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new TraversalLens(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class TraversalPrism[F[_, _], S, T, A](first: Traversal[F, S, T], second: Prism[F, T, A])
      extends Traversal[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z =
      first.fold[Z](s)(zero, (z, t) => second.getOption(t).map(a => f(z, a)).getOrElse(z))

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S =
      first.modify(s, t => second.getOption(t).map(a => second.reverseGet(f(a))).getOrElse(t))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new TraversalPrism(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class TraversalOptional[F[_, _], S, T, A](first: Traversal[F, S, T], second: Optional[F, T, A])
      extends Traversal[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z =
      first.fold[Z](s)(zero, (z, t) => second.getOption(t).map(a => f(z, a)).getOrElse(z))

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S =
      first.modify(s, t => second.getOption(t).map(a => second.set(t, f(a))).getOrElse(t))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new TraversalOptional(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class LensTraversal[F[_, _], S, T, A](first: Lens[F, S, T], second: Traversal[F, T, A])
      extends Traversal[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = second.fold(first.get(s))(zero, f)

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = first.set(s, second.modify(first.get(s), f))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new LensTraversal(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class PrismTraversal[F[_, _], S, T, A](first: Prism[F, S, T], second: Traversal[F, T, A])
      extends Traversal[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z =
      first.getOption(s).map(t => second.fold(t)(zero, f)).getOrElse(zero)

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S =
      first.getOption(s).map(t => first.reverseGet(second.modify(t, f))).getOrElse(s)

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new PrismTraversal(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }

  private case class OptionalTraversal[F[_, _], S, T, A](first: Optional[F, S, T], second: Traversal[F, T, A])
      extends Traversal[F, S, A] {
    def structure: Reflect[F, S] = first.structure

    def focus: Reflect[F, A] = second.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z =
      first.getOption(s).map(t => second.fold(t)(zero, f)).getOrElse(zero)

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S =
      first.getOption(s).map(t => first.set(s, second.modify(t, f))).getOrElse(s)

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new OptionalTraversal(first.refineBinding(f), second.refineBinding(f))

    private[schema] lazy val linearized: ArraySeq[Leaf[F, _, _]] = first.linearized ++ second.linearized
  }
}
