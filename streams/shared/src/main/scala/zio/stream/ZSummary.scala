package zio.stream

import scala.collection.mutable

import zio._
import zio.stream.internal.Builder

/**
 * A `ZSummary[R, E, I, O]` is a bounded aggregator of values of type `I`
 * into values of type `O`, using an environment of type `R` and potential failures
 * of type `E`.
 *
 * ZSummaries can be used to aggregate values on streams in several ways:
 * - Summaries can be converted to transducers using [[ZSummary#toTransducer]] and
 *   be used with [[ZStream#transduce]];
 * - They can be converted to sinks using [[ZSummary#toSink]] and be
 *   used with [[ZStream#run]];
 * - They can be used to perform asynchronous, time-bounded aggregations with
 *   [[[ZStream#aggregateAsync[R1<:R,E1>:E,P](summary*]]] and [[ZStream#aggregateAsyncWithin[R1<:R,E1>:E,P](summary*]];
 * - They can be used directly with [[ZStream#scanWith]] and [[ZStream#zipWithSummary]].
 *
 * ZSummaries can be zipped together (using [[ZSummary#zipWith]]) to create composite
 * aggregations and can be transformed using familiar combinators like [[ZSummary#map]],
 * [[ZSummary#contramap]] and more.
 *
 * The majority of ZSummaries are pure: they do not use environmental effects and
 * cannot fail. However, a select few can be effectful. It is recommended to stick
 * to the pure ones for superior performance.
 */
sealed trait ZSummary[-R, +E, -I, +O] { self =>

  /**
   * Creates a new builder for this summary.
   */
  def builder: UIO[Builder[R, E, I, O]]

  /**
   * Composes two summaries into a summary that feeds incoming elements to both
   * summaries and tuples their results when extracted.
   */
  def <*>[R1 <: R, E1 >: E, I1 <: I, O2](that: ZSummary[R1, E1, I1, O2]): ZSummary[R1, E1, I1, (O, O2)] =
    zipWith(that)((_, _))

  /**
   * Composes two summaries into a summary that feeds incoming elements to both
   * summaries and keeps the result from this summary.
   */
  def <*[R1 <: R, E1 >: E, I1 <: I, O2](that: ZSummary[R1, E1, I1, O2]): ZSummary[R1, E1, I1, O] =
    zipWith(that)((l, _) => l)

  /**
   * Composes two summaries into a summary that feeds incoming elements to both
   * summaries and keeps the result from the other summary.
   */
  def *>[R1 <: R, E1 >: E, I1 <: I, O2](that: ZSummary[R1, E1, I1, O2]): ZSummary[R1, E1, I1, O2] =
    zipWith(that)((_, r) => r)

  /**
   * Applies a function to this summary's inputs.
   */
  def contramap[I0](f: I0 => I): ZSummary[R, E, I0, O] =
    mapBuilder {
      case builder: Builder.Mutable[I, O] =>
        def wrapBuilder(orig: Builder.Mutable[I, O]): Builder.Mutable[I0, O] =
          new Builder.Mutable[I0, O] {
            override def accepts(i: I0): Boolean        = orig.accepts(f(i))
            override def copy(): Builder.Mutable[I0, O] = wrapBuilder(orig.copy())
            override def done(): Boolean                = orig.done()
            override def reset(): Unit                  = orig.reset()
            override def step(i: I0): Unit              = orig.step(f(i))
            override def extract(): O                   = orig.extract()
          }

        wrapBuilder(builder)

      case builder: Builder.Effectual[R, E, I, O] =>
        new Builder.Effectual[R, E, I0, O] {
          def accepts(i: I0): ZIO[R, E, Boolean] = builder.accepts(f(i))
          def reset: ZIO[R, E, Unit]             = builder.reset
          def done: ZIO[R, E, Boolean]           = builder.done
          def step(i: I0): ZIO[R, E, Unit]       = builder.step(f(i))
          def extract: ZIO[R, E, O]              = builder.extract
        }
    }

