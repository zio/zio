package zio.test.mock

import zio.{ Has, IO, Tagged, ZIO }

/**
 * https://github.com/scalamacros/paradise/issues/75
 *
 * We can't define module in the same scope with macro application
 * */
object modules {
  type EmptyModule = Has[EmptyModule.Service]
  object EmptyModule {
    trait Service
  }

  type SinglePureValModule = Has[SinglePureValModule.Service]
  object SinglePureValModule {
    trait Service {
      val foo: ZIO[Any, Nothing, Unit]
    }
  }

  type SimplePureDefsModule = Has[SimplePureDefsModule.Service]
  object SimplePureDefsModule {
    trait Service {
      val static: IO[String, String]
      def zeroParams: IO[String, String]
      def zeroParamsWithParens(): IO[String, String]
      def singleParam(a: Int): IO[String, String]
      def manyParams(a: Int, b: String, c: Long): IO[String, String]
      def manyParamLists(a: Int)(b: String)(c: Long): IO[String, String]
    }
  }

  type SimpleImpureDefsModule = Has[SimpleImpureDefsModule.Service]
  object SimpleImpureDefsModule {
    trait Service {
      def zeroParams: String
      def zeroParamsWithParens(): String
      def singleParam(a: Int): String
      def manyParams(a: Int, b: String, c: Long): String
      def manyParamLists(a: Int)(b: String)(c: Long): String
    }
  }

  type OverloadedPureDefsModule = Has[OverloadedPureDefsModule.Service]
  object OverloadedPureDefsModule {
    trait Service {
      def overloaded(n: Int): IO[String, String]
      def overloaded(n: Long): IO[String, String]
    }
  }

  type OverloadedImpureDefsModule = Has[OverloadedImpureDefsModule.Service]
  object OverloadedImpureDefsModule {
    trait Service {
      def overloaded(n: Int): String
      def overloaded(n: Long): String
    }
  }

  type PolyPureDefsModule = Has[PolyPureDefsModule.Service]
  object PolyPureDefsModule {
    trait Service {
      def polyInput[I: Tagged](v: I): IO[String, String]
      def polyError[E: Tagged](v: String): IO[E, String]
      def polyOutput[A: Tagged](v: String): IO[String, A]
      def polyInputError[I: Tagged, E: Tagged](v: I): IO[E, String]
      def polyInputOutput[I: Tagged, A: Tagged](v: I): IO[String, A]
      def polyErrorOutput[E: Tagged, A: Tagged](v: String): IO[E, A]
      def polyInputErrorOutput[I: Tagged, E: Tagged, A: Tagged](v: I): IO[E, A]
      def polyMixed[A: Tagged]: IO[String, (A, String)]
      def polyBounded[A <: AnyVal: Tagged]: IO[String, A]
    }
  }

  type PolyImpureDefsModule = Has[PolyImpureDefsModule.Service]
  object PolyImpureDefsModule {
    trait Service {
      def polyInput[I: Tagged](v: I): String
      def polyError[E <: Throwable: Tagged](v: String): String
      def polyOutput[A: Tagged](v: String): A
      def polyInputError[I: Tagged, E <: Throwable: Tagged](v: I): String
      def polyInputOutput[I: Tagged, A: Tagged](v: I): A
      def polyErrorOutput[E <: Throwable: Tagged, A: Tagged](v: String): A
      def polyInputErrorOutput[I: Tagged, E <: Throwable: Tagged, A: Tagged](v: I): A
      def polyMixed[A: Tagged]: (A, String)
      def polyBounded[A <: AnyVal: Tagged]: A
    }
  }

  type VarargsPureDefsModule = Has[VarargsPureDefsModule.Service]
  object VarargsPureDefsModule {
    trait Service {
      def simpleVarargs(a: Int, b: String*): IO[String, Int]
      def curriedVarargs(a: Int, b: String*)(c: Long, d: Double*): IO[String, Int]
    }
  }

  type VarargsImpureDefsModule = Has[VarargsImpureDefsModule.Service]
  object VarargsImpureDefsModule {
    trait Service {
      def simpleVarargs(a: Int, b: String*): String
      def curriedVarargs(a: Int, b: String*)(c: Long, d: Double*): String
    }
  }
}
