package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Reflect.Primitive
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.test.Assertion._
import zio.test._

object SchemaSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("SchemaSpec")(
    suite("primitive")(
      test("has consistent equals and hashCode") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.None),
          primitiveBinding = null.asInstanceOf[Binding.Primitive[Long]], // should be ignored in equals and hashCode
          typeName = TypeName.long,
          doc = Doc.Empty,
          modifiers = Nil
        )
        val long2 = long1.copy(primitiveType = PrimitiveType.Long(Validation.Numeric.Positive))
        val long3 = long1.copy(typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Long1"))
        val long4 = long1.copy(doc = Doc("text"))
        val long5 = long1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Schema[Long])(equalTo(Schema[Long])) &&
        assert(Schema[Long].hashCode)(equalTo(Schema[Long].hashCode)) &&
        assert(Schema(long1))(equalTo(Schema[Long])) &&
        assert(Schema(long1).hashCode)(equalTo(Schema[Long].hashCode)) &&
        assert(Schema(long2))(not(equalTo(Schema[Long]))) &&
        assert(Schema(long3))(not(equalTo(Schema[Long]))) &&
        assert(Schema(long4))(not(equalTo(Schema[Long]))) &&
        assert(Schema(long5))(not(equalTo(Schema[Long])))
      }
    ),
    suite("record")(
      test("has consistent equals and hashCode") {
        val record1 = Reflect.Record(
          fields = List[Term[Binding, Record, _]](
            Term("b", Reflect.byte, Doc.Empty, Nil),
            Term("i", Reflect.int, Doc.Empty, Nil)
          ),
          typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record"),
          recordBinding = null.asInstanceOf[Binding.Record[Record]], // should be ignored in equals and hashCode
          doc = Doc.Empty,
          modifiers = Nil
        )
        val record2 = record1.copy(typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record2"))
        val record3 = record1.copy(fields = record1.fields.reverse)
        val record4 = record1.copy(doc = Doc("text"))
        val record5 = record1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Record.schema)(equalTo(Record.schema)) &&
        assert(Record.schema.hashCode)(equalTo(Record.schema.hashCode)) &&
        assert(Schema(record1))(equalTo(Record.schema)) &&
        assert(Schema(record1).hashCode)(equalTo(Record.schema.hashCode)) &&
        assert(Schema(record2))(not(equalTo(Record.schema))) &&
        assert(Schema(record3))(not(equalTo(Record.schema))) &&
        assert(Schema(record4))(not(equalTo(Record.schema))) &&
        assert(Schema(record5))(not(equalTo(Record.schema)))
      },
      test("has consistent fields, length, registers and size") {
        val record1 = Record.schema.reflect.asInstanceOf[Reflect.Record[Binding, Record]]
        val record2 = Case1.schema.reflect.asInstanceOf[Reflect.Record[Binding, Case1]]
        val record3 = Case2.schema.reflect.asInstanceOf[Reflect.Record[Binding, Case2]]
        assert(record1.length)(equalTo(2)) &&
        assert(record1.fields.length)(equalTo(2)) &&
        assert(record1.registers.length)(equalTo(2)) &&
        assert(record1.fields(0).value.isInstanceOf[Primitive[Binding, Byte]])(equalTo(true)) &&
        assert(record1.registers(0).size)(equalTo(1L << RegisterOffset.BytesShift)) &&
        assert(record1.fields(1).value.isInstanceOf[Primitive[Binding, Int]])(equalTo(true)) &&
        assert(record1.registers(1).size)(equalTo(1L << RegisterOffset.IntsShift)) &&
        assert(record1.size)(equalTo(record1.registers.foldLeft(0L)(_ | _.size))) &&
        assert(record2.length)(equalTo(1)) &&
        assert(record2.fields.length)(equalTo(1)) &&
        assert(record2.registers.length)(equalTo(1)) &&
        assert(record2.fields(0).value.isInstanceOf[Primitive[Binding, Double]])(equalTo(true)) &&
        assert(record2.registers(0).size)(equalTo(1L << RegisterOffset.DoublesShift)) &&
        assert(record2.size)(equalTo(record2.registers.foldLeft(0L)(_ | _.size))) &&
        assert(record3.length)(equalTo(1)) &&
        assert(record3.fields.length)(equalTo(1)) &&
        assert(record3.registers.length)(equalTo(1)) &&
        assert(record3.fields(0).value.isInstanceOf[Primitive[Binding, String]])(equalTo(true)) &&
        assert(record3.registers(0).size)(equalTo(1L << RegisterOffset.ObjectsShift)) &&
        assert(record3.size)(equalTo(record3.registers.foldLeft(0L)(_ | _.size)))
      }
    ),
    suite("variant")(
      test("has consistent equals and hashCode") {
        val variant1 = Reflect.Variant[Binding, Variant](
          cases = List(
            Term("case1", Case1.schema.reflect, Doc.Empty, Nil),
            Term("case2", Case2.schema.reflect, Doc.Empty, Nil)
          ),
          typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Variant"),
          variantBinding = null.asInstanceOf[Binding.Variant[Variant]], // should be ignored in equals and hashCode
          doc = Doc.Empty,
          modifiers = Nil
        )
        val variant2 = variant1.copy(cases = variant1.cases.reverse)
        val variant3 = variant1.copy(typeName = TypeName(Namespace(List("zio", "blocks", "schema2"), Nil), "Variant"))
        val variant4 = variant1.copy(doc = Doc("text"))
        val variant5 = variant1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Variant.schema)(equalTo(Variant.schema)) &&
        assert(Variant.schema.hashCode)(equalTo(Variant.schema.hashCode)) &&
        assert(Schema(variant1))(equalTo(Variant.schema)) &&
        assert(Schema(variant1).hashCode)(equalTo(Variant.schema.hashCode)) &&
        assert(Schema(variant2))(not(equalTo(Variant.schema))) &&
        assert(Schema(variant3))(not(equalTo(Variant.schema))) &&
        assert(Schema(variant4))(not(equalTo(Variant.schema))) &&
        assert(Schema(variant5))(not(equalTo(Variant.schema)))
      }
    ),
    suite("sequence")(
      test("has consistent equals and hashCode") {
        val sequence1 = Reflect.Sequence[Binding, Double, List](
          element = Reflect.double,
          typeName = TypeName.list,
          seqBinding = null.asInstanceOf[Binding.Seq[List, Double]], // should be ignored in equals and hashCode
          doc = Doc.Empty,
          modifiers = Nil
        )
        val sequence2 = sequence1.copy(element =
          Primitive(PrimitiveType.Double(Validation.None), Binding.Primitive.double, TypeName.double, Doc("text"), Nil)
        )
        val sequence3 = sequence1.copy(typeName = TypeName[List[Double]](Namespace("scala" :: Nil, Nil), "List2"))
        val sequence4 = sequence1.copy(doc = Doc("text"))
        val sequence5 = sequence1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Schema[List[Double]])(equalTo(Schema[List[Double]])) &&
        assert(Schema[List[Double]].hashCode)(equalTo(Schema[List[Double]].hashCode)) &&
        assert(Schema(sequence1))(equalTo(Schema[List[Double]])) &&
        assert(Schema(sequence1).hashCode)(equalTo(Schema[List[Double]].hashCode)) &&
        assert(Schema(sequence2))(not(equalTo(Schema[List[Double]]))) &&
        assert(Schema(sequence3))(not(equalTo(Schema[List[Double]]))) &&
        assert(Schema(sequence4))(not(equalTo(Schema[List[Double]]))) &&
        assert(Schema(sequence5))(not(equalTo(Schema[List[Double]])))
      }
    ),
    suite("map")(
      test("has consistent equals and hashCode") {
        val map1 = Reflect.Map[Binding, Short, Float, Map](
          key = Reflect.short,
          value = Reflect.float,
          typeName = TypeName.map[Short, Float],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Short, Float]], // should be ignored in equals and hashCode
          doc = Doc.Empty,
          modifiers = Nil
        )
        val map2 = map1.copy(key =
          Primitive(
            PrimitiveType.Short(Validation.Numeric.Positive),
            Binding.Primitive.short,
            TypeName.short,
            Doc.Empty,
            Nil
          )
        )
        val map3 = map1.copy(value =
          Primitive(PrimitiveType.Float(Validation.None), Binding.Primitive.float, TypeName.float, Doc("text"), Nil)
        )
        val map4 = map1.copy(typeName = TypeName[Map[Short, Float]](Namespace("scala" :: Nil, Nil), "Map2"))
        val map5 = map1.copy(doc = Doc("text"))
        val map6 = map1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Schema[Map[Short, Float]])(equalTo(Schema[Map[Short, Float]])) &&
        assert(Schema[Map[Short, Float]].hashCode)(equalTo(Schema[Map[Short, Float]].hashCode)) &&
        assert(Schema(map1))(equalTo(Schema[Map[Short, Float]])) &&
        assert(Schema(map1).hashCode)(equalTo(Schema[Map[Short, Float]].hashCode)) &&
        assert(Schema(map2))(not(equalTo(Schema[Map[Short, Float]]))) &&
        assert(Schema(map3))(not(equalTo(Schema[Map[Short, Float]]))) &&
        assert(Schema(map4))(not(equalTo(Schema[Map[Short, Float]]))) &&
        assert(Schema(map5))(not(equalTo(Schema[Map[Short, Float]]))) &&
        assert(Schema(map6))(not(equalTo(Schema[Map[Short, Float]])))
      }
    ),
    suite("dynamic")(
      test("has consistent equals and hashCode") {
        val dynamic1 = Reflect.Dynamic[Binding](
          dynamicBinding = Binding.Dynamic(),
          doc = Doc.Empty,
          modifiers = Nil
        )
        val dynamic2 = dynamic1.copy(dynamicBinding = null.asInstanceOf[Binding.Dynamic])
        val dynamic3 = dynamic1.copy(doc = Doc("text"))
        val dynamic4 = dynamic1.copy(modifiers = List(Modifier.config("key", "value")))

        assert(Schema(dynamic1))(equalTo(Schema(dynamic1))) &&
        assert(Schema(dynamic1).hashCode)(equalTo(Schema(dynamic1).hashCode)) &&
        assert(Schema(dynamic2))(equalTo(Schema(dynamic1))) &&
        assert(Schema(dynamic2).hashCode)(equalTo(Schema(dynamic1).hashCode)) &&
        assert(Schema(dynamic3))(not(equalTo(Schema(dynamic1)))) &&
        assert(Schema(dynamic4))(not(equalTo(Schema(dynamic1))))
      }
    ),
    suite("deferred")(
      test("has consistent equals and hashCode") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred2 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred3 = Reflect.Deferred[Binding, Int](() =>
          Primitive(PrimitiveType.Int(Validation.Numeric.Positive), Binding.Primitive.int, TypeName.int, Doc.Empty, Nil)
        )

        assert(Schema(deferred1))(equalTo(Schema(deferred1))) &&
        assert(Schema(deferred1).hashCode)(equalTo(Schema(deferred1).hashCode)) &&
        assert(Schema(deferred2))(equalTo(Schema(deferred1))) &&
        assert(Schema(deferred2).hashCode)(equalTo(Schema(deferred1).hashCode)) &&
        assert(Schema(deferred3))(not(equalTo(Schema(deferred1))))
      }
    )
  )

  case class Record(b: Byte, i: Int)

  object Record {
    val schema: Schema[Record] = Schema(
      reflect = Reflect.Record[Binding, Record](
        fields = List(
          Term("b", Reflect.byte, Doc.Empty, Nil),
          Term("i", Reflect.int, Doc.Empty, Nil)
        ),
        typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record"),
        recordBinding = Binding.Record(
          constructor = new Constructor[Record] {
            def size: RegisterOffset = RegisterOffset(bytes = 1, ints = 1)

            def construct(in: Registers, baseOffset: RegisterOffset): Record =
              Record(in.getByte(baseOffset, 0), in.getInt(baseOffset, 0))
          },
          deconstructor = new Deconstructor[Record] {
            def size: RegisterOffset = RegisterOffset(bytes = 1, ints = 1)

            def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Record): Unit = {
              out.setByte(baseOffset, 0, in.b)
              out.setInt(baseOffset, 0, in.i)
            }
          }
        ),
        doc = Doc.Empty,
        modifiers = Nil
      )
    )
  }

  sealed trait Variant

  object Variant {
    val schema: Schema[Variant] = Schema(
      reflect = Reflect.Variant[Binding, Variant](
        cases = List(
          Term("case1", Case1.schema.reflect, Doc.Empty, Nil),
          Term("case2", Case2.schema.reflect, Doc.Empty, Nil)
        ),
        typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Variant"),
        variantBinding = Binding.Variant(
          discriminator = new Discriminator[Variant] {
            override def discriminate(a: Variant): Int = a match {
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
    )
  }

  case class Case1(d: Double) extends Variant

  object Case1 {
    val schema: Schema[Case1] = Schema(
      reflect = Reflect.Record(
        fields = List[Term[Binding, Case1, ?]](
          Term("d", Reflect.double, Doc.Empty, Nil)
        ),
        typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Case1"),
        recordBinding = Binding.Record(
          constructor = new Constructor[Case1] {
            def size: RegisterOffset = RegisterOffset(doubles = 1)

            def construct(in: Registers, baseOffset: RegisterOffset): Case1 =
              Case1(in.getDouble(baseOffset, 0))
          },
          deconstructor = new Deconstructor[Case1] {
            def size: RegisterOffset = RegisterOffset(doubles = 1)

            def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case1): Unit =
              out.setDouble(baseOffset, 0, in.d)
          }
        ),
        doc = Doc.Empty,
        modifiers = Nil
      )
    )
  }

  case class Case2(s: String) extends Variant

  object Case2 {
    val schema: Schema[Case2] = Schema(
      reflect = Reflect.Record(
        fields = List[Term[Binding, Case2, ?]](
          Term("s", Reflect.string, Doc.Empty, Nil)
        ),
        typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Case2"),
        recordBinding = Binding.Record(
          constructor = new Constructor[Case2] {
            def size: RegisterOffset = RegisterOffset(objects = 1)

            def construct(in: Registers, baseOffset: RegisterOffset): Case2 =
              Case2(in.getObject(baseOffset, 0).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[Case2] {
            def size: RegisterOffset = RegisterOffset(objects = 1)

            def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case2): Unit =
              out.setObject(baseOffset, 0, in.s)
          }
        ),
        doc = Doc.Empty,
        modifiers = Nil
      )
    )
  }
}