  /**
   * Applies an effectual function to this summary's inputs.
   */
  def contramapM[R1 <: R, E1 >: E, I0](f: I0 => ZIO[R1, E1, I]): ZSummary[R1, E1, I0, O] =
    mapBuilder {
      case builder: Builder.Mutable[I, O] =>
        new Builder.Effectual[R1, E1, I0, O] {
          def reset: ZIO[R1, E1, Unit]              = UIO(builder.reset())
          def accepts(i0: I0): ZIO[R1, E1, Boolean] = f(i0).flatMap(i => UIO(builder.accepts(i)))
          def done: ZIO[R1, E1, Boolean]            = UIO(builder.done())
          def step(i0: I0): ZIO[R1, E1, Unit]       = f(i0).flatMap(i => UIO(builder.step(i)))
          def extract: ZIO[R1, E1, O]               = UIO(builder.extract())
        }

      case builder: Builder.Effectual[R, E, I, O] =>
        new Builder.Effectual[R1, E1, I0, O] {
          def reset: ZIO[R1, E1, Unit]              = builder.reset
          def accepts(i0: I0): ZIO[R1, E1, Boolean] = f(i0).flatMap(builder.accepts)
          def done: ZIO[R1, E1, Boolean]            = builder.done
          def step(i0: I0): ZIO[R1, E1, Unit]       = f(i0).flatMap(builder.step)
          def extract: ZIO[R1, E1, O]               = builder.extract
        }
    }

  /**
   * Uses the predicate to determine when the summary should stop
   * processing inputs.
   */
  def emitWhen(f: O => Boolean): ZSummary[R, E, I, O] =
    mapBuilder {
      case builder: Builder.Mutable[I, O] =>
        def wrap(orig: Builder.Mutable[I, O]): Builder.Mutable[I, O] =
          new Builder.Mutable[I, O] {
            override def copy(): Builder.Mutable[I, O] = wrap(orig.copy())
            override def reset(): Unit                 = orig.reset()
            override def accepts(i: I): Boolean        = orig.accepts(i)
            override def done(): Boolean               = orig.done() || f(extract())
            override def step(i: I): Unit              = orig.step(i)
            override def extract(): O                  = orig.extract()
          }

        wrap(builder)

      case builder: Builder.Effectual[R, E, I, O] =>
        new Builder.Effectual[R, E, I, O] {
          def accepts(i: I): ZIO[R, E, Boolean] = builder.accepts(i)
          def reset: ZIO[R, E, Unit]            = builder.reset
          def done: ZIO[R, E, Boolean] = builder.done.flatMap { isDone =>
            if (!isDone) builder.extract.map(f)
            else UIO.succeed(isDone)
          }
          def step(i: I): ZIO[R, E, Unit] = builder.step(i)
          def extract: ZIO[R, E, O]       = builder.extract
        }
    }

