package zio.test

import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.collection.immutable.SortedSet

/**
 * The `Annotations` trait provides access to an annotation map that tests can
 * add arbitrary annotations to. Each annotation consists of a string
 * identifier, an initial value, and a function for combining two values. You
 * can think of `Annotations` as a more structured logging service or as a super
 * polymorphic version of the writer monad effect. Each test gets its own
 * `Annotations` service.
 *
 * @see
 *   [[https://zio.dev/reference/test/aspects/annotating-tests/ Annotating Tests]]
 */
trait Annotations extends Serializable {

  /**
   * Combines `value` with the current value of the annotation `key` in this
   * service's map, using the annotation's `combine` function.
   */
  def annotate[V](key: TestAnnotation[V], value: V)(implicit trace: Trace): UIO[Unit]

  /**
   * Returns the current value of the annotation `key` in this service's map, or
   * its initial value when nothing has been added to it.
   */
  def get[V](key: TestAnnotation[V])(implicit trace: Trace): UIO[V]

  /**
   * Returns an effect that runs `zio` and, when it finishes, adds this
   * service's current annotation map to the annotations of its result, whether
   * it succeeds or fails. Values of the same annotation are combined.
   */
  def withAnnotation[R, E](zio: ZIO[R, TestFailure[E], TestSuccess])(implicit
    trace: Trace
  ): ZIO[R, TestFailure[E], TestSuccess]

  /**
   * Returns the fibers that ZIO Test has recorded for the current test in the
   * `TestAnnotation.fibers` annotation, without the calling fiber. The set is
   * empty when no fibers are recorded.
   */
  def supervisedFibers(implicit trace: Trace): UIO[SortedSet[Fiber.Runtime[Any, Any]]]
  private[zio] def unsafe: UnsafeAPI
  private[zio] trait UnsafeAPI {

    /**
     * Combines `value` with the current value of the annotation `key` right
     * away, outside of an effect.
     */
    def annotate[V](key: TestAnnotation[V], value: V)(implicit unsafe: Unsafe): Unit
  }
}

/**
 * Accessors for the current test's [[Annotations]] service, and its default
 * implementation.
 */
object Annotations {

  /**
   * The `Tag` of `Annotations`.
   */
  implicit val tag: Tag[Annotations] = Tag(EnvironmentTag.tagFromTagMacro[Annotations])

  private[test] object unsafe {
    def make()(implicit unsafe: Unsafe): Annotations =
      Test(Ref.unsafe.make(TestAnnotationMap.empty))
  }

  /**
   * The default implementation of [[Annotations]], which keeps the annotation
   * map in `ref`.
   */
  final case class Test(ref: Ref.Atomic[TestAnnotationMap]) extends Annotations {

    /**
     * Combines `value` with the current value of the annotation `key` in this
     * service's map, using the annotation's `combine` function.
     */
    def annotate[V](key: TestAnnotation[V], value: V)(implicit trace: Trace): UIO[Unit] =
      ref.update(_.annotate(key, value))

    /**
     * Returns the current value of the annotation `key` in this service's map,
     * or its initial value when nothing has been added to it.
     */
    def get[V](key: TestAnnotation[V])(implicit trace: Trace): UIO[V] =
      ZIO.succeed(ref.unsafe.get(Unsafe).get(key))

    /**
     * Returns an effect that runs `zio` and, when it finishes, adds this
     * service's current annotation map to the annotations of its result,
     * whether it succeeds or fails. Values of the same annotation are combined.
     */
    def withAnnotation[R, E](zio: ZIO[R, TestFailure[E], TestSuccess])(implicit
      trace: Trace
    ): ZIO[R, TestFailure[E], TestSuccess] =
      zio.mapBoth(_.annotated(ref.unsafe.get(Unsafe)), _.annotated(ref.unsafe.get(Unsafe)))

    /**
     * Returns the fibers that ZIO Test has recorded for the current test in the
     * `TestAnnotation.fibers` annotation, without the calling fiber. The set is
     * empty when no fibers are recorded.
     */
    def supervisedFibers(implicit trace: Trace): UIO[SortedSet[Fiber.Runtime[Any, Any]]] =
      ZIO.fiberIdWith { fiberId =>
        get(TestAnnotation.fibers).map {
          case Right(refs) =>
            val builder = SortedSet.newBuilder[Fiber.Runtime[Any, Any]]
            refs.foreach(_.get().foreach(f => if (f.id != fiberId) builder += f))
            builder.result()
          case _ => SortedSet.empty[Fiber.Runtime[Any, Any]]
        }
      }
    private[zio] def unsafe: UnsafeAPI =
      new UnsafeAPI {
        def annotate[V](key: TestAnnotation[V], value: V)(implicit unsafe: Unsafe): Unit =
          ref.unsafe.update(_.annotate(key, value))(unsafe)
      }
  }

  /**
   * Combines `value` with the current value of the annotation `key` in the
   * current test's `Annotations` service, using the annotation's `combine`
   * function.
   */
  def annotate[V](key: TestAnnotation[V], value: V)(implicit trace: Trace): UIO[Unit] =
    annotationsWith(_.annotate(key, value))

  /**
   * Returns the current value of the annotation `key` in the current test's
   * `Annotations` service, or its initial value when nothing has been added to
   * it.
   */
  def get[V](key: TestAnnotation[V])(implicit trace: Trace): UIO[V] =
    annotationsWith(_.get(key))

  /**
   * Returns the fibers that ZIO Test has recorded for the current test in the
   * `TestAnnotation.fibers` annotation, without the calling fiber. The set is
   * empty when no fibers are recorded.
   */
  def supervisedFibers(implicit trace: Trace): UIO[SortedSet[Fiber.Runtime[Any, Any]]] =
    annotationsWith(_.supervisedFibers)

  /**
   * Constructs a new `Annotations` service with an empty annotation map, and
   * makes it the current test's service while the layer is in use.
   */
  val live: ULayer[Annotations] = {
    implicit val trace = Tracer.newTrace
    ZLayer.scoped {
      val annotations = unsafe.make()(Unsafe)
      withAnnotationsScoped(annotations).as(annotations)
    }
  }

  /**
   * Returns an effect that runs `zio` and, when it finishes, adds the current
   * test's annotation map to the annotations of its result, whether it succeeds
   * or fails. Values of the same annotation are combined. The map is not reset
   * before `zio` runs, and the result type is that of `zio`.
   */
  def withAnnotation[R, E](zio: ZIO[R, TestFailure[E], TestSuccess])(implicit
    trace: Trace
  ): ZIO[R, TestFailure[E], TestSuccess] =
    annotationsWith(_.withAnnotation(zio))
}
