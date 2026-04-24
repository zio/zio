package zio.fiber.mailbox

import zio._
import zio.stream._
import zio.internal.FiberScope

/**
 * Integration layer between FiberMailbox and ZIO's core Fiber system.
 * 
 * This provides:
 * - Direct integration with Fiber message passing
 * - Mailbox-backed ZIO Streams
 * - Fiber supervision with mailbox cleanup
 */
object FiberMailboxIntegration {
  
  /**
   * Creates a mailbox attached to the current fiber's lifecycle.
   * The mailbox will be automatically shutdown when the fiber completes.
   */
  def makeLocal[A](capacity: Int = 0): URIO[Scope, FiberMailbox[A]] = {
    ZIO.acquireRelease {
      FiberMailbox.make[A](capacity)
    }(_.shutdown)
  }
  
  /**
   * Creates an optimized mailbox attached to the current fiber's lifecycle.
   */
  def makeLocalOptimized[A](capacity: Int = 0): URIO[Scope, FiberMailboxOptimized[A]] = {
    ZIO.acquireRelease {
      FiberMailboxOptimized.make[A](capacity)
    }(_.shutdown)
  }
  
  /**
   * Creates a Stream from a mailbox.
   * The stream will continuously read from the mailbox until shutdown.
   */
  def toStream[A](mailbox: FiberMailbox[A]): Stream[Nothing, A] = {
    ZStream.repeatZIOOption(
      mailbox.receive.map {
        case Some(value) => ZIO.succeed(value)
        case None => ZIO.fail(None) // End stream when empty and shutdown
      }.flatten
    )
  }
  
  /**
   * Creates a mailbox that collects values from a Stream.
   */
  def fromStream[R, E, A](stream: ZStream[R, E, A]): ZIO[R with Scope, E, FiberMailbox[A]] = {
    for {
      mailbox <- FiberMailbox.make[A]()
      _ <- stream.foreach(mailbox.send(_)).forkScoped
    } yield mailbox
  }
  
  /**
   * Bidirectional communication channel between two fibers.
   */
  def makeChannel[Req, Res]: UIO[FiberChannel[Req, Res]] = {
    for {
      requestMailbox <- FiberMailboxOptimized.make[Req]()
      responseMailbox <- FiberMailboxOptimized.make[Res]()
    } yield new FiberChannelImpl(requestMailbox, responseMailbox)
  }
  
  /**
   * Pipe messages from one mailbox to another with transformation.
   */
  def pipe[A, B](
    source: FiberMailbox[A],
    sink: FiberMailbox[B]
  )(f: A => B): URIO[Scope, Fiber[Nothing, Unit]] = {
    ZStream.repeatZIOOption(
      source.receive.map {
        case Some(value) => ZIO.succeed(value)
        case None => ZIO.fail(None)
      }.flatten
    )
    .map(f)
    .foreach(sink.send(_))
    .forkScoped
  }
  
  /**
   * Merge multiple mailboxes into a single mailbox.
   */
  def merge[A](
    mailboxes: List[FiberMailbox[A]],
    targetCapacity: Int = 0
  ): URIO[Scope, FiberMailbox[A]] = {
    for {
      merged <- FiberMailbox.make[A](targetCapacity)
      fibers <- ZIO.foreach(mailboxes) { mailbox =>
        toStream(mailbox).foreach(merged.send(_)).forkScoped
      }
    } yield merged
  }
  
  /**
   * Creates a mailbox with rate limiting.
   */
  def makeRateLimited[A](
    capacity: Int,
    rate: Int,
    per: Duration
  ): UIO[FiberMailbox[A]] = {
    for {
      underlying <- FiberMailbox.make[A](capacity)
      rateLimiter <- Ref.make(0L)
      lastReset <- Ref.make(0L)
    } yield new RateLimitedMailbox(underlying, rateLimiter, lastReset, rate, per)
  }
  
  /**
   * Priority mailbox that processes high-priority messages first.
   */
  def makePriority[A](
    priority: A => Int,
    capacity: Int = 0
  ): UIO[FiberMailbox[A]] = {
    ZIO.succeed(new PriorityMailbox[A](priority, capacity))
  }
}

/**
 * Bidirectional channel for request-response patterns.
 */
trait FiberChannel[-Req, +Res] {
  def send(request: Req): UIO[Unit]
  def receive: UIO[Option[Res]]
  def requestResponse(request: Req): UIO[Res]
  def shutdown: UIO[Unit]
}

