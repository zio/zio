package scalaz.zio.interop.reactiveStreams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Keep, Sink => AkkaSink }
import akka.stream.testkit.TestPublisher.Probe
import akka.stream.testkit.scaladsl.TestSource
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.AfterAll
import org.specs2.specification.core.SpecStructure
import scalaz.zio.Exit.Cause
import scalaz.zio.stream.Sink
import scalaz.zio.{ Exit, Task, TestRuntime, UIO }

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
    unsafeRun(UIO(materializer.shutdown()) *> Task.fromFuture(_ => system.terminate()).unit)

  private val e   = new RuntimeException("boom")
  private val seq = List.range(0, 100)

  private val bufferSize = 10

  private def publish(seq: List[Int], failure: Option[Throwable]): Exit[Throwable, List[Int]] = {

    val probePublisherGraph = TestSource.probe[Int].toMat(AkkaSink.asPublisher(fanout = false))(Keep.both)

    def loop(probe: Probe[Int], remaining: List[Int], pending: Int): Task[Unit] =
      for {
        n             <- Task(probe.expectRequest())
        _             <- Task(assert(n + pending <= bufferSize))
        half          = n.toInt / 2 + 1
        (nextN, tail) = remaining.splitAt(half)
        _             <- Task(nextN.foreach(probe.sendNext))
        _ <- if (nextN.size < half) Task(failure.fold(probe.sendComplete())(probe.sendError))
            else loop(probe, tail, n.toInt - half)
      } yield ()

    unsafeRunSync {
      for {
        pp                 <- Task(probePublisherGraph.run())
        (probe, publisher) = pp
        fiber              <- publisher.toStream(bufferSize).run(Sink.collect[Int]).fork
        _                  <- Task(probe.ensureSubscription())
        _                  <- loop(probe, seq, 0)
        r                  <- fiber.join
      } yield r
    }
  }

  private val e1 = publish(seq, None) should_=== Exit.Success(seq)

  private val e2 = publish(Nil, Some(e)) should_=== Exit.Failure(Cause.Fail(`e`))

  private val e3 = publish(seq, Some(e)) should_=== Exit.Failure(Cause.Fail(`e`))
}
