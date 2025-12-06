package zio.test

object SmartAssertionScala3Spec extends ZIOBaseSpec {
  import zio.test.*

  // the `Out` is only necessary to make the `In` opaque to the assertTrue macro
  type Out = Out.In
  object Out {
    opaque type In = Int
    object In {
      def make(in: Int): Either[String, Option[In]] = Right(Some(in))
      extension (x: In) {
        def value: Int = x
      }
    }
  }

  override def spec =
    suite("SmartAssertionScala3Spec")(
      suite("new instance creation")(
        test("anonymous class (trait) with overload and type args - new instance") {
          trait ClassWithOverload[X] {
            def overloaded: Int         = 1
            def overloaded(x: Int): Int = 1
          }
          assertTrue(new ClassWithOverload[Int]() {}.overloaded == 1)
        },
        test("anonymous class with overload - new instance") {
          class ClassWithOverload {
            def overloaded: Int         = 1
            def overloaded(x: Int): Int = 1
          }
          assertTrue(new ClassWithOverload() {}.overloaded == 1)
        },
        test("anonymous class (trait) with overload - new instance") {
          trait ClassWithOverload {
            def overloaded: Int         = 1
            def overloaded(x: Int): Int = 1
          }
          assertTrue(new ClassWithOverload() {}.overloaded == 1)
        },
        test("trait with parameter and overloaded methods") {
          trait TraitOverloadedWithParameter(x: Int) {
            def overloaded: Int = 1

            def overloaded(x: Int) = 1
          }

          assertTrue(new TraitOverloadedWithParameter(1) {}.overloaded == 1)
        },
        test("trait with overloaded methods with type args") {
          trait TraitOverloadedAndTypeArgs[A] {
            def overloaded: Int = 1

            def overloaded(x: Int) = 1
          }

          assertTrue(new TraitOverloadedAndTypeArgs[Int] {}.overloaded == 1)
        },
        test("trait with parameter and overloaded methods with type args") {
          trait TraitOverloadedWithParameterAndTypeArgs[A](x: A) {
            def overloaded: Int = 1

            def overloaded(x: Int) = 1
          }

          assertTrue(new TraitOverloadedWithParameterAndTypeArgs[Int](1) {}.overloaded == 1)
        },
        test("inlined trait with overloaded methods and parameter and type arg") {
          trait TraitOverloadedWithParameterAndTypeArgs[A](x: A) {
            def overloaded: Int = 1

            def overloaded(x: Int) = 1
          }
          @scala.annotation.nowarn
          inline def create = new TraitOverloadedWithParameterAndTypeArgs[Int](1) {}

          assertTrue(create.overloaded == 1)
        },
        test("inlined trait with overloaded methods and parameter") {
          trait TraitOverloadedWithParameter(x: Int) {
            def overloaded: Int = 1

            def overloaded(x: Int) = 1
          }
          @scala.annotation.nowarn
          inline def create =
            new TraitOverloadedWithParameter(1) {}

          assertTrue(create.overloaded == 1)
        },
        test("inlined class with overloaded methods and parameter") {
          class ClassOverloadedWithParameter(x: Int) {
            def overloaded: Int = 1

            def overloaded(x: Int) = 1
          }

          @scala.annotation.nowarn
          inline def create =
            new ClassOverloadedWithParameter(1)

          assertTrue(create.overloaded == 1)
        }
      ),
      test("opaque type lensing (i10165)") {
        assertTrue(Out.In.make(42).is(_.right).get.value == 42)
      }
    )

}