  /**
   * Groups the inputs of this summary using the specified keying function,
   * resulting in a summary that creates a map of keys to outputs of the original summary.
   */
  def groupBy[K, I1 <: I](key: I1 => K): ZSummary[R, E, I1, Map[K, O]] =
    ZSummary {
      self.builder.flatMap {
        case builder: Builder.Mutable[I, O] =>
          def wrap(orig: Builder.Mutable[I, O]): Builder.Mutable[I1, Map[K, O]] =
            new Builder.Mutable[I1, Map[K, O]] {
              var builders: mutable.Map[K, Builder.Mutable[I, O]] = mutable.Map.empty

              override def reset(): Unit =
                builders = mutable.Map.empty

              override def copy(): Builder.Mutable[I1, Map[K, O]] =
                wrap(orig.copy())

              override def accepts(i: I1): Boolean =
                builders.getOrElseUpdate(key(i), orig.copy()).accepts(i)

              override def done(): Boolean =
                builders.exists(_._2.done())

              override def step(i: I1): Unit =
                builders.getOrElseUpdate(key(i), orig.copy()).step(i)

              override def extract(): Map[K, O] =
                builders.toMap.map { case (k, v) => k -> v.extract() }
            }

          UIO.succeed(wrap(builder))

        case _: Builder.Effectual[R, E, I, O] =>
          Ref.make(Map[K, Builder.Effectual[R, E, I, O]]()).map { builders =>
            val makeBuilder = self.builder.map(_.effectual)

            new Builder.Effectual[R, E, I1, Map[K, O]] {
              override def reset: ZIO[R, E, Unit] =
                builders.set(Map.empty)

              override def accepts(i: I1): ZIO[R, E, Boolean] = {
                val k = key(i)
                builders.get.map(_.get(k)).flatMap {
                  case Some(builder) => builder.accepts(i)
                  case None          => makeBuilder.flatMap(b => builders.update(_ + (k -> b)) *> b.accepts(i))
                }
              }

              override def done: ZIO[R, E, Boolean] =
                builders.get.flatMap(bs => ZIO.foreach(bs.values)(_.done)).map(_.exists(identity))

              override def step(i: I1): ZIO[R, E, Unit] = {
                val k = key(i)
                builders.get.map(_.get(k)).flatMap {
                  case Some(builder) => builder.step(i)
                  case None          => makeBuilder.flatMap(b => builders.update(_ + (k -> b)) *> b.step(i))
                }
              }

              override def extract: ZIO[R, E, Map[K, O]] =
                builders.get.flatMap { bs =>
                  ZIO.foreach(bs)((k, v) => v.extract.map(k -> _))
                }
            }
          }
      }

    }

  /**
   * Applies a function to this summary's result.
   */
  def map[O2](f: O => O2): ZSummary[R, E, I, O2] =
    mapBuilder {
      case builder: Builder.Mutable[I, O] =>
        def wrap(orig: Builder.Mutable[I, O]): Builder.Mutable[I, O2] =
          new Builder.Mutable[I, O2] {
            override def accepts(i: I): Boolean         = orig.accepts(i)
            override def copy(): Builder.Mutable[I, O2] = wrap(orig.copy())
            override def done(): Boolean                = orig.done()
            override def reset(): Unit                  = orig.reset()
            override def step(i: I): Unit               = orig.step(i)
            override def extract(): O2                  = f(orig.extract())
          }

        wrap(builder)

      case builder: Builder.Effectual[R, E, I, O] =>
        new Builder.Effectual[R, E, I, O2] {
          def accepts(i: I): ZIO[R, E, Boolean] = builder.accepts(i)
          def reset: ZIO[R, E, Unit]            = builder.reset
          def done: ZIO[R, E, Boolean]          = builder.done
          def step(i: I): ZIO[R, E, Unit]       = builder.step(i)
          def extract: ZIO[R, E, O2]            = builder.extract.map(f)
        }
    }

