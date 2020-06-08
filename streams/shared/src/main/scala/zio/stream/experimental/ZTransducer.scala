package zio.stream.experimental

import java.nio.charset.StandardCharsets

import zio._

/**
 * A `ZTransducer` is a process that transforms values of type `I` and into values of type `O`.
 */
abstract class ZTransducer[-R, +E, -I, +O] {

  val process: ZTransducer.Process[R, E, I, O]

  /**
   * Alias for [[aggregate]].
   */
  def >>>[R1 <: R, E1 >: E, Z](sink: ZSink[R1, E1, O, Z]): ZSink[R1, E1, I, Z] =
    aggregate(sink)

  /**
   * Alias for [[pipe]].
   */
  def >>>[R1 <: R, E1 >: E, A](transducer: ZTransducer[R1, E1, O, A]): ZTransducer[R1, E1, I, A] =
    pipe(transducer)

  /**
   * Compose this transducer with a sink, resulting in a sink that processes elements by piping
   * them through this transducer and piping the results into the sink.
   */
  def aggregate[R1 <: R, E1 >: E, Z](sink: ZSink[R1, E1, O, Z]): ZSink[R1, E1, I, Z] =
    ZSink(process.zip(ZRef.makeManaged(false)).zipWith(sink.process) {
      case (((p1, z1), ref), (p2, z2)) =>
        (
          i => (p1(i) >>= p2).catchAllCause(Pull.recover(Pull.end.ensuring(ref.set(true)))),
          ZIO.ifM(ref.get)(
            z2,
            z1.foldCauseM(
              Cause.sequenceCauseOption(_).fold(z2)(ZIO.halt(_)),
              p2(_).foldCauseM(Cause.sequenceCauseOption(_).fold(z2.ensuring(ref.set(true)))(ZIO.halt(_)), _ => z2)
            )
          )
        )
    })

  /**
   * Returns a transducer that applies this transducer's process to a chunk of input values.
   *
   * @note If this transducer applies a pure transformation, better efficiency can be achieved by overriding this
   *       method.
   */
  def chunked: ZTransducer[R, E, Chunk[I], Chunk[O]] =
    ZTransducer(process.map {
      case (push, pull) =>
        (ZIO.foreach(_)(push), pull.foldCauseM(Pull.recover(Pull.emit(Chunk.empty)), o => Pull.emit(Chunk.single(o))))
    })

  /**
   * Returns a transducer that applies this transducer's process to multiple input values.
   *
   * @note If this transducer applies a pure transformation, better efficiency can be achieved by overriding this
   *       method.
   */
  def foreach: ZTransducer[R, E, Iterable[I], Iterable[O]] =
    ZTransducer(process.map {
      case (push, pull) =>
        (ZIO.foreach(_)(push), pull.foldCauseM(Pull.recover(Pull.emit(Chunk.empty)), o => Pull.emit(Chunk.single(o))))
    })

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def pipe[R1 <: R, E1 >: E, A](transducer: ZTransducer[R1, E1, O, A]): ZTransducer[R1, E1, I, A] =
    ZTransducer(process.zipWith(transducer.process) {
      case ((s1, p1), (s2, p2)) =>
        (
          s1(_) >>= s2,
          p1.foldCauseM(Pull.recover(p2), s2(_) *> p2)
        )
    })

  /**
   * Transforms the outputs of this transducer.
   */
  def map[A](f: O => A): ZTransducer[R, E, I, A] =
    ZTransducer(process.map { case (step, last) => (step(_).map(f), last.map(f)) })
}

object ZTransducer {

  type Process[-R, +E, -I, +O] = URManaged[R, (I => Pull[R, E, O], Pull[R, E, O])]

  /**
   * A transducer that transforms values of type `I` to values of type `O`.
   */
  def apply[R, E, I, O](p: Process[R, E, I, O]): ZTransducer[R, E, I, O] =
    new ZTransducer[R, E, I, O] {
      val process: Process[R, E, I, O] = p
    }

