package scalaz.zio.interop.reactiveStreams

import org.scalatest.{ Matchers, WordSpec }
import scalaz.zio.Exit.Cause
import scalaz.zio.stream.{ Sink, Stream }
import scalaz.zio.{ DefaultRuntime, Exit }

class PublisherToStreamTest extends WordSpec with Matchers with DefaultRuntime {

  "A PublisherStream" should {

    def publish(inputData: Stream[Any, Throwable, Int]): Exit[Throwable, List[Int]] = {
      val resultZIO =
        for {
          publisher <- inputData.toPublisher
          stream    <- publisher.toStream()
          r         <- stream.run(Sink.collect[Int])
        } yield r
      unsafeRunSync(resultZIO)
    }

    "work with a well behaved Publisher" in {
      val seq       = Seq.range(0, 100)
      val inputData = Stream.fromIterable(seq)
      publish(inputData) shouldBe Exit.Success(seq)
    }

    "fail with an initially failed Publisher" in {
      val e         = new RuntimeException("boom")
      val inputData = Stream.fail(e).map(_ => 0)
      publish(inputData) shouldBe Exit.Failure(Cause.Fail(`e`))
    }

    "fail with an eventually failing Publisher" in {
      val e         = new RuntimeException("boom")
      val seq       = Seq.range(0, 100)
      val inputData = Stream.fromIterable(seq) ++ Stream.fail(e).map(_ => 0)
      publish(inputData) shouldBe Exit.Failure(Cause.Fail(`e`))
    }
  }

}
