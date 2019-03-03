package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{Subscriber, Subscription}
import org.reactivestreams.tck.SubscriberWhiteboxVerification.{SubscriberPuppet, WhiteboxSubscriberProbe}
import org.reactivestreams.tck.{SubscriberWhiteboxVerification, TestEnvironment}
import org.scalatest.testng.TestNGSuiteLike
import scalaz.zio.{DefaultRuntime, Promise, Queue, Ref, Runtime}
import scalaz.zio.stream.Sink

class SubscriberTest(env: TestEnvironment)
    extends SubscriberWhiteboxVerification[Int](env)
    with TestNGSuiteLike
    with DefaultRuntime {

  def this() {
    this(new TestEnvironment(500))
  }

  override def createSubscriber(
    probe: WhiteboxSubscriberProbe[Int]
  ): Subscriber[Int] = {
    class ProbedSinkSubscriber[A](runtime: Runtime[_], q: Queue[Int], p: Promise[Throwable, A], expect: Ref[Long])
        extends SinkSubscriber[Int, A](runtime, q, p, expect) {
      override def onSubscribe(s: Subscription): Unit = {
        super.onSubscribe(s)
        probe.registerOnSubscribe(new SubscriberPuppet {
          override def triggerRequest(elements: Long): Unit = s.request(elements)
          override def signalCancel(): Unit                 = s.cancel()
        })
      }
      override def onNext(t: Int): Unit = {
        super.onNext(t)
        probe.registerOnNext(t)
      }
      override def onError(t: Throwable): Unit = {
        super.onError(t)
        probe.registerOnError(t)
      }
      override def onComplete(): Unit = {
        super.onComplete()
        probe.registerOnComplete()
      }
    }
    unsafeRun(
      SinkSubscriber.sinkToSubscriber(Sink.collect[Int], 10, new ProbedSinkSubscriber[List[Int]](_, _, _, _))
    )._1
  }

  override def createElement(element: Int): Int = element
}
