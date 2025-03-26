package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._

import scala.collection.immutable.ArraySeq

sealed trait Reflect[F[_, _], A] extends Reflectable[A] { self =>
  protected def inner: Any

  type NodeBinding <: BindingType

  def refineBinding[G[_, _]](f: RefineBinding[F, G]): Reflect[G, A]

  def noBinding: Reflect[NoBinding, A] = refineBinding(RefineBinding.noBinding())

  def binding(implicit F: HasBinding[F]): Binding[NodeBinding, A]

  def asTerm[S](name: String): Term[F, S, A] = Term(name, this, Doc.Empty, scala.List.empty)

  override def hashCode: Int = inner.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case that: Reflect[_, _] => (this eq that) || inner == that.inner
    case _                   => false
  }
}

object Reflect {
  type Bound[A] = Reflect[Binding, A]

  final case class Record[F[_, _], A](
    fields: scala.List[Term[F, A, ?]],
    typeName: TypeName[A],
    recordBinding: F[BindingType.Record, A],
    doc: Doc,
    modifiers: scala.List[Modifier.Record]
  ) extends Reflect[F, A] { self =>
    protected def inner: Any = (fields, typeName, doc, modifiers)
    type NodeBinding = BindingType.Record

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Record, A] = F.binding(recordBinding)

    def constructor(implicit F: HasBinding[F]): Constructor[A] = F.constructor(recordBinding)

    def deconstructor(implicit F: HasBinding[F]): Deconstructor[A] = F.deconstructor(recordBinding)

    def fieldByName(name: String): scala.Option[Term[F, A, ?]] = fields.find(_.name == name)

    def lensByIndex(index: Int): Lens[F, A, ?] = Lens(self, fields(index))

    def lensByName(name: String): scala.Option[Lens[F, A, ?]] = fieldByName(name).map(Lens(self, _))

    val length: Int = fields.length

