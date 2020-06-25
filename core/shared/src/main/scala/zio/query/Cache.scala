package zio.query

import zio.query.internal.{ BlockedRequest, BlockedRequests, Continue, Result }
import zio.{ IO, Ref, UIO }

/**
 * A `Cache` maintains an internal state with a mapping from requests to `Ref`s
 * that will contain the result of those requests when they are executed. This
 * is used internally by the library to provide deduplication and caching of
 * requests.
 */
trait Cache {

  /**
   * Looks up a request in the cache, failing with the unit value if the
   * request is not in the cache, succeeding with `Ref(None)` if the request is
   * in the cache but has not been executed yet, or `Ref(Some(value))` if the
   * request has been executed.
   */
  def get[E, A](request: Request[E, A]): IO[Unit, Ref[Option[Either[E, A]]]]

  /**
   * Inserts a request and a `Ref` that will contain the result of the request
   * when it is executed into the cache.
   */
  def put[E, A](request: Request[E, A], result: Ref[Option[Either[E, A]]]): UIO[Unit]

  /**
   * Looks up a request in the cache. If the request is not in the cache
   * returns a result that is blocked on the request. If the request is in the
   * cache but has not been executed yet returns a result that is blocked on
   * the previous request. If the request has been executed returns a result
   * that is done.
   */
  private[query] def getOrElseUpdate[R, E, A, B](request: A, dataSource: DataSource[R, A])(
    implicit ev: A <:< Request[E, B]
  ): UIO[Result[R, E, B]]
}

object Cache {

  /**
   * Constructs an empty cache.
   */
  val empty: UIO[Cache] =
    Ref.make(Map.empty[Any, Any]).map(new Impl(_))

  private final class Impl(private val state: Ref[Map[Any, Any]]) extends Cache {

    def get[E, A](request: Request[E, A]): IO[Unit, Ref[Option[Either[E, A]]]] =
      state.get.map(_.get(request).asInstanceOf[Option[Ref[Option[Either[E, A]]]]]).get.orElseFail(())

    def put[E, A](request: Request[E, A], result: Ref[Option[Either[E, A]]]): UIO[Unit] =
      state.update(_ + (request -> result)).unit

    private[query] def getOrElseUpdate[R, E, A, B](request: A, dataSource: DataSource[R, A])(
      implicit ev: A <:< Request[E, B]
    ): UIO[Result[R, E, B]] =
      Ref.make(Option.empty[Either[E, B]]).flatMap { ref =>
        state.modify { map =>
          map.get(request) match {
            case None      => (Left(ref), map + (request -> ref))
            case Some(ref) => (Right(ref.asInstanceOf[Ref[Option[Either[E, B]]]]), map)
          }
        }.flatMap {
          case Left(ref) =>
            UIO.succeedNow(
              Result.blocked(
                BlockedRequests.single(dataSource, BlockedRequest(request, ref)),
                Continue(request, dataSource, ref)
              )
            )
          case Right(ref) =>
            ref.get.map {
              case None    => Result.blocked(BlockedRequests.empty, Continue(request, dataSource, ref))
              case Some(b) => Result.fromEither(b)
            }
        }
      }
  }
}
