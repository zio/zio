package zio.stream.experimental

import java.nio.charset.StandardCharsets

import zio._

/**
 * A `ZTransducer` is a process that transforms values of type `I` and into values of type `O`.
 *
 * @note The process may optionally retain some value of type `O` for subsequent steps.
 */
abstract class ZTransducer[-R, +E, -I, +O] {

  val process: URManaged[R, (I => Pull[R, E, O], Pull[R, E, O])]

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
    ZTransducer(process.zip(ZRef.makeManaged(false)).zipWith(transducer.process) {
      case (((s1, p1), ref), (s2, p2)) =>
        (
          i => (s1(i) >>= s2).catchAllCause(Pull.recover(Pull.end.ensuring(ref.set(true)))),
          ZIO.ifM(ref.get)(
            p2,
            p1.foldCauseM(Pull.recover(p2.ensuring(ref.set(true))), s2(_) *> p2)
          )
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
   * Any leftover chunk is emitted as is, when the transducer process ends.
   */
  def chunkN[A](size: Int): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    chunkN(size, (chunk: Chunk[A]) => Pull.emit(Chunk.single(chunk)))

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
      Process.fromRef(Chunk.empty: Chunk[A])(
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

  /**
   * A transducer that map elements using function the given function.
   */
  def map[A, B](f: A => B): ZTransducer[Any, Nothing, A, B] =
    ZTransducer(Process.succeed(a => ZIO.succeedNow(f(a))))

  /**
   * A transducer that passes elements unchanged.
   */
  def identity[A]: ZTransducer[Any, Nothing, A, A] =
    ZTransducer(Process.succeed(ZIO.succeedNow))

  /**
   * A transducer that divides input strings on the system line separator.
   */
  val newLines: ZTransducer[system.System, Nothing, String, Chunk[String]] =
    ZTransducer(ZIO.accessM[system.System](_.get.lineSeparator).toManaged_.flatMap(splitOn(_).process))

  /**
   * A transducer that divides input strings on the given `separator`.
   * The `retain` flag controls whether the `separator` is to be retained in the output.
   */
  def splitOn(separator: String, retain: Boolean = false): ZTransducer[Any, Nothing, String, Chunk[String]] =
    new ZTransducer[Any, Nothing, String, Chunk[String]] {
      val di: Int = if (retain) separator.length else 0

      val process: Process[Any, Nothing, String, Chunk[String]] =
        Process.fromRef("")(
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
          last => (if (last.isEmpty) Pull.end else Pull.emit(Chunk.single(last)), "")
        )
    }

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

  object Process {

    def fromRef[R, E, S, I, O](init: S)(modify: (S, I) => (O, S), last: S => (Pull[R, E, O], S)): Process[R, E, I, O] =
      ZRef.makeManaged(init).map(ref => (i => ref.modify(modify(_, i)), ref.modify(last).flatten))

    def map[I, O](f: I => O): Process[Any, Nothing, I, O] =
      succeed(i => ZIO.succeedNow(f(i)))

    def succeed[R, E, I, O](step: I => Pull[R, E, O], last: Pull[R, E, O] = Pull.end): Process[R, E, I, O] =
      ZManaged.succeedNow(step -> last)
  }
}
