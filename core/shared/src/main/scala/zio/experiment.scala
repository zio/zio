import zio.{ ZLayer => _, _ }

object Experiment {

  final case class ZLayer[-RIn, +E, +ROut <: Has[_]](value: Managed[Nothing, MemoMap => ZManaged[RIn, E, ROut]]) {
    self =>

    def >>>[E1 >: E, ROut2 <: Has[_]](that: ZLayer[ROut, E1, ROut2]): ZLayer[RIn, E1, ROut2] =
      ZLayer {
        Managed {
          for {
            ref <- Ref.make[Exit[Any, Any] => ZIO[Any, Nothing, Any]](_ => ZIO.unit)
            managed = ZIO.succeed { (map: MemoMap) =>
              for {
                l <- map.get(self).toManaged_.flatMap {
                      case None =>
                        self.value.mapM(_(map).reserve).flatMap {
                          case Reservation(acquire, release) =>
                            acquire.flatMap(a => map.memoize(self, a).as(a)).toManaged_ <* ZIO
                              .environment[RIn]
                              .flatMap(env => ref.update(finalizer => e => release(e).provide(env) *> finalizer(e)))
                              .toManaged_
                        }
                      case Some(service) => ZManaged.succeed(service)
                    }
                r <- map.get(that).toManaged_.flatMap {
                      case None =>
                        that.value
                          .mapM(_(map).reserve)
                          .flatMap {
                            case Reservation(acquire, release) =>
                              acquire.flatMap(a => map.memoize(that, a).as(a)).toManaged_ <* ZIO
                                .environment[ROut]
                                .flatMap(env => ref.update(finalizer => e => release(e).provide(env) *> finalizer(e)))
                                .toManaged_
                          }
                          .provide(l)
                      case Some(service) => ZManaged.succeed(service)
                    }
              } yield r
            }
          } yield Reservation(managed, e => ref.get.flatMap(_(e)))
        }
      }

    def ++[E1 >: E, RIn2, ROut1 >: ROut <: Has[_], ROut2 <: Has[_]](
      that: ZLayer[RIn2, E1, ROut2]
    )(implicit tagged: Tagged[ROut2]): ZLayer[RIn with RIn2, E1, ROut1 with ROut2] =
      ZLayer {
        ZManaged {
          for {
            ref <- Ref.make[Exit[Any, Any] => ZIO[Any, Nothing, Any]](_ => ZIO.unit)
            managed: UIO[MemoMap => ZManaged[RIn with RIn2, E1, ROut1 with ROut2]] = ZIO.succeed { (map: MemoMap) =>
              for {
                l <- map.get(self).toManaged_.flatMap {
                      case None =>
                        self.value.mapM(_(map).reserve).flatMap {
                          case Reservation(acquire, release) =>
                            acquire.flatMap(a => map.memoize(self, a).as(a)).toManaged_ <* ZIO
                              .environment[RIn]
                              .flatMap(env => ref.update(finalizer => e => release(e).provide(env) *> finalizer(e)))
                              .toManaged_
                        }
                      case Some(service) => ZManaged.succeed(service)
                    }
                r <- map.get(that).toManaged_.flatMap {
                      case None =>
                        that.value.mapM(_(map).reserve).flatMap {
                          case Reservation(acquire, release) =>
                            acquire.flatMap(a => map.memoize(that, a).as(a)).toManaged_ <* ZIO
                              .environment[RIn2]
                              .flatMap(env => ref.update(finalizer => e => release(e).provide(env) *> finalizer(e)))
                              .toManaged_
                        }
                      case Some(service) => ZManaged.succeed(service)
                    }
              } yield l.union[ROut2](r)
            }
          } yield Reservation(managed, e => ref.get.flatMap(_(e)))
        }
      }

    def build: ZManaged[RIn, E, ROut] =
      for {
        map   <- MemoMap.make.toManaged_
        outer <- self.value
        r     <- outer(map)
      } yield r
  }

  object ZLayer {

    def fromManaged[R, E, A <: Has[_]](managed: ZManaged[R, E, A]): ZLayer[R, E, A] =
      ZLayer(Managed.succeed(_ => managed))

    def succeed[A <: Has[_]](a: => A): ZLayer[Any, Nothing, A] =
      ZLayer(Managed.succeed(_ => Managed.succeed(a)))
  }

  trait MemoMap {
    def get[E, A, B <: Has[_]](layer: ZLayer[A, E, B]): UIO[Option[B]]
    def memoize[E, A, B <: Has[_]](layer: ZLayer[A, E, B], value: B): UIO[Unit]
  }

  object MemoMap {

    def make: UIO[MemoMap] =
      Ref.make[Map[ZLayer[Nothing, Any, Has[_]], Any]](Map.empty).map { ref =>
        new MemoMap {
          def get[E, A, B <: Has[_]](layer: ZLayer[A, E, B]): UIO[Option[B]] =
            ref.get.map { map =>
              map.get(layer).asInstanceOf[Option[B]]
            }
          def memoize[E, A, B <: Has[_]](layer: ZLayer[A, E, B], value: B): UIO[Unit] =
            ref.update(_ + (layer -> value))
        }
      }
  }

}
