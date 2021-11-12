package zio

object Example extends ZIOAppDefault {

  trait Cache[F[+_], K, V] {
    def get(key: K): ZIO[Any, Nothing, F[V]]
    def put(key: K, value: V): ZIO[Any, Nothing, Unit]
  }

  object Cache {
    def live[F[+_], K, V](f: Option[V] => F[V])(implicit tag: Tag[Cache[F, K, V]]): ZLayer[Any, Nothing, Has[Cache[F, K, V]]] =
      (for {
        cache <- Ref.make(Map.empty[K, V])
      } yield new Cache[F, K, V] {
        def get(key: K): ZIO[Any, Nothing, F[V]] =
          cache.get.map(_.get(key)).map(f)

        def put(key: K, value: V): ZIO[Any, Nothing, Unit] =
          cache.update(_.updated(key, value))
      }).toLayer

    def get[F[+_], K, V](key: K)(implicit tag: Tag[Cache[F, K, V]]): ZIO[Has[Cache[F, K, V]], Nothing, F[V]] =
      ZIO.accessM(_.get.get(key))

    def put[F[+_], K, V](key: K, value: V)(implicit tag: Tag[Cache[F, K, V]]): ZIO[Has[Cache[F, K, V]], Nothing, Unit] =
      ZIO.accessM(_.get.put(key, value))
  }

  val myCache: ZLayer[Any, Nothing, Has[Cache[Option, Int, String]]] =
    Cache.live[Option, Int, String](identity)

// val adamsBadCache: ZLayer[Any, Nothing, Has[Cache[({ type Out[In] = Option[In]})#Out, Int, String]]] =
//   Cache.live[Option, Int, String](identity)

//   Apply(TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),zio),Example$),Cache),List(Apply(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Option),List(TypeParamRef)), TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Int), TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),java),lang),String)))
//   Apply(TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),zio),Example$),Cache),List(Apply(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Option),List(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Double))), TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Int), TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),java),lang),String)))

// Apply(TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),zio),Example$),Cache),List(Apply(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Option),List(TypeParamRef)), TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Int), TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),java),lang),String)))
// Apply(TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),zio),Example$),Cache),List(Apply(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Option),List(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Double))), TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Int), TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),java),lang),String)))

// Apply(TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),zio),Example$),Cache),List(Apply(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Option),List(TypeParamRef)), TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Int), TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),java),lang),String)))
// Apply(TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),zio),Example$),Cache),List(Apply(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Option),List(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Double))), TypeRef(TypeRef(TypeRef(NoPrefix,<root>),scala),Int), TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix,<root>),java),lang),String)))

  type Apply1[A, B] = (A, B)
  type Apply2[A, B] = (B, A)

  trait Cache2[F[_,_], K, V]
  type Cache2A = Cache2[Apply1, Int, String]
  type Cache2B = Cache2[Apply2, Int, String]
  // List String
  // Type Param Ref
  // De Bruin \.2 .1
  // (1, 2) => (2, 1)

  val run =
    (for {
      _     <- Cache.put[Option, Int, String](1, "one")
      value <- Cache.get[Option, Int, String](1)
      _     <- ZIO.debug(value)
      a = Tag[Cache[Option, Int, String]]
      b = Tag[Cache[({ type Out[In] = Option[In]})#Out, Int, String]]
      c = Tag[Cache[({ type Bar = Double; type Out[In] = Option[Bar]})#Out, Int, String]]
      d = Tag[Cache[({ type Out[In] = Option[Double]})#Out, Int, String]]
      e = Tag[Cache[({ type Id[A] = A; type Out[In] = Option[Id[In]]})#Out, Int, String]]
      f = Tag[Cache2A]
      g = Tag[Cache2B]
      _     <- ZIO.debug(a)
      _     <- ZIO.debug(e)
      _     <- ZIO.debug(a == e)
    } yield ()).provideLayer(myCache)
}
