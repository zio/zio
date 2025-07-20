package zio

import zio.test._
import java.util.concurrent.atomic.{AtomicReference, AtomicBoolean}

object InterruptGapSpec extends ZIOSpecDefault {

  def spec = suite("Interruption Gap")(
    test("asyncMaybe should not have interruption gap") {
      val effect = for {
        ref              <- ZIO.attempt(new AtomicReference[Int](42))
        bodyWasCalledRef <- ZIO.attempt(new AtomicBoolean(false))
        holder           <- ZIO.attempt(new AtomicReference[Int](0))
        getAndSave = ZIO.uninterruptibleMask { restore =>
                       restore(innerTask(ref, bodyWasCalledRef)).flatMap { i =>
                         ZIO.attempt(holder.set(i))
                       }
                     }
        fib            <- getAndSave.forkDaemon
        _              <- fib.interrupt
        _              <- fib.await
        bodyWasCalled  <- ZIO.attempt(bodyWasCalledRef.get())
        holderContents <- ZIO.attempt(holder.get())
        _ <- if (bodyWasCalled) {
               ZIO.attempt {
                 assertTrue(holderContents == 42)
               }
             } else {
               ZIO.unit
             }
      } yield ()
      effect.repeatN(9999).as(assertTrue(true))
    } @@ TestAspect.repeat(Schedule.recurs(5)) @@ TestAspect.timeout(30.seconds)
  )

  def innerTask(ref: AtomicReference[Int], bodyWasCalled: AtomicBoolean): Task[Int] =
    ZIO.asyncMaybe { _ =>
      bodyWasCalled.set(true)
      val v      = ref.get()
      val result = if (v != 0) v else 9999
      Some(ZIO.succeed(result))
    }
}
