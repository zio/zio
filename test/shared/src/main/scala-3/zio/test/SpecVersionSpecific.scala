package zio.test

import zio.{ZIO, ZLayer}

private[test] trait SpecVersionSpecific[-R, +E] { self: Spec[R, E] =>

  /**
   * Builds the environment of this spec from `layer`, wiring the layers
   * together at compile time, and provides it to each test. The layers are
   * built once for each test, and once more for each scoped node (such as one
   * that `aroundAll` creates), so tests do not share their services;
   * `provideShared` builds them once for the whole spec. A missing layer is a
   * compile error, and a layer that is not needed gives a compiler warning.
   *
   * {{{
   * import zio.{ULayer, ZIO, ZLayer}
   *
   * trait Database
   * trait Cache
   *
   * val databaseLayer: ULayer[Database] = ZLayer.succeed(new Database {})
   * val cacheLayer: ZLayer[Database, Nothing, Cache] =
   *   ZLayer.fromFunction((_: Database) => new Cache {})
   *
   * val spec: Spec[Any, Nothing] =
   *   test("reads through the cache") {
   *     ZIO.service[Cache].as(assertCompletes)
   *   }.provide(cacheLayer, databaseLayer)
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/test/spec/ Spec]]
   */
  inline def provide[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[Any, E1] =
    ${ SpecLayerMacros.provideImpl[Any, R, E1]('self, 'layer) }

  /**
   * Splits the environment into two parts, providing each test with one part
   * using the specified layers and leaving the remainder `R0`. Use it as
   * `spec.provideSome[R0](layer1, layer2)`; the layers are wired together at
   * compile time and built once for each test, and once more for each scoped
   * node (such as one that `aroundAll` creates).
   *
   * {{{
   * import zio.{ULayer, ZIO, ZLayer}
   *
   * trait Database
   * trait Cache
   *
   * val databaseLayer: ULayer[Database] = ZLayer.succeed(new Database {})
   *
   * val spec: Spec[Database with Cache, Nothing] =
   *   test("reads through the cache") {
   *     ZIO.service[Database].zip(ZIO.service[Cache]).as(assertCompletes)
   *   }
   *
   * val spec2: Spec[Cache, Nothing] = spec.provideSome[Cache](databaseLayer)
   * }}}
   */
  def provideSome[R0] =
    new ProvideSomePartiallyApplied[R0, R, E](self)

  /**
   * Same as `provideSome`, but infers the remainder type instead of taking it
   * as a type argument: the result requires the part of the environment that
   * `layer` does not produce. Scala 3 only.
   *
   * {{{
   * import zio.{ULayer, ZIO, ZLayer}
   *
   * trait Database
   * trait Cache
   *
   * val databaseLayer: ULayer[Database] = ZLayer.succeed(new Database {})
   *
   * val spec: Spec[Database with Cache, Nothing] =
   *   test("reads through the cache") {
   *     ZIO.service[Database].zip(ZIO.service[Cache]).as(assertCompletes)
   *   }
   *
   * val spec2: Spec[Cache, Nothing] = spec.provideSomeAuto(databaseLayer)
   * }}}
   */
  inline transparent def provideSomeAuto[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[_, E1] =
    ${ SpecLayerMacros.provideAutoImpl[R, E1]('self, 'layer) }

  /**
   * Splits the environment into two parts, providing all tests with a shared
   * version of one part using the specified layers and leaving the remainder
   * `R0`. Use it as `spec.provideSomeShared[R0](layer1, layer2)`; the layers
   * are wired together at compile time and built once for the whole spec.
   *
   * {{{
   * import zio.{ULayer, ZIO, ZLayer}
   *
   * trait Database
   * trait Cache
   *
   * val databaseLayer: ULayer[Database] = ZLayer.succeed(new Database {})
   *
   * val spec: Spec[Database with Cache, Nothing] =
   *   suite("queries")(
   *     test("first") {
   *       ZIO.service[Database].zip(ZIO.service[Cache]).as(assertCompletes)
   *     },
   *     test("second") {
   *       ZIO.service[Database].as(assertCompletes)
   *     }
   *   )
   *
   * val spec2: Spec[Cache, Nothing] =
   *   spec.provideSomeShared[Cache](databaseLayer)
   * }}}
   */
  def provideSomeShared[R0] =
    new ProvideSomeSharedPartiallyApplied[R0, R, E](self)

  /**
   * Builds the part of the environment that is not part of `TestEnvironment`
   * from `layer`, wiring the layers together at compile time, and provides it
   * to each test, leaving a spec that only depends on `TestEnvironment`. The
   * layers are built once for each test, and once more for each scoped node
   * (such as one that `aroundAll` creates). Layer inputs that `TestEnvironment`
   * contains are taken from that remainder.
   *
   * {{{
   * import zio.{ULayer, ZIO, ZLayer}
   *
   * trait Logging
   *
   * val loggingLayer: ULayer[Logging] = ZLayer.succeed(new Logging {})
   *
   * val spec: Spec[TestEnvironment with Logging, Nothing] =
   *   test("logs")(ZIO.service[Logging].as(assertCompletes))
   *
   * val spec2: Spec[TestEnvironment, Nothing] =
   *   spec.provideCustom(loggingLayer)
   * }}}
   */
  @deprecated("use provide", "2.0.2")
  inline def provideCustom[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1] =
    ${ SpecLayerMacros.provideImpl[TestEnvironment, R, E1]('self, 'layer) }

  /**
   * Builds the environment of this spec from `layer`, wiring the layers
   * together at compile time, once for the whole spec, and shares the services
   * between all of its tests. The services are released when the spec finishes.
   *
   * {{{
   * import zio.{Ref, ZIO, ZLayer}
   *
   * val counterLayer: ZLayer[Any, Nothing, Ref[Int]] = ZLayer(Ref.make(0))
   *
   * val spec: Spec[Any, Nothing] =
   *   suite("shared counter")(
   *     test("first") {
   *       ZIO.serviceWithZIO[Ref[Int]](_.update(_ + 1)).as(assertCompletes)
   *     },
   *     test("second") {
   *       ZIO.serviceWithZIO[Ref[Int]](_.update(_ + 1)).as(assertCompletes)
   *     }
   *   ).provideShared(counterLayer)
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/test/sharing-layers-between-multiple-files/ Sharing Layers between Multiple Files]]
   */
  inline def provideShared[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[Any, E1] =
    ${ SpecLayerMacros.provideSharedImpl[Any, R, E1]('self, 'layer) }

  /**
   * Builds the part of the environment that is not part of `TestEnvironment`
   * from `layer`, wiring the layers together at compile time, once for the
   * whole spec, and shares the services between all of its tests, leaving a
   * spec that only depends on `TestEnvironment`. Layer inputs that
   * `TestEnvironment` contains are taken from that remainder.
   *
   * {{{
   * import zio.{ULayer, ZIO, ZLayer}
   *
   * trait Logging
   *
   * val loggingLayer: ULayer[Logging] = ZLayer.succeed(new Logging {})
   *
   * val spec: Spec[TestEnvironment with Logging, Nothing] =
   *   test("logs")(ZIO.service[Logging].as(assertCompletes))
   *
   * val spec2: Spec[TestEnvironment, Nothing] =
   *   spec.provideCustomShared(loggingLayer)
   * }}}
   */
  @deprecated("use provideShared", "2.0.2")
  inline def provideCustomShared[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1] =
    ${ SpecLayerMacros.provideSharedImpl[TestEnvironment, R, E1]('self, 'layer) }
}

/**
 * The partially applied form of `provideSome`, with the remainder type `R0`
 * fixed.
 */
final class ProvideSomePartiallyApplied[R0, -R, +E](val self: Spec[R, E]) extends AnyVal {

  /**
   * Builds the part of the environment that `R0` does not contain from `layer`,
   * wiring the layers together at compile time, and provides it to each test,
   * leaving a spec that requires only `R0`. The layers are built once for each
   * test, and once more for each scoped node (such as one that `aroundAll`
   * creates).
   */
  inline def apply[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[R0, E1] =
    ${ SpecLayerMacros.provideSomeImpl[R0, R, E1]('self, 'layer) } // TODO:
}

/**
 * The partially applied form of `provideSomeShared`, with the remainder type
 * `R0` fixed.
 */
final class ProvideSomeSharedPartiallyApplied[R0, -R, +E](val self: Spec[R, E]) extends AnyVal {

  /**
   * Builds the part of the environment that `R0` does not contain from `layer`,
   * wiring the layers together at compile time, once for the whole spec, and
   * shares the services between its tests, leaving a spec that requires only
   * `R0`.
   */
  inline def apply[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[R0, E1] =
    ${ SpecLayerMacros.provideSomeSharedImpl[R0, R, E1]('self, 'layer) }
}
