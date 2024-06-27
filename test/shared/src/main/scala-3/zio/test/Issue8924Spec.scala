import zio._
import zio.test._
import zio.test.Assertion._

object Issue8924Spec extends DefaultRunnableSpec {
  def spec = suite("Issue8924Spec")(
    test("Contravariant type inference should work correctly") {
      object Module {
        opaque type AType[-I, -R, +E, +A] = I => ZIO[R, E, A]

        extension [A, B, C, D](aType: AType[A, B, C, D])
          def toLayer(using Tag[A], Tag[B], Tag[C], Tag[D]): ULayer[AType[A, B, C, D]] =
            ZLayer.succeed[AType[A, B, C, D]](aType)

        def identity[I]: AType[I, Any, Nothing, I] = 
          identity[I].andThen(ZIO.succeed(_))

        def service[I: Tag, R: Tag, E: Tag, A: Tag]: URIO[AType[I, R, E, A], AType[I, R, E, A]] = 
          ZIO.service[AType[I, R, E, A]]
      }

      import Module._

      val intId = identity[Int]
      val layer = intId.toLayer
      val effect = service[Int, Any, Nothing, Int].as("voila!")

      val runtime = Runtime.default
      Unsafe.unsafe { unsafe =>
        given Unsafe = unsafe
        runtime.unsafe.run(effect.provide(layer)).map { result =>
          assert(result)("voila!")
        }.getOrThrowFiberFailure()
      }
    }
  )
}
