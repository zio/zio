package zio.test

import zio.{Layer, FiberRef, UIO, URIO, ZIO, ZLayer, Trace}
import zio.stream.ZStream
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait Sized extends Serializable {
  def size(implicit trace: Trace): UIO[Int]
  def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
  def withSizeGen[R, A](size: Int)(gen: Gen[R, A])(implicit trace: Trace): Gen[R, A]
}

object Sized {

  val default: ZLayer[Any, Nothing, Sized] =
    live(100)(Trace.empty)

  def live(size: Int)(implicit trace: Trace): Layer[Nothing, Sized] =
    ZLayer.scoped(FiberRef.make(size).map { fiberRef =>
      new Sized {
        def size(implicit trace: Trace): UIO[Int] =
          fiberRef.get
        def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
          fiberRef.locally(size)(zio)
        def withSizeGen[R, A](size: Int)(gen: Gen[R, A])(implicit trace: Trace): Gen[R, A] =
          Gen {
            ZStream
              .fromZIO(fiberRef.get)
              .flatMap { oldSize =>
                ZStream.scoped(fiberRef.locallyScoped(size)) *> gen.sample.mapZIO(a => fiberRef.set(oldSize).as(a))
              }
          }
      }
    })

  def size(implicit trace: Trace): URIO[Sized, Int] =
    ZIO.serviceWithZIO(_.size)

  def withSize[R <: Sized, E, A](size: Int)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.serviceWithZIO[Sized](_.withSize(size)(zio))

  def withSizeGen[R <: Sized, A](size: Int)(gen: Gen[R, A])(implicit trace: Trace): Gen[R, A] =
    Gen.fromZIO(ZIO.service[Sized]).flatMap(_.withSizeGen(size)(gen))
}