  /**
   * Applies an effectual function to this summary's result.
   */
  def mapM[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, O2]): ZSummary[R1, E1, I, O2] =
    mapBuilder {
      case builder: Builder.Mutable[I, O] =>
        new Builder.Effectual[R1, E1, I, O2] {
          def reset: ZIO[R1, E1, Unit]            = UIO(builder.reset())
          def accepts(i: I): ZIO[R1, E1, Boolean] = UIO(builder.accepts(i))
          def done: ZIO[R1, E1, Boolean]          = UIO(builder.done())
          def step(i: I): ZIO[R1, E1, Unit]       = UIO(builder.step(i))
          def extract: ZIO[R1, E1, O2]            = UIO(builder.extract()).flatMap(f)
        }

      case builder: Builder.Effectual[R, E, I, O] =>
        new Builder.Effectual[R1, E1, I, O2] {
          def reset: ZIO[R1, E1, Unit]            = builder.reset
          def accepts(i: I): ZIO[R1, E1, Boolean] = builder.accepts(i)
          def done: ZIO[R1, E1, Boolean]          = builder.done
          def step(i: I): ZIO[R1, E1, Unit]       = builder.step(i)
          def extract: ZIO[R1, E1, O2]            = builder.extract.flatMap(f)
        }
    }

  def mapBuilder[R1, E1, I1, O1](f: Builder[R, E, I, O] => Builder[R1, E1, I1, O1]): ZSummary[R1, E1, I1, O1] =
    ZSummary(self.builder.map(f))

  /**
   * Creates a transducer from this summary
   */
  def toTransducer: ZTransducer[R, E, I, O] =
    ZTransducer.fromSummary(self)

  /**
   * Creates a sink from this summary.
   */
  def toSink[I1 <: I]: ZSink[R, E, I1, I1, O] =
    ZSink.fromSummary(self)

  private def zipMutableEffectual[R0, E0, I0, A, B, Z](
    mut: Builder.Mutable[I0, A],
    eff: Builder.Effectual[R0, E0, I0, B]
  )(f: (A, B) => Z): Builder.Effectual[R0, E0, I0, Z] =
    new Builder.Effectual[R0, E0, I0, Z] {
      def accepts(i: I0): ZIO[R0, E0, Boolean] =
        UIO(mut.accepts(i)).flatMap { mutAccepts =>
          if (mutAccepts) eff.accepts(i)
          else UIO.succeed(mutAccepts)
        }

      def reset: ZIO[R0, E0, Unit] =
        UIO(mut.reset()) *> eff.reset

      def done: ZIO[R0, E0, Boolean] =
        UIO(mut.done()).flatMap { mutDone =>
          if (!mutDone) eff.done
          else UIO.succeed(mutDone)
        }

      def step(i: I0): ZIO[R0, E0, Unit] =
        UIO(mut.step(i)) *> eff.step(i)

      def extract: ZIO[R0, E0, Z] =
        UIO(mut.extract()).zipWith(eff.extract)(f)
    }

  private def zipEffectuals[R0, E0, I0, A, B, Z](
    ba: Builder.Effectual[R0, E0, I0, A],
    bb: Builder.Effectual[R0, E0, I0, B]
  )(f: (A, B) => Z): Builder.Effectual[R0, E0, I0, Z] =
    new Builder.Effectual[R0, E0, I0, Z] {
      def reset: ZIO[R0, E0, Unit] = ba.reset *> bb.reset
      def accepts(i: I0): ZIO[R0, E0, Boolean] =
        ba.accepts(i).flatMap { baAccepts =>
          if (baAccepts) bb.accepts(i)
          else UIO.succeed(baAccepts)
        }

      def done: ZIO[R0, E0, Boolean] =
        ba.done.flatMap { baDone =>
          if (!baDone) bb.done
          else UIO.succeed(baDone)
        }

      def step(i: I0): ZIO[R0, E0, Unit] =
        ba.step(i) *> bb.step(i)

      def extract: ZIO[R0, E0, Z] =
        ba.extract.zipWith(bb.extract)(f)
    }

  private def zipMutables[I0, A, B, Z](ba: Builder.Mutable[I0, A], bb: Builder.Mutable[I0, B])(
    f: (A, B) => Z
  ): Builder.Mutable[I0, Z] =
    new Builder.Mutable[I0, Z] {
      override def accepts(i: I0): Boolean =
        ba.accepts(i) && bb.accepts(i)

      override def copy(): Builder.Mutable[I0, Z] =
        zipMutables(ba.copy(), bb.copy())(f)

      override def done(): Boolean =
        ba.done() || bb.done()

      override def reset(): Unit = {
        ba.reset()
        bb.reset()
      }

      override def step(i: I0): Unit = {
        ba.step(i)
        bb.step(i)
      }

      override def extract(): Z =
        f(ba.extract(), bb.extract())
    }

  /**
   * Composes two summaries by feeding incoming elements to both of them, and
   * transforming their results with the provided function.
   */
  def zipWith[R1 <: R, E1 >: E, I1 <: I, O2, O3](
    that: ZSummary[R1, E1, I1, O2]
  )(f: (O, O2) => O3): ZSummary[R1, E1, I1, O3] =
    ZSummary {
      self.builder.zipWith(that.builder) {
        case (selfBuilder: Builder.Effectual[R, E, I, O], thatBuilder: Builder.Effectual[R1, E1, I1, O2]) =>
          zipEffectuals(selfBuilder, thatBuilder)(f)
        case (selfBuilder: Builder.Mutable[I, O], thatBuilder: Builder.Effectual[R1, E1, I1, O2]) =>
          zipMutableEffectual(selfBuilder, thatBuilder)(f)
        case (selfBuilder: Builder.Effectual[R, E, I, O], thatBuilder: Builder.Mutable[I1, O2]) =>
          zipMutableEffectual(thatBuilder, selfBuilder)((o2, o) => f(o, o2))
        case (selfBuilder: Builder.Mutable[I, O], thatBuilder: Builder.Mutable[I1, O2]) =>
          zipMutables(selfBuilder, thatBuilder)(f)
      }
    }
}

