package zio.test.mock

import zio.Has
import zio.test.Assertion._
import zio.test._
import zio.test.mock.modules._

/**
 * https://github.com/scalamacros/paradise/issues/75
 *
 * We can't typecheck @mockable with typeCheck
 */
object MockableSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] = suite("MockableSpec")(
    suite("Mockable macro")(
      test("compiles when applied to object with empty Service") {
        assert({
          @mockable[EmptyModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[EmptyModule] = ModuleMock
          }

          Check
        })(anything)
      },
      test("generates mocks retaining body on objects") {
        assert({
          @mockable[SinglePureValModule.Service]
          object ModuleMock {
            val someFooHelper: Expectation[Has[SinglePureValModule.Service]] = Foo().atLeast(1)
          }

          object Check {
            val mock: Mock[SinglePureValModule] = ModuleMock

            val Foo: ModuleMock.Effect[Unit, Nothing, Unit] = ModuleMock.Foo

            val someFoo: Expectation[Has[SinglePureValModule.Service]] = ModuleMock.someFooHelper
          }

          Check
        })(anything)
      },
      testM("fails when applied to object without type arg") {
        assertM(typeCheck {
          """
            @mockable
            object ModuleMock
          """
        })(isLeft(anything))
      },
      testM("fails when applied to trait") {
        assertM(typeCheck {
          """
            object Module {
              trait Service
            }

            @mockable[Module.Service]
            trait ModuleMock
          """
        })(isLeft(anything))
      },
      testM("fails when applied to class") {
        assertM(typeCheck {
          """
            object Module {
              trait Service
            }

            @mockable[Module.Service]
            class ModuleMock
          """
        })(isLeft(anything))
      },
      test("generates mocks for pure value") {
        assert({
          @mockable[SinglePureValModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[SinglePureValModule] = ModuleMock

            val Foo: ModuleMock.Effect[Unit, Nothing, Unit] = ModuleMock.Foo
          }

          Check
        })(anything)
      },
      test("generates mocks for simple pure methods") {
        assert({
          @mockable[SimplePureDefsModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[SimplePureDefsModule] = ModuleMock

            val Static: ModuleMock.Effect[Unit, String, String]                        = ModuleMock.Static
            val ZeroParams: ModuleMock.Effect[Unit, String, String]                    = ModuleMock.ZeroParams
            val ZeroParamsWithParens: ModuleMock.Effect[Unit, String, String]          = ModuleMock.ZeroParamsWithParens
            val SingleParam: ModuleMock.Effect[Int, String, String]                    = ModuleMock.SingleParam
            val ManyParams: ModuleMock.Effect[(Int, String, Long), String, String]     = ModuleMock.ManyParams
            val ManyParamLists: ModuleMock.Effect[(Int, String, Long), String, String] = ModuleMock.ManyParamLists
          }

          Check
        })(anything)
      },
      test("generates mocks for simple impure methods") {
        assert({
          @mockable[SimpleImpureDefsModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[SimpleImpureDefsModule] = ModuleMock

            val ZeroParams: ModuleMock.Method[Unit, Throwable, String]                    = ModuleMock.ZeroParams
            val ZeroParamsWithParens: ModuleMock.Method[Unit, Throwable, String]          = ModuleMock.ZeroParamsWithParens
            val SingleParam: ModuleMock.Method[Int, Throwable, String]                    = ModuleMock.SingleParam
            val ManyParams: ModuleMock.Method[(Int, String, Long), Throwable, String]     = ModuleMock.ManyParams
            val ManyParamLists: ModuleMock.Method[(Int, String, Long), Throwable, String] = ModuleMock.ManyParamLists
          }

          Check
        })(anything)
      },
      test("generates mocks for overloaded pure methods") {
        assert({
          @mockable[OverloadedPureDefsModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[OverloadedPureDefsModule] = ModuleMock

            val Overloaded_0: ModuleMock.Effect[Int, String, String]  = ModuleMock.Overloaded._0
            val Overloaded_1: ModuleMock.Effect[Long, String, String] = ModuleMock.Overloaded._1
          }

          Check
        })(anything)
      },
      test("generates mocks for overloaded impure methods") {
        assert({
          @mockable[OverloadedImpureDefsModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[OverloadedImpureDefsModule] = ModuleMock

            val Overloaded_0: ModuleMock.Method[Int, Throwable, String]  = ModuleMock.Overloaded._0
            val Overloaded_1: ModuleMock.Method[Long, Throwable, String] = ModuleMock.Overloaded._1
          }

          Check
        })(anything)
      },
      test("generates mocks for poly pure methods") {
        assert({
          @mockable[PolyPureDefsModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[PolyPureDefsModule] = ModuleMock

            val PolyInput: ModuleMock.Poly.Effect.Input[String, String]       = ModuleMock.PolyInput
            val PolyError: ModuleMock.Poly.Effect.Error[String, String]       = ModuleMock.PolyError
            val PolyOutput: ModuleMock.Poly.Effect.Output[String, String]     = ModuleMock.PolyOutput
            val PolyInputError: ModuleMock.Poly.Effect.InputError[String]     = ModuleMock.PolyInputError
            val PolyInputOutput: ModuleMock.Poly.Effect.InputOutput[String]   = ModuleMock.PolyInputOutput
            val PolyErrorOutput: ModuleMock.Poly.Effect.ErrorOutput[String]   = ModuleMock.PolyErrorOutput
            val PolyInputErrorOutput: ModuleMock.Poly.Effect.InputErrorOutput = ModuleMock.PolyInputErrorOutput
            val PolyMixed: ModuleMock.Poly.Effect.Output[Unit, String]        = ModuleMock.PolyMixed
            val PolyBounded: ModuleMock.Poly.Effect.Output[Unit, String]      = ModuleMock.PolyBounded
          }

          Check
        })(anything)
      },
      test("generates mocks for poly impure methods") {
        assert({
          @mockable[PolyImpureDefsModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[PolyImpureDefsModule] = ModuleMock

            val PolyInput: ModuleMock.Poly.Method.Input[Throwable, String]          = ModuleMock.PolyInput
            val PolyError: ModuleMock.Method[String, Throwable, String]             = ModuleMock.PolyError
            val PolyOutput: ModuleMock.Poly.Method.Output[String, Throwable]        = ModuleMock.PolyOutput
            val PolyInputError: ModuleMock.Poly.Method.Input[Throwable, String]     = ModuleMock.PolyInputError
            val PolyInputOutput: ModuleMock.Poly.Method.InputOutput[Throwable]      = ModuleMock.PolyInputOutput
            val PolyErrorOutput: ModuleMock.Poly.Method.Output[String, Throwable]   = ModuleMock.PolyErrorOutput
            val PolyInputErrorOutput: ModuleMock.Poly.Method.InputOutput[Throwable] = ModuleMock.PolyInputErrorOutput
            val PolyMixed: ModuleMock.Poly.Method.Output[Unit, Throwable]           = ModuleMock.PolyMixed
            val PolyBounded: ModuleMock.Poly.Method.Output[Unit, Throwable]         = ModuleMock.PolyBounded
          }

          Check
        })(anything)
      },
      test("generates mocks for varargs pure methods") {
        assert({
          @mockable[VarargsPureDefsModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[VarargsPureDefsModule] = ModuleMock

            val SimpleVarargs: ModuleMock.Effect[(Int, Seq[String]), String, Int] = ModuleMock.SimpleVarargs
            val CurriedVarargs: ModuleMock.Effect[(Int, Seq[String], Long, Seq[Double]), String, Int] =
              ModuleMock.CurriedVarargs
          }

          Check
        })(anything)
      },
      test("generates mocks for varargs impure methods") {
        assert({
          @mockable[VarargsImpureDefsModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[VarargsImpureDefsModule] = ModuleMock

            val SimpleVarargs: ModuleMock.Method[(Int, Seq[String]), Throwable, String] = ModuleMock.SimpleVarargs
            val CurriedVarargs: ModuleMock.Method[(Int, Seq[String], Long, Seq[Double]), Throwable, String] =
              ModuleMock.CurriedVarargs
          }

          Check
        })(anything)
      },
      test("generates mocks for service with default pure method implementations") {
        assert({
          @mockable[DefaultImplPureDefsModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[DefaultImplPureDefsModule] = ModuleMock

            val Foo: ModuleMock.Effect[Int, String, String]    = ModuleMock.Foo
            val Bar: ModuleMock.Effect[String, String, String] = ModuleMock.Bar
          }

          Check
        })(anything)
      },
      test("generates mocks for service with default impure method implementations") {
        assert({
          @mockable[DefaultImplImpureDefsModule.Service]
          object ModuleMock

          object Check {
            val mock: Mock[DefaultImplImpureDefsModule] = ModuleMock

            val Foo: ModuleMock.Method[Int, Throwable, String]    = ModuleMock.Foo
            val Bar: ModuleMock.Method[String, Throwable, String] = ModuleMock.Bar
          }

          Check
        })(anything)
      },
      test("generates mocks for polymorphic services") {
        assert({
          @mockable[PolyModulePureDefsModule.Service[String, Exception, Double]]
          object ModuleMock

          object Check {
            val mock: Mock[PolyModulePureDefsModule[String, Exception, Double]] = ModuleMock

            val Static: ModuleMock.Effect[Unit, Exception, Double]                        = ModuleMock.Static
            val ZeroParams: ModuleMock.Effect[Unit, Exception, Double]                    = ModuleMock.ZeroParams
            val ZeroParamsWithParens: ModuleMock.Effect[Unit, Exception, Double]          = ModuleMock.ZeroParamsWithParens
            val SingleParam: ModuleMock.Effect[Int, Exception, Double]                    = ModuleMock.SingleParam
            val ManyParams: ModuleMock.Effect[(Int, String, Long), Exception, Double]     = ModuleMock.ManyParams
            val ManyParamLists: ModuleMock.Effect[(Int, String, Long), Exception, Double] = ModuleMock.ManyParamLists
          }

          Check
        })(anything)
      }
    )
  )
}
