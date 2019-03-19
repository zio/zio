package scalaz.zio.interop.reactiveStreams
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.core.SpecStructure
import scalaz.zio.stream.Take
import scalaz.zio.{ Queue, TestRuntime, UIO }

class QueueSubscriberSpec(implicit ee: ExecutionEnv) extends TestRuntime {

  def is: SpecStructure =
    "QueueSubscriberSpec".title ^ s2"""
   A QueueSubscriber should
     not throw Exceptions after the queue has been shut down $e1
    """

  def e1 = unsafeRun(
    for {
      runtime <- UIO.runtime[Any]
      q       <- Queue.bounded[Take[Throwable, Int]](10)
      qs      = new QueueSubscriber[Int](runtime, q)
      _       <- q.shutdown
      _       <- UIO(qs.onNext(1))
      _       <- UIO(qs.onError(new Throwable("boom")))
      _       <- UIO(qs.onComplete())
    } yield success
  )
}