    def registerByName(name: String): scala.Option[Register[?]] = {
      val i = fields.indexWhere(_.name == name)
      if (i >= 0) Some(registers(i))
      else None
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Record[G, A] =
      Record(fields.map(_.refineBinding(f)), typeName, f(recordBinding), doc, modifiers)

    val registers: IndexedSeq[Register[?]] = {
      val registers      = new Array[Register[?]](length)
      var registerOffset = RegisterOffset.Zero
      var i              = 0
      fields.foreach { term =>
        term.value match {
          case Reflect.Primitive(primType, _, _, _, _) =>
            primType match {
              case PrimitiveType.Unit =>
                registers(i) = Register.Unit
              case _: PrimitiveType.Boolean =>
                registers(i) = Register.Boolean(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementBooleansAndBytes(registerOffset)
              case _: PrimitiveType.Byte =>
                registers(i) = Register.Byte(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementBooleansAndBytes(registerOffset)
              case _: PrimitiveType.Char =>
                registers(i) = Register.Char(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementCharsAndShorts(registerOffset)
              case _: PrimitiveType.Short =>
                registers(i) = Register.Short(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementCharsAndShorts(registerOffset)
              case _: PrimitiveType.Float =>
                registers(i) = Register.Float(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementFloatsAndInts(registerOffset)
              case _: PrimitiveType.Int =>
                registers(i) = Register.Int(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementFloatsAndInts(registerOffset)
              case _: PrimitiveType.Double =>
                registers(i) = Register.Double(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementDoublesAndLongs(registerOffset)
              case _: PrimitiveType.Long =>
                registers(i) = Register.Long(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementDoublesAndLongs(registerOffset)
              case _ =>
                registers(i) = Register.Object(RegisterOffset.getObjects(registerOffset))
                registerOffset = RegisterOffset.incrementObjects(registerOffset)
            }
          case _ =>
            registers(i) = Register.Object(RegisterOffset.getObjects(registerOffset))
            registerOffset = RegisterOffset.incrementObjects(registerOffset)
        }
        i += 1
      }
      ArraySeq.unsafeWrapArray(registers)
    }

    val usedRegisters: RegisterOffset = registers.foldLeft(RegisterOffset.Zero) { (acc, register) =>
      RegisterOffset.add(acc, register.usedRegisters)
    }
  }

  object Record {
    type Bound[A] = Record[Binding, A]
  }

  final case class Variant[F[_, _], A](
    cases: scala.List[Term[F, A, ? <: A]],
    typeName: TypeName[A],
    variantBinding: F[BindingType.Variant, A],
    doc: Doc,
    modifiers: scala.List[Modifier.Variant]
  ) extends Reflect[F, A] {
    protected def inner: Any = (cases, typeName, doc, modifiers)

    type NodeBinding = BindingType.Variant

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Variant, A] = F.binding(variantBinding)

    def caseByName(name: String): scala.Option[Term[F, A, ? <: A]] = cases.find(_.name == name)

    def discriminator(implicit F: HasBinding[F]): Discriminator[A] = F.discriminator(variantBinding)

    def matchers(implicit F: HasBinding[F]): Matchers[A] = F.matchers(variantBinding)

    def prismByIndex(index: Int): Prism[F, A, ? <: A] = Prism(this, cases(index))

    def prismByName(name: String): scala.Option[Prism[F, A, ? <: A]] = caseByName(name).map(Prism(this, _))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Variant[G, A] =
      Variant(cases.map(_.refineBinding(f)), typeName, f(variantBinding), doc, modifiers)
  }

  object Variant {
    type Bound[A] = Variant[Binding, A]
  }

  final case class Sequence[F[_, _], A, C[_]](
    element: Reflect[F, A],
    seqBinding: F[BindingType.Seq[C], C[A]],
    typeName: TypeName[C[A]],
    doc: Doc,
    modifiers: List[Modifier.Seq]
  ) extends Reflect[F, C[A]] {
    protected def inner: Any = (element, typeName, doc, modifiers)

    type NodeBinding = BindingType.Seq[C]

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Seq[C], C[A]] = F.binding(seqBinding)

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Sequence[G, A, C] =
      Sequence(element.refineBinding(f), f(seqBinding), typeName, doc, modifiers)

    def seqConstructor(implicit F: HasBinding[F]): SeqConstructor[C] = F.seqConstructor(seqBinding)

    def seqDeconstructor(implicit F: HasBinding[F]): SeqDeconstructor[C] = F.seqDeconstructor(seqBinding)

    def values: Traversal[F, C[A], A] = Traversal.seqValues(this)
  }

  object Sequence {
    type Bound[A, C[_]] = Sequence[Binding, A, C]
  }

  final case class Map[F[_, _], Key, Value, M[_, _]](
    key: Reflect[F, Key],
    value: Reflect[F, Value],
    mapBinding: F[BindingType.Map[M], M[Key, Value]],
    typeName: TypeName[M[Key, Value]],
    doc: Doc,
    modifiers: List[Modifier.Map]
  ) extends Reflect[F, M[Key, Value]] {
    protected def inner: Any = (key, value, typeName, doc, modifiers)

    type NodeBinding = BindingType.Map[M]

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Map[M], M[Key, Value]] = F.binding(mapBinding)

    def mapConstructor(implicit F: HasBinding[F]): MapConstructor[M] = F.mapConstructor(mapBinding)

    def mapDeconstructor(implicit F: HasBinding[F]): MapDeconstructor[M] = F.mapDeconstructor(mapBinding)

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Map[G, Key, Value, M] =
      Map(key.refineBinding(f), value.refineBinding(f), f(mapBinding), typeName, doc, modifiers)

    def keys: Traversal[F, M[Key, Value], Key] = Traversal.mapKeys(this)

    def values: Traversal[F, M[Key, Value], Value] = Traversal.mapValues(this)
  }

  object Map {
    type Bound[K, V, M[_, _]] = Map[Binding, K, V, M]
  }

  final case class Dynamic[F[_, _]](
    dynamicBinding: F[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: scala.List[Modifier.Dynamic]
  ) extends Reflect[F, DynamicValue] {
    protected def inner: Any = (modifiers, modifiers, doc)

    type NodeBinding = BindingType.Dynamic

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Dynamic, DynamicValue] = F.binding(dynamicBinding)

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Reflect[G, DynamicValue] =
      Dynamic(f(dynamicBinding), doc, modifiers)
  }

  final case class Primitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    primitiveBinding: F[BindingType.Primitive, A],
    typeName: TypeName[A],
    doc: Doc,
    modifiers: scala.List[Modifier.Primitive]
  ) extends Reflect[F, A] { self =>
    protected def inner: Any = (primitiveType, typeName, doc, modifiers)

    type NodeBinding = BindingType.Primitive

    def binding(implicit F: HasBinding[F]): Binding.Primitive[A] = F.primitive(primitiveBinding)

    def defaultValue(implicit F: HasBinding[F]): scala.Option[() => A] = binding.defaultValue

    def examples(implicit F: HasBinding[F]): scala.List[A] = binding.examples

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Primitive[G, A] =
      Primitive(primitiveType, f(primitiveBinding), typeName, doc, modifiers)
  }

  final case class Deferred[F[_, _], A](_value: () => Reflect[F, A]) extends Reflect[F, A] {
    protected def inner: Any = value.inner

    lazy val value: Reflect[F, A] = _value()

    type NodeBinding = value.NodeBinding

    def binding(implicit F: HasBinding[F]): Binding[NodeBinding, A] = value.binding

    def modifiers: scala.List[Modifier] = value.modifiers

    def doc: Doc = value.doc

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Reflect[G, A] = {
      val v = visited.get
      if (v.containsKey(this)) value.asInstanceOf[Reflect[G, A]] // exit from recursion
      else {
        v.put(this, this)
        try value.refineBinding(f)
        finally v.remove(this)
      }
    }

    override def hashCode: Int = {
      val v = visited.get
      if (v.containsKey(this)) 0 // exit from recursion
      else {
        v.put(this, this)
        try innerHashCode
        finally v.remove(this)
      }
    }

    override def equals(obj: Any): Boolean = obj match {
      case that: Reflect[_, _] =>
        (this eq that) || {
          val v = visited.get
          if (v.containsKey(this)) true // exit from recursion
          else {
            v.put(this, this)
            try inner == that.inner
            finally v.remove(this)
          }
        }
      case _ => false
    }

    private[this] lazy val visited: ThreadLocal[java.util.IdentityHashMap[AnyRef, AnyRef]] =
      new ThreadLocal[java.util.IdentityHashMap[AnyRef, AnyRef]] {
        override def initialValue: java.util.IdentityHashMap[AnyRef, AnyRef] =
          new java.util.IdentityHashMap[AnyRef, AnyRef]
      }
    private[this] lazy val innerHashCode = inner.hashCode
  }

  def unit[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Unit] =
    Primitive(PrimitiveType.Unit, F.fromBinding(Binding.Primitive.unit), TypeName.unit, Doc.Empty, Nil)

  def boolean[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Boolean] =
    Primitive(
      PrimitiveType.Boolean(Validation.None),
      F.fromBinding(Binding.Primitive.boolean),
      TypeName.boolean,
      Doc.Empty,
      Nil
    )

  def byte[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Byte] =
    Primitive(PrimitiveType.Byte(Validation.None), F.fromBinding(Binding.Primitive.byte), TypeName.byte, Doc.Empty, Nil)

  def short[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Short] =
    Primitive(
      PrimitiveType.Short(Validation.None),
      F.fromBinding(Binding.Primitive.short),
      TypeName.short,
      Doc.Empty,
      Nil
    )

  def int[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Int] =
    Primitive(
      PrimitiveType.Int(Validation.None),
      F.fromBinding(Binding.Primitive.int),
      TypeName.int,
      Doc.Empty,
      Nil
    )

  def long[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Long] =
    Primitive(
      PrimitiveType.Long(Validation.None),
      F.fromBinding(Binding.Primitive.long),
      TypeName.long,
      Doc.Empty,
      Nil
    )

  def float[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Float] =
    Primitive(
      PrimitiveType.Float(Validation.None),
      F.fromBinding(Binding.Primitive.float),
      TypeName.float,
      Doc.Empty,
      Nil
    )

  def double[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Double] =
    Primitive(
      PrimitiveType.Double(Validation.None),
      F.fromBinding(Binding.Primitive.double),
      TypeName.double,
      Doc.Empty,
      Nil
    )

  def char[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Char] =
    Primitive(
      PrimitiveType.Char(Validation.None),
      F.fromBinding(Binding.Primitive.char),
      TypeName.char,
      Doc.Empty,
      Nil
    )

  def string[F[_, _]](implicit F: FromBinding[F]): Reflect[F, String] =
    Primitive(
      PrimitiveType.String(Validation.None),
      F.fromBinding(Binding.Primitive.string),
      TypeName.string,
      Doc.Empty,
      Nil
    )

  def bigInt[F[_, _]](implicit F: FromBinding[F]): Reflect[F, BigInt] =
    Primitive(
      PrimitiveType.BigInt(Validation.None),
      F.fromBinding(Binding.Primitive.bigInt),
      TypeName.bigInt,
      Doc.Empty,
      Nil
    )

  def bigDecimal[F[_, _]](implicit F: FromBinding[F]): Reflect[F, BigDecimal] =
    Primitive(
      PrimitiveType.BigDecimal(Validation.None),
      F.fromBinding(Binding.Primitive.bigDecimal),
      TypeName.bigDecimal,
      Doc.Empty,
      Nil
    )

  def dayOfWeek[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.DayOfWeek] =
    Primitive(
      PrimitiveType.DayOfWeek(Validation.None),
      F.fromBinding(Binding.Primitive.dayOfWeek),
      TypeName.dayOfWeek,
      Doc.Empty,
      Nil
    )

  def duration[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Duration] =
    Primitive(
      PrimitiveType.Duration(Validation.None),
      F.fromBinding(Binding.Primitive.duration),
      TypeName.duration,
      Doc.Empty,
      Nil
    )

  def instant[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Instant] =
    Primitive(
      PrimitiveType.Instant(Validation.None),
      F.fromBinding(Binding.Primitive.instant),
      TypeName.instant,
      Doc.Empty,
      Nil
    )

  def localDate[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalDate] =
    Primitive(
      PrimitiveType.LocalDate(Validation.None),
      F.fromBinding(Binding.Primitive.localDate),
      TypeName.localDate,
      Doc.Empty,
      Nil
    )

  def localDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalDateTime] =
    Primitive(
      PrimitiveType.LocalDateTime(Validation.None),
      F.fromBinding(Binding.Primitive.localDateTime),
      TypeName.localDateTime,
      Doc.Empty,
      Nil
    )

  def localTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalTime] =
    Primitive(
      PrimitiveType.LocalTime(Validation.None),
      F.fromBinding(Binding.Primitive.localTime),
      TypeName.localTime,
      Doc.Empty,
      Nil
    )

  def month[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Month] =
    Primitive(
      PrimitiveType.Month(Validation.None),
      F.fromBinding(Binding.Primitive.month),
      TypeName.month,
      Doc.Empty,
      Nil
    )

  def monthDay[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.MonthDay] =
    Primitive(
      PrimitiveType.MonthDay(Validation.None),
      F.fromBinding(Binding.Primitive.monthDay),
      TypeName.monthDay,
      Doc.Empty,
      Nil
    )

  def offsetDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.OffsetDateTime] =
    Primitive(
      PrimitiveType.OffsetDateTime(Validation.None),
      F.fromBinding(Binding.Primitive.offsetDateTime),
      TypeName.offsetDateTime,
      Doc.Empty,
      Nil
    )

  def offsetTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.OffsetTime] =
    Primitive(
      PrimitiveType.OffsetTime(Validation.None),
      F.fromBinding(Binding.Primitive.offsetTime),
      TypeName.offsetTime,
      Doc.Empty,
      Nil
    )