object ZSummary {
  def apply[R, E, I, O](makeBuilder: UIO[Builder[R, E, I, O]]): ZSummary[R, E, I, O] =
    new ZSummary[R, E, I, O] {
      def builder: zio.UIO[Builder[R, E, I, O]] = makeBuilder
    }

  /**
   * A summary that counts the number of elements appended to it.
   */
  val count: ZSummary[Any, Nothing, Any, Long] = foldLeft(0L)((acc, _) => acc + 1)

  /**
   * A summary that counts the distinct number of elements appended to it.
   *
   * NOTE: This uses a Set under the hood, so be wary of memory usage on inputs
   * of unbounded sizes.
   */
  val countDistinct: ZSummary[Any, Nothing, Any, Int] =
    foldLeft(Set[Any]())(_ + (_: Any)).map(_.size)

  /**
   * A summary that emits `false` until `n` elements have been appended to it,
   * and then emits `true`.
   */
  def countN(n: Int): ZSummary[Any, Nothing, Any, Boolean] = count.map(_ >= n)

  /**
   * Creates a summary that aggregates elements appended to it into a chunk.
   */
  def collectAll[I]: ZSummary[Any, Nothing, I, Chunk[I]] =
    ZSummary {
      UIO {
        def make(): Builder.Mutable[I, Chunk[I]] =
          new Builder.Mutable[I, Chunk[I]] {
            var buf = mutable.Buffer.empty[I]

            override def copy(): Builder.Mutable[I, Chunk[I]] =
              make()

            override def reset(): Unit =
              buf = mutable.Buffer.empty[I]

            override def accepts(i: I): Boolean =
              true

            override def done(): Boolean =
              false

            override def step(i: I): Unit = {
              buf.append(i)
              ()
            }

            override def extract(): Chunk[I] =
              Chunk.fromIterable(buf)
          }

        make()
      }
    }

  /**
   * Creates a summary that aggregates elements appended to it into an `n`-sized chunk
   */
  def collectAllN[I](n: Int): ZSummary[Any, Nothing, I, Chunk[I]] =
    collectAll[I].emitWhen(_.size >= n)

  /**
   * A summary that aggregates elements appended to it into a set.
   */
  def collectAllToSet[I]: ZSummary[Any, Nothing, I, Set[I]] =
    ZSummary {
      UIO {
        def make(): Builder.Mutable[I, Set[I]] = new Builder.Mutable[I, Set[I]] {
          var buf = mutable.Set.empty[I]

          override def copy(): Builder.Mutable[I, Set[I]] =
            make()

          override def reset(): Unit =
            buf = mutable.Set.empty[I]

          override def accepts(i: I): Boolean = true

          override def done(): Boolean = false

          override def step(i: I): Unit = {
            buf.add(i)
            ()
          }

          override def extract(): Set[I] =
            buf.toSet
        }

        make()
      }
    }

