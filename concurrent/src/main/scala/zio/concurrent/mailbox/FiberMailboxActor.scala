package zio.fiber.mailbox

import zio._
import zio.stream._
import java.util.concurrent.atomic._
import scala.annotation.tailrec

/**
 * Actor-style mailbox with typed message handling.
 * Provides request/response patterns and message type dispatch.
 */
trait FiberMailboxActor[-Req, +Res] {
  def tell(request: Req): UIO[Unit]
  def ask(request: Req): UIO[Res]
  def askTimeout(request: Req, timeout: Duration): UIO[Option[Res]]
  def stream: Stream[Nothing, Req]
  def shutdown: UIO[Unit]
}

object FiberMailboxActor {
  
  /**
   * Creates an actor mailbox with a handler function.
   */
  def make[Req, Res](
    handler: Req => UIO[Res],
    capacity: Int = 0
  ): UIO[FiberMailboxActor[Req, Res]] = {
    for {
      mailbox <- FiberMailboxOptimized.make[ActorMessage[Req, Res]](capacity)
      state <- Ref.make[ActorState](ActorState.Running)
      actor = new ActorMailboxImpl(mailbox, handler, state)
      _ <- actor.run.forkDaemon
    } yield actor
  }
  
  /**
   * Creates a stateful actor with accumulating state.
   */
  def makeStateful[S, Req, Res](
    initialState: S,
    handler: (S, Req) => UIO[(S, Res)],
    capacity: Int = 0
  ): UIO[FiberMailboxActor[Req, Res]] = {
    for {
      mailbox <- FiberMailboxOptimized.make[ActorMessage[Req, Res]](capacity)
      state <- Ref.make[S](initialState)
      actorStatus <- Ref.make[ActorState](ActorState.Running)
      actor = new StatefulActorMailboxImpl(mailbox, state, handler, actorStatus)
      _ <- actor.run.forkDaemon
    } yield actor
  }
  
  /**
   * Creates a pub/sub style mailbox for broadcasting.
   */
  def makePubSub[A](capacity: Int = 0): UIO[FiberMailboxPubSub[A]] = {
    for {
      subscribers <- Ref.make[List[FiberMailbox[A]]](Nil)
    } yield new PubSubImpl[A](subscribers, capacity)
  }
}

// Internal message type for actor communication
private sealed trait ActorMessage[Req, Res]
private object ActorMessage {
  case class Tell[Req, Res](request: Req) extends ActorMessage[Req, Res]
  case class Ask[Req, Res](
    request: Req, 
    promise: Promise[Nothing, Res],
    timeout: Option[Duration]
  ) extends ActorMessage[Req, Res]
}

private sealed trait ActorState
private object ActorState {
  case object Running extends ActorState
  case object ShuttingDown extends ActorState
  case object Shutdown extends ActorState
}

/**
 * Actor mailbox implementation.
 */
private class ActorMailboxImpl[Req, Res](
  mailbox: FiberMailboxOptimized[ActorMessage[Req, Res]],
  handler: Req => UIO[Res],
  state: Ref[ActorState]
) extends FiberMailboxActor[Req, Res] {
  import ActorMessage._
  
  override def tell(request: Req): UIO[Unit] = 
    mailbox.send(Tell(request))
  
  override def ask(request: Req): UIO[Res] = {
    for {
      promise <- Promise.make[Nothing, Res]
      _ <- mailbox.send(Ask(request, promise, None))
      result <- promise.await
    } yield result
  }
  
  override def askTimeout(request: Req, timeout: Duration): UIO[Option[Res]] = {
    for {
      promise <- Promise.make[Nothing, Res]
      _ <- mailbox.send(Ask(request, promise, Some(timeout)))
      result <- promise.await.timeout(timeout)
    } yield result
  }
  
  override def stream: Stream[Nothing, Req] = 
    ZStream.repeatZIOOption(
      mailbox.receiveTimeout(1.second).map(_.fold[ZIO[Any, Option[Nothing], Req]](
        ZIO.fail(None)
      )(msg => ZIO.succeed(msg match {
        case Tell(req) => req
        case Ask(req, _, _) => req
      })))
    )
  
  override def shutdown: UIO[Unit] = state.set(ActorState.Shutdown) *> mailbox.shutdown
  
  def run: UIO[Unit] = {
    def processLoop: UIO[Unit] = {
      mailbox.receive.flatMap {
        case None => ZIO.unit
        case Some(message) =>
          message match {
            case Tell(request) =>
              handler(request).ignore *> processLoop
            case Ask(request, promise, timeout) =>
              handler(request).flatMap(promise.succeed(_)).ignore *> processLoop
          }
      }
    }
    
    processLoop
  }
}

/**
 * Stateful actor mailbox implementation.
 */
