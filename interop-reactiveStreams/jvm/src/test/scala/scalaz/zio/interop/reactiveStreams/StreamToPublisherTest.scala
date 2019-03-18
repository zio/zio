package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.Publisher
import org.reactivestreams.tck.{ PublisherVerification, TestEnvironment }
import org.scalatestplus.testng.TestNGSuiteLike
import scalaz.zio.DefaultRuntime
import scalaz.zio.stream.Stream

class StreamToPublisherTest(env: TestEnvironment, publisherShutdownTimeout: Long)
    extends PublisherVerification[Int](env, publisherShutdownTimeout)
    with TestNGSuiteLike
    with DefaultRuntime {

  def this() {
    this(new TestEnvironment(500), 1000)
  }

  def createPublisher(elements: Long): Publisher[Int] =
    unsafeRun(
      Stream
        .unfold(elements)(n => if (n > 0) Some((1, n - 1)) else None)
        .toPublisher
    )

  override def createFailedPublisher(): Publisher[Int] =
    unsafeRun(
      Stream
        .fail(new RuntimeException("boom!"))
        .map(_.asInstanceOf[Int])
        .toPublisher
    )

}
