package zio

import zio._
import zio.test._

object BlackboxTagRegressionSpec extends ZIOSpecDefault {

  object Module {
    opaque type AType[-I, -R, +E, +A] = I => ZIO[R, E, A]

    extension [A, B, C, D](aType: AType[A, B, C, D]) {
      def toLayer(implicit tagA: Tag[A], tagB: Tag[B], tagC: Tag[C], tagD: Tag[D]): ULayer[AType[A, B, C, D]] =
        ZLayer.succeed[AType[A, B, C, D]](aType)
    }

    def identity[I]: AType[I, Any, Nothing, I] =
      Predef.identity[I].andThen(ZIO.succeed(_))

    def service[I: Tag, R: Tag, E: Tag, A: Tag]: URIO[AType[I, R, E, A], AType[I, R, E, A]] =
      ZIO.service[AType[I, R, E, A]]
  }

  def isExpectedType(f: ULayer[Module.AType[Int, Any, Nothing, Int]]) = true

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suiteAll("BlackboxTagRegressionSpec") {
      // https://github.com/zio/zio/issues/8924
      // Reported upstream at https://github.com/scala/scala3/issues/24678
      test("Issue #8924 example compiles as long as zio.Tag is a blackbox macro") {
        val intId  = Module.identity[Int]
        val layer  = intId.toLayer
        val effect = Module.service[Int, Any, Nothing, Int].as("voila!")

        effect.provide(layer).as {
          assertTrue(isExpectedType(layer))
        }
      }
    }
}
