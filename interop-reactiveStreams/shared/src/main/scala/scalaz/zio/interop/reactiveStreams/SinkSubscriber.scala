package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Subscriber, Subscription }
import scalaz.zio.stream.{ Sink, Stream }
import scalaz.zio.{ Promise, Queue, Runtime, Task, ZIO }

class SinkSubscriber[A, B](
  runtime: Runtime[_],
  q: Queue[A],
  p: Promise[Throwable, B]
) extends Subscriber[A] {

  // all signals in reactive streams are serialized, so we don't need any synchronization
  private var subscriptionOpt: Option[Subscription] = None

  override def onSubscribe(subscription: Subscription): Unit =
    if (subscription == null) {
      throw new NullPointerException("s was null in onSubscribe")
    } else if (subscriptionOpt.isDefined) {
      subscription.cancel()
    } else {
      subscriptionOpt = Some(subscription)
      runtime.unsafeRunAsync(q.awaitShutdown *> Task(subscription.cancel()))(_ => ())
      // see reactive streams rule 3.17. We do not track demand beyond Long.MaxValue
      subscription.request(Long.MaxValue)
    }

  override def onNext(t: A): Unit =
    if (t == null) {
      throw new NullPointerException("t was null in onNext")
    } else {
      runtime.unsafeRun(q.offer(t).void)
    }

  override def onError(e: Throwable): Unit =
    if (e == null) {
      throw new NullPointerException("t was null in onError")
    } else {
      runtime.unsafeRun(p.fail(e) *> q.shutdown)
    }

  override def onComplete(): Unit =
    runtime.unsafeRun(q.shutdown)
}

object SinkSubscriber {
  private[reactiveStreams] def sinkToSubscriber[R, E <: Throwable, A0, A, B](
    sink: Sink[R, E, A0, A, B],
    qSize: Int = 10
  ): ZIO[R, E, (Subscriber[A], Task[B])] =
    for {
      runtime <- ZIO.runtime[R]
      q       <- Queue.bounded[A](qSize)
      p       <- Promise.make[Throwable, B]
      _       <- p.done(Stream.fromQueue(q).run(sink).provide(runtime.Environment)).fork
    } yield (new SinkSubscriber[A, B](runtime, q, p), p.await)
}
