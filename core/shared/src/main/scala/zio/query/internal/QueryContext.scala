package zio.query.internal

import zio.query.Cache

/**
 * `QueryContext` maintains the context of a query. Currently `QueryContext`
 * simply maintains a `Cache` of requests and results but this will be
 * augmented with other functionality such as logging and metrics in the
 * future.
 */
private[query] final case class QueryContext(cache: Cache)
