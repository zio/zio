package zio.stream.experimental

import zio._

/**
 * A `ZPipeline[Env, Err, In, Out]` is a polymorphic stream transformer. Pipelines accept a stream
 * as input, and return the transformed stream as output.
 *
 * Pipelines can be thought of as a recipe for calling a bunch of methods on a source stream, to
 * yield a new (transformed) stream. A nice mental model is the following type alias:
 *
 * {{{
 * type ZPipeline[Env, Err, In, Out] = ZStream[Env, Err, In] => ZStream[Env, Err, Out]
 * }}}
 *
 * This encoding of a pipeline with a type alias is not used because it does not infer well.
 * In its place, this trait captures the polymorphism inherent to many pipelines, which can
 * therefore be more flexible about the environment and error types of the streams they transform.
 *
 * There is no fundamental requirement for pipelines to exist, because everything pipelines do
 * can be done directly on a stream. However, because pipelines separate the stream transformation
 * from the source stream itself, it becomes possible to abstract over stream transformations at the
 * level of values, creating, storing, and passing around reusable transformation pipelines that can
 * be applied to many different streams.
 *
 * The most common way to create a pipeline is to convert a sink into a pipeline (in general,
 * transforming elements of a stream requires the power of a sink). However, the companion object
 * has lots of other pipeline constructors based on the methods of stream.
 */
trait ZPipeline[-Env, +Err, -In, +Out] { self =>

  /**
   * Composes two pipelines into one pipeline, by first applying the transformation of this
   * pipeline, and then applying the transformation of the specified pipeline.
   */
  final def >>>[Env1 <: Env, Err1 >: Err, Out2](
    that: ZPipeline[Env1, Err1, Out, Out2]
  ): ZPipeline[Env1, Err1, In, Out2] =
    new ZPipeline[Env1, Err1, In, Out2] {
      def apply[Env0 <: Env1, Err0 >: Err1](stream: ZStream[Env0, Err0, In]): ZStream[Env0, Err0, Out2] =
        that(self(stream))
    }

  /**
   * Composes two pipelines into one pipeline, by first applying the transformation of the
   * specified pipeline, and then applying the transformation of this pipeline.
   */
  final def <<<[Env1 <: Env, Err1 >: Err, In2](
    that: ZPipeline[Env1, Err1, In2, In]
  ): ZPipeline[Env1, Err1, In2, Out] =
    new ZPipeline[Env1, Err1, In2, Out] {
      def apply[Env0 <: Env1, Err0 >: Err1](stream: ZStream[Env0, Err0, In2]): ZStream[Env0, Err0, Out] =
        self(that(stream))
    }

  /**
   * A named version of the `>>>` operator.
   */
  final def andThen[Env1 <: Env, Err1 >: Err, Out2](
    that: ZPipeline[Env1, Err1, Out, Out2]
  ): ZPipeline[Env1, Err1, In, Out2] =
    self >>> that

  def apply[Env1 <: Env, Err1 >: Err](stream: ZStream[Env1, Err1, In]): ZStream[Env1, Err1, Out]

  /**
   * A named version of the `<<<` operator.
   */
  final def compose[Env1 <: Env, Err1 >: Err, In2](
    that: ZPipeline[Env1, Err1, In2, In]
  ): ZPipeline[Env1, Err1, In2, Out] =
    self <<< that
}
object ZPipeline {

  /**
   * A shorter version of [[ZPipeline.identity]], which can facilitate more compact definition
   * of pipelines.
   *
   * {{{
   * ZPipeline[Int].filter(_ % 2 != 0)
   * }}}
   */
  def apply[I]: ZPipeline[Any, Nothing, I, I] = identity[I]

