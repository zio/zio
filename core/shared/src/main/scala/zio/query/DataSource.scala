package zio.query

import zio.{ NeedsEnv, ZIO }

/**
 * A `DataSource[R, A]` requires an environment `R` and is capable of executing
 * requests of type `A`.
 *
 * Data sources must implement the method `runAll` which takes a collection of
 * requests and returns an effect with a `CompletedRequestMap` containing a
 * mapping from requests to results. The type of the collection of requests is
 * a `Vector[Vector[A]]`. The outer `Vector` represents batches of requests that
 * must be performed sequentially. The inner `Vector` represents a batch of
 * requests that can be performed in parallel. This allows data sources to
 * introspect on all the requests being executed and optimize the query.
 *
 * Data sources will typically be parameterized on a subtype of `Request[A]`,
 * though that is not strictly necessarily as long as the data source can map
 * the request type to a `Request[A]`. Data sources can then pattern match on
 * the collection of requests to determine the information requested, execute
 * the query, and place the results into the `CompletedRequestsMap` using
 * [[CompletedRequestMap.empty]] and [[CompletedRequestMap.insert]]. Data
 * sources must provide results for all requests received. Failure to do so
 * will cause a query to die with a `QueryFailure` when run.
 */
trait DataSource[-R, -A] { self =>

  /**
   * The data source's identifier.
   */
  val identifier: String

  /**
   * Execute a collection of requests. The outer `Vector` represents batches
   * of requests that must be performed sequentially. The inner `Vector`
   * represents a batch of requests that can be performed in parallel.
   */
  def runAll(requests: Vector[Vector[A]]): ZIO[R, Nothing, CompletedRequestMap]

  /**
   * Returns a data source that executes at most `n` requests in parallel.
   */
  def batchN(n: Int): DataSource[R, A] =
    new DataSource[R, A] {
      val identifier = s"${self}.batchN($n)"
      def runAll(requests: Vector[Vector[A]]): ZIO[R, Nothing, CompletedRequestMap] =
        if (n < 1)
          ZIO.die(new IllegalArgumentException("batchN: n must be at least 1"))
        else
          self.runAll(requests.foldLeft[Vector[Vector[A]]](Vector.empty)(_ ++ _.grouped(n)))
    }

  /**
   * Returns a new data source that executes requests of type `B` using the
   * specified function to transform `B` requests into requests that this data
   * source can execute.
   */
  final def contramap[B](f: Described[B => A]): DataSource[R, B] =
    new DataSource[R, B] {
      val identifier = s"${self.identifier}.contramap(${f.description})"
      def runAll(requests: Vector[Vector[B]]): ZIO[R, Nothing, CompletedRequestMap] =
        self.runAll(requests.map(_.map(f.value)))
    }

  /**
   * Returns a new data source that executes requests of type `B` using the
   * specified effectual function to transform `B` requests into requests that
   * this data source can execute.
   */
  final def contramapM[R1 <: R, B](f: Described[B => ZIO[R1, Nothing, A]]): DataSource[R1, B] =
    new DataSource[R1, B] {
      val identifier = s"${self.identifier}.contramapM(${f.description})"
      def runAll(requests: Vector[Vector[B]]): ZIO[R1, Nothing, CompletedRequestMap] =
        ZIO.foreach(requests)(ZIO.foreachPar(_)(f.value)).flatMap { request =>
          self.runAll(request.map(_.toVector).toVector)
        }
    }

  /**
   * Returns a new data source that executes requests of type `C` using the
   * specified function to transform `C` requests into requests that either
   * this data source or that data source can execute.
   */
  final def eitherWith[R1 <: R, B, C](
    that: DataSource[R1, B]
  )(f: Described[C => Either[A, B]]): DataSource[R1, C] =
    new DataSource[R1, C] {
      val identifier = s"${self.identifier}.eitherWith(${that.identifier})(${f.description})"
      def runAll(requests: Vector[Vector[C]]): ZIO[R1, Nothing, CompletedRequestMap] =
        ZIO
          .foreach(requests) { requests =>
            val (as, bs) = requests.partitionMap(f.value)
            self.runAll(Vector(as)).zipWithPar(that.runAll(Vector(bs)))(_ ++ _)
          }
          .map(_.foldLeft(CompletedRequestMap.empty)(_ ++ _))

    }

  override final def equals(that: Any): Boolean =
    that match {
      case that: DataSource[_, _] => this.identifier == that.identifier
    }

  override final def hashCode: Int =
    identifier.hashCode

  /**
   * Provides this data source with its required environment.
   */
  final def provide(r: Described[R])(implicit ev: NeedsEnv[R]): DataSource[Any, A] =
    provideSome(Described(_ => r.value, s"_ => ${r.description}"))

