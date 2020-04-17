package zio.stream.experimental

import scala.collection.mutable

import zio._

// Contract notes for transducers:
// - When a None is received, the transducer must flush all of its internal state
//   and remain empty until subsequent Some(Chunk) values.
//
//   Stated differently, after a first push(None), all subsequent push(None) must
//   result in Chunk.empty.
abstract class ZTransducer[-R, +E, -I, +O](
  val push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, E, Chunk[O]]]
) extends ZConduit[R, E, I, O, Nothing](push.map(_.andThen(_.mapError(Left(_))))) { self =>

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
}

object ZTransducer {
  def apply[R, E, I, O](
    push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, E, Chunk[O]]]
  ): ZTransducer[R, E, I, O] =
    new ZTransducer(push) {}

  /**
   * A transducer that re-chunks the elements fed to it into chunks of up to
   * `n` elements each.
   */
  def chunkN[I](n: Int): ZTransducer[Any, Nothing, I, I] =
    ZTransducer {
      for {
        buffered <- ZRef.makeManaged[Chunk[I]](Chunk.empty)
        push = { (input: Option[Chunk[I]]) =>
          input match {
            case None =>
              buffered
                .modify(buf => (if (buf.isEmpty) Push.emit(Chunk.empty) else Push.emit(buf)) -> Chunk.empty)
                .flatten
            case Some(is) =>
              buffered.modify { buf0 =>
                val buf = buf0 ++ is
                if (buf.length >= n) {
                  val (out, buf1) = buf.splitAt(n)
                  Push.emit(out) -> buf1
                } else
                  Push.next -> buf
              }.flatten
          }
        }
      } yield push
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
    ZTransducer {
      ZRef.makeManaged(Option(Map[K, I]())).map { state =>
        {
          case None => state.getAndSet(None).map(Chunk.fromIterable(_))
          case Some(is) =>
            state.modify { maybeAcc =>
              val acc = maybeAcc.getOrElse(Map())

              val (output, newAcc, _) = is.fold((List[Map[K, I]](), acc, acc.keys.size)) {
                case ((mapsOfN, currMap, keyCount), i) =>
                  val k = key(i)
                  if (currMap.contains(k) && keyCount <= n)
                    (mapsOfN, currMap.updated(k, f(currMap(k), i)), keyCount)
                  else if (!currMap.contains(k) && keyCount < n)
                    (mapsOfN, currMap.updated(k, i), keyCount + 1)
                  else
                    (currMap :: mapsOfN, Map(k -> i), 1)
              }

              Chunk.fromIterable(output.reverse) -> Some(newAcc)
            }
        }
      }
    }

  /**
   * Creates a transducer accumulating incoming values into sets of maximum size `n`.
   */
  def collectAllToSetN[I](n: Long): ZTransducer[Any, Nothing, I, Set[I]] =
    ZTransducer {
      ZRef.makeManaged(Option(Set[I]())).map { state =>
        {
          case None => state.getAndSet(None).map(Chunk.fromIterable(_))
          case Some(is) =>
            state.modify { maybeAcc =>
              val acc = maybeAcc.getOrElse(Set())

              val (output, newAcc, _) = is.fold((List[Set[I]](), acc, acc.size)) {
                case ((setsOfN, currSet, setSize), i) =>
                  if (currSet(i)) (setsOfN, currSet, setSize)
                  else if (!currSet(i) && setSize < n) (setsOfN, currSet + i, setSize + 1)
                  else (currSet :: setsOfN, Set(i), 1)
              }

              Chunk.fromIterable(output.reverse) -> Some(newAcc)
            }
        }
      }
    }

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
        in.fold[(Chunk[O], O, Boolean)]((Chunk.empty, state, progress)) {
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
    foldWeighted[I, O](z)(_ => 1, max)(f)

  /**
   * Creates a transducer that effectfully folds elements of type `I` into a structure
   * of type `O` until `max` elements have been folded.
   *
   * Like [[foldWeightedM]], but with a constant cost function of 1.
   */
  def foldUntilM[R, E, I, O](z: O, max: Long)(f: (O, I) => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    foldWeightedM[R, E, I, O](z)(_ => UIO.succeedNow(1), max)(f)

  /**
   * Creates a transducer that folds elements of type `I` into a structure
   * of type `O`, until `max` worth of elements (determined by the `costFn`)
   * have been folded.
   *
   * @note Elements that have an individual cost larger than `max` will
   * force the transducer to cross the `max` cost. See [[foldWeightedDecompose]]
   * for a variant that can handle these cases.
   */
  def foldWeighted[I, O](z: O)(costFn: I => Long, max: Long)(f: (O, I) => O): ZTransducer[Any, Nothing, I, O] =
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
  )(costFn: I => Long, max: Long, decompose: I => Chunk[I])(f: (O, I) => O): ZTransducer[Any, Nothing, I, O] =
    ZTransducer {
      case class FoldWeightedState(result: O, cost: Long)

      val initial = FoldWeightedState(z, 0)

      def go(
        in: Chunk[I],
        os0: Chunk[O],
        state: FoldWeightedState,
        progress: Boolean
      ): (Chunk[O], FoldWeightedState, Boolean) =
        in.fold[(Chunk[O], FoldWeightedState, Boolean)]((os0, state, progress)) {
          case ((os0, state, _), i) =>
            val total = state.cost + costFn(i)
            if (total > max) {
              val is = decompose(i)
              if (is.isEmpty)
                (os0 + f(state.result, i), initial, false)
              else if (is.length == 1)
                (os0 + f(state.result, is(0)), initial, false)
              else
                go(is, os0, state, progress)
            } else if (total == max)
              (os0 + f(state.result, i), initial, false)
            else
              (os0, FoldWeightedState(f(state.result, i), total), true)
        }

      ZRef.makeManaged[Option[FoldWeightedState]](Some(initial)).map { state =>
        {
          case Some(in) =>
            state.modify { s =>
              val (o, s2, progress) = go(in, Chunk.empty, s.getOrElse(initial), s.nonEmpty)
              if (progress)
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
  def foldWeightedM[R, E, I, S](
    z: S
  )(costFn: I => ZIO[R, E, Long], max: Long)(f: (S, I) => ZIO[R, E, S]): ZTransducer[R, E, I, S] =
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
    costFn: I => ZIO[R, E, Long],
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
        progress: Boolean
      ): ZIO[R, E, (Chunk[O], FoldWeightedState, Boolean)] =
        in.foldM[R, E, (Chunk[O], FoldWeightedState, Boolean)]((os, state, progress)) {
          case ((os, state, _), i) =>
            costFn(i).flatMap { cost =>
              val total = cost + state.cost
              if (total > max)
                decompose(i).flatMap(is =>
                  if (is.isEmpty)
                    f(state.result, i).map(o => ((os + o), initial, false))
                  else if (is.length == 1)
                    f(state.result, is(0)).map(o => ((os + o), initial, false))
                  else {
                    go(is, os, state, progress)
                  }
                )
              else if (total == max)
                f(state.result, i).map(o => ((os + o), initial, false))
              else
                f(state.result, i).map(o => (os, FoldWeightedState(o, total), true))
            }
        }

      ZRef.makeManaged[Option[FoldWeightedState]](Some(initial)).map { state =>
        {
          case Some(in) =>
            state.get.flatMap(s => go(in, Chunk.empty, s.getOrElse(initial), s.nonEmpty)).flatMap {
              case (os, s, progress) =>
                if (progress)
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
   * Creates a transducer from a chunk processing function.
   */
  def fromPush[R, E, I, O](push: Option[Chunk[I]] => ZIO[R, E, Chunk[O]]): ZTransducer[R, E, I, O] =
    ZTransducer(Managed.succeed(push))

  /**
   * Splits strings on newlines. Handles both Windows newlines (`\r\n`) and UNIX newlines (`\n`).
   */
  val splitLines: ZTransducer[Any, Nothing, String, String] =
    ZTransducer {
      ZRef.makeManaged[(Option[String], Boolean)]((None, false)).map { stateRef =>
        {
          case None =>
            stateRef.getAndSet((None, false)).flatMap {
              case (None, _)      => ZIO.succeed(Chunk.empty)
              case (Some(str), _) => ZIO.succeed(Chunk(str))
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
              if (leftovers.isEmpty) ZIO.succeed(Chunk.empty)
              else ZIO.succeed(Chunk.single(new String(leftovers.toArray[Byte], "UTF-8")))
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
