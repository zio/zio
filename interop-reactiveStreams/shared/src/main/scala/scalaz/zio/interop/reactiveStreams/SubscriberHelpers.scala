package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Subscriber, Subscription }
import scalaz.zio.stream.Sink.Step
import scalaz.zio.stream.ZSink
import scalaz.zio.{ Chunk, Queue, Runtime, UIO }

private[reactiveStreams] object SubscriberHelpers {

  def demandUnfoldSink[A](
    subscriber: Subscriber[_ >: A],
    demand: Queue[Long]
  ): ZSink[Any, Nothing, Unit, A, Unit] =
    new ZSink[Any, Nothing, Unit, A, Unit] {
      override type State = Long

      override def initial: UIO[Step[Long, Nothing]] = UIO(Step.more(0L))

      override def step(state: Long, a: A): UIO[Step[Long, Nothing]] =
        demand.isShutdown.flatMap {
          case true               => UIO(Step.done(state, Chunk.empty))
          case false if state > 0 => UIO(subscriber.onNext(a)).map(_ => Step.more(state - 1))
          case false              => demand.take.flatMap(n => UIO(subscriber.onNext(a)).map(_ => Step.more(n - 1)))
        }

      override def extract(state: Long): UIO[Unit] =
        demand.isShutdown.flatMap {
          case true  => UIO.unit
          case false => UIO(subscriber.onComplete())
        }
    }

  def createSubscription[A](
    subscriber: Subscriber[_ >: A],
    demand: Queue[Long],
    runtime: Runtime[_]
  ): Subscription =
    new Subscription {
      override def request(n: Long): Unit = {
        if (n <= 0) subscriber.onError(new IllegalArgumentException("n must be > 0"))
        runtime.unsafeRunAsync_(demand.offer(n).void)
      }
      override def cancel(): Unit = runtime.unsafeRun(demand.shutdown)
    }
}