private class StatefulActorMailboxImpl[S, Req, Res](
  mailbox: FiberMailboxOptimized[ActorMessage[Req, Res]],
  currentState: Ref[S],
  handler: (S, Req) => UIO[(S, Res)],
  actorState: Ref[ActorState]
) extends FiberMailboxActor[Req, Res] {
  import ActorMessage._
  
  override def tell(request: Req): UIO[Unit] = 
    mailbox.send(Tell(request))
  
  override def ask(request: Req): UIO[Res] = {
    for {
      promise <- Promise.make[Nothing, Res]
      _ <- mailbox.send(Ask(request, promise, None))
      result <- promise.await
    } yield result
  }
  
  override def askTimeout(request: Req, timeout: Duration): UIO[Option[Res]] = {
    for {
      promise <- Promise.make[Nothing, Res]
      _ <- mailbox.send(Ask(request, promise, Some(timeout)))
      result <- promise.await.timeout(timeout)
    } yield result
  }
  
  override def stream: Stream[Nothing, Req] = 
    ZStream.repeatZIOOption(
      mailbox.receiveTimeout(1.second).map(_.fold[ZIO[Any, Option[Nothing], Req]](
        ZIO.fail(None)
      )(msg => ZIO.succeed(msg match {
        case Tell(req) => req
        case Ask(req, _, _) => req
      })))
    )
  
  override def shutdown: UIO[Unit] = actorState.set(ActorState.Shutdown) *> mailbox.shutdown
  
  def run: UIO[Unit] = {
    def processLoop: UIO[Unit] = {
      mailbox.receive.flatMap {
        case None => ZIO.unit
        case Some(message) =>
          message match {
            case Tell(request) =>
              currentState.get.flatMap { state =>
                handler(state, request).flatMap { case (newState, _) =>
                  currentState.set(newState)
                }
              }.ignore *> processLoop
            case Ask(request, promise, timeout) =>
              currentState.get.flatMap { state =>
                handler(state, request).flatMap { case (newState, result) =>
                  currentState.set(newState) *> promise.succeed(result)
                }
              }.ignore *> processLoop
          }
      }
    }
    
    processLoop
  }
}

/**
 * Pub/Sub mailbox interface.
 */
trait FiberMailboxPubSub[A] {
  def publish(message: A): UIO[Unit]
  def subscribe: UIO[FiberMailbox[A]]
  def unsubscribe(mailbox: FiberMailbox[A]): UIO[Unit]
  def subscriberCount: UIO[Int]
}

/**
 * Pub/Sub implementation using fan-out pattern.
 */
private class PubSubImpl[A](
  subscribersRef: Ref[List[FiberMailbox[A]]],
  capacity: Int
) extends FiberMailboxPubSub[A] {
  
  override def publish(message: A): UIO[Unit] = {
    subscribersRef.get.flatMap { subscribers =>
      ZIO.foreachPar(subscribers)(_.send(message)).unit
    }
  }
  
  override def subscribe: UIO[FiberMailbox[A]] = {
    for {
      mailbox <- FiberMailboxOptimized.make[A](capacity)
      _ <- subscribersRef.update(mailbox :: _)
    } yield mailbox
  }
  
  override def unsubscribe(mailbox: FiberMailbox[A]): UIO[Unit] = {
    subscribersRef.update(_.filterNot(_ eq mailbox)) *> mailbox.shutdown
  }
  
  override def subscriberCount: UIO[Int] = subscribersRef.get.map(_.size)
}

/**
 * Mailbox router for load balancing across multiple mailboxes.
 */
class FiberMailboxRouter[-A](strategy: RoutingStrategy[A]) {
  private val targets = new AtomicReference[List[FiberMailbox[A]]](Nil)
  private val counter = new AtomicInteger(0)
  
  def addTarget(mailbox: FiberMailbox[A]): UIO[Unit] = 
    ZIO.succeed(targets.updateAndGet(_ :+ mailbox)).unit
  
  def removeTarget(mailbox: FiberMailbox[A]): UIO[Unit] = 
    ZIO.succeed(targets.updateAndGet(_.filterNot(_ eq mailbox))).unit
  
  def route(message: A): UIO[Unit] = {
    val targetList = targets.get()
    if (targetList.isEmpty) ZIO.unit
    else {
      strategy match {
        case RoutingStrategy.RoundRobin =>
          val idx = counter.getAndIncrement() % targetList.size
          targetList(idx.abs % targetList.size).send(message)
        case RoutingStrategy.Random =>
          val idx = scala.util.Random.nextInt(targetList.size)
          targetList(idx).send(message)
        case RoutingStrategy.Broadcast =>
          ZIO.foreach(targetList)(_.send(message)).unit
        case RoutingStrategy.LeastLoaded =>
          // Simplified - would track actual load
          val idx = counter.getAndIncrement() % targetList.size
          targetList(idx.abs % targetList.size).send(message)
      }
    }
  }
}

sealed trait RoutingStrategy[A]
object RoutingStrategy {
  case object RoundRobin extends RoutingStrategy[Any]
  case object Random extends RoutingStrategy[Any]
  case object Broadcast extends RoutingStrategy[Any]
  case object LeastLoaded extends RoutingStrategy[Any]
}