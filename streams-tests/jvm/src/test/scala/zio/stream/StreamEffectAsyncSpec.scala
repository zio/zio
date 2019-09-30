package zio.stream

import org.specs2.ScalaCheck
import zio.Exit.Success
import zio.ZQueueSpecUtil.waitForValue
import zio.{ IO, Promise, Ref, Task, TestRuntime, UIO, ZIO }

class StreamEffectAsyncSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with ScalaCheck {

  def is = "StreamEffectAsyncSpec".title ^ s2"""
  Stream.effectAsync
    effectAsync                 $effectAsync

  Stream.effectAsyncMaybe
    effectAsyncMaybe signal end stream $effectAsyncMaybeSignalEndStream
    effectAsyncMaybe Some              $effectAsyncMaybeSome
    effectAsyncMaybe None              $effectAsyncMaybeNone
    effectAsyncMaybe back pressure     $effectAsyncMaybeBackPressure

  Stream.effectAsyncM
    effectAsyncM                   $effectAsyncM
    effectAsyncM signal end stream $effectAsyncMSignalEndStream
    effectAsyncM back pressure     $effectAsyncMBackPressure

  Stream.effectAsyncInterrupt
    effectAsyncInterrupt Left              $effectAsyncInterruptLeft
    effectAsyncInterrupt Right             $effectAsyncInterruptRight
    effectAsyncInterrupt signal end stream $effectAsyncInterruptSignalEndStream
    effectAsyncInterrupt back pressure     $effectAsyncInterruptBackPressure
  """

  private def effectAsync =
    prop { list: List[Int] =>
      val s = Stream.effectAsync[Throwable, Int] { k =>
        inParallel {
          list.foreach(a => k(Task.succeed(a)))
        }
      }

      unsafeRunSync(s.take(list.size).runCollect) must_=== Success(list)
    }

  private def effectAsyncM =
    prop { list: List[Int] =>
      unsafeRun {
        for {
          latch <- Promise.make[Nothing, Unit]
          fiber <- ZStream
                    .effectAsyncM[Any, Throwable, Int] { k =>
                      inParallel {
                        list.foreach(a => k(Task.succeed(a)))
                      }
                      latch.succeed(()) *>
                        Task.unit
                    }
                    .take(list.size)
                    .run(Sink.collectAll[Int])
                    .fork
          _ <- latch.await
          s <- fiber.join
        } yield s must_=== list
      }
    }

  private def effectAsyncMSignalEndStream = unsafeRun {
    for {
      result <- Stream
                 .effectAsyncM[Nothing, Int] { k =>
                   inParallel {
                     k(IO.fail(None))
                   }
                   UIO.succeed(())
                 }
                 .runCollect
    } yield result must_=== List()
  }

  private def effectAsyncMBackPressure = unsafeRun {
    for {
      refCnt  <- Ref.make(0)
      refDone <- Ref.make[Boolean](false)
      stream = ZStream.effectAsyncM[Any, Throwable, Int](
        cb => {
          inParallel {
            // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
            (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeed(1)))
            cb(refDone.set(true) *> ZIO.fail(None))
          }
          UIO.unit
        },
        5
      )
      run    <- stream.run(ZSink.fromEffect(ZIO.never)).fork
      _      <- waitForValue(refCnt.get, 7)
      isDone <- refDone.get
      _      <- run.interrupt
    } yield isDone must_=== false
  }

  private def effectAsyncMaybeSignalEndStream = unsafeRun {
    for {
      result <- Stream
                 .effectAsyncMaybe[Nothing, Int] { k =>
                   k(IO.fail(None))
                   None
                 }
                 .runCollect
    } yield result must_=== List()
  }

  private def effectAsyncMaybeSome =
    prop { list: List[Int] =>
      val s = Stream.effectAsyncMaybe[Throwable, Int] { _ =>
        Some(Stream.fromIterable(list))
      }

      unsafeRunSync(s.runCollect.map(_.take(list.size))) must_=== Success(list)
    }

  private def effectAsyncMaybeNone =
    prop { list: List[Int] =>
      val s = Stream.effectAsyncMaybe[Throwable, Int] { k =>
        inParallel {
          list.foreach(a => k(Task.succeed(a)))
        }
        None
      }

      unsafeRunSync(s.take(list.size).runCollect) must_=== Success(list)
    }
  private def effectAsyncMaybeBackPressure = unsafeRun {
    for {
      refCnt  <- Ref.make(0)
      refDone <- Ref.make[Boolean](false)
      stream = ZStream.effectAsyncMaybe[Any, Throwable, Int](
        cb => {
          inParallel {
            // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
            (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeed(1)))
            cb(refDone.set(true) *> ZIO.fail(None))
          }
          None
        },
        5
      )
      run    <- stream.run(ZSink.fromEffect(ZIO.never)).fork
      _      <- waitForValue(refCnt.get, 7)
      isDone <- refDone.get
      _      <- run.interrupt
    } yield isDone must_=== false
  }

  private def effectAsyncInterruptLeft = unsafeRun {
    for {
      cancelled <- Ref.make(false)
      latch     <- Promise.make[Nothing, Unit]
      fiber <- Stream
                .effectAsyncInterrupt[Nothing, Unit] { offer =>
                  inParallel {
                    offer(ZIO.succeed(()))
                  }
                  Left(cancelled.set(true))
                }
                .tap(_ => latch.succeed(()))
                .run(Sink.collectAll[Unit])
                .fork
      _      <- latch.await
      _      <- fiber.interrupt
      result <- cancelled.get
    } yield result must_=== true
  }

  private def effectAsyncInterruptRight =
    prop { list: List[Int] =>
      val s = Stream.effectAsyncInterrupt[Throwable, Int] { _ =>
        Right(Stream.fromIterable(list))
      }

      unsafeRunSync(s.take(list.size).runCollect) must_=== Success(list)
    }

  private def effectAsyncInterruptSignalEndStream = unsafeRun {
    for {
      result <- Stream
                 .effectAsyncInterrupt[Nothing, Int] { k =>
                   inParallel {
                     k(IO.fail(None))
                   }
                   Left(UIO.succeed(()))
                 }
                 .runCollect
    } yield result must_=== List()
  }

  private def effectAsyncInterruptBackPressure = unsafeRun {
    for {
      refCnt  <- Ref.make(0)
      refDone <- Ref.make[Boolean](false)
      stream = ZStream.effectAsyncInterrupt[Any, Throwable, Int](
        cb => {
          inParallel {
            // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
            (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeed(1)))
            cb(refDone.set(true) *> ZIO.fail(None))
          }
          Left(UIO.unit)
        },
        5
      )
      run    <- stream.run(ZSink.fromEffect(ZIO.never)).fork
      _      <- waitForValue(refCnt.get, 7)
      isDone <- refDone.get
      _      <- run.interrupt
    } yield isDone must_=== false
  }

  private def inParallel(action: => Unit): Unit =
    ec.execute(() => action)
}
