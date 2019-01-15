package scalaz.zio
package interop

import cats.effect.Effect
import fs2.Stream
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.AroundTimeout
import scalaz.zio.interop.catz._

class ZioWithFs2Spec(implicit ee: ExecutionEnv) extends Specification with AroundTimeout with RTS {

  def is = s2"""
  A simple fs2 join must
    work if `F` is `cats.effect.IO`          ${simpleJoin(fIsCats)}
    work if `F` is `scalaz.zio.interop.Task` ${simpleJoin(fIsZio)}

  fs2 resource handling must
    work when fiber is failed                ${bracketFail}
    work when fiber is terminated            ${bracketTerminate}
    work when fiber is interrupted           ${bracketInterrupt}
  """

  def simpleJoin(ints: => List[Int]) = {
    import scala.concurrent.duration._

    upTo(2.seconds) {
      ints must_=== List(1, 1)
    }
  }

  import scalaz.zio.duration._

  def fIsCats = testCaseJoin[cats.effect.IO].unsafeRunSync()

  def fIsZio: List[Int] =
    unsafeRun(testCaseJoin[scalaz.zio.interop.Task])

  def bracketFail =
    unsafeRun {
      (for {
        started  <- Promise.make[Nothing, Unit]
        released <- Promise.make[Nothing, Unit]
        fail     <- Promise.make[Nothing, Unit]
        _ <- Stream
              .bracket(started.succeed(()).void)(_ => released.succeed(()).void)
              .evalMap[Task, Unit] { _ =>
                fail.await *> IO.fail(new Exception())
              }
              .compile
              .drain
              .fork

        _ <- started.await
        _ <- fail.succeed(())
        _ <- released.await
      } yield ()).timeout(10.seconds)
    } must beSome(())

  def bracketTerminate =
    unsafeRun {
      (for {
        started   <- Promise.make[Nothing, Unit]
        released  <- Promise.make[Nothing, Unit]
        terminate <- Promise.make[Nothing, Unit]
        _ <- Stream
              .bracket(started.succeed(()).void)(_ => released.succeed(()).void)
              .evalMap[Task, Unit] { _ =>
                terminate.await *> IO.die(new Exception())
              }
              .compile
              .drain
              .fork

        _ <- started.await
        _ <- terminate.succeed(())
        _ <- released.await
      } yield ()).timeout(10.seconds)
    } must beSome(())

  def bracketInterrupt =
    unsafeRun {
      (for {
        started  <- Promise.make[Nothing, Unit]
        released <- Promise.make[Nothing, Unit]
        f <- Stream
              .bracket(IO.unit)(_ => released.succeed(()).void)
              .evalMap[Task, Unit](_ => started.succeed(()) *> IO.never)
              .compile
              .drain
              .fork

        _ <- started.await
        _ <- f.interrupt
        _ <- released.await
      } yield ()).timeout(10.seconds)
    } must beSome(())

  def testCaseJoin[F[_]: Effect]: F[List[Int]] = {
    def one: F[Int]       = Effect[F].delay(1)
    val s: Stream[F, Int] = Stream.eval(one)
    // TODO: there is no join anymore and we can't satisfy parJoin requirements yet
    //val ss: Stream[F, Stream[F, Int]] = Stream.emits(List(s, s))
    //ss.join(2).compile.toList
    s.interleave(s).compile.toList
  }
}
