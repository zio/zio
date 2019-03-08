package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Subscriber, Subscription }
import scalaz.zio.stream.Take
import scalaz.zio.stream.Take.{ End, Fail, Value }
import scalaz.zio.{ Queue, Runtime, UIO }

private[reactiveStreams] class QueueSubscriber[A](
  runtime: Runtime[_],
  q: Queue[Take[Throwable, A]]
) extends Subscriber[A] {

  // all signals in reactive streams are serialized, so we don't need any synchronization
  private var subscriptionOpt: Option[Subscription] = None
  private var signalledDemand                       = 0

  def signalDemand: UIO[Unit] =
    subscriptionOpt.fold(UIO.unit) { subscription =>
      q.size.flatMap { size =>
        if (size == 0) {
          val signalDemand = q.capacity - signalledDemand
          UIO(subscription.request(signalDemand.toLong))
        } else {
          UIO.unit
        }
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
      runtime.unsafeRun(q.offer(Value(t)))
      signalledDemand -= 1
    }

  override def onError(e: Throwable): Unit =
    if (e == null) {
      throw new NullPointerException("t was null in onError")
    } else {
      signalledDemand = 0
      subscriptionOpt = None
      runtime.unsafeRun(q.offer(Fail(e)).void)
    }

  override def onComplete(): Unit = {
    signalledDemand = 0
    subscriptionOpt = None
    runtime.unsafeRun(q.offer(End).void)
  }
}