  def period[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Period] =
    Primitive(
      PrimitiveType.Period(Validation.None),
      F.fromBinding(Binding.Primitive.period),
      TypeName.period,
      Doc.Empty,
      Nil
    )

  def year[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Year] =
    Primitive(
      PrimitiveType.Year(Validation.None),
      F.fromBinding(Binding.Primitive.year),
      TypeName.year,
      Doc.Empty,
      Nil
    )

  def yearMonth[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.YearMonth] =
    Primitive(
      PrimitiveType.YearMonth(Validation.None),
      F.fromBinding(Binding.Primitive.yearMonth),
      TypeName.yearMonth,
      Doc.Empty,
      Nil
    )

  def zoneId[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZoneId] =
    Primitive(
      PrimitiveType.ZoneId(Validation.None),
      F.fromBinding(Binding.Primitive.zoneId),
      TypeName.zoneId,
      Doc.Empty,
      Nil
    )

  def zoneOffset[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZoneOffset] =
    Primitive(
      PrimitiveType.ZoneOffset(Validation.None),
      F.fromBinding(Binding.Primitive.zoneOffset),
      TypeName.zoneOffset,
      Doc.Empty,
      Nil
    )

  def zonedDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZonedDateTime] =
    Primitive(
      PrimitiveType.ZonedDateTime(Validation.None),
      F.fromBinding(Binding.Primitive.zonedDateTime),
      TypeName.zonedDateTime,
      Doc.Empty,
      Nil
    )

  def currency[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.util.Currency] =
    Primitive(
      PrimitiveType.Currency(Validation.None),
      F.fromBinding(Binding.Primitive.currency),
      TypeName.currency,
      Doc.Empty,
      Nil
    )

  def uuid[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.util.UUID] =
    Primitive(
      PrimitiveType.UUID(Validation.None),
      F.fromBinding(Binding.Primitive.uuid),
      TypeName.uuid,
      Doc.Empty,
      Nil
    )

  def set[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Predef.Set] =
    Sequence(element, F.fromBinding(Binding.Seq.set), TypeName.set[A], Doc.Empty, Nil)

  def list[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, scala.List] =
    Sequence(element, F.fromBinding(Binding.Seq.list), TypeName.list[A], Doc.Empty, Nil)

  def vector[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, scala.Vector] =
    Sequence(element, F.fromBinding(Binding.Seq.vector), TypeName.vector[A], Doc.Empty, Nil)

  def array[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, scala.Array] =
    Sequence(element, F.fromBinding(Binding.Seq.array), TypeName.array[A], Doc.Empty, Nil)

  def map[F[_, _], A, B](key: Reflect[F, A], value: Reflect[F, B])(implicit
    F: FromBinding[F]
  ): Map[F, A, B, collection.immutable.Map] =
    Map(key, value, F.fromBinding(Binding.Map.map), TypeName.map[A, B], Doc.Empty, Nil)

  def some[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Record[F, Some[A]] =
    Record(
      scala.List(Term("value", element, Doc.Empty, scala.List.empty)),
      TypeName.some[A],
      F.fromBinding(Binding.Record.some[A]),
      Doc.Empty,
      scala.List.empty
    )

  def none[F[_, _]](implicit F: FromBinding[F]): Record[F, None.type] =
    Record(
      scala.List.empty,
      TypeName.none,
      F.fromBinding(Binding.Record.none),
      Doc.Empty,
      scala.List.empty
    )

  def option[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Variant[F, scala.Option[A]] = {
    val noneTerm: Term[F, scala.Option[A], None.type] = Term("None", none, Doc.Empty, scala.List.empty)
    val someTerm: Term[F, scala.Option[A], Some[A]]   = Term("Some", some[F, A](element), Doc.Empty, scala.List.empty)
    Variant(
      scala.List(noneTerm, someTerm),
      TypeName.option[A],
      F.fromBinding(Binding.Variant.option[A]),
      Doc.Empty,
      scala.List.empty
    )
  }

  def left[F[_, _], A, B](element: Reflect[F, A])(implicit F: FromBinding[F]): Record[F, Left[A, B]] =
    Record(
      scala.List(Term("value", element, Doc.Empty, scala.List.empty)),
      TypeName.left[A, B],
      F.fromBinding(Binding.Record.left[A, B]),
      Doc.Empty,
      scala.List.empty
    )

  def right[F[_, _], A, B](element: Reflect[F, B])(implicit F: FromBinding[F]): Record[F, Right[A, B]] =
    Record(
      scala.List(Term("value", element, Doc.Empty, scala.List.empty)),
      TypeName.right[A, B],
      F.fromBinding(Binding.Record.right[A, B]),
      Doc.Empty,
      scala.List.empty
    )

  def either[F[_, _], L, R](l: Reflect[F, L], r: Reflect[F, R])(implicit
    F: FromBinding[F]
  ): Variant[F, scala.Either[L, R]] = {
    val leftTerm: Term[F, scala.Either[L, R], Left[L, R]]   = Term("Left", left(l), Doc.Empty, scala.List.empty)
    val rightTerm: Term[F, scala.Either[L, R], Right[L, R]] = Term("Right", right(r), Doc.Empty, scala.List.empty)
    Variant(
      scala.List(leftTerm, rightTerm),
      TypeName.either[L, R],
      F.fromBinding(Binding.Variant.either[L, R]),
      Doc.Empty,
      scala.List.empty
    )
  }

  def tuple2[F[_, _], A, B](_1: Reflect[F, A], _2: Reflect[F, B])(implicit F: FromBinding[F]): Record[F, (A, B)] =
    Record(
      scala.List(Term("_1", _1, Doc.Empty, scala.List.empty), Term("_2", _2, Doc.Empty, scala.List.empty)),
      TypeName.tuple2[A, B],
      F.fromBinding(Binding.Record.tuple2[A, B]),
      Doc.Empty,
      scala.List.empty
    )

  def tuple3[F[_, _], A, B, C](_1: Reflect[F, A], _2: Reflect[F, B], _3: Reflect[F, C])(implicit
    F: FromBinding[F]
  ): Record[F, (A, B, C)] =
    Record(
      scala.List(
        Term("_1", _1, Doc.Empty, scala.List.empty),
        Term("_2", _2, Doc.Empty, scala.List.empty),
        Term("_3", _3, Doc.Empty, scala.List.empty)
      ),
      TypeName.tuple3[A, B, C],
      F.fromBinding(Binding.Record.tuple3[A, B, C]),
      Doc.Empty,
      scala.List.empty
    )

  def tuple4[F[_, _], A, B, C, D](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D)] =
    Record(
      scala.List(
        Term("_1", _1, Doc.Empty, scala.List.empty),
        Term("_2", _2, Doc.Empty, scala.List.empty),
        Term("_3", _3, Doc.Empty, scala.List.empty),
        Term("_4", _4, Doc.Empty, scala.List.empty)
      ),
      TypeName.tuple4[A, B, C, D],
      F.fromBinding(Binding.Record.tuple4[A, B, C, D]),
      Doc.Empty,
      scala.List.empty
    )

  def tuple5[F[_, _], A, B, C, D, E](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E)] =
    Record(
      scala.List(
        Term("_1", _1, Doc.Empty, scala.List.empty),
        Term("_2", _2, Doc.Empty, scala.List.empty),
        Term("_3", _3, Doc.Empty, scala.List.empty),
        Term("_4", _4, Doc.Empty, scala.List.empty),
        Term("_5", _5, Doc.Empty, scala.List.empty)
      ),
      TypeName.tuple5[A, B, C, D, E],
      F.fromBinding(Binding.Record.tuple5[A, B, C, D, E]),
      Doc.Empty,
      scala.List.empty
    )

  object Extractors {
    object List {
      def unapply[F[_, _], A](reflect: Reflect[F, scala.List[A]]): scala.Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.list => Some(element)
          case _                                                     => None
        }
    }
    object Vector {
      def unapply[F[_, _], A](reflect: Reflect[F, scala.Vector[A]]): scala.Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.vector => Some(element)
          case _                                                       => None
        }
    }
    object Set {
      def unapply[F[_, _], A](reflect: Reflect[F, Predef.Set[A]]): scala.Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.set => Some(element)
          case _                                                    => None
        }
    }
    object Array {
      def unapply[F[_, _], A](reflect: Reflect[F, scala.Array[A]]): scala.Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.array => Some(element)
          case _                                                      => None
        }
    }
    object Option {
      def unapply[F[_, _], A](reflect: Reflect[F, scala.Option[A]]): scala.Option[Reflect[F, A]] =
        reflect match {
          case Variant(noneTerm :: someTerm :: Nil, tn, _, _, _) if tn == TypeName.option =>
            (noneTerm, someTerm) match {
              case (Term("None", _, _, _), Term("Some", element, _, _)) => Some(element.asInstanceOf[Reflect[F, A]])
              case _                                                    => None
            }
          case _ => None
        }
    }
    object Either {
      def unapply[F[_, _], L, R](
        reflect: Reflect[F, scala.Either[L, R]]
      ): scala.Option[(Reflect[F, L], Reflect[F, R])] =
        reflect match {
          case Variant(leftTerm :: rightTerm :: Nil, tn, _, _, _) if tn == TypeName.either =>
            (leftTerm, rightTerm) match {
              case (Term("Left", left, _, _), Term("Right", right, _, _)) =>
                Some((left.asInstanceOf[Reflect[F, L]], right.asInstanceOf[Reflect[F, R]]))
              case _ => None
            }
          case _ => None
        }
    }
  }
}
