/*
 * NOTE: Partial changes only for illustration
 * Goal: Add shutdownCause functionality with error type E to Queue
 * This is a simplified and focused modification sketch, not the full file
 */

// Add to the sealed abstract class definition:
sealed abstract class Queue[+E, +A] extends Dequeue.Internal[A] with Enqueue.Internal[A] {
  def shutdownCause(cause: Cause[E])(implicit trace: Trace): UIO[Chunk[A]]
}

// Modify the createQueue method signature to include the error type:
private def createQueue[E, A](
  queue: MutableConcurrentQueue[A],
  strategy: Strategy[A],
  fiberId: FiberId
)(implicit unsafe: Unsafe): Queue[E, A] = {
  val p = Promise.unsafe.make[Nothing, Unit](fiberId)
  val errRef = Runtime.default.unsafe.run(Ref.make[Option[Cause[E]]](None)).getOrThrowFiberFailure()
  unsafeCreate(queue, new ConcurrentDeque[Promise[Nothing, A]], p, new AtomicBoolean(false), strategy, errRef)
}

// Add errRef to QueueImpl constructor and shutdownCause implementation:
private final class QueueImpl[E, A](
  queue: MutableConcurrentQueue[A],
  takers: ConcurrentDeque[Promise[Nothing, A]],
  shutdownHook: Promise[Nothing, Unit],
  shutdownFlag: AtomicBoolean,
  strategy: Strategy[A],
  shutdownCauseRef: Ref[Option[Cause[E]]]
) extends Queue[E, A] {

  override def shutdownCause(cause: Cause[E])(implicit trace: Trace): UIO[Chunk[A]] =
    ZIO.fiberIdWith { fiberId =>
      for {
        alreadySet <- shutdownCauseRef.modify {
          case None    => (false, Some(cause))
          case Some(c) => (true, Some(c))
        }
        items <- takeAll
        _ <- if (!alreadySet) {
          shutdownHook.unsafe.succeedUnit(Unsafe.unsafe)
          val it = unsafePollAll(takers).iterator
          while (it.hasNext) {
            it.next().unsafe.done(Exit.failCause(cause))(Unsafe.unsafe)
          }
          strategy.shutdown(fiberId)
          ZIO.unit
        } else ZIO.unit
      } yield items
    }.uninterruptible

  // Override offer to check for shutdown with cause
  override def offer(a: A)(implicit trace: Trace): UIO[Boolean] =
    shutdownCauseRef.get.flatMap {
      case Some(cause) => ZIO.failCause(cause)
      case None        => originalOfferLogic(a)
    }

  def originalOfferLogic(a: A)(implicit trace: Trace): UIO[Boolean] = ???

  // Similarly override take, takeAll, etc., to fail with shutdown cause
}

// Update all factory methods (bounded, sliding, etc.) to carry E type
// And adjust tests accordingly in QueueSpec.scala
