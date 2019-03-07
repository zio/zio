package scalaz.zio.interop.reactiveStreams

import org.scalatest.{ Matchers, WordSpec }
import scalaz.zio.DefaultRuntime
import scalaz.zio.stream.{ Sink, Stream }

class SubscriberToSinkTest extends WordSpec with Matchers with DefaultRuntime {

  "A SubscriberSink" should {
    "work" in {
      val seq = Seq.range(0, 100)
      val resultZIO =
        for {
          subRIO            <- Sink.collect[Int].toSubscriber()
          (subscriber, rio) = subRIO
          sink              <- subscriber.toSink
          _                 <- Stream.fromIterable(seq).run(sink)
          r                 <- rio
        } yield r should contain theSameElementsInOrderAs seq
      unsafeRun(resultZIO)
    }
  }
}
