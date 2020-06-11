package zio.stream.experimental

import java.nio.charset.StandardCharsets

import zio._

/**
 * A `ZTransducer` is a process that transforms values of type `I` into values of type `O`.
 */
abstract class ZTransducer[-R, +E, -I, +O] {
  self =>

  val process: ZTransducer.Process[R, E, I, O]

  /**
   * Alias for [[pipe]].
   */
  def >>>[R1 <: R, E1 >: E, A](transducer: ZTransducer[R1, E1, O, A]): ZTransducer[R1, E1, I, A] =
    pipe(transducer)

  /**
   * Alias for [[pipe]].
   */
  def >>>[R1 <: R, E1 >: E, A](sink: ZSink[R1, E1, O, A]): ZSink[R1, E1, I, A] =
    pipe(sink)

  /**
   * Returns a transducer that applies this transducer's process to a chunk of input values.
   *
   * @note If this transducer applies a pure transformation, better efficiency can be achieved by overriding this
   *       method.
   */
  def chunked: ZTransducer[R, E, Chunk[I], Chunk[O]] =
    ZTransducer(process.map {
      case (push, read) => (ZIO.foreach(_)(push), Read.sequence(read))
    })

  def discard[R1 <: R, E1 >: E, I1 <: I](f: I1 => ZIO[R1, E1, Any]): ZTransducer[R1, E1, I1, O] =
    ZTransducer(process.map { case (push, read) => (push, _.fold[ZIO[R1, E1, Any]](ZIO.unit)(f) *> read(None)) })

  def discard_ : ZTransducer[R, E, I, O] =
    discard(_ => ZIO.unit)

  /**
   * Returns a transducer that applies this transducer's process to multiple input values.
   *
   * @note If this transducer applies a pure transformation, better efficiency can be achieved by overriding this
   *       method.
   */
  def foreach: ZTransducer[R, E, Iterable[I], Iterable[O]] =
    ZTransducer(process.map {
      case (push, read) => (ZIO.foreach(_)(push), Read.sequence(read))
    })

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def pipe[R1 <: R, E1 >: E, A](transducer: ZTransducer[R1, E1, O, A]): ZTransducer[R1, E1, I, A] =
    ZTransducer(process.zipWith(transducer.process) {
      case ((p1, r1), (p2, r2)) => (p1(_) >>= p2, r1(_) >>= r2)
    })

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def pipe[R1 <: R, E1 >: E, A](sink: ZSink[R1, E1, O, A]): ZSink[R1, E1, I, A] =
    ZSink(process.zipWith(sink.process) {
      case ((p1, r1), (p2, r2)) => (p1(_) >>= p2, r1(_) >>= r2)
    })
}

object ZTransducer {

  type Process[-R, +E, -I, +O] = URManaged[R, (Push[R, E, I, O], Read[R, E, Option[I], Option[O]])]

  /**
   * A transducer that transforms values of type `I` to values of type `O`.
   */
  def apply[R, E, I, O](p: Process[R, E, I, O]): ZTransducer[R, E, I, O] =
    new ZTransducer[R, E, I, O] {
      val process: Process[R, E, I, O] = p
    }

  def chunk[A]: ZTransducer[Any, Nothing, A, Chunk[A]] =
    map(Chunk.single)

  /**
   * A transducer that divides input chunks into chunks with a length bounded by `max`.
   */
  def chunkLimit[A](max: Int): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    map(chunk =>
      if (chunk.isEmpty) Chunk.empty
      else if (chunk.length <= max) Chunk.single(chunk)
      else {
        val builder = ChunkBuilder.make[Chunk[A]]()
        var rem     = chunk
        do {
          builder += rem.take(max)
          rem = rem.drop(max)
        } while (rem.nonEmpty)
        builder.result()
      }
    )

  /**
   * A transducer that divides input chunks into fixed `size` chunks.
   * If the last chunk does not have `size` elements, it is dropped.
   */
  def chunkN[A](size: Int): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    chunkN(size, (_: Chunk[A]) => Chunk.empty)

  /**
   * A transducer that divides input chunks into fixed `size` chunks.
   * The `pad` element is used to pad the transducer's leftover if it does not have `size` elements.
   */
  def chunkN[A](size: Int, pad: A): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    chunkN(size, (c: Chunk[A]) => c.padTo(size, pad))

  /**
   * A transducer that divides input chunks into fixed `size` chunks.
   * The `pad` function is called on the transducer's leftover if it does not have `size` elements.
   */
  def chunkN[A](size: Int, pad: Chunk[A] => Chunk[A]): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    fold[Chunk[A], Chunk[Chunk[A]], Chunk[A]](Chunk.empty)(
      (state, chunk) => {
        var rem = state ++ chunk
        if (rem.length < size) (Chunk.empty, rem)
        else {
          val builder = ChunkBuilder.make[Chunk[A]]()
          do {
            builder += rem.take(size)
            rem = rem.drop(size)
          } while (rem.length >= size)
          (builder.result(), rem)
        }
      },
      (state, l) => {
        var rem = l.fold(state)(state ++ _)
        if (rem.isEmpty) (None, Chunk.empty)
        else {
          val builder = ChunkBuilder.make[Chunk[A]]()
          do {
            builder += rem.take(size)
            rem = rem.drop(size)
          } while (rem.length >= size)
          if (rem.nonEmpty) builder += pad(rem)
          (Some(builder.result()), Chunk.empty)
        }
      }
    )

