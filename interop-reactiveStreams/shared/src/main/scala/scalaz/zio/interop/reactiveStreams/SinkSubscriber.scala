package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Subscriber, Subscription }
import scalaz.zio.stream.{ Sink, Stream }
import scalaz.zio.{ Promise, Queue, Ref, Runtime, Task, UIO, ZIO }

class SinkSubscriber[T, A](
  runtime: Runtime[_],
  q: Queue[T],
  p: Promise[Throwable, A],
  expected: Ref[Long]
) extends Subscriber[T] {

  var subscription: Option[Subscription] = None

  override def onSubscribe(s: Subscription): Unit =
    if (s == null) {
      throw new NullPointerException("s was null in onSubscribe")
    } else if (subscription.isDefined) {
      s.cancel()
    } else {
      subscription = Some(s)
      runtime.unsafeRunAsync(q.awaitShutdown *> Task(s.cancel()))(_ => ())
      runtime.unsafeRun(
        expected.update { _ =>
          s.request(Long.MaxValue)
          Long.MaxValue
        }.void
      )
    }

  override def onNext(t: T): Unit =
    if (t == null) {
      throw new NullPointerException("t was null in onNext")
    } else {
      runtime.unsafeRun(
        expected.modify { n =>
          if (n > 0) {
            (q.offer(t).void, n - 1)
          } else {
            (q.offer(t) *> Task(subscription.foreach(_.request(Long.MaxValue))).void, Long.MaxValue)
          }
        }.flatten
      )
    }

  override def onError(t: Throwable): Unit =
    if (t == null) {
      throw new NullPointerException("t was null in onError")
    } else {
      runtime.unsafeRun(p.fail(t) *> q.shutdown)
    }

  override def onComplete(): Unit =
    runtime.unsafeRun(q.shutdown)
}

object SinkSubscriber {
  private[reactiveStreams] def sinkToSubscriber[T, A](
    sink: Sink[Any, _ <: Throwable, Unit, T, A],
    qSize: Int = 10,
    makeSubscriber: (Runtime[_], Queue[T], Promise[Throwable, A], Ref[Long]) => SinkSubscriber[T, A] =
      new SinkSubscriber[T, A](_, _, _, _)
  ): UIO[(Subscriber[T], Task[A])] =
    for {
      runtime <- ZIO.runtime[Any]
      q       <- Queue.bounded[T](qSize)
      p       <- Promise.make[Throwable, A]
      expect  <- Ref.make(0L)
      _       <- p.done(Stream.fromQueue(q).run(sink)).fork
    } yield (makeSubscriber(runtime, q, p, expect), p.await)
}
