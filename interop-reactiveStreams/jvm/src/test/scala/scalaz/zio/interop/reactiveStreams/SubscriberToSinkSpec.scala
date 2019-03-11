package scalaz.zio.interop.reactiveStreams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Keep, Source, Sink => AkkaSink }
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.AfterAll
import org.specs2.specification.core.SpecStructure
import scalaz.zio.stream.Stream
import scalaz.zio.{ Task, TestRuntime, UIO }

class SubscriberToSinkSpec(implicit ee: ExecutionEnv) extends TestRuntime with AfterAll {

  def is: SpecStructure =
    "SubscriberToSinkSpec".title ^ s2"""
   Check if a `Subscriber`converted to a `Sink` correctly
     works $e1
    """
  implicit private val system: ActorSystem             = ActorSystem()
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  override def afterAll(): Unit =
    unsafeRun(UIO(materializer.shutdown()) *> Task.fromFuture(_ => system.terminate()).void)

  private val e1 = {
    val seq = List.range(0, 100)

    unsafeRun(
      for {
        subSeqF            <- UIO(Source.asSubscriber[Int].toMat(AkkaSink.seq)(Keep.both).run())
        (subscriber, seqF) = subSeqF
        sink               <- subscriber.toSink
        _                  <- Stream.fromIterable(seq).run(sink)
        r                  <- Task.fromFuture(_ => seqF)
      } yield r should_=== seq
    )
  }
}