  /**
   * A transducer that divides input chunks into chunks with a length bounded by `max`.
   */
  def chunkLimit[A](max: Int): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    map(chunk =>
      if (chunk.length <= max) Chunk.single(chunk)
      else {
        val builder = ChunkBuilder.make[Chunk[A]]()
        var rem     = chunk
        while (rem.nonEmpty) {
          builder += rem.take(max)
          rem = rem.drop(max)
        }
        builder.result()
      }
    )

  /**
   * A transducer that divides input chunks into fixed `size` chunks.
   * Any leftover chunk is dropped, when the transducer process ends.
   */
  def chunkN[A](size: Int): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    chunkN(size, (_: Chunk[A]) => Pull.end)

  /**
   * A transducer that divides input chunks into fixed `size` chunks.
   * The `pad` element is used to pad any leftover chunk to `size`, when the transducer process ends.
   */
  def chunkN[A](size: Int, pad: A): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    chunkN(size, (chunk: Chunk[A]) => Pull.emit(Chunk.single(chunk.padTo(size, pad))))

  /**
   * A transducer that divides input chunks into fixed `size` chunks.
   * The `pad` function is called on the last leftover, when the transducer process ends.
   */
  def chunkN[R, E, A](
    size: Int,
    pad: Chunk[A] => Pull[R, E, Chunk[Chunk[A]]]
  ): ZTransducer[R, E, Chunk[A], Chunk[Chunk[A]]] =
    ZTransducer(
      Process.fold(Chunk.empty: Chunk[A])(
        (state, chunk: Chunk[A]) => {
          var rem: Chunk[A] = state ++ chunk
          val builder       = ChunkBuilder.make[Chunk[A]]()
          while (rem.length > size) {
            builder += rem.take(size)
            rem = rem.drop(size)
          }
          (builder.result(), rem)
        },
        state => (if (state.isEmpty) Pull.end else pad(state), Chunk.empty)
      )
    )

  val empty: ZTransducer[Any, Nothing, Any, Nothing] =
    ZTransducer(Process.empty)

  /**
   * A transducer that passes elements unchanged.
   */
  def identity[A]: ZTransducer[Any, Nothing, A, A] =
    ZTransducer(Process.push(ZIO.succeedNow))

  /**
   * A transducer that map elements using function the given function.
   */
  def map[A, B](f: A => B): ZTransducer[Any, Nothing, A, B] =
    ZTransducer(Process.push(a => ZIO.succeedNow(f(a))))

  /**
   * A transducer that divides input strings on the system line separator.
   */
  val newLines: ZTransducer[system.System, Nothing, String, Chunk[String]] =
    ZTransducer(ZIO.accessM[system.System](_.get.lineSeparator).toManaged_.flatMap(splitOn(_).process))

  /**
   * A transducer that decodes a chunk of bytes to a UTF-8 string.
   */
  val utf8Decode: ZTransducer[Any, Nothing, Chunk[Byte], String] =
    new ZTransducer[Any, Nothing, Chunk[Byte], String] {
      val process: Process[Any, Nothing, Chunk[Byte], String] =
        Process.map(make)

      override def chunked: ZTransducer[Any, Nothing, Chunk[Chunk[Byte]], Chunk[String]] =
        ZTransducer(Process.map(_.map(make)))

      override def foreach: ZTransducer[Any, Nothing, Iterable[Chunk[Byte]], Iterable[String]] =
        ZTransducer(Process.map(_.map(make)))

      @inline private def make(bytes: Chunk[Byte]): String =
        new String(bytes.toArray, StandardCharsets.UTF_8)
    }

