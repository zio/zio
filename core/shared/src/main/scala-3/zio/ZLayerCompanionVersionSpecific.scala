package zio

import zio.internal.macros._
import scala.deriving._

/**
 * The result of `ZLayer.make[R]`, which fixes the type `R` of the services to
 * build.
 */
final class WirePartiallyApplied[R](val dummy: Boolean = true) extends AnyVal {

  /**
   * Wires `layer` at compile time into a layer that produces `R` and requires
   * no services, as described on `ZLayer.make`.
   */
  inline def apply[E](inline layer: ZLayer[_, E, _]*): ZLayer[Any, E, R] =
    ${ LayerMacros.constructStaticProvideLayer[Any, R, E]('layer) }
}

/**
 * The result of `ZLayer.makeSome[R0, R]`, which fixes the remainder `R0` and
 * the type `R` of the services to build.
 */
final class WireSomePartiallyApplied[R0, R](val dummy: Boolean = true) extends AnyVal {

  /**
   * Wires `layer` at compile time into a layer that produces `R` and requires
   * `R0`, as described on `ZLayer.makeSome`.
   */
  inline def apply[E](inline layer: ZLayer[_, E, _]*): ZLayer[R0, E, R] =
    ${ LayerMacros.constructStaticProvideSomeLayer[R0, R, E]('layer) }
}

private[zio] transparent trait ZLayerCompanionVersionSpecific {

  /**
   * Automatically assembles a layer for the provided type `R`, at compile time,
   * from the given layers. The macro feeds each layer the outputs of the layers
   * that produce its inputs, and the resulting layer requires no services.
   *
   * Compilation fails when the type parameter `R` is omitted, when a required
   * service has no layer, when two layers produce the same service, or when
   * layers depend on each other in a cycle. The macro emits a compiler warning
   * that lists the layers that are not needed, except layers whose output is
   * `Unit`: those are built for their side effects. Including
   * `ZLayer.Debug.tree` or `ZLayer.Debug.mermaid` prints the graph of the
   * layers as a compiler warning.
   *
   * {{{
   * final case class Engine(horsepower: Int)
   * final case class Wheels(count: Int)
   * final case class Car(engine: Engine, wheels: Wheels)
   *
   * val engineLayer: ULayer[Engine] = ZLayer.succeed(Engine(150))
   * val wheelsLayer: ULayer[Wheels] = ZLayer.succeed(Wheels(4))
   * val carLayer: URLayer[Engine with Wheels, Car] =
   *   ZLayer.fromFunction((engine: Engine, wheels: Wheels) => Car(engine, wheels))
   *
   * val layer: ULayer[Car] = ZLayer.make[Car](carLayer, wheelsLayer, engineLayer)
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/contextual/zlayer/ ZLayer]]
   */
  inline def make[R]: WirePartiallyApplied[R] =
    new WirePartiallyApplied[R]()

  /**
   * Automatically assembles a layer for the provided type `R`, leaving a
   * remainder `R0`: the services of `R0` are not built by the given layers but
   * become the inputs of the resulting layer.
   *
   * It reports the same compile errors and warnings as `make`, and it warns
   * when `R0` is `Any`. On Scala 3 it also warns about each type of `R0` that
   * is not needed. On Scala 2 that warning appears when no type of `R0` is
   * needed, but not when only some of them are unused.
   *
   * {{{
   * final case class Engine(horsepower: Int)
   * final case class Wheels(count: Int)
   * final case class Car(engine: Engine, wheels: Wheels)
   *
   * val wheelsLayer: ULayer[Wheels] = ZLayer.succeed(Wheels(4))
   * val carLayer: URLayer[Engine with Wheels, Car] =
   *   ZLayer.fromFunction((engine: Engine, wheels: Wheels) => Car(engine, wheels))
   *
   * val layer: URLayer[Engine, Car] = ZLayer.makeSome[Engine, Car](carLayer, wheelsLayer)
   * }}}
   */
  def makeSome[R0, R] =
    new WireSomePartiallyApplied[R0, R]

  /**
   * Automatically derives a simple layer for the provided type `A`, at compile
   * time, from its primary constructor. Each constructor parameter becomes a
   * service that the layer requires, unless an implicit
   * `ZLayer.Derive.Default.WithContext` exists for its type: then the layer
   * uses that default and requires the services of the default instead.
   *
   * When `A` extends `ZLayer.Derive.Scoped` or `ZLayer.Derive.AcquireRelease`,
   * the layer runs its `scoped` effect after constructing `A`, and the
   * finalizers of that effect run when the layer is released. Compilation fails
   * for a trait, an abstract class or a Java class.
   *
   * {{{
   * final case class Engine(horsepower: Int)
   * final case class Wheels(count: Int)
   * class Car(wheels: Wheels, engine: Engine)
   *
   * val carLayer: URLayer[Wheels with Engine, Car] = ZLayer.derive[Car]
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/contextual/automatic-zlayer-derivation/ Automatic ZLayer Derivation]]
   */
  transparent inline def derive[A]: ZLayer[Nothing, Any, A] =
    ZLayerDerivationMacros.deriveLayer[A]
}
