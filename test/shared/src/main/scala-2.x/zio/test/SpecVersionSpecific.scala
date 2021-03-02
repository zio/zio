package zio.test

import zio.ZLayer
import zio.test.environment.TestEnvironment

private[test] trait SpecVersionSpecific[-R, +E, +T] { self: Spec[R, E, T] =>

  /**
   * Automatically assembles a layer for the spec, translating it up a level.
   */
  def provideLayer[E1 >: E](layers: ZLayer[_, E1, _]*): Spec[Any, E1, T] =
    macro SpecProvideLayerAutoMacros.provideLayerImpl[R, E1, T]

  /**
   * Automatically constructs the part of the environment that is not part of the
   * `TestEnvironment`, leaving an effect that only depends on the `TestEnvironment`.
   * This will also satisfy transitive `TestEnvironment` requirements with
   * `TestEnvironment.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: ZIO[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The TestEnvironment you use later will provide both Blocking to flyLayer and
   * // Console to zio
   * val zio2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   zio.provideCustomLayer(oldLadyLayer, flyLayer)
   * }}}
   */
  def provideCustomLayer[E1 >: E](layers: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    macro SpecProvideLayerAutoMacros.provideCustomLayerImpl[R, E1, T]

  /**
   * Automatically assembles a layer for the spec, sharing services between all tests.
   */
  def provideLayerManualSharedAuto[E1 >: E](layers: ZLayer[_, E1, _]*): Spec[Any, E1, T] =
    macro SpecProvideLayerAutoMacros.provideLayerManualSharedAutoImpl[R, E1, T]

  /**
   * Automatically constructs the part of the environment that is not part of the
   * `TestEnvironment`, leaving an effect that only depends on the `TestEnvironment`,
   * sharing services between all tests.
   *
   * This will also satisfy transitive `TestEnvironment` requirements with
   * `TestEnvironment.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: ZIO[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The TestEnvironment you use later will provide both Blocking to flyLayer and
   * // Console to zio
   * val zio2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   zio.provideCustomLayer(oldLadyLayer, flyLayer)
   * }}}
   */
  def provideCustomLayerManualSharedAuto[E1 >: E](layers: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    macro SpecProvideLayerAutoMacros.provideCustomLayerManualSharedAutoImpl[R, E1, T]
}
