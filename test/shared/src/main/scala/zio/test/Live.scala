package zio.test

import zio.{IO, ZEnv, ZIO, ZLayer, Trace}
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The `Live` trait provides access to the "live" default ZIO services from
 * within ZIO Test for workflows such as printing test results to the console or
 * timing out tests where it is necessary to access the real implementations of
 * these services.
 *
 * The easiest way to access the "live" services is to use the `live` method
 * with a workflow that would otherwise use the test version of the default ZIO
 * services.
 *
 * {{{
 * import zio.Clock
 * import zio.test._
 *
 * val realTime = live(Clock.nanoTime)
 * }}}
 *
 * The `withLive` method can be used to apply a transformation to a workflow
 * with the live services while ensuring that the workflow itself still runs
 * with the test services, for example to time out a test. Both of these methods
 * are re-exported in the ZIO Test package object for easy availability.
 */
trait Live {
  def provide[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
}

object Live {

  /**
   * Constructs a new `Live` service that implements the `Live` interface. This
   * typically should not be necessary as the `TestEnvironment` already includes
   * the `Live` service but could be useful if you are mixing in interfaces to
   * create your own environment type.
   */
  val default: ZLayer[ZEnv, Nothing, Live] = {
    implicit val trace = Tracer.newTrace
    ZLayer {
      ZIO
        .environmentWith[ZEnv] { zenv =>
          new Live {
            def provide[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
              ZEnv.services.locallyWith(_.unionAll(zenv))(zio)
          }
        }
    }
  }

  /**
   * Provides a workflow with the "live" default ZIO services.
   */
  def live[R <: Live, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R with Live, E, A] =
    ZIO.serviceWithZIO[Live](_.provide(zio))

  /**
   * Runs a transformation function with the live default ZIO services while
   * ensuring that the workflow itself is run with the test services.
   */
  def withLive[R <: Live, E, E1, A, B](
    zio: ZIO[R, E, A]
  )(f: ZIO[R, E, A] => ZIO[R, E1, B])(implicit trace: Trace): ZIO[R, E1, B] =
    ZEnv.services.getWith(services => live(f(ZEnv.services.locally(services)(zio))))
}
