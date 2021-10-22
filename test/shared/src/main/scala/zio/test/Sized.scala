package zio.test

import zio.{FiberRef, Has, Deps, UIO, URIO, ZIO, ZDeps, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait Sized extends Serializable {
  def size(implicit trace: ZTraceElement): UIO[Int]
  def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A]
}

object Sized {

  def live(size: Int)(implicit trace: ZTraceElement): Deps[Nothing, Has[Sized]] =
    ZDeps.fromZIO(FiberRef.make(size).map { fiberRef =>
      new Sized {
        def size(implicit trace: ZTraceElement): UIO[Int] =
          fiberRef.get
        def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
          fiberRef.locally(size)(zio)
      }
    })

  def size(implicit trace: ZTraceElement): URIO[Has[Sized], Int] =
    ZIO.accessZIO[Has[Sized]](_.get.size)

  def withSize[R <: Has[Sized], E, A](size: Int)(zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
    ZIO.accessZIO[R](_.get.withSize(size)(zio))
}
