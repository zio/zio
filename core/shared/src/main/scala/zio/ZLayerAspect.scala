package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

trait ZLayerAspect[+LowerRIn, -UpperRIn, +LowerE, -UpperE, +LowerROut, -UpperROut] { self =>

  def apply[RIn >: LowerRIn <: UpperRIn, E >: LowerE <: UpperE, ROut >: LowerROut <: UpperROut](
    layer: ZLayer[RIn, E, ROut]
  )(implicit
    trace: Trace
  ): ZLayer[RIn, E, ROut]

  def >>>[
    LowerRIn1 >: LowerRIn,
    UpperRIn1 <: UpperRIn,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerROut1 >: LowerROut,
    UpperROut1 <: UpperROut
  ](
    that: ZLayerAspect[LowerRIn1, UpperRIn1, LowerE1, UpperE1, LowerROut1, UpperROut1]
  ): ZLayerAspect[LowerRIn1, UpperRIn1, LowerE1, UpperE1, LowerROut1, UpperROut1] =
    self.andThen(that)

  /**
   * Returns a new aspect that represents the sequential composition of this
   * aspect with the specified one.
   */
  def @@[
    LowerRIn1 >: LowerRIn,
    UpperRIn1 <: UpperRIn,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerROut1 >: LowerROut,
    UpperROut1 <: UpperROut
  ](
    that: ZLayerAspect[LowerRIn1, UpperRIn1, LowerE1, UpperE1, LowerROut1, UpperROut1]
  ): ZLayerAspect[LowerRIn1, UpperRIn1, LowerE1, UpperE1, LowerROut1, UpperROut1] =
    self >>> that

  def andThen[
    LowerRIn1 >: LowerRIn,
    UpperRIn1 <: UpperRIn,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerROut1 >: LowerROut,
    UpperROut1 <: UpperROut
  ](
    that: ZLayerAspect[LowerRIn1, UpperRIn1, LowerE1, UpperE1, LowerROut1, UpperROut1]
  ): ZLayerAspect[LowerRIn1, UpperRIn1, LowerE1, UpperE1, LowerROut1, UpperROut1] =
    new ZLayerAspect[LowerRIn1, UpperRIn1, LowerE1, UpperE1, LowerROut1, UpperROut1] {
      def apply[RIn >: LowerRIn1 <: UpperRIn1, E >: LowerE1 <: UpperE1, ROut >: LowerROut1 <: UpperROut1](
        layer: ZLayer[RIn, E, ROut]
      )(implicit trace: Trace): ZLayer[RIn, E, ROut] =
        that(self(layer))
    }
}

object ZLayerAspect {

  /**
   * An aspect that prints the results of layers to the console for debugging
   * purposes.
   */
  val debug: ZLayerAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZLayerAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[RIn, E, ROut](layer: ZLayer[RIn, E, ROut])(implicit trace: Trace): ZLayer[RIn, E, ROut] =
        layer.debug
    }

  /**
   * An aspect that prints the results of a layer to the console for debugging
   * purposes, using a specified user-defined prefix label.
   */
  def debug(prefix: => String): ZLayerAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZLayerAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[RIn, E, ROut](layer: ZLayer[RIn, E, ROut])(implicit trace: Trace): ZLayer[RIn, E, ROut] =
        layer.debug(prefix)
    }

  /**
   * An aspect that returns layers unchanged.
   */
  val identity: ZLayerAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZLayerAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[RIn, E, ROut](layer: ZLayer[RIn, E, ROut])(implicit trace: Trace): ZLayer[RIn, E, ROut] =
        layer
    }

  /**
   * An aspect that retries layers according to the specified schedule.
   */
  def retry[RIn1, E1](schedule: Schedule[RIn1, E1, Any]): ZLayerAspect[Nothing, RIn1, Nothing, E1, Nothing, Any] =
    new ZLayerAspect[Nothing, RIn1, Nothing, E1, Nothing, Any] {
      def apply[RIn <: RIn1, E <: E1, ROut](layer: ZLayer[RIn, E, ROut])(implicit trace: Trace): ZLayer[RIn, E, ROut] =
        layer.retry(schedule)
    }
}