  /**
   * Creates a summary accumulating incoming values into sets of maximum size `n`.
   */
  def collectAllToSetN[I](n: Int): ZSummary[Any, Nothing, I, Set[I]] =
    ZSummary {
      UIO {
        def make(): Builder.Mutable[I, Set[I]] =
          new Builder.Mutable[I, Set[I]] {
            var builder = mutable.Set.empty[I]

            override def copy(): Builder.Mutable[I, Set[I]] =
              make()

            override def reset(): Unit =
              builder = mutable.Set.empty[I]

            override def accepts(i: I): Boolean =
              builder.size < n || (builder.size == n && builder(i))

            override def done(): Boolean =
              builder.size > n

            override def step(i: I): Unit = {
              builder += i
              ()
            }

            override def extract(): Set[I] =
              builder.toSet
          }

        make()
      }
    }

  /**
   * A summary that aggregates elements appended to it into a map, using the
   * specified keying and merging functions.
   */
  def collectAllToMap[K, I](key: I => K)(merge: (I, I) => I): ZSummary[Any, Nothing, I, Map[K, I]] =
    ZSummary {
      UIO {
        def make(): Builder.Mutable[I, Map[K, I]] =
          new Builder.Mutable[I, Map[K, I]] {
            var builder = mutable.Map.empty[K, I]

            override def copy()                 = make()
            override def accepts(i: I): Boolean = true
            override def done(): Boolean        = false
            override def reset(): Unit          = builder = mutable.Map.empty[K, I]
            override def step(i: I): Unit = {
              val k = key(i)

              if (builder.contains(k)) builder.update(k, merge(builder(k), i))
              else builder.update(k, i)
            }

            override def extract(): Map[K, I] = builder.toMap
          }

        make()
      }
    }

  /**
   * Creates a summary accumulating incoming values into maps of up to `n` keys. Elements
   * are mapped to keys using the function `key`; elements mapped to the same key will
   * be merged with the function `f`.
   */
  def collectAllToMapN[K, I](n: Int)(key: I => K)(merge: (I, I) => I): ZSummary[Any, Nothing, I, Map[K, I]] =
    ZSummary {
      UIO {
        def make(): Builder.Mutable[I, Map[K, I]] =
          new Builder.Mutable[I, Map[K, I]] {
            var builder = mutable.Map.empty[K, I]

            override def copy()        = make()
            override def reset(): Unit = builder = mutable.Map.empty[K, I]
            override def accepts(i: I): Boolean =
              builder.size < n || (builder.size == n && builder.contains(key(i)))

            override def done(): Boolean =
              builder.size > n

            override def step(i: I): Unit = {
              val k = key(i)

              if (builder.contains(k)) builder.update(k, merge(builder(k), i))
              else builder.update(k, i)
            }

            override def extract(): Map[K, I] = builder.toMap
          }

        make()
      }
    }

  /**
   * Accumulates incoming elements into a chunk as long as they verify predicate `p`.
   */
  def collectAllWhile[I](p: I => Boolean): ZSummary[Any, Nothing, I, List[I]] =
    fold[(List[I], Boolean), I]((Nil, true))(_._2) { case ((as, _), a) =>
      if (p(a)) (a :: as, true) else (as, false)
    }.map(_._1.reverse)

  /**
   * Accumulates incoming elements into a chunk as long as they verify effectful predicate `p`.
   */
  def collectAllWhileM[R, E, I](p: I => ZIO[R, E, Boolean]): ZSummary[R, E, I, List[I]] =
    foldM[R, E, (List[I], Boolean), I]((Nil, true))(_._2) { case ((as, _), a) =>
      p(a).map(if (_) (a :: as, true) else (as, false))
    }.map(_._1.reverse)

  /**
   * Creates a summary that dies with the specified error.
   */
  def die(error: Throwable): ZSummary[Any, Nothing, Any, Nothing] =
    halt(Cause.die(error))

  /**
   * Creates a summary that completes immediately with the specified value.
   */
  def done[E, A](exit: Exit[E, A]): ZSummary[Any, E, Any, A] =
    fromEffect(ZIO.done(exit))