  /**
   * Provides this data source with part of its required environment.
   */
  final def provideSome[R0](f: Described[R0 => R])(implicit ev: NeedsEnv[R]): DataSource[R0, A] =
    new DataSource[R0, A] {
      val identifier = s"${self.identifier}.provideSome(${f.description})"
      def runAll(requests: Vector[Vector[A]]): ZIO[R0, Nothing, CompletedRequestMap] =
        self.runAll(requests).provideSome(f.value)
    }

  /**
   * Returns a new data source that executes requests by sending them to this
   * data source and that data source, returning the results from the first
   * data source to complete and safely interrupting the loser.
   */
  final def race[R1 <: R, A1 <: A](that: DataSource[R1, A1]): DataSource[R1, A1] =
    new DataSource[R1, A1] {
      val identifier = s"${self.identifier}.race(${that.identifier})"
      def runAll(requests: Vector[Vector[A1]]): ZIO[R1, Nothing, CompletedRequestMap] =
        self.runAll(requests).race(that.runAll(requests))
    }

  override final def toString: String =
    identifier
}

object DataSource {

  /**
   * A data source that executes requests that can be performed in parallel in
   * batches but does not further optimize batches of requests that must be
   * performed sequentially.
   */
  trait Batched[-R, -A] extends DataSource[R, A] {
    def run(requests: Vector[A]): ZIO[R, Nothing, CompletedRequestMap]
    final def runAll(requests: Vector[Vector[A]]): ZIO[R, Nothing, CompletedRequestMap] =
      ZIO.foreach(requests)(run).map(_.foldLeft(CompletedRequestMap.empty)(_ ++ _))
  }

  object Batched {

    /**
     * Constructs a data source from a function taking a collection of requests
     * and returning a `CompletedRequestMap`.
     */
    def make[R, A](name: String)(f: Vector[A] => ZIO[R, Nothing, CompletedRequestMap]): DataSource[R, A] =
      new DataSource.Batched[R, A] {
        val identifier: String = name
        def run(requests: Vector[A]): ZIO[R, Nothing, CompletedRequestMap] =
          f(requests)
      }
  }

  /**
   * Constructs a data source from a pure function.
   */
  def fromFunction[A, B](
    name: String
  )(f: A => B)(implicit ev: A <:< Request[Nothing, B]): DataSource[Any, A] =
    new DataSource.Batched[Any, A] {
      val identifier: String = name
      def run(requests: Vector[A]): ZIO[Any, Nothing, CompletedRequestMap] =
        ZIO.succeedNow(requests.foldLeft(CompletedRequestMap.empty)((map, k) => map.insert(k)(Right(f(k)))))
    }

  /**
   * Constructs a data source from a pure function that takes a list of
   * requests and returns a list of results of the same size. Each item in the
   * result list must correspond to the item at the same index in the request
   * list.
   */
  def fromFunctionBatched[A, B](
    name: String
  )(f: Vector[A] => Vector[B])(implicit ev: A <:< Request[Nothing, B]): DataSource[Any, A] =
    fromFunctionBatchedM(name)(as => ZIO.succeedNow(f(as)))

  /**
   * Constructs a data source from an effectual function that takes a list of
   * requests and returns a list of results of the same size. Each item in the
   * result list must correspond to the item at the same index in the request
   * list.
   */
  def fromFunctionBatchedM[R, E, A, B](
    name: String
  )(f: Vector[A] => ZIO[R, E, Vector[B]])(implicit ev: A <:< Request[E, B]): DataSource[R, A] =
    new DataSource.Batched[R, A] {
      val identifier: String = name
      def run(requests: Vector[A]): ZIO[R, Nothing, CompletedRequestMap] =
        f(requests)
          .fold(
            e => requests.map((_, Left(e))),
            bs => requests.zip(bs.map(Right(_)))
          )
          .map(_.foldLeft(CompletedRequestMap.empty) {
            case (map, (k, v)) => map.insert(k)(v)
          })
    }

  /**
   * Constructs a data source from a pure function that takes a list of
   * requests and returns a list of optional results of the same size. Each
   * item in the result list must correspond to the item at the same index in
   * the request list.
   */
  def fromFunctionBatchedOption[A, B](
    name: String
  )(f: Vector[A] => Vector[Option[B]])(implicit ev: A <:< Request[Nothing, B]): DataSource[Any, A] =
    fromFunctionBatchedOptionM(name)(as => ZIO.succeedNow(f(as)))

