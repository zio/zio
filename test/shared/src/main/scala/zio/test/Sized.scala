package zio.test

import zio.{FiberRef, Has, Layer, UIO, URIO, ZIO, ZLayer}

trait Sized extends Serializable {
  def size: UIO[Int]
  def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A]
}

object Sized {

  def live(size: Int): Layer[Nothing, Has[Sized]] =
    ZLayer.fromEffect(FiberRef.make(size).map { fiberRef =>
      new Sized {
        val size: UIO[Int] =
          fiberRef.get
        def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
          fiberRef.locally(size)(zio)
      }
    })

  def size: URIO[Has[Sized], Int] =
    ZIO.accessM[Has[Sized]](_.get.size)

  def withSize[R <: Has[Sized], E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.accessM[R](_.get.withSize(size)(zio))
}
