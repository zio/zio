package zio.test

import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The `Live` trait provides access to the "live" default ZIO services from
 * within ZIO Test for workflows such as printing test results to the console or
 * timing out tests where it is necessary to access the real implementations of
 * these services.
 *
 * To access the "live" services, use the `live` method with a workflow that
 * would otherwise use the test version of the default ZIO services.
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
 * are re-exported in the ZIO Test package object.
 *
 * @see
 *   [[https://zio.dev/reference/test/services/live/ Live]]
 */
trait Live {

  /**
   * Runs `zio` with the live `Clock`, `Console`, `System` and `Random` services
   * in place of the test versions.
   */
  def provide[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
}

/**
 * The `Live` service: its `Test` implementation, the layer that builds it, and
 * the `live` and `withLive` methods that use the current `Live` service.
 */
object Live {

  /** The `Tag` of `Live`, used to find it in a `ZEnvironment`. */
  implicit val tag: Tag[Live] = Tag(EnvironmentTag.tagFromTagMacro[Live])

  /**
   * The `Live` implementation that the test environment installs. It holds the
   * live `Clock`, `Console`, `System` and `Random` services in `zenv`.
   */
  final case class Test(zenv: ZEnvironment[Clock with Console with System with Random]) extends Live {

    /**
     * Runs `zio` with the services in `zenv` replacing the default services of
     * the same types; the other default services are kept.
     */
    def provide[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      DefaultServices.currentServices.locallyWith(_.unionAll(zenv))(zio)
  }

  /**
   * Constructs a new `Live` service that implements the `Live` interface. This
   * typically should not be necessary as the `TestEnvironment` already includes
   * the `Live` service but could be useful if you are mixing in interfaces to
   * create your own environment type.
   */
  val default: ZLayer[Clock with Console with System with Random, Nothing, Live] = {
    implicit val trace = Tracer.newTrace
    ZLayer.scoped {
      ZIO.environmentWithZIO[Clock with Console with System with Random] { zenv =>
        val live = Test(zenv)
        withLiveScoped(live).as(live)
      }
    }
  }

  /**
   * Provides a workflow with the "live" default ZIO services.
   */
  def live[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    liveWith(_.provide(zio))

  /**
   * Runs a transformation function with the live default ZIO services while
   * ensuring that the workflow itself is run with the test services.
   *
   * {{{
   * import zio._
   * import zio.test._
   *
   * // The timeout uses the live clock; the sleep uses the TestClock,
   * // so `result` succeeds with None after 100 milliseconds of real time.
   * val result =
   *   Live.withLive(ZIO.sleep(1.hour).as("slept"))(_.timeout(100.millis))
   * }}}
   */
  def withLive[R, E, E1, A, B](
    zio: ZIO[R, E, A]
  )(f: ZIO[R, E, A] => ZIO[R, E1, B])(implicit trace: Trace): ZIO[R, E1, B] =
    DefaultServices.currentServices.getWith(services => live(f(DefaultServices.currentServices.locally(services)(zio))))
}
