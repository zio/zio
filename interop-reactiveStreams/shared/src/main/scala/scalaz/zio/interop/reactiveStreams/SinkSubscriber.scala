package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Subscriber, Subscription }
import scalaz.zio.stream.{ Sink, Stream }
import scalaz.zio.{ Promise, Queue, Runtime, Task, UIO, ZIO }

class SinkSubscriber[T, A](
  runtime: Runtime[_],
  q: Queue[T],
  p: Promise[Throwable, A]
) extends Subscriber[T] {

  // all signals in reactive streams are serialized, so we don't need any synchronization
  private var subscriptionOpt: Option[Subscription] = None
  private var expected: Long                        = Long.MaxValue

  override def onSubscribe(subscription: Subscription): Unit =
    if (subscription == null) {
      throw new NullPointerException("s was null in onSubscribe")
    } else if (subscriptionOpt.isDefined) {
      subscription.cancel()
    } else {
      subscriptionOpt = Some(subscription)
      runtime.unsafeRunAsync(q.awaitShutdown *> Task(subscription.cancel()))(_ => ())
      subscription.request(Long.MaxValue)
    }

  override def onNext(t: T): Unit =
    if (t == null) {
      throw new NullPointerException("t was null in onNext")
    } else {
      expected -= 1
      runtime.unsafeRun(q.offer(t).void)
      if (expected <= 0) subscriptionOpt.foreach(_.request(Long.MaxValue))
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
  private[reactiveStreams] def sinkToSubscriber[T, A](
    sink: Sink[Any, _ <: Throwable, Unit, T, A],
    qSize: Int = 10
  ): UIO[(Subscriber[T], Task[A])] =
    for {
      runtime <- ZIO.runtime[Any]
      q       <- Queue.bounded[T](qSize)
      p       <- Promise.make[Throwable, A]
      _       <- p.done(Stream.fromQueue(q).run(sink)).fork
    } yield (new SinkSubscriber[T, A](runtime, q, p), p.await)
}
