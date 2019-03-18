package scalaz.zio.interop.reactiveStreams
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.core.SpecStructure
import scalaz.zio._

class QueueSubscriberSpec(implicit ee: ExecutionEnv) extends TestRuntime {

  def is: SpecStructure =
    "QueueSubscriberSpec".title ^ s2"""
   A QueueSubscriber should
     not throw Exceptions after the queue has been shut down $e1
    """

  def e1 = unsafeRun(
    for {
      subStr          <- QueueSubscriber.make[Int](10)
      (subscriber, _) = subStr
      _               <- UIO(subscriber.onComplete())
      _               <- UIO(subscriber.onNext(1))
      _               <- UIO(subscriber.onError(new Throwable("boom")))
    } yield success
  )
}
