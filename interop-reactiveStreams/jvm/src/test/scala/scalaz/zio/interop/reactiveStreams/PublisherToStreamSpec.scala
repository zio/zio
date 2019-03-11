package scalaz.zio.interop.reactiveStreams

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, Sink => AkkaSink}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.AfterAll
import org.specs2.specification.core.SpecStructure
import scalaz.zio.Exit.Cause
import scalaz.zio.stream.Sink
import scalaz.zio.{Exit, Task, TestRuntime, UIO}

class PublisherToStreamSpec(implicit ee: ExecutionEnv) extends TestRuntime with AfterAll {

  def is: SpecStructure =
    "PublisherToStreamSpec".title ^ s2"""
   Check if a `Publisher`converted to a `Stream` correctly
     works with a well behaved `Publisher` $e1
     fails with an initially failed `Publisher` $e2
     fails with an eventually failing `Publisher` $e3
    """

  implicit private val system: ActorSystem             = ActorSystem()
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  override def afterAll(): Unit =
    unsafeRun(UIO(materializer.shutdown()) *> Task.fromFuture(_ => system.terminate()).void)

  private val e   = new RuntimeException("boom")
  private val seq = List.range(0, 100)

  private def publish(src: Source[Int, NotUsed]): Exit[Throwable, List[Int]] =
    unsafeRunSync(
      for {
        publisher <- UIO(src.runWith(AkkaSink.asPublisher(fanout = false)))
        stream    <- publisher.toStream()
        r         <- stream.run(Sink.collect[Int])
      } yield r
    )

  private val e1 = publish(Source(seq)) should_=== Exit.Success(seq)

  private val e2 = publish(Source.failed[Int](e)) should_=== Exit.Failure(Cause.Fail(`e`))

  private val e3 = publish(Source(seq).concat(Source.failed(e))) should_=== Exit.Failure(Cause.Fail(`e`))
}
