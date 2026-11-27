package zio.test

import zio._
import zio.stream.ZStream
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A service that holds the size parameter of a test. Sized generators such as
 * `Gen.listOf`, `Gen.string` and `Gen.small` read it to bound the size of the
 * values they generate. The test environment provides a `Sized` with size
 * 100, and `TestAspect.size` changes it for a test.
 */
trait Sized extends Serializable {
  /** Returns the current size. */
  def size(implicit trace: Trace): UIO[Int]
  /** Runs `zio` with the size set to `size`, then restores the previous size. */
  def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
  /** Returns a generator that runs `gen` with the size set to `size`. */
  def withSizeGen[R, A](size: Int)(gen: Gen[R, A])(implicit trace: Trace): Gen[R, A]
}

/** Constructors of the [[Sized]] service and accessors for the current one. */
object Sized {

  /** The `Tag` of the `Sized` service. */
  implicit val tag: Tag[Sized] = Tag(EnvironmentTag.tagFromTagMacro[Sized])

  private[test] object unsafe {
    def make(size: Int)(implicit unsafe: Unsafe): Sized =
      Test(FiberRef.unsafe.make(size))
  }

  /** The implementation of [[Sized]] that keeps the size in a `FiberRef`. */
  final case class Test(fiberRef: FiberRef[Int]) extends Sized {
    /** Returns the size stored in the `FiberRef`. */
    def size(implicit trace: Trace): UIO[Int] =
      fiberRef.get
    /** Runs `zio` with the size set to `size`, then restores the previous size. */
    def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      fiberRef.locally(size)(zio)
    /**
     * Returns a generator that runs `gen` with the size set to `size`. The
     * previous size is restored before each generated value is emitted.
     */
    def withSizeGen[R, A](size: Int)(gen: Gen[R, A])(implicit trace: Trace): Gen[R, A] =
      Gen {
        ZStream
          .fromZIO(fiberRef.get)
          .flatMap { oldSize =>
            ZStream.scoped(fiberRef.locallyScoped(size)) *> gen.sample.mapZIO(a => fiberRef.set(oldSize).as(a))
          }
      }
  }

  /** A layer that provides a `Sized` service with size 100. */
  val default: ZLayer[Any, Nothing, Sized] =
    live(100)(Trace.empty)

  /**
   * A layer that provides a `Sized` service with the given size, and makes it
   * the current `Sized` service while the layer's scope is open.
   */
  def live(size: Int)(implicit trace: Trace): Layer[Nothing, Sized] =
    ZLayer.scoped {
      val sized = unsafe.make(size)(Unsafe)
      withSizedScoped(sized).as(sized)
    }

  private[test] val initial: Sized =
    unsafe.make(100)(Unsafe.unsafe)

  /** Returns the size of the current `Sized` service. */
  def size(implicit trace: Trace): UIO[Int] =
    sizedWith(_.size)

  /**
   * Runs `zio` with the size of the current `Sized` service set to `size`,
   * then restores the previous size.
   */
  def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    sizedWith(_.withSize(size)(zio))

  /**
   * Returns a generator that runs `gen` with the size of the current `Sized`
   * service set to `size`.
   */
  def withSizeGen[R, A](size: Int)(gen: Gen[R, A])(implicit trace: Trace): Gen[R, A] =
    Gen.fromZIO(sized).flatMap(_.withSizeGen(size)(gen))
}
