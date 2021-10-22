package zio.test

import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.collection.immutable.SortedSet

/**
 * The `Annotations` trait provides access to an annotation map that tests
 * can add arbitrary annotations to. Each annotation consists of a string
 * identifier, an initial value, and a function for combining two values.
 * Annotations form monoids and you can think of `Annotations` as a more
 * structured logging service or as a super polymorphic version of the writer
 * monad effect.
 */
trait Annotations extends Serializable {
  def annotate[V](key: TestAnnotation[V], value: V)(implicit trace: ZTraceElement): UIO[Unit]
  def get[V](key: TestAnnotation[V])(implicit trace: ZTraceElement): UIO[V]
  def withAnnotation[R, E, A](zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, Annotated[E], Annotated[A]]
  def supervisedFibers(implicit trace: ZTraceElement): UIO[SortedSet[Fiber.Runtime[Any, Any]]]
}

object Annotations {

  /**
   * Accesses an `Annotations` instance in the environment and appends the
   * specified annotation to the annotation map.
   */
  def annotate[V](key: TestAnnotation[V], value: V)(implicit trace: ZTraceElement): URIO[Has[Annotations], Unit] =
    ZIO.accessZIO(_.get.annotate(key, value))

  /**
   * Accesses an `Annotations` instance in the environment and retrieves the
   * annotation of the specified type, or its default value if there is none.
   */
  def get[V](key: TestAnnotation[V])(implicit trace: ZTraceElement): URIO[Has[Annotations], V] =
    ZIO.accessZIO(_.get.get(key))

  /**
   * Returns a set of all fibers in this test.
   */
  def supervisedFibers(implicit
    trace: ZTraceElement
  ): ZIO[Has[Annotations], Nothing, SortedSet[Fiber.Runtime[Any, Any]]] =
    ZIO.accessZIO(_.get.supervisedFibers)

  /**
   * Constructs a new `Annotations` service.
   */
  val live: UDeps[Has[Annotations]] = {
    implicit val trace = Tracer.newTrace
    ZDeps.fromZIO(FiberRef.make(TestAnnotationMap.empty).map { fiberRef =>
      new Annotations {
        def annotate[V](key: TestAnnotation[V], value: V)(implicit trace: ZTraceElement): UIO[Unit] =
          fiberRef.update(_.annotate(key, value))
        def get[V](key: TestAnnotation[V])(implicit trace: ZTraceElement): UIO[V] =
          fiberRef.get.map(_.get(key))
        def withAnnotation[R, E, A](zio: ZIO[R, E, A])(implicit
          trace: ZTraceElement
        ): ZIO[R, Annotated[E], Annotated[A]] =
          fiberRef.locally(TestAnnotationMap.empty) {
            zio.foldZIO(e => fiberRef.get.map((e, _)).flip, a => fiberRef.get.map((a, _)))
          }
        def supervisedFibers(implicit trace: ZTraceElement): UIO[SortedSet[Fiber.Runtime[Any, Any]]] =
          ZIO.descriptorWith { descriptor =>
            get(TestAnnotation.fibers).flatMap {
              case Left(_) => ZIO.succeedNow(SortedSet.empty[Fiber.Runtime[Any, Any]])
              case Right(refs) =>
                ZIO
                  .foreach(refs)(ref => ZIO.succeed(ref.get))
                  .map(_.foldLeft(SortedSet.empty[Fiber.Runtime[Any, Any]])(_ ++ _))
                  .map(_.filter(_.id != descriptor.id))
            }
          }
      }
    })
  }

  /**
   * Accesses an `Annotations` instance in the environment and executes the
   * specified effect with an empty annotation map, returning the annotation
   * map along with the result of execution.
   */
  def withAnnotation[R <: Has[Annotations], E, A](zio: ZIO[R, E, A])(implicit
    trace: ZTraceElement
  ): ZIO[R, Annotated[E], Annotated[A]] =
    ZIO.accessZIO(_.get.withAnnotation(zio))
}
