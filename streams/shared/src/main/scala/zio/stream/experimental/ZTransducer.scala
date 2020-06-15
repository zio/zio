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
      case (push, read) => (ZIO.foreach(_)(push), ZIO.foreach(_)(read))
    })

  def mapError[F](f: E => F): ZTransducer[R, F, I, O] =
    ZTransducer(process.map {
      case (push, read) => (push(_).mapError(_.map(f)), read(_).mapError(f))
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

  def sanitize[R1 <: R, E1 >: E, I1 <: I](f: Chunk[I1] => ZIO[R1, E1, Any]): ZTransducer[R1, E1, I1, O] =
    ZTransducer(process.map {
      case (push, read) => (push, f(_) *> read(Chunk.empty))
    })

  def sanitize_ : ZTransducer[R, E, I, O] =
    sanitize(_ => ZIO.unit)
}

object ZTransducer {

  type Process[-R, +E, -I, +O] = URManaged[R, (Push[R, E, I, O], Read[R, E, Chunk[I], Chunk[O]])]

  /**
   * A transducer that transforms values of type `I` to values of type `O`.
   */
  def apply[R, E, I, O](p: Process[R, E, I, O]): ZTransducer[R, E, I, O] =
    new ZTransducer[R, E, I, O] {
      val process: Process[R, E, I, O] = p
    }

  def chunked[A]: ZTransducer[Any, Nothing, A, Chunk[A]] =
    map(Chunk.single)

  /**
   * A transducer that divides input chunks into chunks with a length bounded by `max`.
   */
  def chunkLimit[A](max: Int): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    if (max <= 0) dieMessage(s"cannot limit chunks for size $max")
    else
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
   * If the last leftover does not have `size` elements, it is dropped.
   */
  def chunkN[A](size: Int): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    chunkN(size, (_: Chunk[A]) => Chunk.empty)

  /**
   * A transducer that divides input chunks into fixed `size` chunks.
   * Leftovers are also resized and the the last leftover is padded with `pad` if it does not have `size` elements.
   */
  def chunkN[A](size: Int, pad: A): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    chunkN(size, (c: Chunk[A]) => Chunk.single(c.padTo(size, pad)))

  /**
   * A transducer that divides input chunks into fixed `size` chunks.
   * Leftovers are also resized and the `pad` function is called on the last leftover if it does not have `size`
   * elements.
   */
  def chunkN[A](size: Int, pad: Chunk[A] => Chunk[Chunk[A]]): ZTransducer[Any, Nothing, Chunk[A], Chunk[Chunk[A]]] =
    if (size <= 0) dieMessage(s"cannot create $size sized chunks")
    else {

      def step(b: ChunkBuilder[Chunk[A]], as: Chunk[A]): Chunk[A] = {
        var rem = as
        while (rem.length >= size) {
          b += rem.take(size)
          rem = rem.drop(size)
        }
        rem
      }

      fold[Chunk[A], Chunk[Chunk[A]], Chunk[A]](Chunk.empty)(
        (state, chunk) => {
          val builder = ChunkBuilder.make[Chunk[A]]()
          val rem     = step(builder, state ++ chunk)
          (builder.result(), rem)
        },
        (state, leftover) => {
          val outer = ChunkBuilder.make[Chunk[Chunk[A]]]()
          var rem     = state
          leftover.foreach { xs =>
            val inner = ChunkBuilder.make[Chunk[A]]()
            rem = step(inner, xs)
            val result = inner.result()
            if (result.nonEmpty) outer += result
          }
          if (rem.nonEmpty) {
            val padded = pad(rem)
            if (padded.nonEmpty) outer += padded
          }
          (outer.result(), Chunk.empty)
        }
      )
    }

  def die(t: Throwable): ZTransducer[Any, Nothing, Any, Nothing] =
    halt(Cause.die(t))

  def dieMessage(s: String): ZTransducer[Any, Nothing, Any, Nothing] =
    die(new IllegalArgumentException(s))

  def halt[E](cause: Cause[E]): ZTransducer[Any, E, Any, Nothing] =
    ZTransducer(Process.halt(cause))

  def fold[I, O, S](
    init: => S
  )(push: (S, I) => (O, S), read: (S, Chunk[I]) => (Chunk[O], S)): ZTransducer[Any, Nothing, I, O] =
    new ZTransducer[Any, Nothing, I, O] {

      val process: Process[Any, Nothing, I, O] = Process.fold(init)(push, read)

      override def chunked: ZTransducer[Any, Nothing, Chunk[I], Chunk[O]] =
        ZTransducer(
          Process.fold(init)(
            (s, i) => {
              val b = ChunkBuilder.make[O]()
              var z = s
              i.foreach { i =>
                val os = push(z, i)
                b += os._1
                z = os._2
              }
              (b.result(), z)
            },
            (s, l) => {
              val (o, z) = read(s, l.flatten)
              (if (o.isEmpty) Chunk.empty else Chunk.single(o), z)
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

      val process: Process[Any, Nothing, A, B] = Process.map(f)

      override def chunked: ZTransducer[Any, Nothing, Chunk[A], Chunk[B]] = ZTransducer(Process.map(_.map(f)))
    }

  def mapM[R, E, I, O](f: I => ZIO[R, E, O]): ZTransducer[R, E, I, O] =
    ZTransducer(Process.mapM(f))

  /**
   * A transducer that divides input strings on the system line separator.
   */
  val newLines: ZTransducer[system.System, Nothing, String, Chunk[String]] =
    ZTransducer(ZIO.accessM[system.System](_.get.lineSeparator).toManaged_.flatMap(separate(_).process))

  /**
   * A transducer that divides an input string on `separator`.
   * The `retain` flag controls whether `separator` is retained in the output.
   */
  def separate(separator: String, retain: Boolean = false): ZTransducer[Any, Nothing, String, Chunk[String]] =
    if (separator.isEmpty) dieMessage("cannot separate with empty separator")
    else {
      val di: Int = if (retain) 0 else separator.length

      def step(builder: ChunkBuilder[String], state: String): String = {
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

      fold("")(
        (state, s: String) => {
          val builder = ChunkBuilder.make[String]()
          val rem     = step(builder, state + s)
          (builder.result(), rem)
        },
        (state, leftover) => {
          val outer = ChunkBuilder.make[Chunk[String]]()
          var rem     = state
          leftover.foreach { xs =>
            val inner = ChunkBuilder.make[String]()
            rem = step(inner, xs)
            val result = inner.result()
            if (result.nonEmpty) outer += result
          }
          if (rem.nonEmpty) outer += Chunk.single(rem)
          (outer.result(), "")
        }
      )
    }

  def take[I](n: Long): ZTransducer[Any, Nothing, I, I] =
    if (n <= 0) dieMessage(s"cannot take $n elements")
    else
      new ZTransducer[Any, Nothing, I, I] {

        val process: Process[Any, Nothing, I, I] =
          ZRef
            .makeManaged(Option.empty[I])
            .flatMap(ref =>
              Process.foldM(n)(
                (s, i) => if (s <= 0) (ref.set(Some(i)) *> Pull.end, 0) else (Pull.emit(i), s - 1),
                (s, l) => (ref.modify(i => (i.fold(l)(_ +: l), None)), s)
              )
            )

        override def chunked: ZTransducer[Any, Nothing, Chunk[I], Chunk[I]] =
          ZTransducer(
            ZRef
              .makeManaged(Chunk.empty: Chunk[I])
              .flatMap(ref =>
                Process.foldM(n)(
                  (s, i) =>
                    if (s <= 0) (ref.update(_ ++ i) *> Pull.end, 0)
                    else if (i.length <= s) (Pull.emit(i), s - i.length)
                    else {
                      val (take, drop) = i.splitAt(s.toInt)
                      (ref.set(drop) *> Pull.emit(take), 0)
                    },
                  (s, l) => (ref.modify(i => (if (i.isEmpty) l else i +: l, Chunk.empty)), s)
                )
              )
          )
      }

  def takeUntil[I](p: I => Boolean): ZTransducer[Any, Nothing, I, I] =
    new ZTransducer[Any, Nothing, I, I] {

      val process: Process[Any, Nothing, I, I] =
        ZRef
          .makeManaged(Option.empty[I])
          .flatMap(ref =>
            Process.foldM(false)(
              (s, i) => if (s) (ref.set(Some(i)) *> Pull.end, s) else (Pull.emit(i), p(i)),
              (s, l) => (ref.modify(i => (i.fold(l)(_ +: l), None)), s)
            )
          )

      override def chunked: ZTransducer[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer(
          ZRef
            .makeManaged(Chunk.empty: Chunk[I])
            .flatMap(ref =>
              Process.foldM(false)(
                (s, i) =>
                  if (s) (ref.update(_ ++ i) *> Pull.end, s)
                  else {
                    val j = i.indexWhere(p)
                    if (j == -1) (Pull.emit(i), false)
                    else {
                      val (take, drop) = i.splitAt(j + 1)
                      (ref.set(drop) *> Pull.emit(take), true)
                    }
                  },
                (s, l) => (ref.modify(i => (if (i.isEmpty) i +: l else l, Chunk.empty)), s)
              )
            )
        )
    }

  def takeWhile[I](p: I => Boolean): ZTransducer[Any, Nothing, I, I] =
    new ZTransducer[Any, Nothing, I, I] {

      val process: Process[Any, Nothing, I, I] =
        Process.foldM(Option.empty[I])(
          (_, i) => if (p(i)) (Pull.emit(i), None) else (Pull.end, Some(i)),
          (s, l) => (ZIO.succeedNow(s.fold(l)(_ +: l)), None)
        )

      override def chunked: ZTransducer[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer(
          Process.foldM(Chunk.empty: Chunk[I])(
            (s, i) =>
              if (s.nonEmpty) (Pull.end, s ++ i)
              else {
                val take = i.takeWhile(p)
                if (take.isEmpty) (Pull.end, i) else (Pull.emit(take), i.drop(take.length))
              },
            (s, l) => (ZIO.succeedNow(if (s.isEmpty) l else s +: l), Chunk.empty)
          )
        )
    }

  /**
   * A transducer that decodes a chunk of bytes to a UTF-8 string.
   */
  val utf8Decode: ZTransducer[Any, Nothing, Chunk[Byte], String] =
    map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8))

  object Process {

    def fold[I, O, S](
      init: => S
    )(push: (S, I) => (O, S), read: (S, Chunk[I]) => (Chunk[O], S)): Process[Any, Nothing, I, O] =
      ZRef
        .makeManaged(init)
        .map(ref => (i => ref.modify(push(_, i)), l => ref.modify(read(_, l))))

    def foldM[R, E, I, O, S](
      init: => S
    )(push: (S, I) => (Pull[R, E, O], S), read: (S, Chunk[I]) => (ZIO[R, E, Chunk[O]], S)): Process[R, E, I, O] =
      ZRef
        .makeManaged(init)
        .map(ref => (i => ref.modify(push(_, i)).flatten, l => ref.modify(read(_, l)).flatten))

    def halt[E](c: Cause[E]): Process[Any, E, Any, Nothing] =
      ZManaged.succeedNow((_ => Pull.halt(c), _ => ZIO.halt(c)))

    def map[I, O](f: I => O): Process[Any, Nothing, I, O] =
      ZManaged.succeedNow((i => Pull.emit(f(i)), l => ZIO.succeedNow(l.map(f))))

    def mapM[R, E, I, O](f: I => ZIO[R, E, O]): Process[R, E, I, O] =
      ZManaged.succeedNow((i => Pull(f(i)), _.mapM(f)))
  }
}
