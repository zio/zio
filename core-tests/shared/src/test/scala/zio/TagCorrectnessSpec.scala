package zio

import zio.test._

object TagCorrectnessSpec extends ZIOBaseSpec {

  def spec =
    suite("TagCorrectnessSpec")(
      // https://github.com/zio/zio/issues/4802
      test("Issue #4802") {
        ZIO
          .serviceWithZIO[Ref[Int]](_.get)
          .provide(ZLayer(Ref.make(10)))
          .map { int =>
            assertTrue(int == 10)
          }
      },
      // https://github.com/zio/zio/issues/5421
      test("Issue #5421") {
        def combine[A, B: Tag](
          za: UIO[ZEnvironment[A]],
          zb: UIO[ZEnvironment[B]]
        ): UIO[ZEnvironment[A with B]] =
          za.zip(zb).map { case (a, b) => a.++[B](b) }

        val zint = ZIO.succeed(ZEnvironment(2))
        val zstr = ZIO.succeed(ZEnvironment("hi"))

        val zboth = combine[Int, String](zint, zstr)

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
          val live: ULayer[Service[Int]] =
            ZLayer.succeed {
              new Service[Int] {
                def foo(t: Int) =
                  ZIO.succeed(t)
              }
            }
        }

        def foo[T](t: T)(implicit tag: Tag[T]): URIO[Service[T], T] =
          ZIO.serviceWithZIO(_.foo(t))

        foo(12).provide(Service.live).map { result =>
          assertTrue(result == 12)
        }
      },
      // https://github.com/zio/zio/issues/4564
      test("Issue #4564") {
        trait Svc[A]
        def testBaseLayer[R, A](implicit tag: Tag[A]): ZLayer[R, Nothing, Svc[A]] =
          ZLayer(ZIO.environmentWith[R](_ => new Svc[A] {}))
        def testSecondLayer[A](implicit tag: Tag[A]): ZLayer[Svc[A], Nothing, Svc[A]] =
          ZLayer.environment[Svc[A]]

        val layer                                  = testBaseLayer[Any, String] >>> testSecondLayer[String]
        val zio: ZIO[Svc[String], Nothing, String] = ZIO.succeed("a")
        zio.provideLayer(layer).as(assertCompletes)
      },
      // https://github.com/zio/zio/issues/3816
      test("Issue #3816") {
        class Container[A](val a: A)
        type ContainerProvider[A, D <: Container[A]] = ContainerProvider.Service[A, D]

        object ContainerProvider {

          trait Service[A, D <: Container[A]] {
            def provide: IO[Throwable, D]
          }

          def layer[A, D <: Container[A]](
            container: D
          )(implicit tagA: Tag[A], tagD: Tag[D]): ULayer[ContainerProvider[A, D]] =
            ZLayer.succeed {
              new Service[A, D] {
                def provide: IO[Throwable, D] = ZIO.succeed(container)
              }
            }

          def provide[A, D <: Container[A]](implicit
            tagA: Tag[A],
            tagD: Tag[D]
          ): ZIO[ContainerProvider[A, D], Throwable, D] =
            ZIO.serviceWithZIO(_.provide)
        }

        ZIO
          .environmentWithZIO[ContainerProvider[Int, Container[Int]]] { _ =>
            ContainerProvider.provide[Int, Container[Int]]
          }
          .provide(ContainerProvider.layer[Int, Container[Int]](new Container(10)))
          .either
          .map { result =>
            assertTrue(result.isRight)
          }
      }
    )
}

/**
 * Higher-Kinded Tag Correctness Example
 */
object HigherKindedTagCorrectness extends ZIOBaseSpec {

  trait Cache[F[_], K, V] {
    def get(key: K): ZIO[Any, Nothing, F[V]]
    def put(key: K, value: V): ZIO[Any, Nothing, Unit]
  }

  object Cache {
    def live[F[_], K, V](
      f: Option[V] => F[V]
    )(implicit tag: Tag[Cache[F, K, V]]): ZLayer[Any, Nothing, Cache[F, K, V]] =
      ZLayer {
        for {
          cache <- Ref.make(Map.empty[K, V])
        } yield new Cache[F, K, V] {
          def get(key: K): ZIO[Any, Nothing, F[V]] =
            cache.get.map(_.get(key)).map(f)

          def put(key: K, value: V): ZIO[Any, Nothing, Unit] =
            cache.update(_.updated(key, value))
        }
      }

    def get[F[_], K, V](key: K)(implicit tag: Tag[Cache[F, K, V]]): ZIO[Cache[F, K, V], Nothing, F[V]] =
      ZIO.serviceWithZIO(_.get(key))

    def put[F[_], K, V](key: K, value: V)(implicit
      tag: Tag[Cache[F, K, V]]
    ): ZIO[Cache[F, K, V], Nothing, Unit] =
      ZIO.serviceWithZIO(_.put(key, value))
  }

  val myCache: ZLayer[Any, Nothing, Cache[Option, Int, String]] =
    Cache.live[Option, Int, String](identity)

  def spec =
    suite("HigherKindedTagCorrectness")(
      test("wow") {
        (for {
          _     <- Cache.put[Option, Int, String](1, "one")
          value <- Cache.get[Option, Int, String](1)
          _     <- ZIO.debug(value)
          a      = Tag[Cache[Option, Int, String]]
          b      = Tag[Cache[({ type Out[In] = Option[In] })#Out, Int, String]]
          c      = Tag[Cache[({ type Bar = Double; type Out[In] = Option[Bar] })#Out, Int, String]]
          d      = Tag[Cache[({ type Out[In] = Option[Double] })#Out, Int, String]]
          e      = Tag[Cache[({ type Id[A] = A; type Out[In] = Option[Id[In]] })#Out, Int, String]]
          _     <- ZIO.debug(s"WHAT" + b)
        } yield assertTrue(
          a.tag <:< b.tag,
          b.tag <:< a.tag,
          a.tag <:< e.tag,
          e.tag <:< a.tag,
          c.tag <:< d.tag,
          d.tag <:< c.tag,
          !(a.tag <:< c.tag),
          !(c.tag <:< a.tag),
          !(a.tag <:< d.tag)
        )).provideLayer(myCache)
      }
    ) @@ TestAspect.exceptNative

}
