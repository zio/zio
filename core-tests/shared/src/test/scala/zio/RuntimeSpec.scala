package zio

import zio.test._
import zio.test.TestAspect.jvmOnly

object RuntimeSpec extends ZIOBaseSpec {
  val r = Runtime.default

  def foo =
    for {
      _ <- ZIO.succeed(42)
      _ <- ZIO.fail("Uh oh!")
    } yield ()

  def bar =
    for {
      _ <- ZIO.succeed(92)
      a <- foo
    } yield a

  def buz =
    for {
      _ <- ZIO.async[Any, Nothing, Int](k => k(ZIO.succeed(42)))
      a <- bar
    } yield a

  def traceOf(exit: Exit[Any, Any]): Chunk[String] =
    exit.fold[ZTrace](_.trace, _ => ZTrace.none).stackTrace.map(_.toString)

  def fastPath[E, A](zio: ZIO[Any, E, A]): Exit[E, A] =
    r.unsafeRunSyncFast(zio)

  def slowPath[E, A](zio: ZIO[Any, E, A]): Task[Exit[E, A]] =
    ZIO.attemptBlocking(r.defaultUnsafeRunSync(zio))

  def spec = suite("RuntimeSpec") {
    suite("primitives") {
      test("ZIO.succeed") {
        assertTrue(fastPath(ZIO.succeed(42)) == Exit.succeed(42))
      }
    } +
      suite("fallback") {
        test("merged traces") {

          for {
            exit <- slowPath(buz)
            trace = traceOf(exit)
          } yield assertTrue(
            trace.exists(_.contains("foo")) && trace.exists(_.contains("bar")) && trace.exists(_.contains("buz"))
          )
        } @@ jvmOnly
      } +
      suite("traces") {
        test("depth of 2") {
          val exit = fastPath(bar)

          val trace = traceOf(exit)

          assertTrue(trace.exists(_.contains("foo")) && trace.exists(_.contains("bar")))
        }
      }
  }
}