  /**
   * Creates a pipeline that collects elements with the specified partial function.
   *
   * {{{
   * ZPipeline.collect[Option[Int], Int] { case Some(v) => v }
   * }}}
   */
  def collect[In, Out](f: PartialFunction[In, Out]): ZPipeline[Any, Nothing, In, Out] =
    new ZPipeline[Any, Nothing, In, Out] {
      def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, In]): ZStream[Env1, Err1, Out] =
        stream.collect(f)
    }

  /**
   * Creates a transducer that always dies with the specified exception.
   *
   * {{{
   * ZPipeline.die(new IllegalStateException)
   * }}}
   */
  def die(e: => Throwable): ZPipeline[Any, Nothing, Any, Nothing] =
    ZPipeline.halt(Cause.die(e))

  /**
   * Creates a pipeline that drops elements until the specified predicate evaluates to true.
   *
   * {{{
   * ZPipeline.dropUntil[Int](_ > 100)
   * }}}
   */
  def dropUntil[In](f: In => Boolean): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, In]): ZStream[Env1, Err1, In] =
        stream.dropUntil(f)
    }

  /**
   * Creates a pipeline that drops elements while the specified predicate evaluates to true.
   *
   * {{{
   * ZPipeline.dropWhile[Int](_ <= 100)
   * }}}
   */
  def dropWhile[In](f: In => Boolean): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, In]): ZStream[Env1, Err1, In] =
        stream.dropWhile(f)
    }

  /**
   * Creates a pipeline that always fails with the specified value.
   */
  def fail[E](e: => E): ZPipeline[Any, E, Any, Nothing] =
    ZPipeline.halt(Cause.fail(e))

  /**
   * Creates a pipeline that filters elements according to the specified predicate.
   */
  def filter[In](f: In => Boolean): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, In]): ZStream[Env1, Err1, In] =
        stream.filter(f)
    }

  /**
   * Creates a pipeline from the provided fold function, which operates on the state and the
   * elements of the source stream.
   *
   * {{{
   * val counter = ZPipeline.foldLeft(0)((count, _) => count + 1)
   * }}}
   */
  def foldLeft[In, Out](z: Out)(f: (Out, In) => Out): ZPipeline[Any, Nothing, In, Out] =
    fold(z)(_ => true)(f)

  /**
   * Creates a transducer by effectfully folding over a structure of type `O`. The transducer will
   * fold the inputs until the stream ends, resulting in a stream with one element.
   */
  def foldLeftM[R, E, I, O](z: O)(f: (O, I) => ZIO[R, E, O]): ZPipeline[R, E, I, O] =
    foldM(z)(_ => true)(f)

  /**
   * A stateful fold that will emit the state and reset to the starting state every time the
   * specified predicate returns false.
   */
  def fold[I, O](out0: O)(contFn: O => Boolean)(f: (O, I) => O): ZPipeline[Any, Nothing, I, O] =
    new ZPipeline[Any, Nothing, I, O] {
      def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, I]): ZStream[Env1, Err1, O] =
        stream
          .mapAccum(out0) { case (o0, i) =>
            val o = f(o0, i)

            if (contFn(o)) (o, Some(o))
            else (out0, None)
          }
          .collect { case Some(v) => v }
    }

  /**
   * A stateful fold that will emit the state and reset to the starting state every time the
   * specified predicate returns false.
   */
  def foldM[R, E, I, O](out0: O)(contFn: O => Boolean)(f: (O, I) => ZIO[R, E, O]): ZPipeline[R, E, I, O] =
    new ZPipeline[R, E, I, O] {
      def apply[Env1 <: R, Err1 >: E](stream: ZStream[Env1, Err1, I]): ZStream[Env1, Err1, O] =
        stream
          .mapAccumM(out0) { case (o, i) =>
            f(o, i).map { o =>
              if (contFn(o)) (o, Some(o))
              else (out0, None)
            }
          }
          .collect { case Some(v) => v }
    }

  /**
   * Creates a transducer that folds elements of type `I` into a structure
   * of type `O` until `max` elements have been folded.
   *
   * Like foldWeighted, but with a constant cost function of 1.
   */
  def foldUntil[I, O](z: O, max: Long)(f: (O, I) => O): ZPipeline[Any, Nothing, I, O] =
    fold[I, (O, Long)]((z, 0))(_._2 < max) { case ((o, count), i) =>
      (f(o, i), count + 1)
    } >>> ZPipeline.map(_._1)

  /**
   * Creates a transducer that effectfully folds elements of type `I` into a structure
   * of type `O` until `max` elements have been folded.
   *
   * Like foldWeightedM, but with a constant cost function of 1.
   */
  def foldUntilM[R, E, I, O](z: O, max: Long)(f: (O, I) => ZIO[R, E, O]): ZPipeline[R, E, I, O] =
    foldM[R, E, I, (O, Long)]((z, 0))(_._2 < max) { case ((o, count), i) =>
      f(o, i).map((_, count + 1))
    } >>> ZPipeline.map(_._1)

  /**
   * Creates a pipeline that effectfully maps elements to the specified effectfully-computed
   * value.
   */
  def fromEffect[R, E, A](zio: ZIO[R, E, A]): ZPipeline[R, E, Any, A] =
    new ZPipeline[R, E, Any, A] {
      def apply[Env1 <: R, Err1 >: E](stream: ZStream[Env1, Err1, Any]): ZStream[Env1, Err1, A] =
        stream.mapM(_ => zio)
    }

  /**
   * Creates a pipeline from a sink, by transforming input streams with [[ZStream#transduce]].
   */
  // TODO