  /**
   * Constructs a data source from an effectual function that takes a list of
   * requests and returns a list of optional results of the same size. Each
   * item in the result list must correspond to the item at the same index in
   * the request list.
   */
  def fromFunctionBatchedOptionM[R, E, A, B](
    name: String
  )(f: Vector[A] => ZIO[R, E, Vector[Option[B]]])(implicit ev: A <:< Request[E, B]): DataSource[R, A] =
    new DataSource.Batched[R, A] {
      val identifier: String = name
      def run(requests: Vector[A]): ZIO[R, Nothing, CompletedRequestMap] =
        f(requests)
          .fold(
            e => requests.map((_, Left(e))),
            bs => requests.zip(bs.map(Right(_)))
          )
          .map(_.foldLeft(CompletedRequestMap.empty) {
            case (map, (k, v)) => map.insertOption(k)(v)
          })
    }

  /**
   * Constructs a data source from a function that takes a list of requests and
   * returns a list of results of the same size. Uses the specified function to
   * associate each result with the corresponding effect, allowing the function
   * to return the list of results in a different order than the list of
   * requests.
   */
  def fromFunctionBatchedWith[A, B](
    name: String
  )(f: Vector[A] => Vector[B], g: B => Request[Nothing, B])(
    implicit ev: A <:< Request[Nothing, B]
  ): DataSource[Any, A] =
    fromFunctionBatchedWithM(name)(as => ZIO.succeedNow(f(as)), g)

  /**
   * Constructs a data source from an effectual function that takes a list of
   * requests and returns a list of results of the same size. Uses the
   * specified function to associate each result with the corresponding effect,
   * allowing the function to return the list of results in a different order
   * than the list of requests.
   */
  def fromFunctionBatchedWithM[R, E, A, B](
    name: String
  )(f: Vector[A] => ZIO[R, E, Vector[B]], g: B => Request[E, B])(
    implicit ev: A <:< Request[E, B]
  ): DataSource[R, A] =
    new DataSource.Batched[R, A] {
      val identifier: String = name
      def run(requests: Vector[A]): ZIO[R, Nothing, CompletedRequestMap] =
        f(requests)
          .fold(
            e => requests.map(a => (ev(a), Left(e))),
            bs => bs.map(b => (g(b), Right(b)))
          )
          .map(_.foldLeft(CompletedRequestMap.empty) {
            case (map, (k, v)) => map.insert(k)(v)
          })
    }

  /**
   * Constructs a data source from an effectual function.
   */
  def fromFunctionM[R, E, A, B](
    name: String
  )(f: A => ZIO[R, E, B])(implicit ev: A <:< Request[E, B]): DataSource[R, A] =
    new DataSource.Batched[R, A] {
      val identifier: String = name
      def run(requests: Vector[A]): ZIO[R, Nothing, CompletedRequestMap] =
        ZIO
          .foreachPar(requests)(a => f(a).either.map((a, _)))
          .map(_.foldLeft(CompletedRequestMap.empty) { case (map, (k, v)) => map.insert(k)(v) })
    }

  /**
   * Constructs a data source from a pure function that may not provide results
   * for all requests received.
   */
  def fromFunctionOption[A, B](
    name: String
  )(f: A => Option[B])(implicit ev: A <:< Request[Nothing, B]): DataSource[Any, A] =
    fromFunctionOptionM(name)(a => ZIO.succeedNow(f(a)))

  /**
   * Constructs a data source from an effectual function that may not provide
   * results for all requests received.
   */
  def fromFunctionOptionM[R, E, A, B](
    name: String
  )(f: A => ZIO[R, E, Option[B]])(implicit ev: A <:< Request[E, B]): DataSource[R, A] =
    new DataSource.Batched[R, A] {
      val identifier: String = name
      def run(requests: Vector[A]): ZIO[R, Nothing, CompletedRequestMap] =
        ZIO
          .foreachPar(requests)(a => f(a).either.map((a, _)))
          .map(_.foldLeft(CompletedRequestMap.empty) {
            case (map, (k, v)) => map.insertOption(k)(v)
          })
    }

  /**
   * Constructs a data source from a function taking a collection of requests
   * and returning a `CompletedRequestMap`.
   */
  def make[R, A](name: String)(f: Vector[Vector[A]] => ZIO[R, Nothing, CompletedRequestMap]): DataSource[R, A] =
    new DataSource[R, A] {
      val identifier: String = name
      def runAll(requests: Vector[Vector[A]]): ZIO[R, Nothing, CompletedRequestMap] =
        f(requests)
    }

  /**
   * A data source that never executes requests.
   */
  val never: DataSource[Any, Any] =
    new DataSource[Any, Any] {
      val identifier = "never"
      def runAll(requests: Vector[Vector[Any]]): ZIO[Any, Nothing, CompletedRequestMap] =
        ZIO.never
    }
}
