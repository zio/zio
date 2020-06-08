package zio.stream.experimental

import java.nio.charset.StandardCharsets

import zio._

/**
 * A `ZTransducer` is a process that transforms values of type `I` and into values of type `O`.
 *
 * @note The process may optionally retain some value of type `O` for subsequent steps.
 */
trait ZTransducer[-R, +E, -I, +O] {

  def process: URManaged[R, (I => Pull[R, E, O], Pull[R, E, O])]

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def >>>[R1 <: R, E1 >: E, A](transducer: ZTransducer[R1, E1, O, A]): ZTransducer[R1, E1, I, A] =
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
   * Compose this transducer with a sink, resulting in a sink that processes elements by piping
   * them through this transducer and piping the results into the sink.
   */
  def >>>[R1 <: R, E1 >: E, Z](sink: ZSink[R1, E1, O, Z]): ZSink[R1, E1, I, Z] =
    ZSink(process.zip(ZRef.makeManaged(false)).zipWith(sink.process) {
      case (((s1, p1), ref), (s2, p2)) =>
        (
          i => (s1(i) >>= s2).catchAllCause(Pull.recover(Pull.end.ensuring(ref.set(true)))),
          ZIO.ifM(ref.get)(
            p2,
            p1.foldCauseM(
              Cause.sequenceCauseOption(_).fold(p2)(ZIO.halt(_)),
              s2(_).foldCauseM(Cause.sequenceCauseOption(_).fold(p2.ensuring(ref.set(true)))(ZIO.halt(_)), _ => p2)
            )
          )
        )
    })

  /**
   * Returns a transducer that applies this transducer's process to multiple input values.
   *
   * @note If this transducer applies a pure transformation, better efficiency can be achieved by overriding this
   *       method.
   */
  def chunked: ZTransducer[R, E, Chunk[I], Chunk[O]] =
    ZTransducer(process.map {
      case (step, last) =>
        (ZIO.foreach(_)(step), last.foldCauseM(Pull.recover(Pull.emit(Chunk.empty)), o => Pull.emit(Chunk.single(o))))
    })

  /**
   * Returns a transducer that applies this transducer's process to multiple input values.
   *
   * @note If this transducer applies a pure transformation, better efficiency can be achieved by overriding this
   *       method.
   */
  def forall: ZTransducer[R, E, Iterable[I], Iterable[O]] =
    ZTransducer(process.map {
      case (step, last) =>
        (ZIO.foreach(_)(step), last.foldCauseM(Pull.recover(Pull.emit(Chunk.empty)), o => Pull.emit(Chunk.single(o))))
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
      val process: URManaged[R, (I => Pull[R, E, O], Pull[R, E, O])] = p
    }

  /**
   * A transducer that divides chunks into chunks with a length bounded by `max`.
   */
  def chunkLimit[A](max: Int): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    succeed(chunk =>
      ZIO.succeedNow(
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
    )

  /**
   * A transducer that divides chunks into fixed `size` chunks.
   * The `pad` element is used to pad the last leftover to `size`, when the transducer process ends.
   */
  def chunkN[A](size: Int, pad: A): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    chunkN(size, (chunk: Chunk[A]) => Pull.emit(Chunk.single(chunk.padTo(size, pad))))

  /**
   * A transducer that transforms chunks into a chunk of chunks where each chunk has exactly `size` elements.
   * The `pad` function is called on the last leftover, when the transducer process ends.
   */
  def chunkN[R, E, A](
    size: Int,
    pad: Chunk[A] => Pull[R, E, Chunk[Chunk[A]]]
  ): ZTransducer[R, E, Chunk[A], Chunk[Chunk[A]]] =
    ZTransducer(
      ZRef
        .makeManaged(Chunk.empty: Chunk[A])
        .map(ref =>
          (
            chunk =>
              ref.modify { state =>
                var rem     = state ++ chunk
                val builder = ChunkBuilder.make[Chunk[A]]()
                while (rem.length > size) {
                  builder += rem.take(size)
                  rem = rem.drop(size)
                }
                (builder.result(), rem)
              },
            ref
              .getAndSet(Chunk.empty)
              .flatMap(rem =>
                if (rem.isEmpty) Pull.end
                else pad(rem)
              )
          )
        )
    )

  /**
   * A transducer that passes elements unchanged.
   */
  def identity[A]: ZTransducer[Any, Nothing, A, A] =
    succeed(ZIO.succeedNow)

  /**
   * A transducer that map elements using function the given function.
   */
  def map[A, B](f: A => B): ZTransducer[Any, Nothing, A, B] =
    succeed(a => ZIO.succeedNow(f(a)))

  /**
   * A transducer that divides input strings on the system line separator.
   */
  val newLines: ZTransducer[system.System, Nothing, String, Chunk[String]] =
    new ZTransducer[system.System, Nothing, String, Chunk[String]] {
      val builder: ZManaged[system.System, Nothing, (Ref[String], String)] =
        ZRef.makeManaged("").zip(ZIO.accessM[system.System](_.get.lineSeparator).toManaged_)

      val process: Process[system.System, Nothing, String, Chunk[String]] =
        builder.map {
          case (ref, sep) =>
            val di = sep.length
            (
              (s: String) =>
                ref.get.flatMap(l =>
                  ZIO.effectSuspendTotal {
                    val cb  = ChunkBuilder.make[String]()
                    var rem = l ++ s
                    var i   = rem.indexOf(sep)
                    while (i != -1) {
                      cb += rem.take(i)
                      rem = rem.drop(i + di)
                      i = rem.indexOf(sep)
                    }
                    ref.set(rem).as(cb.result())
                  }
                ),
              ref
                .getAndSet("")
                .flatMap(s =>
                  if (s.isEmpty) Pull.end
                  else Pull.emit(Chunk.single(s))
                )
            )
        }
    }

  /**
   * A transducer that applies `step` to input values and yields `last` on completion.
   */
  def succeed[R, E, I, O](step: I => Pull[R, E, O], last: Pull[R, E, O] = Pull.end): ZTransducer[R, E, I, O] =
    ZTransducer(ZManaged.succeedNow(step -> last))

  /**
   * A transducer that decodes a chunk of bytes to a UTF-8 string.
   */
  val utf8Decode: ZTransducer[Any, Nothing, Chunk[Byte], String] =
    new ZTransducer[Any, Nothing, Chunk[Byte], String] {
      val process: Process[Any, Nothing, Chunk[Byte], String] =
        ZManaged.succeedNow(
          ((c: Chunk[Byte]) => ZIO.succeedNow(new String(c.toArray, StandardCharsets.UTF_8)), Pull.end)
        )

      override def chunked: ZTransducer[Any, Nothing, Chunk[Chunk[Byte]], Chunk[String]] =
        ZTransducer(
          ZManaged.succeedNow(
            (
              (c: Chunk[Chunk[Byte]]) =>
              ZIO.succeedNow {
                val f = c.flatten
                if (f.isEmpty) Chunk.empty else Chunk.single(new String(f.toArray, StandardCharsets.UTF_8))
              },
            Pull.end
            )
          )
        )

      override def forall: ZTransducer[Any, Nothing, Iterable[Chunk[Byte]], Iterable[String]] =
        ZTransducer(
          ZManaged.succeedNow(
            (
              (c: Iterable[Chunk[Byte]]) =>
              ZIO.succeedNow {
                val f = c.flatten
                if (f.isEmpty) Chunk.empty else Chunk.single(new String(f.toArray, StandardCharsets.UTF_8))
              },
            Pull.end
            )
          )
        )
    }
}
