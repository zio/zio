package zio.query.internal

import scala.annotation.tailrec

import zio.ZIO
import zio.query.internal.BlockedRequests._
import zio.query.{ DataSource, DataSourceAspect, Described }

/**
 * `BlockedRequests` captures a collection of blocked requests as a data
 * structure. By doing this the library is able to preserve information about
 * which requests must be performed sequentially and which can be performed in
 * parallel, allowing for maximum possible batching and pipelining while
 * preserving ordering guarantees.
 */
private[query] sealed trait BlockedRequests[-R] { self =>

  /**
   * Combines this collection of blocked requests with the specified collection
   * of blocked requests, in parallel.
   */
  final def &&[R1 <: R](that: BlockedRequests[R1]): BlockedRequests[R1] =
    Both(self, that)

  /**
   * Combines this collection of blocked requests with the specified collection
   * of blocked requests, in sequence.
   */
  final def ++[R1 <: R](that: BlockedRequests[R1]): BlockedRequests[R1] =
    Then(self, that)

  /**
   * Transforms all data sources with the specified data source aspect, which
   * can change the environment type of data sources but must preserve the
   * request type of each data source.
   */
  final def mapDataSources[R1 <: R](f: DataSourceAspect[R1]): BlockedRequests[R1] =
    self match {
      case Empty          => Empty
      case Both(l, r)     => Both(l.mapDataSources(f), r.mapDataSources(f))
      case Then(l, r)     => Then(l.mapDataSources(f), r.mapDataSources(f))
      case Single(ds, br) => Single(f(ds), br)
    }

  /**
   * Provides each data source with part of its required environment.
   */
  final def provideSome[R0](f: Described[R0 => R]): BlockedRequests[R0] =
    self match {
      case Empty          => Empty
      case Both(l, r)     => Both(l.provideSome(f), r.provideSome(f))
      case Then(l, r)     => Then(l.provideSome(f), r.provideSome(f))
      case Single(ds, br) => Single(ds.provideSome(f), br)
    }

  /**
   * Executes all requests, submitting requests to each data source in
   * parallel.
   */
  val run: ZIO[R, Nothing, Unit] =
    ZIO.effectSuspendTotal {
      ZIO.foreach_(BlockedRequests.flatten(self)) { requestsByDataSource =>
        ZIO.foreach(requestsByDataSource.toIterable) {
          case (dataSource, sequential) =>
            for {
              completedRequests <- dataSource.runAll(sequential.map(_.map(_.request)))
              _ <- ZIO.foreach_(sequential) { parallel =>
                    ZIO.foreach_(parallel) { blockedRequest =>
                      blockedRequest.result.set(completedRequests.lookup(blockedRequest.request))
                    }
                  }
            } yield ()
        }
      }
    }
}

private[query] object BlockedRequests {

  /**
   * The empty collection of blocked requests.
   */
  val empty: BlockedRequests[Any] =
    Empty

  /**
   * Constructs a collection of blocked requests from the specified blocked
   * request and data source.
   */
  def single[R, K](dataSource: DataSource[R, K], blockedRequest: BlockedRequest[K]): BlockedRequests[R] =
    Single(dataSource, blockedRequest)

  final case class Both[-R](left: BlockedRequests[R], right: BlockedRequests[R]) extends BlockedRequests[R]

  case object Empty extends BlockedRequests[Any]

  final case class Single[-R, A](dataSource: DataSource[R, A], blockedRequest: BlockedRequest[A])
      extends BlockedRequests[R]

  final case class Then[-R](left: BlockedRequests[R], right: BlockedRequests[R]) extends BlockedRequests[R]

  /**
   * Flattens a collection of blocked requests into a collection of pipelined
   * and batched requests that can be submitted for execution.
   */
  private def flatten[R](
    blockedRequests: BlockedRequests[R]
  ): List[Sequential[R]] = {

    @tailrec
    def loop[R](
      blockedRequests: List[BlockedRequests[R]],
      flattened: List[Sequential[R]]
    ): List[Sequential[R]] = {
      val (parallel, sequential) =
        blockedRequests.foldLeft[(Parallel[R], List[BlockedRequests[R]])]((Parallel.empty, List.empty)) {
          case ((parallel, sequential), blockedRequest) =>
            val (par, seq) = step(blockedRequest)
            (parallel ++ par, sequential ++ seq)
        }
      val updated = merge(flattened, parallel)
      if (sequential.isEmpty) updated.reverse
      else loop(sequential, updated)
    }

    loop(List(blockedRequests), List.empty)
  }

  /**
   * Takes one step in evaluating a collection of blocked requests, returning a
   * collection of blocked requests that can be performed in parallel and a
   * list of blocked requests that must be performed sequentially after those
   * requests.
   */
  private def step[R](
    c: BlockedRequests[R]
  ): (Parallel[R], List[BlockedRequests[R]]) = {

    @tailrec
    def loop[R](
      blockedRequests: BlockedRequests[R],
      stack: List[BlockedRequests[R]],
      parallel: Parallel[R],
      sequential: List[BlockedRequests[R]]
    ): (Parallel[R], List[BlockedRequests[R]]) =
      blockedRequests match {
        case Empty =>
          if (stack.isEmpty) (parallel, sequential)
          else loop(stack.head, stack.tail, parallel, sequential)
        case Then(left, right) =>
          left match {
            case Empty      => loop(right, stack, parallel, sequential)
            case Then(l, r) => loop(Then(l, Then(r, right)), stack, parallel, sequential)
            case Both(l, r) => loop(Both(Then(l, right), Then(r, right)), stack, parallel, sequential)
            case o          => loop(o, stack, parallel, right :: sequential)
          }
        case Both(left, right) => loop(left, right :: stack, parallel, sequential)
        case Single(dataSource, request) =>
          if (stack.isEmpty) (parallel ++ Parallel(dataSource, request), sequential)
          else loop(stack.head, stack.tail, parallel ++ Parallel(dataSource, request), sequential)
      }

    loop(c, List.empty, Parallel.empty, List.empty)
  }

  /**
   * Merges a collection of requests that must be executed sequentially with a
   * collection of requests that can be executed in parallel. If the
   * collections are both from the same single data source then the requests
   * can be pipelined while preserving ordering guarantees.
   */
  private def merge[R](sequential: List[Sequential[R]], parallel: Parallel[R]): List[Sequential[R]] =
    if (sequential.isEmpty)
      List(parallel.sequential)
    else if (parallel.isEmpty)
      sequential
    else if (sequential.head.keys.size == 1 && parallel.keys.size == 1 && sequential.head.keys == parallel.keys)
      (sequential.head ++ parallel.sequential) :: sequential.tail
    else
      parallel.sequential :: sequential
}
