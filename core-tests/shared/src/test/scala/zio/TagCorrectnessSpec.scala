package zio.test

import zio._

/**
 * There have been many issues with Tag on Scala 3. This suite collects and
 * tests those issues to ensure no regressions.
 */
object TagCorrectnessSpec extends DefaultRunnableSpec {
  def spec =
    suite("TagCorrectnessSpec")(
      // https://github.com/zio/zio/issues/4802
      test("Issue #4802") {
        ZIO
          .serviceWith[Ref[Int]](_.get)
          .inject(Ref.make(10).toLayer)
          .map { int =>
            assertTrue(int == 10)
          }
      },
      // https://github.com/zio/zio/issues/5421
      test("Issue #5421") {
        def combine[A: Tag, B: Tag](za: UIO[Has[A]], zb: UIO[Has[B]]): UIO[Has[A] with Has[B]] =
          za.zip(zb).map { case (a, b) => a ++ b }

        val zint = ZIO.succeed(Has(2))
        val zstr = ZIO.succeed(Has("hi"))

        val zboth = combine(zint, zstr)

        zboth.map { result =>
          assertTrue(result.get[Int] == 2, result.get[String] == "hi")
        }
      },
      // https://github.com/zio/zio/issues/4809#issuecomment-822001203
      test("Issue #4809") {
        trait Service[T] {
          def foo(t: T): UIO[T]
        }

        object Service {
          val live: ULayer[Has[Service[Int]]] =
            ZLayer.succeed {
              new Service[Int] {
                def foo(t: Int) =
                  ZIO.succeed(t)
              }
            }
        }

        def foo[T: Tag](t: T): URIO[Has[Service[T]], T] =
          ZIO.serviceWith(_.foo(t))

        foo(12).inject(Service.live).map { result =>
          assertTrue(result == 12)
        }
      },
      // https://github.com/zio/zio/issues/4564
      test("Issue #4564") {
        trait Svc[A]
        def testBaseLayer[R, A: Tag]: ZLayer[R, Nothing, Has[Svc[A]]] =
          ZIO.access[R](env => new Svc[A] {}).toLayer[Svc[A]]
        def testSecondLayer[A: Tag]: ZLayer[Has[Svc[A]], Nothing, Has[Svc[A]]] =
          ZLayer.fromFunction[Has[Svc[A]], Svc[A]] { s =>
            s.get
          }

        val layer = testBaseLayer[Any, String] >>> testSecondLayer[String]
        ZIO.unit.provideCustomLayer(layer).as(assertTrue(true))
      },
      // https://github.com/zio/zio/issues/3816
      test("Issue #3816") {
        class Container[A](val a: A)
        type ContainerProvider[A, D <: Container[A]] = Has[ContainerProvider.Service[A, D]]

        object ContainerProvider {

          trait Service[A, D <: Container[A]] {
            def provide: IO[Throwable, D]
          }

          def layer[A: Tag, D <: Container[A]: Tag](container: D): Layer[Nothing, ContainerProvider[A, D]] =
            ZLayer.succeed {
              new Service[A, D] {
                def provide: IO[Throwable, D] = IO.succeed(container)
              }
            }

          def provide[A: Tag, D <: Container[A]: Tag]: ZIO[ContainerProvider[A, D], Throwable, D] =
            ZIO.accessM(_.get.provide)
        }

        ZIO
          .accessZIO[ContainerProvider[Int, Container[Int]]] { x =>
            ContainerProvider.provide
          }
          .inject(ContainerProvider.layer[Int, Container[Int]](new Container(10)))
          .either
          .map { result =>
            assertTrue(result.isRight)
          }
      }
      // https://github.com/zio/zio/issues/3629
      // test("Issue #3629") {
      //   import zio.stream._
      //   import java.nio.ByteBuffer

      //   val clock: Has[Clock] = Has(Clock.ClockLive)

      //   def putObject[R <: zio.Has[_]: Tag](content: ZStream[R, Throwable, Byte]): ZIO[R, Throwable, Unit] = {
      //     val _                                          = content
      //     val z: ZIO[Has[Clock] with R, Throwable, Unit] = ZIO.unit
      //     val e: Has[R]                                  = Has(1).asInstanceOf[Has[R]]
      //     clock union e
      //     ZIO.unit
      //     // z.provideSomeLayer[R](r => clock.union(r))
      //   }

      //   val c                                 = Chunk.fromByteBuffer(ByteBuffer.allocate(1))
      //   val data: ZStream[Any, Nothing, Byte] = ZStream.fromChunks(c)

      //   putObject(data).map { res =>
      //     assertTrue(res == ())
      //   }

      // }
    )
}