  /**
   * A transducer that divides input strings on the given `separator`.
   * The `retain` flag controls whether the `separator` is to be retained in the output.
   * The `leftover` flag is for the `chunked` operator and controls whether remainders are propagated downstream.
   */
  def splitOn(
    separator: String,
    retain: Boolean = false,
    leftover: Boolean = false
  ): ZTransducer[Any, Nothing, String, Chunk[String]] =
    new ZTransducer[Any, Nothing, String, Chunk[String]] {
      val di: Int = if (retain) separator.length else 0

      val process: Process[Any, Nothing, String, Chunk[String]] =
        Process.fold("")(
          (last, s: String) => {
            val cb  = ChunkBuilder.make[String]()
            var rem = last ++ s
            var i   = rem.indexOf(separator)
            while (i != -1) {
              i = i + di
              cb += rem.take(i)
              rem = rem.drop(i)
              i = rem.indexOf(separator)
            }
            (cb.result(), rem)
          },
          last => (if (leftover && last.nonEmpty) Pull.emit(Chunk.single(last)) else Pull.end, "")
        )
    }

  /**
   * A transducer that passes `n` input values and then terminates.
   * The `leftover` flag is for the `chunked` operator and controls whether remainders are propagated downstream.
   */
  def take[I](n: Long, leftover: Boolean = false): ZTransducer[Any, Nothing, I, I] =
    new ZTransducer[Any, Nothing, I, I] {
      val process: Process[Any, Nothing, I, I] =
        Process.foldM(n)((s, i) => if (s > 0) (Pull.emit(i), s - 1) else (Pull.end, s))

      override def chunked: ZTransducer[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer(
          Process.foldM(n -> (Chunk.empty: Chunk[I]))(
            {
              case ((rem, last), is) =>
                if (rem <= 0) (Pull.end, (0, last ++ is))
                else if (is.length <= rem) (Pull.emit(is), (rem - is.length, Chunk.empty))
                else {
                  val lr = is.splitAt(rem.toInt)
                  (Pull.emit(lr._1), (0, lr._2))
                }
            }, {
              case (_, last) =>
                (if (leftover && last.nonEmpty) Pull.emit(last) else Pull.end, (0, Chunk.empty))
            }
          )
        )

      override def foreach: ZTransducer[Any, Nothing, Iterable[I], Iterable[I]] =
        ZTransducer(
          Process.foldM(n -> (Chunk.empty: Iterable[I]))(
            {
              case ((rem, last), is) =>
                if (rem <= 0) (Pull.end, (0, last ++ is))
                else if (is.size <= rem) (Pull.emit(is), (rem - is.size, Chunk.empty))
                else {
                  val lr = is.splitAt(rem.toInt)
                  (Pull.emit(lr._1), (0, lr._2))
                }
            }, {
              case (_, last) =>
                (if (leftover && last.nonEmpty) Pull.emit(last) else Pull.end, (0, Chunk.empty))
            }
          )
        )
    }

  /**
   * A transducer that passes input values until the predicate `p` is satisfied.
   * The `leftover` flag is for the `chunked` operator and controls whether remainders are propagated downstream.
   */
  def takeUntil[I](p: I => Boolean, leftover: Boolean = false): ZTransducer[Any, Nothing, I, I] =
    new ZTransducer[Any, Nothing, I, I] {
      val process: Process[Any, Nothing, I, I] =
        Process.foldM(true)((s, i) => if (s) (Pull.emit(i), p(i)) else (Pull.end, s))

      override def chunked: ZTransducer[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer(
          Process.foldM(Chunk.empty: Chunk[I])(
            (s, is) => if (s.isEmpty) split(is) else (Pull.end, s ++ is),
            s => (if (leftover && s.nonEmpty) Pull.emit(s) else Pull.end, Chunk.empty)
          )
        )

      override def foreach: ZTransducer[Any, Nothing, Iterable[I], Iterable[I]] =
        ZTransducer(
          Process.foldM(Chunk.empty: Iterable[I])(
            (s, is) => if (s.isEmpty) split(is) else (Pull.end, s ++ is),
            s => (if (leftover && s.nonEmpty) Pull.emit(s) else Pull.end, Chunk.empty)
          )
        )

      private def split(is: Chunk[I]): (Pull[Any, Nothing, Chunk[I]], Chunk[I]) =
        if (is.isEmpty) (Pull.emit(Chunk.empty), Chunk.empty)
        else {
          val lr = is.span(p)
          lr.copy(_1 = if (lr._1.isEmpty) Pull.end else Pull.emit(lr._1))
        }

      private def split(is: Iterable[I]): (Pull[Any, Nothing, Iterable[I]], Iterable[I]) =
        if (is.isEmpty) (Pull.emit(Chunk.empty), Chunk.empty)
        else {
          val lr = is.span(p)
          lr.copy(_1 = if (lr._1.isEmpty) Pull.end else Pull.emit(lr._1))
        }
    }

  /**
   * A transducer that passes input values while the predicate `p` is satisfied.
   * The `leftover` flag is for the `chunked` operator and controls whether remainders are propagated downstream.
   */
  def takeWhile[I](p: I => Boolean, leftover: Boolean = false): ZTransducer[Any, Nothing, I, I] =
    new ZTransducer[Any, Nothing, I, I] {
      val process: Process[Any, Nothing, I, I] =
        Process.push(i => if (p(i)) Pull.emit(i) else Pull.end)

      override def chunked: ZTransducer[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer(
          Process.foldM(Chunk.empty: Chunk[I])(
            (s, is) => if (s.isEmpty) split(is) else (Pull.end, s ++ is),
            s => (if (leftover && s.nonEmpty) Pull.emit(s) else Pull.end, Chunk.empty)
          )
        )

      override def foreach: ZTransducer[Any, Nothing, Iterable[I], Iterable[I]] =
        ZTransducer(
          Process.foldM(Chunk.empty: Iterable[I])(
            (s, is) => if (s.isEmpty) split(is) else (Pull.end, s ++ is),
            s => (if (leftover && s.nonEmpty) Pull.emit(s) else Pull.end, Chunk.empty)
          )
        )

      private def split(is: Chunk[I]): (Pull[Any, Nothing, Chunk[I]], Chunk[I]) =
        if (is.isEmpty) (Pull.emit(Chunk.empty), Chunk.empty)
        else {
          val lr = is.span(p)
          lr.copy(_1 = if (lr._1.isEmpty) Pull.end else Pull.emit(lr._1))
        }

      private def split(is: Iterable[I]): (Pull[Any, Nothing, Iterable[I]], Iterable[I]) =
        if (is.isEmpty) (Pull.emit(Chunk.empty), Chunk.empty)
        else {
          val lr = is.span(p)
          lr.copy(_1 = if (lr._1.isEmpty) Pull.end else Pull.emit(lr._1))
        }
    }

  object Process {

    val empty: Process[Any, Nothing, Any, Nothing] =
      push(_ => Pull.end, Pull.end)

    def fold[R, E, S, I, O](
      init: S
    )(modify: (S, I) => (O, S), last: S => (Pull[R, E, O], S) = (s: S) => Pull.end -> s): Process[R, E, I, O] =
      ZRef.makeManaged(init).map(ref => (i => ref.modify(modify(_, i)), ref.modify(last).flatten))

    def foldM[R, E, S, I, O](
      init: S
    )(
      modify: (S, I) => (Pull[R, E, O], S),
      last: S => (Pull[R, E, O], S) = (s: S) => Pull.end -> s
    ): Process[R, E, I, O] =
      ZRef.makeManaged(init).map(ref => (i => ref.modify(modify(_, i)).flatten, ref.modify(last).flatten))

    def map[I, O](f: I => O): Process[Any, Nothing, I, O] =
      push(i => ZIO.succeedNow(f(i)))

    def mapM[R, E, I, O](f: I => Pull[R, E, O]): Process[R, E, I, O] =
      push(f)

    def push[R, E, I, O](step: I => Pull[R, E, O], last: Pull[R, E, O] = Pull.end): Process[R, E, I, O] =
      ZManaged.succeedNow(step -> last)
  }
}
