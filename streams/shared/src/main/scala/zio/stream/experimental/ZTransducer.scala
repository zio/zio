package zio.stream.experimental

import scala.annotation.tailrec
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
   * Creates a sink accumulating incoming values into a list of maximum size `n`.
   */
  def collectAllN[I](n: Long): ZTransducer[Any, Nothing, I, List[I]] =
    foldUntil[I, List[I]](Nil, n)((list, element) => element :: list).map(_.reverse)

  /**
   * Accumulates incoming elements into a list as long as they verify predicate `p`.
   */
  def collectAllWhile[I](p: I => Boolean): ZTransducer[Any, Nothing, I, List[I]] =
    fold[I, (List[I], Boolean)]((Nil, true))(_._2) {
      case ((as, _), a) => if (p(a)) (a :: as, true) else (as, false)
    }.map(_._1.reverse)

  /**
   * Accumulates incoming elements into a list as long as they verify effectful predicate `p`.
   */
  def collectAllWhileM[R, E, I](p: I => ZIO[R, E, Boolean]): ZTransducer[R, E, I, List[I]] =
    foldM[R, E, I, (List[I], Boolean)]((Nil, true))(_._2) {
      case ((as, _), a) => p(a).map(if (_) (a :: as, true) else (as, false))
    }.map(_._1.reverse)

  def die(e: => Throwable): ZTransducer[Any, Nothing, Any, Nothing] =
    ZTransducer(Managed.succeed((_: Any) => IO.die(e)))

  def fail[E](e: => E): ZTransducer[Any, E, Any, Nothing] =
    ZTransducer(ZManaged.succeed((_: Option[Any]) => ZIO.fail(e)))

  def fromEffect[R, E, A](zio: ZIO[R, E, A]): ZTransducer[R, E, Any, A] =
    ZTransducer(Managed.succeed((_: Any) => zio.map(Chunk.single(_))))

  def fromPush[R, E, I, O](push: Option[Chunk[I]] => ZIO[R, E, Chunk[O]]): ZTransducer[R, E, I, O] =
    ZTransducer(Managed.succeed(push))

  /**
   * Creates a transducer by folding over a structure of type `S`.
   */
  def fold[I, O](z: O)(contFn: O => Boolean)(f: (O, I) => O): ZTransducer[Any, Nothing, I, O] =
    ZTransducer {
      final case class FoldState(started: Boolean, result: O)

      val initial = FoldState(false, z)

      @tailrec def go(in: Chunk[I], os0: Chunk[O], state: FoldState): (Chunk[O], FoldState) =
        in.headOption match {
          case None => os0 -> state
          case Some(i) =>
            val o = f(state.result, i)
            if (contFn(o))
              go(in.drop(1), os0, FoldState(true, o))
            else
              go(in.drop(1), os0 + o, initial)
        }

      ZRef.makeManaged(initial).map { state =>
        {
          case Some(in) => state.modify(go(in, Chunk.empty, _))
          case None     => state.getAndSet(initial).map(s => if (s.started) Chunk.single(s.result) else Chunk.empty)
        }
      }
    }

  /**
   * Creates a sink by effectfully folding over a structure of type `S`.
   */
  def foldM[R, E, I, O](z: O)(contFn: O => Boolean)(f: (O, I) => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    ZTransducer {
      final case class FoldState(started: Boolean, result: O)

      val initial = FoldState(false, z)

      def go(in: Chunk[I], os0: Chunk[O], state: FoldState): ZIO[R, E, (Chunk[O], FoldState)] =
        in.headOption match {
          case None => ZIO.succeedNow(os0 -> state)
          case Some(i) =>
            f(state.result, i).flatMap { o =>
              if (contFn(o))
                go(in.drop(1), os0, FoldState(true, o))
              else
                go(in.drop(1), os0 + o, initial)
            }
        }

      ZRef.makeManaged(initial).map { state =>
        {
          case Some(in) =>
            state.get.flatMap(go(in, Chunk.empty, _)).flatMap {
              case (os, s) => state.set(s) *> Push.emit(os)
            }
          case None =>
            state.getAndSet(initial).map(s => if (s.started) Chunk.single(s.result) else Chunk.empty)
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
   * cause the stream to hang. See [[foldWeightedDecompose]] for
   * a variant that can handle these.
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
   * The [[foldWeightedDecomposeM]] allows the decompose function
   * to return a `ZIO` value, and consequently it allows the transducer
   * to fail.
   */
  def foldWeightedDecompose[I, O](
    z: O
  )(costFn: I => Long, max: Long, decompose: I => Chunk[I])(f: (O, I) => O): ZTransducer[Any, Nothing, I, O] =
    ZTransducer {
      case class FoldWeightedState(started: Boolean, result: O, cost: Long)

      val initial = FoldWeightedState(false, z, 0)

      @tailrec def go(in: Chunk[I], os0: Chunk[O], state: FoldWeightedState): (Chunk[O], FoldWeightedState) =
        in.headOption match {
          case None => os0 -> state
          case Some(i) =>
            val total = state.cost + costFn(i)
            if (total > max) {
              val is = decompose(i)
              if (is.isEmpty)
                go(in.drop(1), os0 + f(state.result, i), initial)
              else if (is.length == 1)
                go(in.drop(1), os0 + f(state.result, is(0)), initial)
              else
                go(is ++ in.drop(1), os0, state)
            } else if (total == max)
              go(in.drop(1), os0 + f(state.result, i), initial)
            else
              go(in.drop(1), os0, FoldWeightedState(true, f(state.result, i), total))
        }

      ZRef.makeManaged(initial).map { state =>
        {
          case Some(in) => state.modify(go(in, Chunk.empty, _))
          case None     => state.getAndSet(initial).map(s => if (s.started) Chunk.single(s.result) else Chunk.empty)
        }
      }
    }

  /**
   * Creates a transducer that effectfully folds elements of type `I` into a structure
   * of type `S`, until `max` worth of elements (determined by the `costFn`) have
   * been folded.
   *
   * @note Elements that have an individual cost larger than `max` will
   * cause the stream to hang. See [[foldWeightedDecomposeM]] for
   * a variant that can handle these.
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
   * cause an `S` aggregate to cross `max` into smaller elements. See
   * [[foldWeightedDecompose]] for an example.
   */
  def foldWeightedDecomposeM[R, E, I, O](z: O)(
    costFn: I => ZIO[R, E, Long],
    max: Long,
    decompose: I => ZIO[R, E, Chunk[I]]
  )(f: (O, I) => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    ZTransducer {
      final case class FoldWeightedState(started: Boolean, result: O, cost: Long)

      val initial = FoldWeightedState(false, z, 0)

      def go(in: Chunk[I], os: Chunk[O], state: FoldWeightedState): ZIO[R, E, (FoldWeightedState, Chunk[O])] =
        in.headOption match {
          case None => ZIO.succeedNow(state -> os)
          case Some(i) =>
            costFn(i).flatMap { cost =>
              val total = cost + state.cost
              if (total > max)
                decompose(i).flatMap(is =>
                  if (is.isEmpty)
                    f(state.result, i).flatMap(o => go(in.drop(1), os + o, initial))
                  else if (is.length == 1)
                    f(state.result, is(0)).flatMap(o => go(in.drop(1), os + o, initial))
                  else
                    go(is ++ in.drop(1), os, state)
                )
              else if (total == max)
                f(state.result, i).flatMap(o => go(in.drop(1), os + o, initial))
              else
                f(state.result, i).flatMap(o => go(in.drop(1), os, FoldWeightedState(true, o, total)))
            }
        }

      ZRef.makeManaged(initial).map { state =>
        {
          case Some(in) =>
            state.get.flatMap(go(in, Chunk.empty, _)).flatMap { case (s, os) => state.set(s) *> Push.emit(os) }
          case None =>
            state.getAndSet(initial).map(s => if (s.started) Chunk.single(s.result) else Chunk.empty)
        }
      }
    }

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
