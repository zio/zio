package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object OpticSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("OpticSpec")(
    suite("Lens")(
      test("has consistent equals and hashCode") {
        assert(Record1.b)(equalTo(Record1.b)) &&
        assert(Record1.b.hashCode)(equalTo(Record1.b.hashCode)) &&
        assert(Record2.r1_b)(equalTo(Record2.r1_b)) &&
        assert(Record2.r1_b.hashCode)(equalTo(Record2.r1_b.hashCode)) &&
        assert(Record1.f: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.l: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.li: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.r1: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.r1_f: Any)(not(equalTo(Record2.r1_b)))
      },
      test("has associative equals and hashCode") {
        assert(Record3.r2_r1_b_left_associative: Any)(equalTo(Record3.r2_r1_b_right_associative)) &&
        assert(Record3.r2_r1_b_left_associative.hashCode)(equalTo(Record3.r2_r1_b_right_associative.hashCode))
      }
    ),
    suite("Prism")(
      test("has consistent equals and hashCode") {
        assert(Variant1.c1)(equalTo(Variant1.c1)) &&
        assert(Variant1.c1.hashCode)(equalTo(Variant1.c1.hashCode)) &&
        assert(Variant1.c2: Any)(not(equalTo(Variant1.c1)))
      }
    ),
    suite("Optional")(
      test("has consistent equals and hashCode") {
        assert(Variant1.c1_d)(equalTo(Variant1.c1_d)) &&
        assert(Variant1.c1_d.hashCode)(equalTo(Variant1.c1_d.hashCode)) &&
        assert(Variant1.c2_r3_r2_r1_b_right_associative)(equalTo(Variant1.c2_r3_r2_r1_b_right_associative)) &&
        assert(Variant1.c2_r3_r2_r1_b_right_associative.hashCode)(
          equalTo(Variant1.c2_r3_r2_r1_b_right_associative.hashCode)
        ) &&
        assert(Variant1.c2_r3: Any)(not(equalTo(Variant1.c1_d))) &&
        assert(Variant1.c2_r3: Any)(not(equalTo(Variant1.c2_r3_r2_r1_b_right_associative)))
      },
      test("has associative equals and hashCode") {
        assert(Variant1.c2_r3_r2_r1_b_left_associative)(equalTo(Variant1.c2_r3_r2_r1_b_right_associative)) &&
        assert(Variant1.c2_r3_r2_r1_b_left_associative.hashCode)(
          equalTo(Variant1.c2_r3_r2_r1_b_right_associative.hashCode)
        )
      }
    ),
    suite("Traversal")(
      test("has consistent equals and hashCode") {
        assert(Record2.li)(equalTo(Record2.li)) &&
        assert(Record2.li.hashCode)(equalTo(Record2.li.hashCode)) &&
        assert(Record2.r1_f: Any)(not(equalTo(Record2.li)))
      }
    )
  )

  case class Record1(b: Boolean, f: Float)

  object Record1 {
    val reflect: Reflect.Record[Binding, Record1] = Reflect.Record(
      fields = List[Term[Binding, Record1, ?]](
        Term("b", Reflect.boolean, Doc.Empty, Nil),
        Term("f", Reflect.float, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record1"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Record1] {
          def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1, floats = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): Record1 =
            Record1(in.getBoolean(baseOffset, 0), in.getFloat(baseOffset, 0))
        },
        deconstructor = new Deconstructor[Record1] {
          def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1, floats = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Record1): Unit = {
            out.setBoolean(baseOffset, 0, in.b)
            out.setFloat(baseOffset, 0, in.f)
          }
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val b: Lens[Binding, Record1, Boolean] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Record1, Boolean]])
    val f: Lens[Binding, Record1, Float]   = Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Record1, Float]])
  }

  case class Record2(l: Long, li: List[Int], r1: Record1)

  object Record2 {
    val reflect: Reflect.Record[Binding, Record2] = Reflect.Record[Binding, Record2](
      fields = List(
        Term("l", Reflect.long, Doc.Empty, Nil),
        Term("li", Reflect.list(Reflect.int), Doc.Empty, Nil),
        Term("r1", Record1.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record2"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Record2] {
          def usedRegisters: RegisterOffset = RegisterOffset(longs = 1, objects = 2)

          def construct(in: Registers, baseOffset: RegisterOffset): Record2 =
            Record2(
              in.getLong(baseOffset, 0),
              in.getObject(baseOffset, 0).asInstanceOf[List[Int]],
              in.getObject(baseOffset, 1).asInstanceOf[Record1]
            )
        },
        deconstructor = new Deconstructor[Record2] {
          def usedRegisters: RegisterOffset = RegisterOffset(longs = 1, objects = 2)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Record2): Unit = {
            out.setLong(baseOffset, 0, in.l)
            out.setObject(baseOffset, 0, in.li)
            out.setObject(baseOffset, 1, in.r1)
          }
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val l: Lens[Binding, Record2, Long] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Record2, Long]])
    val li: Traversal[Binding, Record2, Int] =
      Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Record2, List[Int]]]).list
    val r1: Lens[Binding, Record2, Record1] =
      Lens(reflect, reflect.fields(2).asInstanceOf[Term.Bound[Record2, Record1]])
    val r1_b: Lens[Binding, Record2, Boolean] = r1(Record1.b)
    val r1_f: Lens[Binding, Record2, Float]   = r1(Record1.f)
  }

  case class Record3(r1: Record1, r2: Record2)

  object Record3 {
    val reflect: Reflect.Record[Binding, Record3] = Reflect.Record[Binding, Record3](
      fields = List(
        Term("r1", Record1.reflect, Doc.Empty, Nil),
        Term("r2", Record2.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record3"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Record3] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 2)

          def construct(in: Registers, baseOffset: RegisterOffset): Record3 =
            Record3(
              in.getObject(baseOffset, 0).asInstanceOf[Record1],
              in.getObject(baseOffset, 1).asInstanceOf[Record2]
            )
        },
        deconstructor = new Deconstructor[Record3] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 2)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Record3): Unit = {
            out.setObject(baseOffset, 0, in.r1)
            out.setObject(baseOffset, 1, in.r2)
          }
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val r1: Lens[Binding, Record3, Record1] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Record3, Record1]])
    val r2: Lens[Binding, Record3, Record2] =
      Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Record3, Record2]])
    val r2_r1_b_left_associative: Lens[Binding, Record3, Boolean]  = r2(Record2.r1)(Record1.b)
    val r2_r1_b_right_associative: Lens[Binding, Record3, Boolean] = r2(Record2.r1(Record1.b))
  }

  sealed trait Variant1

  object Variant1 {
    val reflect: Reflect.Variant[Binding, Variant1] = Reflect.Variant[Binding, Variant1](
      cases = List(
        Term("case1", Case1.reflect, Doc.Empty, Nil),
        Term("case2", Case2.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Variant"),
      variantBinding = Binding.Variant(
        discriminator = new Discriminator[Variant1] {
          override def discriminate(a: Variant1): Int = a match {
            case _: Case1 => 0
            case _: Case2 => 1
          }
        },
        matchers = Matchers(
          new Matcher[Case1] {
            override def unsafeDowncast(a: Any): Case1 = a.asInstanceOf[Case1]
          },
          new Matcher[Case2] {
            override def unsafeDowncast(a: Any): Case2 = a.asInstanceOf[Case2]
          }
        )
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val c1: Prism[Binding, Variant1, Case1]                                   = Prism(reflect, reflect.cases(0).asInstanceOf[Term.Bound[Variant1, Case1]])
    val c2: Prism[Binding, Variant1, Case2]                                   = Prism(reflect, reflect.cases(1).asInstanceOf[Term.Bound[Variant1, Case2]])
    val c1_d: Optional[Binding, Variant1, Double]                             = c1(Case1.d)
    val c2_r3: Optional[Binding, Variant1, Record3]                           = c2(Case2.r3)
    val c2_r3_r2_r1_b_left_associative: Optional[Binding, Variant1, Boolean]  = c2_r3(Record3.r2_r1_b_left_associative)
    val c2_r3_r2_r1_b_right_associative: Optional[Binding, Variant1, Boolean] = c2_r3(Record3.r2_r1_b_right_associative)
  }

  case class Case1(d: Double) extends Variant1

  object Case1 {
    val reflect: Reflect.Record[Binding, Case1] = Reflect.Record(
      fields = List[Term[Binding, Case1, ?]](
        Term("d", Reflect.double, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Case1"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Case1] {
          def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): Case1 =
            Case1(in.getDouble(baseOffset, 0))
        },
        deconstructor = new Deconstructor[Case1] {
          def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case1): Unit =
            out.setDouble(baseOffset, 0, in.d)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val d: Lens[Binding, Case1, Double] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case1, Double]])
  }

  case class Case2(r: Record3) extends Variant1

  object Case2 {
    val reflect: Reflect.Record[Binding, Case2] = Reflect.Record(
      fields = List[Term[Binding, Case2, ?]](
        Term("r3", Record3.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Case2"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Case2] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): Case2 =
            Case2(in.getObject(baseOffset, 0).asInstanceOf[Record3])
        },
        deconstructor = new Deconstructor[Case2] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case2): Unit =
            out.setObject(baseOffset, 0, in.r)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val r3: Lens[Binding, Case2, Record3] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case2, Record3]])
  }
}
