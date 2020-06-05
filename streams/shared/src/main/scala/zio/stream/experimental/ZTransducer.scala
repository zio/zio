package zio.stream.experimental

import java.nio.charset.StandardCharsets

import zio._
import zio.stream.experimental.ZTransducer.Step

/**
 * A `ZTransducer` is a process that transforms values of type `I` and into values of type `O`.
 *
 * @note The process may optionally retain some value of type `O` for subsequent steps.
 */
final class ZTransducer[-R, +E, -I, +O] private (val process: URManaged[R, (Step[R, E, I, O], ZIO[R, E, Option[O]])]) {

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def >>>[R1 <: R, E1 >: E, A](transducer: ZTransducer[R1, E1, O, A]): ZTransducer[R1, E1, I, A] =
    ZTransducer(process.zipWith(transducer.process) {
      case ((ls, lo), (rs, ro)) => (ls(_) >>= rs, lo.flatMap(_.fold(ro)(rs(_) *> ro)))
    })

  /**
   * Compose this transducer with a sink, resulting in a sink that processes elements by piping
   * them through this transducer and piping the results into the sink.
   */
  def >>>[R1 <: R, E1 >: E, Z](sink: ZSink[R1, E1, O, Z]): ZSink[R1, E1, I, Z] =
    ZSink(process.zipWith(sink.process) {
      case ((step, last), (push, done)) =>
        (
          step(_).mapError(Left(_)) >>= push,
          last.flatMap(
            _.fold(done)(push(_).catchAllCause(Cause.sequenceCauseEither(_).fold(ZIO.halt(_), ZIO.succeedNow)) *> done)
          )
        )
    })

  /**
   * Returns a transducer that applies this transducer's process to multiple input values.
   *
   * @note If this transducer applies a pure transformation, better efficiency can be achieved by using `map`.
   */
  def chunked: ZTransducer[R, E, Chunk[I], Chunk[O]] =
    ZTransducer(process.map {
      case (step, last) => (ZIO.foreach(_)(step), last.map(_.map(Chunk.single)))
    })

  /**
   * Transforms the outputs of this transducer.
   */
  def map[A](f: O => A): ZTransducer[R, E, I, A] =
    ZTransducer(process.map { case (step, last) => (step(_).map(f), last.map(_.map(f))) })
}

object ZTransducer {

  type Step[-R, +E, -I, +O] = I => ZIO[R, E, O]

  /**
   * A transducer that transforms values of type `I` to values of type `O`.
   */
  def apply[R, E, I, O](step: URManaged[R, (Step[R, E, I, O], ZIO[R, E, Option[O]])]): ZTransducer[R, E, I, O] =
    new ZTransducer(step)

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
    chunkN(
      size,
      (chunk: Chunk[A]) => if (chunk.isEmpty) ZIO.none else ZIO.succeedNow(Some(Chunk.single(chunk.padTo(size, pad))))
    )

  /**
   * A transducer that transforms chunks into a chunk of chunks where each chunk has exactly `size` elements.
   * The `pad` function is called on the last leftover, when the transducer process ends.
   */
  def chunkN[R, E, A](
    size: Int,
    pad: Chunk[A] => ZIO[R, E, Option[Chunk[Chunk[A]]]]
  ): ZTransducer[R, E, Chunk[A], Chunk[Chunk[A]]] =
    apply(
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
                if (rem.isEmpty) ZIO.none
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
   * A transducer that divides strings on the system line separator.
   */
  val newLines: ZTransducer[system.System, Nothing, String, Chunk[String]] =
    apply(
      ZRef
        .makeManaged("")
        .zipWith(ZIO.accessM[system.System](_.get.lineSeparator).toManaged_) { (ref, sep) =>
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
              .map(s =>
                if (s.isEmpty) None
                else Option(Chunk.single(s))
              )
          )
        }
    )

  /**
   * A transducer that applies `step` to input values and with leftover `last`.
   */
  def succeed[R, E, I, O](step: Step[R, E, I, O], last: ZIO[R, E, Option[O]] = ZIO.none): ZTransducer[R, E, I, O] =
    ZTransducer(ZManaged.succeedNow(step -> last))

  /**
   * A transducer that decodes a chunk of bytes to a UTF-8 string.
   */
  val utf8Decode: ZTransducer[Any, Nothing, Chunk[Byte], String] =
    succeed(chunk => ZIO.succeedNow(new String(chunk.toArray, StandardCharsets.UTF_8)))
}
