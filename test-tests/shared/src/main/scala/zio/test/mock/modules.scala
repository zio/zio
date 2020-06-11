package zio.test.mock

import zio.{ Has, IO, Tag, UIO }

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
      val foo: UIO[Unit]
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
      def polyInput[I: Tag](v: I): IO[String, String]
      def polyError[E: Tag](v: String): IO[E, String]
      def polyOutput[A: Tag](v: String): IO[String, A]
      def polyInputError[I: Tag, E: Tag](v: I): IO[E, String]
      def polyInputOutput[I: Tag, A: Tag](v: I): IO[String, A]
      def polyErrorOutput[E: Tag, A: Tag](v: String): IO[E, A]
      def polyInputErrorOutput[I: Tag, E: Tag, A: Tag](v: I): IO[E, A]
      def polyMixed[A: Tag]: IO[String, (A, String)]
      def polyBounded[A <: AnyVal: Tag]: IO[String, A]
    }
  }

  type PolyImpureDefsModule = Has[PolyImpureDefsModule.Service]
  object PolyImpureDefsModule {
    trait Service {
      def polyInput[I: Tag](v: I): String
      def polyError[E <: Throwable: Tag](v: String): String
      def polyOutput[A: Tag](v: String): A
      def polyInputError[I: Tag, E <: Throwable: Tag](v: I): String
      def polyInputOutput[I: Tag, A: Tag](v: I): A
      def polyErrorOutput[E <: Throwable: Tag, A: Tag](v: String): A
      def polyInputErrorOutput[I: Tag, E <: Throwable: Tag, A: Tag](v: I): A
      def polyMixed[A: Tag]: (A, String)
      def polyBounded[A <: AnyVal: Tag]: A
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
