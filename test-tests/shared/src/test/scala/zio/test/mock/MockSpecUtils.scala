package zio.test.mock

import zio.duration._
import zio.test.environment.Live
import zio.test.mock.module.T22
import zio.test.{Assertion, ZSpec, assertM, testM}
import zio.{Has, IO, ULayer, ZIO}

trait MockSpecUtils[R] {

  lazy val intTuple22: T22[Int] =
    (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)

  private[mock] def testValue[E, A](name: String)(
    mock: ULayer[R],
    app: ZIO[R, E, A],
    check: Assertion[A]
  ): ZSpec[Any, E] = testM(name) {
    val result = mock.build.use[Any, E, A](app.provide _)
    assertM(result)(check)
  }

  private[mock] def testError[E, A](name: String)(
    mock: ULayer[R],
    app: ZIO[R, E, A],
    check: Assertion[E]
  ): ZSpec[Any, A] = testM(name) {
    val result = mock.build.use[Any, A, E](app.flip.provide _)
    assertM(result)(check)
  }

  private[mock] def testValueTimeboxed[E, A](name: String)(duration: Duration)(
    mock: ULayer[R],
    app: ZIO[R, E, A],
    check: Assertion[Option[A]]
  ): ZSpec[Has[Live], E] = testM(name) {
    val result =
      Live.live {
        mock.build
          .use(app.provide _)
          .timeout(duration)
      }

    assertM(result)(check)
  }

  private[mock] def testDied[E, A](name: String)(
    mock: ULayer[R],
    app: ZIO[R, E, A],
    check: Assertion[Throwable]
  ): ZSpec[Any, Any] = testM(name) {
    val result: IO[Any, Throwable] =
      mock.build
        .use(app.provide _)
        .orElse(ZIO.unit)
        .absorb
        .flip

    assertM(result)(check)
  }
}