private class FiberChannelImpl[Req, Res](
  requestMailbox: FiberMailboxOptimized[Req],
  responseMailbox: FiberMailboxOptimized[Res]
) extends FiberChannel[Req, Res] {
  
  override def send(request: Req): UIO[Unit] = 
    requestMailbox.send(request)
  
  override def receive: UIO[Option[Res]] = 
    responseMailbox.receive
  
  override def requestResponse(request: Req): UIO[Res] = {
    // Simplified - would need correlation IDs for concurrent requests
    send(request) *> responseMailbox.receive.map(_.get)
  }
  
  override def shutdown: UIO[Unit] = 
    requestMailbox.shutdown *> responseMailbox.shutdown
}

/**
 * Rate-limited mailbox wrapper.
 */
private class RateLimitedMailbox[A](
  underlying: FiberMailbox[A],
  rateLimiter: Ref[Long],
  lastReset: Ref[Long],
  rate: Int,
  per: Duration
) extends FiberMailbox[A] {
  
  override def send(message: A): UIO[Unit] = {
    for {
      now <- Clock.currentTime(TimeUnit.MILLISECONDS)
      currentWindow <- lastReset.get
      windowStart = now / per.toMillis
      _ <- ZIO.when(windowStart > currentWindow) {
        lastReset.set(windowStart) *> rateLimiter.set(0)
      }
      current <- rateLimiter.getAndUpdate(_ + 1)
      _ <- ZIO.when(current >= rate) {
        val nextWindow = (windowStart + 1) * per.toMillis
        val waitTime = nextWindow - now
        Clock.sleep(waitTime.millis)
      }
      _ <- underlying.send(message)
    } yield ()
  }
  
  override def receive: UIO[Option[A]] = underlying.receive
  override def receiveAll: UIO[Chunk[A]] = underlying.receiveAll
  override def shutdown: UIO[Unit] = underlying.shutdown
  override def awaitShutdown: UIO[Unit] = underlying.awaitShutdown
}

import java.util.concurrent.TimeUnit

/**
 * Priority mailbox using multiple internal queues.
 */
private class PriorityMailbox[A](
  priority: A => Int,
  capacity: Int
) extends FiberMailbox[A] {
  
  // Use 3 priority levels: High (0), Normal (1), Low (2)
  private val highPriority = java.util.concurrent.ConcurrentLinkedQueue[A]
  private val normalPriority = java.util.concurrent.ConcurrentLinkedQueue[A]
  private val lowPriority = java.util.concurrent.ConcurrentLinkedQueue[A]
  
  private val shutdownFlag = new java.util.concurrent.atomic.AtomicBoolean(false)
  
  override def send(message: A): UIO[Unit] = ZIO.succeed {
    if (!shutdownFlag.get()) {
      priority(message) match {
        case 0 => highPriority.offer(message)
        case 2 => lowPriority.offer(message)
        case _ => normalPriority.offer(message)
      }
      ()
    } else ()
  }
  
  override def receive: UIO[Option[A]] = ZIO.succeed {
    val value = highPriority.poll()
    if (value != null) Some(value)
    else {
      val normal = normalPriority.poll()
      if (normal != null) Some(normal)
      else {
        val low = lowPriority.poll()
        if (low != null) Some(low)
        else if (shutdownFlag.get()) None
        else None
      }
    }
  }
  
  override def receiveAll: UIO[Chunk[A]] = ZIO.succeed {
    val builder = ChunkBuilder.make[A]()
    var continue = true
    while (continue) {
      receive match {
        case ZIO.Success(_, Some(value)) => builder += value
        case _ => continue = false
      }
    }
    builder.result()
  }
  
  override def shutdown: UIO[Unit] = ZIO.succeed(shutdownFlag.set(true))
  override def awaitShutdown: UIO[Unit] = ZIO.unit
  
  // Need to fix this import - add the proper queue
  private def highPriority = new java.util.concurrent.ConcurrentLinkedQueue[A]()
  private def normalPriority = new java.util.concurrent.ConcurrentLinkedQueue[A]()
  private def lowPriority = new java.util.concurrent.ConcurrentLinkedQueue[A]()
}

import zio.ChunkBuilder
import java.util.concurrent.ConcurrentLinkedQueue