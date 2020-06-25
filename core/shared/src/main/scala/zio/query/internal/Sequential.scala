package zio.query.internal

import zio.Chunk
import zio.query.DataSource

/**
 * A `Sequential[R]` maintains a mapping from data sources to batches of
 * requests from those data sources that must be executed sequentially.
 */
private[query] final class Sequential[-R](
  private val map: Map[DataSource[Any, Any], Chunk[Chunk[BlockedRequest[Any]]]]
) { self =>

  /**
   * Combines this collection of batches of requests that must be executed
   * sequentially with that collection of batches of requests that must be
   * executed sequentially to return a new collection of batches of requests
   * that must be executed sequentially.
   */
  def ++[R1 <: R](that: Sequential[R1]): Sequential[R1] =
    new Sequential(
      that.map.foldLeft(self.map) {
        case (map, (k, v)) =>
          map + (k -> map.get(k).fold[Chunk[Chunk[BlockedRequest[Any]]]](Chunk.empty)(_ ++ v))
      }
    )

  /**
   * Returns whether this collection of batches of requests is empty.
   */
  def isEmpty: Boolean =
    map.isEmpty

  /**
   * Returns a collection of the data sources that the batches of requests in
   * this collection are from.
   */
  def keys: Iterable[DataSource[R, Any]] =
    map.keys

  /**
   * Converts this collection of batches requests that must be executed
   * sequentially to an `Iterable` containing mappings from data sources to
   * batches of requests from those data sources.
   */
  def toIterable: Iterable[(DataSource[R, Any], Chunk[Chunk[BlockedRequest[Any]]])] =
    map
}