  /**
   * Creates a summary that fails with the specified error.
   */
  def fail[E](error: E): ZSummary[Any, E, Any, Nothing] =
    halt(Cause.fail(error))

  /**
   * Creates a summary that halts immediately with the specified cause.
   */
  def halt[E](cause: Cause[E]): ZSummary[Any, E, Any, Nothing] =
    done(Exit.halt(cause))

  /**
   * Creates a summary that returns the first element appended to it.
   */
  def head[I]: ZSummary[Any, Nothing, I, Option[I]] =
    fold(None: Option[I])(_.isEmpty) {
      case (s @ Some(_), _) => s
      case (None, i)        => Some(i)
    }

  /**
   * Creates a summary that completes immediately with the specified effect.
   */
  def fromEffect[R, E, A](zio: ZIO[R, E, A]): ZSummary[R, E, Any, A] =
    ZSummary {
      UIO {
        new Builder.Effectual[R, E, Any, A] {
          def reset: ZIO[Any, E, Unit]              = UIO.unit
          def accepts(i: Any): ZIO[Any, E, Boolean] = UIO.succeed(false)
          def done: ZIO[Any, E, Boolean]            = UIO.succeed(true)
          def step(i: Any): ZIO[Any, E, Unit]       = UIO.unit
          def extract: ZIO[R, E, A]                 = zio
        }
      }
    }

  /**
   * Creates a summary that executes the specified fold until `contFn` evalutes to false.
   */
  def fold[S, I](init0: S)(contFn: S => Boolean)(f: (S, I) => S): ZSummary[Any, Nothing, I, S] =
    ZSummary {
      UIO {
        def make(): Builder.Mutable[I, S] =
          new Builder.Mutable[I, S] {
            var curr                            = init0
            override def copy()                 = make()
            override def reset(): Unit          = curr = init0
            override def accepts(i: I): Boolean = true
            override def done(): Boolean        = !contFn(curr)
            override def step(i: I): Unit       = curr = f(curr, i)
            override def extract(): S           = curr
          }

        make()
      }
    }

  def foldM[R, E, S, I](init0: S)(contFn: S => Boolean)(f: (S, I) => ZIO[R, E, S]): ZSummary[R, E, I, S] =
    ZSummary {
      Ref.make(init0).map { ref =>
        new Builder.Effectual[R, E, I, S] {
          def reset: ZIO[R, E, Unit]            = ref.set(init0)
          def accepts(i: I): ZIO[R, E, Boolean] = UIO.succeed(true)
          def done: ZIO[R, E, Boolean]          = ref.get.map(!contFn(_))
          def step(i: I): ZIO[R, E, Unit]       = ref.get.flatMap(f(_, i)).flatMap(ref.set)
          def extract: ZIO[R, E, S]             = ref.get
        }
      }
    }

  /**
   * Creates a summary that folds elements of type `I` into a structure
   * of type `S` until `max` elements have been folded.
   *
   * Like [[foldWeighted]], but with a constant cost function of 1.
   */
  def foldUntil[S, I](z: S, max: Long)(f: (S, I) => S): ZSummary[Any, Nothing, I, S] =
    fold[(S, Long), I]((z, 0L))(_._2 < max) { case ((o, count), i) =>
      (f(o, i), count + 1)
    }.map(_._1)

  /**
   * Creates a summary that effectfully folds elements of type `I` into a structure
   * of type `O` until `max` elements have been folded.
   *
   * Like [[foldWeightedM]], but with a constant cost function of 1.
   */
  def foldUntilM[R, E, I, O](z: O, max: Long)(f: (O, I) => ZIO[R, E, O]): ZSummary[R, E, I, O] =
    foldM[R, E, (O, Long), I]((z, 0))(_._2 < max) { case ((o, count), i) =>
      f(o, i).map((_, count + 1))
    }.map(_._1)

