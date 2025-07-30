import java.util.concurrent.atomic.AtomicReference
import zio._

object Main extends zio.ZIOAppDefault {
  var q: Queue = Queue.unbounded[Int]
  dumpQ()

  private def dumpQ(): Unit = ZIO.unit.flatMap { _ =>
    val t = for {
      ref <- ZIO.attempt {
        new AtomicReference[Int]
      }
      fib <- ZIO.uninterruptibleMask { restore =>
        restore(q.take).flatMap { item =>
          ZIO.attempt {
            ref.set(item)
          }
        }
      }.forkDaemon
      _ <- q.offer(1) *> ZIO.sleep(1.millis) *> fib.interrupt
      _ <- fib.await
      s <- ZIO.attempt(ref.get())
      _ <- if (s eq null) {
        // take was cancelled, item should be in q
        q.take.flatMap { item =>
          if (item != 1) ZIO.die(new AssertionError("incorrect item: " + item))
          else ZIO.unit
        }
      } else {
        // take was completed, q should be empty
        q.isEmpty.flatMap { empty =>
          if (!empty) ZIO.die(new AssertionError("nonempty q after completed take"))
          else ZIO.unit
        }
      }
      _ <- ZIO.attempt(println("ok"))
    } yield ()
    t.repeatN(10)
  }
}