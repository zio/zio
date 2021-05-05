package zio.macros

import zio.{Has, ZLayer}

/**
 * Represent the ability to construct a layer with resulting type [[Out]]
 * from constructor of type [[From]]
 */
trait LayerFromConstructor[From <: Out, Out] {

  /**
   * The input type of resulting layer.
   */
  type In

  def layer: ZLayer[In, Nothing, Has[Out]]
}

object LayerFromConstructor {
  type Aux[From <: Out, Out, In0] = LayerFromConstructor[From, Out] {
    type In = In0
  }

  def apply[From <: Out, Out](implicit lfc: LayerFromConstructor[From, Out]): Aux[From, Out, gen.In] = lfc

  implicit def materialize[From <: Out, Out, In]: LayerFromConstructor.Aux[From, Out, In] =
    macro LayerFromConstructorMacro.materialize[From, Out, In]
}
