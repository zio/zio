package zio.stream

import scala.collection.mutable

import zio._

// Contract notes for transducers:
// - When a None is received, the transducer must flush all of its internal state
//   and remain empty until subsequent Some(Chunk) values.
//
//   Stated differently, after a first push(None), all subsequent push(None) must
//   result in Chunk.empty.
abstract class ZTransducer[-R, +E, -I, +O](val push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, E, Chunk[O]]]) {
  self =>

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, O3](that: ZTransducer[R1, E1, O2, O3]): ZTransducer[R1, E1, I, O3] =
    ZTransducer {
      self.push.zipWith(that.push) { (pushLeft, pushRight) =>
        {
          case None =>
            pushLeft(None).flatMap(cl =>
              if (cl.isEmpty) pushRight(None)
              else pushRight(Some(cl)).zipWith(pushRight(None))(_ ++ _)
            )
          case inputs @ Some(_) =>
            pushLeft(inputs).flatMap(cl => pushRight(Some(cl)))
        }
      }
    }

  /**
   * Compose this transducer with a sink, resulting in a sink that processes elements by piping
   * them through this transducer and piping the results into the sink.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, I1 <: I, Z](that: ZSink[R1, E1, O2, Z]): ZSink[R1, E1, I1, Z] =
    ZSink {
      self.push.zipWith(that.push) { (pushSelf, pushThat) =>
        {
          case None =>
            pushSelf(None)
              .mapError(Left(_))
              .flatMap(chunk => pushThat(Some(chunk)) *> pushThat(None))
          case inputs @ Some(_) =>
            pushSelf(inputs)
              .mapError(Left(_))
              .flatMap(chunk => pushThat(Some(chunk)))
        }
      }
    }

  final def contramap[J](f: J => I): ZTransducer[R, E, J, O] =
    ZTransducer(self.push.map(push => is => push(is.map(_.map(f)))))

  final def contramapM[R1 <: R, E1 >: E, J](f: J => ZIO[R1, E1, I]): ZTransducer[R1, E1, J, O] =
    ZTransducer[R1, E1, J, O](self.push.map(push => is => ZIO.foreach(is)(_.mapM(f)).flatMap(push)))

  /**
   * Filters the outputs of this transducer.
   */
  final def filter(p: O => Boolean): ZTransducer[R, E, I, O] =
    ZTransducer(self.push.map(push => i => push(i).map(_.filter(p))))

  /**
   * Transforms the outputs of this transducer.
   */
  final def map[P](f: O => P): ZTransducer[R, E, I, P] =
    ZTransducer(self.push.map(push => i => push(i).map(_.map(f))))

  /**
   * Transforms the chunks emitted by this transducer.
   */
  final def mapChunks[O2](f: Chunk[O] => Chunk[O2]): ZTransducer[R, E, I, O2] =
    ZTransducer {
      self.push.map(push => (input: Option[Chunk[I]]) => push(input).map(f))
    }

  /**
   * Effectfully transforms the chunks emitted by this transducer.
   */
  final def mapChunksM[R1 <: R, E1 >: E, O2](
    f: Chunk[O] => ZIO[R1, E1, Chunk[O2]]
  ): ZTransducer[R1, E1, I, O2] =
    ZTransducer {
      self.push.map(push => (input: Option[Chunk[I]]) => push(input).flatMap(f))
    }

  /**
   * Transforms the outputs of this transducer.
   */
  final def mapError[E1](f: E => E1): ZTransducer[R, E1, I, O] =
    ZTransducer(self.push.map(push => i => push(i).mapError(f)))

  /**
   * Effectually transforms the outputs of this transducer
   */
  final def mapM[R1 <: R, E1 >: E, P](f: O => ZIO[R1, E1, P]): ZTransducer[R1, E1, I, P] =
    ZTransducer[R1, E1, I, P](self.push.map(push => i => push(i).flatMap(_.mapM(f))))

  /**
   * Runs both transducers in parallel on the input, emitting the result from the
   * one that finishes first.
   * If both finished simultaneously, the first one has priority.
   *
   * NB! Result might depend on the way how the stream is chunked.
   * See {@link #raceBoth} for details.
   */
  def race[R1 <: R, E1 >: E, I1 <: I, O1 >: O](
    that: ZTransducer[R1, E1, I1, O1]
  ): ZTransducer[R1, E1, I1, O1] =
    self.raceBoth(that).map(_.merge)

  /**
   * Runs both transducers in parallel on the input, emitting the result from the
   * one that finishes first.
   * If both finished simultaneously, the first one has priority.
   *
   * NB! Due to the batch nature of streams,
   * result might depend on the way how the stream is chunked.
   * If one of the transducers emitted a value at the moment `t1`,
   * and then another emitted a value at the moment `t2 > t1`,
   * the second value could be emitted instead of the first
   * in case `t1` and `t2` belong to the same chunk.
   *
   * If such deviations are not tolerated,
   * consider rechunking the stream before transducing:
   * `stream.chunkN(1).transduce(a.raceBoth(b))`
   */
  def raceBoth[R1 <: R, E1 >: E, I1 <: I, O1](
    that: ZTransducer[R1, E1, I1, O1]
  ): ZTransducer[R1, E1, I1, Either[O, O1]] = {
    case class State(leftDebt: Long, rightDebt: Long)

    def updateState(s: State, left: Chunk[O], right: Chunk[O1]): (State, Chunk[Either[O, O1]]) = {
      val leftsAvailable = left.size.toLong - s.leftDebt
      if (leftsAvailable > 0) {
        val leftWinners     = left.takeRight(leftsAvailable.toInt).map(Left(_))
        val rightsAvailable = right.size.toLong - s.rightDebt - leftsAvailable
        if (rightsAvailable > 0) {
          val rightWinners = right.takeRight(rightsAvailable.toInt).map(Right(_))
          (State(rightsAvailable, 0), leftWinners ++ rightWinners)
        } else {
          (State(0, -rightsAvailable), leftWinners)
        }
      } else {
        val rightsAvailable = right.size - s.rightDebt
        if (rightsAvailable > 0) {
          val rightWinners = right.takeRight(rightsAvailable.toInt).map(Right(_))
          (State(-leftsAvailable + rightsAvailable, 0), rightWinners)
        } else {
          (State(-leftsAvailable, -rightsAvailable), Chunk.empty)
        }
      }
    }

    ZTransducer {
      for {
        ref <- ZRef.make[State](State(0, 0)).toManaged_
        p1  <- self.push
        p2  <- that.push
      } yield { (in: Option[Chunk[I1]]) =>
        for {
          st       <- ref.get
          newState <- p1(in).zipWithPar(p2(in)) { case (lefts, rights) => updateState(st, lefts, rights) }
          _        <- ref.set(newState._1)
        } yield {
          newState._2
        }
      }
    }
  }
}

