package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Subscriber, Subscription }
import scalaz.zio.stream.{ SinkR, Stream }
import scalaz.zio.{ Promise, Queue, Runtime, Task, UIO, ZIO }

class SinkSubscriber[A, B](
  runtime: Runtime[_],
  q: Queue[A],
  p: Promise[Throwable, B]
) extends Subscriber[A] {

  // all signals in reactive streams are serialized, so we don't need any synchronization
  private var subscriptionOpt: Option[Subscription] = None
  private var signalledDemand                       = q.capacity

  def signalDemand: UIO[Unit] =
    q.size.flatMap { size =>
      if (size == 0) {
        val signalDemand = q.capacity - signalledDemand
        UIO(subscriptionOpt.foreach(_.request(signalDemand.toLong)))
      } else {
        UIO.unit
      }
    }

  override def onSubscribe(subscription: Subscription): Unit =
    if (subscription == null) {
      throw new NullPointerException("s was null in onSubscribe")
    } else if (subscriptionOpt.isDefined) {
      subscription.cancel()
    } else {
      subscriptionOpt = Some(subscription)
      runtime.unsafeRunAsync(q.awaitShutdown *> UIO(subscription.cancel()))(_ => ())
      subscription.request(q.capacity.toLong)
    }

  override def onNext(t: A): Unit =
    if (t == null) {
      throw new NullPointerException("t was null in onNext")
    } else {
      runtime.unsafeRun(q.offer(t))
      signalledDemand -= 1
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
    sink: SinkR[R, E, A0, A, B],
    qSize: Int = 10
  ): ZIO[R, Nothing, (Subscriber[A], Task[B])] =
    for {
      runtime    <- ZIO.runtime[R]
      q          <- Queue.bounded[A](qSize)
      p          <- Promise.make[Throwable, B]
      subscriber = new SinkSubscriber[A, B](runtime, q, p)
      fiber <- Stream
                .fromQueue(q)
                .tap(_ => subscriber.signalDemand)
                .run(sink)
                .provide(runtime.Environment)
                .fork
      _ <- p.done(fiber.join).fork
    } yield (subscriber, p.await)
}