  def fold[I, O, Z](
    init: Z
  )(push: (Z, I) => (O, Z), read: (Z, Option[I]) => (Option[O], Z)): ZTransducer[Any, Nothing, I, O] =
    new ZTransducer[Any, Nothing, I, O] {
      val process: Process[Any, Nothing, I, O] =
        Process.fold(init)(push, read)

      override def chunked: ZTransducer[Any, Nothing, Chunk[I], Chunk[O]] =
        ZTransducer(
          Process.fold(init)(
            (s, is: Chunk[I]) => {
              val b = ChunkBuilder.make[O]()
              var z = s
              is.foreach { i =>
                val os = push(z, i)
                b += os._1
                z = os._2
              }
              (b.result(), z)
            },
            (s, l) => {
              val b = ChunkBuilder.make[O]()
              var z = s
              l.foreach(_.foreach { i =>
                val os = read(z, Some(i))
                os._1.foreach(b += _)
                z = os._2
              })
              val ls = b.result()
              (if (ls.isEmpty) None else Option(ls), z)
            }
          )
        )

      override def foreach: ZTransducer[Any, Nothing, Iterable[I], Iterable[O]] =
        ZTransducer(
          Process.fold(init)(
            (s, is: Iterable[I]) => {
              val b = ChunkBuilder.make[O]()
              var z = s
              is.foreach { i =>
                val os = push(z, i)
                b += os._1
                z = os._2
              }
              (b.result(), z)
            },
            (s, l) => {
              val b = ChunkBuilder.make[O]()
              var z = s
              l.foreach(_.foreach { i =>
                val os = read(z, Some(i))
                os._1.foreach(b += _)
                z = os._2
              })
              val ls = b.result()
              (if (ls.isEmpty) None else Option(ls), z)
            }
          )
        )
    }

  /**
   * A transducer that passes elements unchanged.
   */
  def identity[A]: ZTransducer[Any, Nothing, A, A] =
    map(a => a)

  /**
   * A transducer that map elements using function the given function.
   */
  def map[A, B](f: A => B): ZTransducer[Any, Nothing, A, B] =
    new ZTransducer[Any, Nothing, A, B] {
      val process: Process[Any, Nothing, A, B] =
        Process.map(f)

      override def chunked: ZTransducer[Any, Nothing, Chunk[A], Chunk[B]] =
        ZTransducer(Process.map(_.map(f)))

      override def foreach: ZTransducer[Any, Nothing, Iterable[A], Iterable[B]] =
        ZTransducer(Process.map(_.map(f)))
    }

  def mapM[R, E, I, O](f: I => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    ZTransducer(Process.mapM(f))

  /**
   * A transducer that divides input strings on the system line separator.
   */
  val newLines: ZTransducer[system.System, Nothing, String, Chunk[String]] =
    ZTransducer(ZIO.accessM[system.System](_.get.lineSeparator).toManaged_.flatMap(split(_).process))

  /**
   * A transducer that divides an input string on the given `separator` (and discards it).
   */
  def split(separator: String): ZTransducer[Any, Nothing, String, Chunk[String]] =
    new ZTransducer[Any, Nothing, String, Chunk[String]] {
      val di: Int = separator.length

      val process: Process[Any, Nothing, String, Chunk[String]] =
        Process.fold("")(
          (state, s: String) => {
            val builder = ChunkBuilder.make[String]()
            val rem     = step(builder, state + s)
            (builder.result(), rem)
          },
          (state, s) => {
            val builder = ChunkBuilder.make[String]()
            val rem     = step(builder, state + s)
            if (rem.nonEmpty) builder += rem
            (Option(builder.result()), "")
          }
        )

      private def step(builder: ChunkBuilder[String], state: String): String = {
        var rem = state
        var i   = rem.indexOf(separator)
        while (i != -1) {
          i = i + di
          builder += rem.take(i)
          rem = rem.drop(i)
          i = rem.indexOf(separator)
        }
        rem
      }
    }

  /**
   * A transducer that decodes a chunk of bytes to a UTF-8 string.
   */
  val utf8Decode: ZTransducer[Any, Nothing, Chunk[Byte], String] =
    map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8))

  object Process {

    def fold[R, E, I, O, Z](
      init: Z
    )(push: (Z, I) => (O, Z), read: (Z, Option[I]) => (Option[O], Z)): Process[R, E, I, O] =
      ZRef
        .makeManaged(init)
        .map(ref => (i => ref.modify(push(_, i)).flatMap(Pull.emit), l => ref.modify(read(_, l))))

    def map[I, O](f: I => O): Process[Any, Nothing, I, O] =
      ZManaged.succeedNow((i => Pull.emit(f(i)), l => ZIO.succeedNow(l.map(f))))

    def mapM[R, E, I, O](f: I => ZIO[R, E, O]): Process[R, E, I, O] =
      ZManaged.succeedNow(
        (i => Pull(f(i)), l => l.fold[ZIO[R, E, Option[O]]](ZIO.none)(f(_).map(Some(_))))
      )
  }
}
