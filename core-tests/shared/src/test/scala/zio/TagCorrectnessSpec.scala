package zio

import zio.test._

object TagCorrectnessSpec extends DefaultRunnableSpec {

  def spec =
    suite("TagCorrectnessSpec")(
      // https://github.com/zio/zio/issues/4802
      test("Issue #4802") {
        ZIO
          .serviceWith[Ref[Int]](_.get)
          .inject(Ref.make(10).toServiceBuilder)
          .map { int =>
            assertTrue(int == 10)
          }
      },
      // https://github.com/zio/zio/issues/5421
      test("Issue #5421") {
        def combine[A, B](
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
          val live: UServiceBuilder[Service[Int]] =
            ZServiceBuilder.succeed {
              new Service[Int] {
                def foo(t: Int) =
                  ZIO.succeed(t)
              }
            }
        }

        def foo[T: Tag](t: T): URIO[Service[T], T] =
          ZIO.serviceWith(_.foo(t))

        foo(12).inject(Service.live).map { result =>
          assertTrue(result == 12)
        }
      },
      // https://github.com/zio/zio/issues/4564
      test("Issue #4564") {
        trait Svc[A]
        def testBaseLayer[R, A: Tag]: ZServiceBuilder[R, Nothing, Svc[A]] =
          ZIO.access[R](_ => new Svc[A] {}).toServiceBuilder[Svc[A]]
        def testSecondLayer[A: Tag]: ZServiceBuilder[Svc[A], Nothing, Svc[A]] =
          ZServiceBuilder.fromFunction[Svc[A], Svc[A]] { s =>
            s
          }

        val layer = testBaseLayer[Any, String] >>> testSecondLayer[String]
        ZIO.unit.provideCustomServices(layer).as(assertTrue(true))
      },
      // https://github.com/zio/zio/issues/3816
      test("Issue #3816") {
        class Container[A](val a: A)
        type ContainerProvider[A, D <: Container[A]] = ContainerProvider.Service[A, D]

        object ContainerProvider {

          trait Service[A, D <: Container[A]] {
            def provide: IO[Throwable, D]
          }

          def layer[A: Tag, D <: Container[A]: Tag](container: D): UServiceBuilder[ContainerProvider[A, D]] =
            ZServiceBuilder.succeed {
              new Service[A, D] {
                def provide: IO[Throwable, D] = IO.succeed(container)
              }
            }

          def provide[A: Tag, D <: Container[A]: Tag]: ZIO[ContainerProvider[A, D], Throwable, D] =
            ZIO.accessZIO(_.get.provide)
        }

        ZIO
          .accessZIO[ContainerProvider[Int, Container[Int]]] { _ =>
            ContainerProvider.provide[Int, Container[Int]]
          }
          .inject(ContainerProvider.layer[Int, Container[Int]](new Container(10)))
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
object HigherKindedTagCorrectness extends DefaultRunnableSpec {

  trait Cache[F[+_], K, V] {
    def get(key: K): ZIO[Any, Nothing, F[V]]
    def put(key: K, value: V): ZIO[Any, Nothing, Unit]
  }

  object Cache {
    def live[F[+_], K, V](
      f: Option[V] => F[V]
    )(implicit tag: Tag[Cache[F, K, V]]): ZServiceBuilder[Any, Nothing, Cache[F, K, V]] =
      (for {
        cache <- Ref.make(Map.empty[K, V])
      } yield new Cache[F, K, V] {
        def get(key: K): ZIO[Any, Nothing, F[V]] =
          cache.get.map(_.get(key)).map(f)

        def put(key: K, value: V): ZIO[Any, Nothing, Unit] =
          cache.update(_.updated(key, value))
      }).toServiceBuilder

    def get[F[+_], K, V](key: K)(implicit tag: Tag[Cache[F, K, V]]): ZIO[Cache[F, K, V], Nothing, F[V]] =
      ZIO.accessZIO(_.get.get(key))

    def put[F[+_], K, V](key: K, value: V)(implicit tag: Tag[Cache[F, K, V]]): ZIO[Cache[F, K, V], Nothing, Unit] =
      ZIO.accessZIO(_.get.put(key, value))
  }

  val myCache: ZServiceBuilder[Any, Nothing, Cache[Option, Int, String]] =
    Cache.live[Option, Int, String](identity)

  def spec =
    suite("HigherKindedTagCorrectness")(
      test("wow") {
        (for {
          _     <- Cache.put[Option, Int, String](1, "one")
          value <- Cache.get[Option, Int, String](1)
          _     <- ZIO.debug(value)
          a      = Tag[Cache[Option, Int, String]]
          b      = Tag[Cache[({ type Out[+In] = Option[In] })#Out, Int, String]]
          c      = Tag[Cache[({ type Bar = Double; type Out[+In] = Option[Bar] })#Out, Int, String]]
          d      = Tag[Cache[({ type Out[+In] = Option[Double] })#Out, Int, String]]
          e      = Tag[Cache[({ type Id[+A] = A; type Out[+In] = Option[Id[In]] })#Out, Int, String]]
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
        )).provideServices(myCache)
      }
    )

}