object ZTransducer {
  def apply[R, E, I, O](
    push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, E, Chunk[O]]]
  ): ZTransducer[R, E, I, O] =
    new ZTransducer(push) {}

  /**
   * Shorthand form for [[ZTransducer.identity]]. Use as:
   * {{{
   * ZTransducer[Int].filter(_ % 2 != 0)
   * }}}
   */
  def apply[I]: ZTransducer[Any, Nothing, I, I] = identity[I]

  /**
   * The identity transducer. Passed elements through.
   */
  def identity[I]: ZTransducer[Any, Nothing, I, I] =
    ZTransducer.fromPush {
      case Some(is) => ZIO.succeedNow(is)
      case None     => ZIO.succeedNow(Chunk.empty)
    }

  /**
   * Creates a transducer accumulating incoming values into lists of maximum size `n`.
   */
  def collectAllN[I](n: Long): ZTransducer[Any, Nothing, I, List[I]] =
    foldUntil[I, List[I]](Nil, n)((list, element) => element :: list).map(_.reverse)

  /**
   * Creates a transducer accumulating incoming values into maps of up to `n` keys. Elements
   * are mapped to keys using the function `key`; elements mapped to the same key will
   * be merged with the function `f`.
   */
  def collectAllToMapN[K, I](n: Long)(key: I => K)(f: (I, I) => I): ZTransducer[Any, Nothing, I, Map[K, I]] =
    foldWeighted(Map[K, I]())((acc, i: I) => if (acc contains key(i)) 0 else 1, n) { (acc, i) =>
      val k = key(i)

      if (acc contains k) acc.updated(k, f(acc(k), i))
      else acc.updated(k, i)
    }

  /**
   * Creates a transducer accumulating incoming values into sets of maximum size `n`.
   */
  def collectAllToSetN[I](n: Long): ZTransducer[Any, Nothing, I, Set[I]] =
    foldWeighted(Set[I]())((acc, i: I) => if (acc(i)) 0 else 1, n)(_ + _)

  /**
   * Accumulates incoming elements into a list as long as they verify predicate `p`.
   */
  def collectAllWhile[I](p: I => Boolean): ZTransducer[Any, Nothing, I, List[I]] =
    fold[I, (List[I], Boolean)]((Nil, true))(_._2) {
      case ((as, _), a) => if (p(a)) (a :: as, true) else (as, false)
    }.map(_._1.reverse).filter(_.nonEmpty)

  /**
   * Accumulates incoming elements into a list as long as they verify effectful predicate `p`.
   */
  def collectAllWhileM[R, E, I](p: I => ZIO[R, E, Boolean]): ZTransducer[R, E, I, List[I]] =
    foldM[R, E, I, (List[I], Boolean)]((Nil, true))(_._2) {
      case ((as, _), a) => p(a).map(if (_) (a :: as, true) else (as, false))
    }.map(_._1.reverse).filter(_.nonEmpty)

  /**
   * Creates a transducer that always dies with the specified exception.
   */
  def die(e: => Throwable): ZTransducer[Any, Nothing, Any, Nothing] =
    ZTransducer(Managed.succeed((_: Any) => IO.die(e)))

  /**
   * Creates a transducer that starts consuming values as soon as one fails
   * the predicate `p`.
   */
  def dropWhile[I](p: I => Boolean): ZTransducer[Any, Nothing, I, I] =
    ZTransducer {
      for {
        dropping <- ZRef.makeManaged(true)
        push = { (is: Option[Chunk[I]]) =>
          is match {
            case None => UIO(Chunk.empty)
            case Some(is) =>
              dropping.modify {
                case false => is -> false
                case true =>
                  val is1 = is.dropWhile(p)
                  is1 -> (is1.length == 0)
              }
          }
        }
      } yield push
    }

  /**
   * Creates a transducer that starts consuming values as soon as one fails
   * the effectful predicate `p`.
   */
  def dropWhileM[R, E, I](p: I => ZIO[R, E, Boolean]): ZTransducer[R, E, I, I] =
    ZTransducer {
      for {
        dropping <- ZRef.makeManaged(true)
        push = { (is: Option[Chunk[I]]) =>
          is match {
            case None => UIO(Chunk.empty)
            case Some(is) =>
              dropping.get.flatMap {
                case false => UIO(is -> false)
                case true  => is.dropWhileM(p).map(is1 => is1 -> (is1.length == 0))
              }.flatMap { case (is, pt) => dropping.set(pt) as is }
          }
        }
      } yield push
    }

  /**
   * Creates a transducer that always fails with the specified failure.
   */
  def fail[E](e: => E): ZTransducer[Any, E, Any, Nothing] =
    ZTransducer(ZManaged.succeed((_: Option[Any]) => ZIO.fail(e)))

  /**
   * Creates a transducer by folding over a structure of type `O` for as long as
   * `contFn` results in `true`. The transducer will emit a value when `contFn`
   * evaluates to `false` and then restart the folding.
   */
  def fold[I, O](z: O)(contFn: O => Boolean)(f: (O, I) => O): ZTransducer[Any, Nothing, I, O] =
    ZTransducer {
      def go(in: Chunk[I], state: O, progress: Boolean): (Chunk[O], O, Boolean) =
        in.foldLeft[(Chunk[O], O, Boolean)]((Chunk.empty, state, progress)) {
          case ((os0, state, _), i) =>
            val o = f(state, i)
            if (contFn(o))
              (os0, o, true)
            else
              (os0 + o, z, false)
        }

      ZRef.makeManaged[Option[O]](Some(z)).map { state =>
        {
          case Some(in) =>
            state.modify { s =>
              val (o, s2, progress) = go(in, s.getOrElse(z), s.nonEmpty)
              if (progress)
                o -> Some(s2)
              else
                o -> None
            }
          case None => state.getAndSet(None).map(_.fold[Chunk[O]](Chunk.empty)(Chunk.single(_)))
        }
      }
    }

  /**
   * Creates a transducer by folding over a structure of type `O`. The transducer will
   * fold the inputs until the stream ends, resulting in a stream with one element.
   */
  def foldLeft[I, O](z: O)(f: (O, I) => O): ZTransducer[Any, Nothing, I, O] =
    fold(z)(_ => true)(f)

  /**
   * Creates a transducer by effectfully folding over a structure of type `O`. The transducer will
   * fold the inputs until the stream ends, resulting in a stream with one element.
   */
  def foldLeftM[R, E, I, O](z: O)(f: (O, I) => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    foldM(z)(_ => true)(f)

  /**
   * Creates a sink by effectfully folding over a structure of type `S`.
   */
  def foldM[R, E, I, O](z: O)(contFn: O => Boolean)(f: (O, I) => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    ZTransducer {
      val initial = Some(z)

      def go(in: Chunk[I], state: O, progress: Boolean): ZIO[R, E, (Chunk[O], O, Boolean)] =
        in.foldM[R, E, (Chunk[O], O, Boolean)]((Chunk.empty, state, progress)) {
          case ((os0, state, _), i) =>
            f(state, i).map { o =>
              if (contFn(o))
                (os0, o, true)
              else
                (os0 + o, z, false)
            }
        }

      ZRef.makeManaged[Option[O]](initial).map { state =>
        {
          case Some(in) =>
            state.get.flatMap(s => go(in, s.getOrElse(z), s.nonEmpty)).flatMap {
              case (os, s, progress) =>
                if (progress)
                  state.set(Some(s)) *> Push.emit(os)
                else
                  state.set(None) *> Push.emit(os)
            }
          case None =>
            state.getAndSet(None).map(_.fold[Chunk[O]](Chunk.empty)(Chunk.single(_)))
        }
      }
    }

  /**
   * Creates a transducer that folds elements of type `I` into a structure
   * of type `O` until `max` elements have been folded.
   *
   * Like [[foldWeighted]], but with a constant cost function of 1.
   */
  def foldUntil[I, O](z: O, max: Long)(f: (O, I) => O): ZTransducer[Any, Nothing, I, O] =
    foldWeighted[I, O](z)((_, _) => 1, max)(f)

  /**
   * Creates a transducer that effectfully folds elements of type `I` into a structure
   * of type `O` until `max` elements have been folded.
   *
   * Like [[foldWeightedM]], but with a constant cost function of 1.
   */
  def foldUntilM[R, E, I, O](z: O, max: Long)(f: (O, I) => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    foldWeightedM[R, E, I, O](z)((_, _) => UIO.succeedNow(1), max)(f)

  /**
   * Creates a transducer that folds elements of type `I` into a structure
   * of type `O`, until `max` worth of elements (determined by the `costFn`)
   * have been folded.
   *
   * @note Elements that have an individual cost larger than `max` will
   * force the transducer to cross the `max` cost. See [[foldWeightedDecompose]]
   * for a variant that can handle these cases.
   */
  def foldWeighted[I, O](z: O)(costFn: (O, I) => Long, max: Long)(f: (O, I) => O): ZTransducer[Any, Nothing, I, O] =
    foldWeightedDecompose[I, O](z)(costFn, max, Chunk.single(_))(f)

  /**
   * Creates a transducer that folds elements of type `I` into a structure
   * of type `O`, until `max` worth of elements (determined by the `costFn`)
   * have been folded.
   *
   * The `decompose` function will be used for decomposing elements that
   * cause an `O` aggregate to cross `max` into smaller elements. For
   * example:
   * {{{
   * Stream(1, 5, 1)
   *  .aggregate(
   *    ZTransducer
   *      .foldWeightedDecompose(List[Int]())((i: Int) => i.toLong, 4,
   *        (i: Int) => Chunk(i - 1, 1)) { (acc, el) =>
   *        el :: acc
   *      }
   *      .map(_.reverse)
   *  )
   *  .runCollect
   * }}}
   *
   * The stream would emit the elements `List(1), List(4), List(1, 1)`.
   *
   * Be vigilant with this function, it has to generate "simpler" values
   * or the fold may never end. A value is considered indivisible if
   * `decompose` yields the empty chunk or a single-valued chunk. In
   * these cases, there is no other choice than to yield a value that
   * will cross the threshold.
   *
   * The [[foldWeightedDecomposeM]] allows the decompose function
   * to return a `ZIO` value, and consequently it allows the transducer
   * to fail.
   */
  def foldWeightedDecompose[I, O](
    z: O
  )(costFn: (O, I) => Long, max: Long, decompose: I => Chunk[I])(f: (O, I) => O): ZTransducer[Any, Nothing, I, O] =
    ZTransducer {
      case class FoldWeightedState(result: O, cost: Long)

      val initial = FoldWeightedState(z, 0)

      def go(
        in: Chunk[I],
        os0: Chunk[O],
        state: FoldWeightedState,
        dirty: Boolean
      ): (Chunk[O], FoldWeightedState, Boolean) =
        in.foldLeft[(Chunk[O], FoldWeightedState, Boolean)]((os0, state, dirty)) {
          case ((os0, state, _), i) =>
            val total = state.cost + costFn(state.result, i)

            if (total > max) {
              val is = decompose(i)

              if (is.length <= 1 && !dirty)
                // If `i` cannot be decomposed, we need to cross the `max` threshold. To
                // minimize "injury", we only allow this when we haven't added anything else
                // to the aggregate (dirty = false).
                (os0 + f(state.result, if (is.nonEmpty) is(0) else i), initial, false)
              else if (is.length <= 1 && dirty) {
                // If the state is dirty and `i` cannot be decomposed, we close the current
                // aggregate and a create new one from `is`. We're not adding `f(initial, i)` to
                // the results immediately because it could be that `i` by itself does not
                // cross the threshold, so we can attempt to aggregate it with subsequent elements.
                val elem = if (is.nonEmpty) is(0) else i
                (os0 + state.result, FoldWeightedState(f(initial.result, elem), costFn(initial.result, elem)), true)
              } else
                // `i` got decomposed, so we will recurse and see whether the decomposition
                // can be aggregated without crossing `max`.
                go(is, os0, state, dirty)
            } else (os0, FoldWeightedState(f(state.result, i), total), true)
        }

      ZRef.makeManaged[Option[FoldWeightedState]](Some(initial)).map { state =>
        {
          case Some(in) =>
            state.modify { s =>
              val (o, s2, dirty) = go(in, Chunk.empty, s.getOrElse(initial), s.nonEmpty)
              if (dirty)
                o -> Some(s2)
              else
                o -> None
            }
          case None => state.getAndSet(None).map(_.fold[Chunk[O]](Chunk.empty)(s => Chunk.single(s.result)))
        }
      }
    }

  /**
   * Creates a transducer that effectfully folds elements of type `I` into a structure
   * of type `S`, until `max` worth of elements (determined by the `costFn`) have
   * been folded.
   *
   * @note Elements that have an individual cost larger than `max` will
   * force the transducer to cross the `max` cost. See [[foldWeightedDecomposeM]]
   * for a variant that can handle these cases.
   */
  def foldWeightedM[R, E, I, O](
    z: O
  )(costFn: (O, I) => ZIO[R, E, Long], max: Long)(f: (O, I) => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    foldWeightedDecomposeM(z)(costFn, max, (i: I) => UIO.succeedNow(Chunk.single(i)))(f)

  /**
   * Creates a transducer that effectfully folds elements of type `I` into a structure
   * of type `S`, until `max` worth of elements (determined by the `costFn`) have
   * been folded.
   *
   * The `decompose` function will be used for decomposing elements that
   * cause an `S` aggregate to cross `max` into smaller elements. Be vigilant with
   * this function, it has to generate "simpler" values or the fold may never end.
   * A value is considered indivisible if `decompose` yields the empty chunk or a
   * single-valued chunk. In these cases, there is no other choice than to yield
   * a value that will cross the threshold.
   *
   * See [[foldWeightedDecompose]] for an example.
   */
  def foldWeightedDecomposeM[R, E, I, O](z: O)(
    costFn: (O, I) => ZIO[R, E, Long],
    max: Long,
    decompose: I => ZIO[R, E, Chunk[I]]
  )(f: (O, I) => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    ZTransducer {
      final case class FoldWeightedState(result: O, cost: Long)

      val initial = FoldWeightedState(z, 0)

      def go(
        in: Chunk[I],
        os: Chunk[O],
        state: FoldWeightedState,
        dirty: Boolean
      ): ZIO[R, E, (Chunk[O], FoldWeightedState, Boolean)] =
        in.foldM[R, E, (Chunk[O], FoldWeightedState, Boolean)]((os, state, dirty)) {
          case ((os, state, _), i) =>
            costFn(state.result, i).flatMap { cost =>
              val total = cost + state.cost
              if (total > max)
                decompose(i).flatMap(is =>
                  // See comments on `foldWeightedDecompose` for details on every case here.
                  if (is.length <= 1 && !dirty)
                    f(state.result, if (is.nonEmpty) is(0) else i).map(o => ((os + o), initial, false))
                  else if (is.length <= 1 && dirty) {
                    val elem = if (is.nonEmpty) is(0) else i

                    f(initial.result, elem).zipWith(costFn(initial.result, elem)) { (s, cost) =>
                      (os + state.result, FoldWeightedState(s, cost), true)
                    }
                  } else go(is, os, state, dirty)
                )
              else
                f(state.result, i).map(o => (os, FoldWeightedState(o, total), true))
            }
        }

      ZRef.makeManaged[Option[FoldWeightedState]](Some(initial)).map { state =>
        {
          case Some(in) =>
            state.get.flatMap(s => go(in, Chunk.empty, s.getOrElse(initial), s.nonEmpty)).flatMap {
              case (os, s, dirty) =>
                if (dirty)
                  state.set(Some(s)) *> Push.emit(os)
                else
                  state.set(None) *> Push.emit(os)
            }
          case None =>
            state.getAndSet(None).map(_.fold[Chunk[O]](Chunk.empty)(s => Chunk.single(s.result)))
        }
      }
    }

  /**
   * Creates a transducer that always evaluates the specified effect.
   */
  def fromEffect[R, E, A](zio: ZIO[R, E, A]): ZTransducer[R, E, Any, A] =
    ZTransducer(Managed.succeed((_: Any) => zio.map(Chunk.single(_))))

  /**
   * Creates a transducer that purely transforms incoming values.
   */
  def fromFunction[I, O](f: I => O): ZTransducer[Any, Unit, I, O] =
    identity.map(f)

  /**
   * Creates a transducer that effectfully transforms incoming values.
   */
  def fromFunctionM[R, E, I, O](f: I => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    identity.mapM(f(_))

  /**
   * Creates a transducer from a chunk processing function.
   */
  def fromPush[R, E, I, O](push: Option[Chunk[I]] => ZIO[R, E, Chunk[O]]): ZTransducer[R, E, I, O] =
    ZTransducer(Managed.succeed(push))

  /**
   * Creates a transducer that returns the first element of the stream, if it exists.
   */
  def head[O]: ZTransducer[Any, Nothing, O, Option[O]] =
    foldLeft[O, Option[O]](Option.empty[O]) {
      case (acc, a) =>
        acc match {
          case Some(_) => acc
          case None    => Some(a)
        }
    }

  /**
   * Creates a transducer that returns the last element of the stream, if it exists.
   */
  def last[O]: ZTransducer[Any, Nothing, O, Option[O]] =
    foldLeft[O, Option[O]](Option.empty[O])((_, a) => Some(a))

  /**
   * Splits strings on newlines. Handles both Windows newlines (`\r\n`) and UNIX newlines (`\n`).
   */
  val splitLines: ZTransducer[Any, Nothing, String, String] =
    ZTransducer {
      ZRef.makeManaged[(Option[String], Boolean)]((None, false)).map { stateRef =>
        {
          case None =>
            stateRef.getAndSet((None, false)).flatMap {
              case (None, _)      => ZIO.succeedNow(Chunk.empty)
              case (Some(str), _) => ZIO.succeedNow(Chunk(str))
            }

          case Some(strings) =>
            stateRef.modify {
              case (leftover, wasSplitCRLF) =>
                val buf    = mutable.ArrayBuffer[String]()
                var inCRLF = wasSplitCRLF
                var carry  = leftover getOrElse ""

                (Chunk.fromIterable(leftover) ++ strings).foreach { string =>
                  val concat = carry + string

                  if (concat.length() > 0) {
                    var i =
                      // If we had a split CRLF, we start reading
                      // from the last character of the leftover (which was the '\r')
                      if (inCRLF && carry.length > 0) carry.length - 1
                      // Otherwise we just skip over the entire previous leftover as
                      // it doesn't contain a newline.
                      else carry.length
                    var sliceStart = 0

                    while (i < concat.length()) {
                      if (concat(i) == '\n') {
                        buf += concat.substring(sliceStart, i)
                        i += 1
                        sliceStart = i
                      } else if (concat(i) == '\r' && (i + 1) < concat.length && concat(i + 1) == '\n') {
                        buf += concat.substring(sliceStart, i)
                        i += 2
                        sliceStart = i
                      } else if (concat(i) == '\r' && i == concat.length - 1) {
                        inCRLF = true
                        i += 1
                      } else {
                        i += 1
                      }
                    }

                    carry = concat.substring(sliceStart, concat.length)
                  }
                }

                (Chunk.fromArray(buf.toArray), (if (carry.length() > 0) Some(carry) else None, inCRLF))
            }
        }
      }
    }

  /**
   * Splits strings on a delimiter.
   */
  def splitOn(delimiter: String): ZTransducer[Any, Nothing, String, String] =
    ZTransducer {
      ZRef.makeManaged[(Option[String], Int)](None -> 0).map { state =>
        {
          case None =>
            state.modify {
              case s @ (None, _) => Chunk.empty     -> s
              case (Some(s), _)  => Chunk.single(s) -> (None -> 0)
            }
          case Some(is) =>
            state.modify { s0 =>
              var out: mutable.ArrayBuffer[String] = null
              var chunkIndex                       = 0
              var buffer                           = s0._1.getOrElse("")
              var delimIndex                       = s0._2
              while (chunkIndex < is.length) {
                val in    = buffer + is(chunkIndex)
                var index = buffer.length
                var start = 0
                buffer = ""
                while (index < in.length) {
                  while (delimIndex < delimiter.length && index < in.length && in(index) == delimiter(delimIndex)) {
                    delimIndex += 1
                    index += 1
                  }
                  if (delimIndex == delimiter.length || in == "") {
                    if (out eq null) out = mutable.ArrayBuffer[String]()
                    out += in.substring(start, index - delimiter.length)
                    delimIndex = 0
                    start = index
                  }
                  if (index < in.length) {
                    delimIndex = 0
                    while (index < in.length && in(index) != delimiter(0)) index += 1;
                  }
                }

                if (start < in.length) {
                  buffer = in.drop(start)
                }

                chunkIndex += 1
              }

              val chunk = if (out eq null) Chunk.empty else Chunk.fromArray(out.toArray)
              val buf   = if (buffer == "") None else Some(buffer)

              chunk -> (buf -> delimIndex)
            }
        }
      }
    }

  /**
   * Decodes chunks of UTF-8 bytes into strings.
   *
   * This transducer uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  val utf8Decode: ZTransducer[Any, Nothing, Byte, String] =
    ZTransducer {
      def is2ByteSequenceStart(b: Byte) = (b & 0xE0) == 0xC0
      def is3ByteSequenceStart(b: Byte) = (b & 0xF0) == 0xE0
      def is4ByteSequenceStart(b: Byte) = (b & 0xF8) == 0xF0
      def computeSplit(chunk: Chunk[Byte]) = {
        // There are 3 bad patterns we need to check to detect an incomplete chunk:
        // - 2/3/4 byte sequences that start on the last byte
        // - 3/4 byte sequences that start on the second-to-last byte
        // - 4 byte sequences that start on the third-to-last byte
        //
        // Otherwise, we can convert the entire concatenated chunk to a string.
        val len = chunk.length

        if (len >= 1 &&
            (is2ByteSequenceStart(chunk(len - 1)) ||
            is3ByteSequenceStart(chunk(len - 1)) ||
            is4ByteSequenceStart(chunk(len - 1))))
          len - 1
        else if (len >= 2 &&
                 (is3ByteSequenceStart(chunk(len - 2)) ||
                 is4ByteSequenceStart(chunk(len - 2))))
          len - 2
        else if (len >= 3 && is4ByteSequenceStart(chunk(len - 3)))
          len - 3
        else len
      }

      ZRef.makeManaged[Chunk[Byte]](Chunk.empty).map { stateRef =>
        {
          case None =>
            stateRef.getAndSet(Chunk.empty).flatMap { leftovers =>
              if (leftovers.isEmpty) ZIO.succeedNow(Chunk.empty)
              else ZIO.succeedNow(Chunk.single(new String(leftovers.toArray[Byte], "UTF-8")))
            }

          case Some(bytes) =>
            stateRef.modify { leftovers =>
              val concat = leftovers ++ bytes

              val (toConvert, newLeftovers) = concat.splitAt(computeSplit(concat))

              if (toConvert.isEmpty) (Chunk.empty, newLeftovers)
              else (Chunk.single(new String(toConvert.toArray[Byte], "UTF-8")), newLeftovers)
            }
        }
      }
    }

  object Push {
    def emit[A](a: A): UIO[Chunk[A]]         = IO.succeedNow(Chunk.single(a))
    def emit[A](as: Chunk[A]): UIO[Chunk[A]] = IO.succeedNow(as)
    val next: UIO[Chunk[Nothing]]            = IO.succeedNow(Chunk.empty)
  }
}