//  def fromSink[Env, Err, In, Out](sink: ZSink[Env, Err, In, In, Out]): ZPipeline[Env, Err, In, Out] =
//    new ZPipeline[Env, Err, In, Out] {
//      def apply[Env1 <: Env, Err1 >: Err](stream: ZStream[Env1, Err1, In]): ZStream[Env1, Err1, Out] =
//        stream.transduce(sink)
//    }

  /**
   * Creates a transducer that always dies with the specified exception.
   */
  def halt[E](cause: => Cause[E]): ZPipeline[Any, E, Any, Nothing] =
    new ZPipeline[Any, E, Any, Nothing] {
      def apply[Env1 <: Any, Err1 >: E](stream: ZStream[Env1, Err1, Any]): ZStream[Env1, Err1, Nothing] =
        ZStream.halt(cause)
    }

  /**
   * The identity pipeline, which does not modify streams in any way.
   */
  def identity[A]: ZPipeline[Any, Nothing, A, A] =
    new ZPipeline[Any, Nothing, A, A] {
      def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, A]): ZStream[Env1, Err1, A] =
        stream
    }

  /**
   * Creates a pipeline that maps elements with the specified function.
   */
  def map[In, Out](f: In => Out): ZPipeline[Any, Nothing, In, Out] =
    new ZPipeline[Any, Nothing, In, Out] {
      def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, In]): ZStream[Env1, Err1, Out] =
        stream.map(f)
    }

  /**
   * Creates a pipeline that maps elements with the specified effectful function.
   */
  def mapM[Env0, Err0, In, Out](f: In => ZIO[Env0, Err0, Out]): ZPipeline[Env0, Err0, In, Out] =
    new ZPipeline[Env0, Err0, In, Out] {
      def apply[Env1 <: Env0, Err1 >: Err0](stream: ZStream[Env1, Err1, In]): ZStream[Env1, Err1, Out] =
        stream.mapM(f)
    }

  /**
   * Creates a pipeline that always succeeds with the specified value.
   */
  def succeed[A](a: => A): ZPipeline[Any, Nothing, Any, A] =
    new ZPipeline[Any, Nothing, Any, A] {
      def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, Any]): ZStream[Env1, Err1, A] =
        ZStream.succeed(a)
    }

  /**
   * Creates a pipeline that takes elements until the specified predicate evaluates to true.
   */
  def takeUntil[In](f: In => Boolean): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, In]): ZStream[Env1, Err1, In] =
        stream.takeUntil(f)
    }

  /**
   * Creates a pipeline that takes elements while the specified predicate evaluates to true.
   */
  def takeWhile[In](f: In => Boolean): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, In]): ZStream[Env1, Err1, In] =
        stream.takeWhile(f)
    }

}
