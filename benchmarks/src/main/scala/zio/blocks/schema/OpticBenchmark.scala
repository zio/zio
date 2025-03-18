package zio.blocks.schema

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.{Binding, Constructor, Deconstructor, RegisterOffset, Registers}
import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
class LensGetBenchmark {
  import Domain._

  var a: A = A(B(C(D(E("test")))))

  @Benchmark
  def direct: String = a.b.c.d.e.s

  @Benchmark
  def monocle: String = A.b_c_d_e_s_monocle_get.get(a)

  @Benchmark
  def zioBlocks: String = A.b_c_d_e_s.get(a)
}

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
class LensSetBenchmark {
  import Domain._

  var a: A = A(B(C(D(E("test")))))

  @Benchmark
  def direct: A = a.copy(b = a.b.copy(c = a.b.c.copy(d = a.b.c.d.copy(e = a.b.c.d.e.copy(s = "test2")))))

  @Benchmark
  def monocle: A = A.b_c_d_e_s_monocle_set.replace("test2").apply(a)

  @Benchmark
  def quicklens: A = A.b_c_d_e_s_qiocklens.apply(a).setTo("test2")

  @Benchmark
  def zioBlocks: A = A.b_c_d_e_s.set(a, "test2")
}

object Domain {
  case class E(s: String)

  object E {
    val reflect: Reflect.Record.Bound[E] = Reflect.Record(
      fields = List(
        Term("s", Reflect.string, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "E"),
      recordBinding = Binding.Record(
        constructor = new Constructor[E] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): E =
            E(in.getObject(baseOffset, 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[E] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: E): Unit =
            out.setObject(baseOffset, 0, in.s)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val s: Lens.Bound[E, String] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[E, String]])
  }

  case class D(e: E)

  object D {
    val reflect: Reflect.Record.Bound[D] = Reflect.Record(
      fields = List(
        Term("e", E.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "D"),
      recordBinding = Binding.Record(
        constructor = new Constructor[D] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): D =
            D(in.getObject(baseOffset, 0).asInstanceOf[E])
        },
        deconstructor = new Deconstructor[D] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: D): Unit =
            out.setObject(baseOffset, 0, in.e)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val e: Lens.Bound[D, E] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[D, E]])
  }

  case class C(d: D)

  object C {
    val reflect: Reflect.Record.Bound[C] = Reflect.Record(
      fields = List(
        Term("d", D.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "C"),
      recordBinding = Binding.Record(
        constructor = new Constructor[C] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): C =
            C(in.getObject(baseOffset, 0).asInstanceOf[D])
        },
        deconstructor = new Deconstructor[C] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: C): Unit =
            out.setObject(baseOffset, 0, in.d)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val d: Lens.Bound[C, D] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[C, D]])
  }

  case class B(c: C)

  object B {
    val reflect: Reflect.Record.Bound[B] = Reflect.Record(
      fields = List(
        Term("c", C.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "B"),
      recordBinding = Binding.Record(
        constructor = new Constructor[B] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): B =
            B(in.getObject(baseOffset, 0).asInstanceOf[C])
        },
        deconstructor = new Deconstructor[B] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: B): Unit =
            out.setObject(baseOffset, 0, in.c)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val c: Lens.Bound[B, C] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[B, C]])
  }

  case class A(b: B)

  object A {
    val reflect: Reflect.Record.Bound[A] = Reflect.Record(
      fields = List(
        Term("b", B.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "A"),
      recordBinding = Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): A =
            A(in.getObject(baseOffset, 0).asInstanceOf[B])
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit =
            out.setObject(baseOffset, 0, in.b)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val b: Lens.Bound[A, B]              = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[A, B]])
    val b_c_d_e_s: Lens.Bound[A, String] = b.apply(B.c).apply(C.d).apply(D.e).apply(E.s)

    import com.softwaremill.quicklens._

    val b_c_d_e_s_qiocklens =
      (modify(_: A)(_.b))
        .andThenModify(modify(_: B)(_.c))
        .andThenModify(modify(_: C)(_.d))
        .andThenModify(modify(_: D)(_.e))
        .andThenModify(modify(_: E)(_.s))

    import monocle.Focus

    val b_c_d_e_s_monocle_get =
      Focus[A](_.b).andThen(Focus[B](_.c)).andThen(Focus[C](_.d)).andThen(Focus[D](_.e)).andThen(Focus[E](_.s)).asGetter

    val b_c_d_e_s_monocle_set =
      Focus[A](_.b).andThen(Focus[B](_.c)).andThen(Focus[C](_.d)).andThen(Focus[D](_.e)).andThen(Focus[E](_.s)).asSetter
  }
}