  /**
   * Creates a summary that folds elements of type `I` into a structure
   * of type `S`, until `max` worth of elements (determined by the `costFn`)
   * have been folded.
   *
   * @note Elements that have an individual cost larger than `max` will
   * force the summary to cross the `max` cost.
   */
  def foldWeighted[S, I](init0: S)(costFn: (S, I) => Long, max: Long)(f: (S, I) => S): ZSummary[Any, Nothing, I, S] =
    ZSummary {
      UIO {
        def make(): Builder.Mutable[I, S] =
          new Builder.Mutable[I, S] {
            var curr = init0
            var cost = 0L

            override def copy() = make()
            override def reset(): Unit = {
              curr = init0
              cost = 0L
            }

            override def accepts(i: I): Boolean =
              // Accept the element if the total cost is under the threshold,
              (costFn(curr, i) + cost <= max) ||
                // Or if the element's individual cost (measured against the initial state)
                // is over the threshold.
                costFn(init0, i) > max

            override def done(): Boolean =
              cost > max

            override def step(i: I): Unit = {
              cost = cost + costFn(curr, i)
              curr = f(curr, i)
            }

            override def extract(): S = curr
          }

        make()
      }
    }

  def foldWeightedM[R, E, S, I](
    init0: S
  )(costFn: (S, I) => ZIO[R, E, Long], max: Long)(f: (S, I) => ZIO[R, E, S]): ZSummary[R, E, I, S] =
    ZSummary {
      Ref.make((init0, 0L)).map { ref =>
        new Builder.Effectual[R, E, I, S] {
          def reset: ZIO[R, E, Unit] = ref.set((init0, 0L))

          def accepts(i: I): ZIO[R, E, Boolean] =
            ref.get.flatMap { case (s, currCost) =>
              costFn(s, i).flatMap { iCost =>
                if (currCost + iCost <= max) UIO.succeed(true)
                else costFn(init0, i).map(_ > max)
              }
            }

          def done: ZIO[R, E, Boolean] = ref.get.map(_._2 > max)
          def step(i: I): ZIO[R, E, Unit] =
            ref.get.flatMap { case (s, cost) =>
              f(s, i)
                .zipWith(costFn(s, i)) { (newS, iCost) =>
                  ref.set((newS, cost + iCost))
                }
                .flatten
            }
          def extract: ZIO[R, E, S] = ref.get.map(_._1)
        }
      }
    }

  /**
   * Creates a summary that executes the specified fold.
   */
  def foldLeft[S, I](init0: S)(f: (S, I) => S): ZSummary[Any, Nothing, I, S] =
    fold(init0)(_ => true)(f)

  /**
   * Creates a summary that executes the specified fold.
   */
  def foldLeftM[R, E, S, I](init0: S)(f: (S, I) => ZIO[R, E, S]): ZSummary[R, E, I, S] =
    foldM(init0)(_ => true)(f)

  /**
   * A summary that keeps the last value it processed.
   */
  def last[A]: ZSummary[Any, Nothing, A, Option[A]] = foldLeft(None: Option[A])((_, a) => Some(a))

  /**
   * Creates a summary that reduces incoming elements using the specified function.
   */
  def reduce[A](f: (A, A) => A): ZSummary[Any, Nothing, A, Option[A]] =
    foldLeft(None: Option[A]) {
      case (None, a)     => Some(a)
      case (Some(a0), a) => Some(f(a0, a))
    }

  def reduceM[R, E, A](f: (A, A) => ZIO[R, E, A]): ZSummary[R, E, A, Option[A]] =
    foldLeftM(None: Option[A]) {
      case (None, a)     => UIO.succeed(Some(a))
      case (Some(a0), a) => f(a0, a).map(Some(_))
    }

  /**
   * Creates a summary that immediately completes with the specified value.
   */
  def succeed[A](value: A): ZSummary[Any, Nothing, Any, A] =
    done(Exit.succeed(value))

  /**
   * A summary that sums integers.
   */
  val sum: ZSummary[Any, Nothing, Int, Int] = foldLeft(0)(_ + _)
}
