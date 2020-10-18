package zio.stream

import zio._

/**
 * A cursor that allows effectful iteration over a stream. Usage is similar to
 * [[ZStream#Pull]], but additionally offers the option to traverse a stream multiple
 * times.
 */
trait StreamCursor[-R, +E, +O] {

  /**
   * Pull the next chunk of the stream. As with [[ZStream#Pull]], `fail(None)` signals end of stream.
   * If any cursor has advanced past this point already, the effect will not be reevaluated.
   */
  val next: ZIO[R, Option[E], Chunk[O]]

  /**
   * Creates a new cursor at the current position in the stream. The new cursor is guranteed to
   * see the same elements as the original cursor when advancing.
   */
  val split: UIO[StreamCursor[R, E, O]]

  /**
   * Create a stream backed by this cursor.
   */
  final lazy val stream: ZStream[R, E, O] =
    ZStream(ZManaged.succeedNow(next))

}

object StreamCursor {

  def fromPull[R, E, O](pull: ZStream.Pull[R, E, O]): UIO[StreamCursor[R, E, O]] = {
    final case class Node(
      chunk: Chunk[O],
      next: ZIO[R, Option[E], Node]
    )

    sealed trait State
    object State {
      case object NotStarted                             extends State
      final case class Done(exit: Exit[Option[E], Node]) extends State
    }

    def makeCursor(getNode: ZIO[R, Option[E], Node]): UIO[StreamCursor[R, E, O]] =
      Ref.make(getNode).map { ref =>
        new StreamCursor[R, E, O] { self =>
          def nextNode: ZIO[R, Option[E], Node] =
            ref.get.flatten.flatMap { node =>
              ref.set(node.next).as(node)
            }
          val next: ZIO[R, Option[E], Chunk[O]] =
            nextNode.map(_.chunk)
          val split: UIO[StreamCursor[R, E, O]] =
            ref.get.flatMap(makeCursor)
        }
      }

    def makeGetNode(pull: ZIO[R, Option[E], Chunk[O]], sema: Semaphore): UIO[ZIO[R, Option[E], Node]] =
      Ref.make[State](State.NotStarted).map { ref =>
        ref.get.flatMap {
          case State.NotStarted =>
            sema.withPermit {
              ref.get.flatMap {
                case State.NotStarted =>
                  ZIO.uninterruptibleMask { restore =>
                    for {
                      exit <- restore(pull).run
                      result <- exit.foldM(
                                  cause => ZIO.succeedNow(Exit.halt(cause)),
                                  chunk => makeGetNode(pull, sema).map(Node(chunk, _)).run
                                )
                      _    <- ref.set(State.Done(result))
                      node <- ZIO.done(result)
                    } yield node
                  }
                case State.Done(exit) =>
                  ZIO.done(exit)
              }
            }
          case State.Done(exit) =>
            ZIO.done(exit)
        }
      }

    for {
      sema    <- Semaphore.make(1)
      getNode <- makeGetNode(pull, sema)
      cursor  <- makeCursor(getNode)
    } yield cursor
  }
}
