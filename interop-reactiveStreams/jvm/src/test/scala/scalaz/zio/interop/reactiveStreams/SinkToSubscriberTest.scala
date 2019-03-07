package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.Subscriber
import org.reactivestreams.tck.{ SubscriberBlackboxVerification, TestEnvironment }
import org.scalatestplus.testng.TestNGSuiteLike
import scalaz.zio.DefaultRuntime
import scalaz.zio.stream.Sink

class SinkToSubscriberTest(env: TestEnvironment)
    extends SubscriberBlackboxVerification[Int](env)
    with TestNGSuiteLike
    with DefaultRuntime {

  def this() {
    this(new TestEnvironment(500))
  }

  override def createSubscriber(): Subscriber[Int] =
    unsafeRun(Sink.collect[Int].toSubscriber())._1

  override def createElement(element: Int): Int = element
}
