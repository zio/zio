/*
 * Copyright 2018-2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.nio._
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.collection.mutable.Builder
import scala.reflect.{ClassTag, classTag}

/**
 * A `Chunk[A]` represents a chunk of values of type `A`. Chunks are designed
 * are usually backed by arrays, but expose a purely functional, safe interface
 * to the underlying elements, and they become lazy on operations that would be
 * costly with arrays, such as repeated concatenation.
 *
 * The implementation of balanced concatenation is based on the one for
 * Conc-Trees in "Conc-Trees for Functional and Parallel Programming" by
 * Aleksandar Prokopec and Martin Odersky.
 * [[http://aleksandar-prokopec.com/resources/docs/lcpc-conc-trees.pdf]]
 *
 * NOTE: For performance reasons `Chunk` does not box primitive types. As a
 * result, it is not safe to construct chunks from heterogeneous primitive
 * types.
 */
sealed abstract class Chunk[+A] extends ChunkLike[A] with Serializable { self =>
  def chunkIterator: Chunk.ChunkIterator[A]

  /**
   * Returns the concatenation of this chunk with the specified chunk.
   */
  final def ++[A1 >: A](that: Chunk[A1]): Chunk[A1] =
    (self, that) match {
      case (Chunk.AppendN(start, buffer, bufferUsed, _), that) =>
        val chunk = Chunk.fromArray(buffer.asInstanceOf[Array[A1]]).take(bufferUsed)
        start ++ chunk ++ that
      case (self, Chunk.PrependN(end, buffer, bufferUsed, _)) =>
        val chunk = Chunk.fromArray(buffer.asInstanceOf[Array[A1]]).takeRight(bufferUsed)
        self ++ chunk ++ end
      case (self, Chunk.Empty) => self
      case (Chunk.Empty, that) => that
      case (self, that) =>
        val diff = that.depth - self.depth
        if (math.abs(diff) <= 1) Chunk.Concat(self, that)
        else if (diff < -1) {
          if (self.left.depth >= self.right.depth) {
            val nr = self.right ++ that
            Chunk.Concat(self.left, nr)
          } else {
            val nrr = self.right.right ++ that
            if (nrr.depth == self.depth - 3) {
              val nr = Chunk.Concat(self.right.left, nrr)
              Chunk.Concat(self.left, nr)
            } else {
              val nl = Chunk.Concat(self.left, self.right.left)
              Chunk.Concat(nl, nrr)
            }
          }
        } else {
          if (that.right.depth >= that.left.depth) {
            val nl = self ++ that.left
            Chunk.Concat(nl, that.right)
          } else {
            val nll = self ++ that.left.left
            if (nll.depth == that.depth - 3) {
              val nl = Chunk.Concat(nll, that.left.right)
              Chunk.Concat(nl, that.right)
            } else {
              val nr = Chunk.Concat(that.left.right, that.right)
              Chunk.Concat(nll, nr)
            }
          }
        }
    }

  final def ++[A1 >: A](that: NonEmptyChunk[A1]): NonEmptyChunk[A1] =
    that.prepend(self)

  /**
   * Converts a chunk of ints to a chunk of bits.
   */
  final def asBitsInt(endianness: Chunk.Endianness)(implicit ev: A <:< Int): Chunk[Boolean] =
    Chunk.BitChunkInt(self.asInstanceOf[Chunk[Int]], endianness, 0, length << 5)

  /**
   * Converts a chunk of longs to a chunk of bits.
   */
  final def asBitsLong(endianness: Chunk.Endianness)(implicit ev: A <:< Long): Chunk[Boolean] =
    Chunk.BitChunkLong(self.asInstanceOf[Chunk[Long]], endianness, 0, length << 6)

  /**
   * Converts a chunk of bytes to a chunk of bits.
   */
  final def asBits(implicit ev: A <:< Byte): Chunk[Boolean] =
    Chunk.BitChunk(self.map(ev), 0, length << 3)

  /**
   * Get the element at the specified index.
   */
  def boolean(index: Int)(implicit ev: A <:< Boolean): Boolean =
    ev(apply(index))

  /**
   * Get the element at the specified index.
   */
  def byte(index: Int)(implicit ev: A <:< Byte): Byte =
    ev(apply(index))

  /**
   * Get the element at the specified index.
   */
  def char(index: Int)(implicit ev: A <:< Char): Char =
    ev(apply(index))

  /**
   * Transforms all elements of the chunk for as long as the specified partial
   * function is defined.
   */
  def collectWhile[B](pf: PartialFunction[A, B]): Chunk[B] =
    if (isEmpty) Chunk.empty else self.materialize.collectWhile(pf)

  def collectWhileZIO[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]])(implicit
    trace: Trace
  ): ZIO[R, E, Chunk[B]] =
    if (isEmpty) ZIO.succeedNow(Chunk.empty) else self.materialize.collectWhileZIO(pf)

  /**
   * Returns a filtered, mapped subset of the elements of this chunk based on a
   * .
   */
  def collectZIO[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]])(implicit trace: Trace): ZIO[R, E, Chunk[B]] =
    if (isEmpty) ZIO.succeedNow(Chunk.empty) else self.materialize.collectZIO(pf)

  /**
   * Determines whether this chunk and the specified chunk have the same length
   * and every pair of corresponding elements of this chunk and the specified
   * chunk satisfy the specified predicate.
   */
  final def corresponds[B](that: Chunk[B])(f: (A, B) => Boolean): Boolean =
    if (self.length != that.length) false
    else {
      val leftIterator  = self.chunkIterator
      val rightIterator = that.chunkIterator
      var index         = 0
      var equal         = true
      while (equal && leftIterator.hasNextAt(index) && rightIterator.hasNextAt(index)) {
        val a = leftIterator.nextAt(index)
        val b = rightIterator.nextAt(index)
        index += 1
        equal = f(a, b)
      }
      equal
    }

  /**
   * Deduplicates adjacent elements that are identical.
   */
  def dedupe: Chunk[A] = {
    val builder = ChunkBuilder.make[A]()

    var lastA = null.asInstanceOf[A]

    foreach { a =>
      if (a != lastA) builder += a

      lastA = a
    }

    builder.result()
  }

  /**
   * Get the element at the specified index.
   */
  def double(index: Int)(implicit ev: A <:< Double): Double =
    ev(apply(index))

  /**
   * Drops the first `n` elements of the chunk.
   */
  override def drop(n: Int): Chunk[A] = {
    val len = self.length

    if (n <= 0) self
    else if (n >= len) Chunk.empty
    else
      self match {
        case Chunk.Slice(c, o, l) => Chunk.Slice(c, o + n, l - n)
        case Chunk.Concat(l, r) =>
          if (n > l.length) r.drop(n - l.length)
          else Chunk.Concat(l.drop(n), r)
        case _ => Chunk.Slice(self, n, len - n)
      }
  }

  /**
   * Drops the last `n` elements of the chunk.
   */
  override def dropRight(n: Int): Chunk[A] = {
    val len = self.length

    if (n <= 0) self
    else if (n >= len) Chunk.empty
    else
      self match {
        case Chunk.Slice(c, o, l) => Chunk.Slice(c, o, l - n)
        case Chunk.Concat(l, r) =>
          if (n > r.length) l.dropRight(n - r.length)
          else Chunk.Concat(l, r.dropRight(n))
        case _ => Chunk.Slice(self, 0, len - n)
      }
  }

  /**
   * Drops all elements until the predicate returns true.
   */
  def dropUntil(f: A => Boolean): Chunk[A] = {
    val iterator = self.chunkIterator
    var continue = true
    var i        = 0
    while (continue && iterator.hasNextAt(i)) {
      val a = iterator.nextAt(i)
      if (f(a)) continue = false
      i += 1
    }
    drop(i)
  }

  /**
   * Drops all elements until the effectful predicate returns true.
   */
  def dropUntilZIO[R, E](p: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Chunk[A]] =
    ZIO.suspendSucceed {
      val builder = ChunkBuilder.make[A]()
      builder.sizeHint(self.length)
      var dropping: ZIO[R, E, Boolean] = ZIO.succeedNow(false)
      val iterator                     = self.chunkIterator
      var index                        = 0
      while (iterator.hasNextAt(index)) {
        val a = iterator.nextAt(index)
        index += 1
        dropping = dropping.flatMap {
          case true =>
            builder += a
            ZIO.succeed(true)

          case false =>
            p(a)
        }
      }
      dropping as builder.result()
    }

  /**
   * Drops all elements so long as the predicate returns true.
   */
  override def dropWhile(f: A => Boolean): Chunk[A] = {
    val iterator = self.chunkIterator
    var continue = true
    var i        = 0
    while (continue && iterator.hasNextAt(i)) {
      val a = iterator.nextAt(i)
      if (f(a)) {
        i += 1
      } else {
        continue = false
      }
    }
    drop(i)
  }

  /**
   * Drops all elements so long as the effectful predicate returns true.
   */
  def dropWhileZIO[R, E](p: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Chunk[A]] =
    ZIO.suspendSucceed {
      val length  = self.length
      val builder = ChunkBuilder.make[A]()
      builder.sizeHint(length)
      var dropping: ZIO[R, E, Boolean] = ZIO.succeedNow(true)
      val iterator                     = self.chunkIterator
      var index                        = 0
      while (iterator.hasNextAt(index)) {
        val a = iterator.nextAt(index)
        index += 1
        dropping = dropping.flatMap { d =>
          (if (d) p(a) else ZIO.succeed(false)).map {
            case true =>
              true
            case false =>
              builder += a
              false
          }
        }
      }
      dropping as builder.result()
    }

  override final def equals(that: Any): Boolean =
    that match {
      case that: Seq[_] => self.corresponds(that)(_ == _)
      case _            => false
    }

  /**
   * Determines whether a predicate is satisfied for at least one element of
   * this chunk.
   */
  override final def exists(f: A => Boolean): Boolean = {
    val iterator = self.chunkIterator
    var index    = 0
    var exists   = false
    while (!exists && iterator.hasNextAt(index)) {
      val a = iterator.nextAt(index)
      index += 1
      exists = f(a)
    }
    exists
  }

  /**
   * Returns a filtered subset of this chunk.
   */
  override def filter(f: A => Boolean): Chunk[A] = {
    val iterator = self.chunkIterator
    var index    = 0
    val builder  = ChunkBuilder.make[A]()
    builder.sizeHint(length)
    while (iterator.hasNextAt(index)) {
      val a = iterator.nextAt(index)
      index += 1
      if (f(a)) {
        builder += a
      }
    }
    builder.result()
  }

  /**
   * Filters this chunk by the specified effectful predicate, retaining all
   * elements for which the predicate evaluates to true.
   */
  final def filterZIO[R, E](f: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Chunk[A]] =
    ZIO.suspendSucceed {
      val iterator = self.chunkIterator
      var index    = 0
      val builder  = ChunkBuilder.make[A]()
      builder.sizeHint(length)
      var dest: ZIO[R, E, ChunkBuilder[A]] = ZIO.succeedNow(builder)
      while (iterator.hasNextAt(index)) {
        val a = iterator.nextAt(index)
        index += 1
        dest = dest.zipWith(f(a)) { case (builder, res) =>
          if (res) builder += a else builder
        }
      }
      dest.map(_.result())
    }

  /**
   * Returns the first element that satisfies the predicate.
   */
  override final def find(f: A => Boolean): Option[A] = {
    val iterator          = self.chunkIterator
    var index             = 0
    var result: Option[A] = None
    while (result.isEmpty && iterator.hasNextAt(index)) {
      val a = iterator.nextAt(index)
      index += 1
      if (f(a)) {
        result = Some(a)
      }
    }
    result
  }

  /**
   * Returns the first element that satisfies the effectful predicate.
   */
  final def findZIO[R, E](f: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZIO.suspendSucceed {
      val iterator = self.chunkIterator
      var index    = 0

      def loop(iterator: Chunk.ChunkIterator[A]): ZIO[R, E, Option[A]] =
        if (iterator.hasNextAt(index)) {
          val a = iterator.nextAt(index)
          index += 1

          f(a).flatMap {
            if (_) ZIO.succeedNow(Some(a))
            else loop(iterator)
          }
        } else {
          ZIO.succeedNow(None)
        }

      loop(iterator)
    }

  /**
   * Get the element at the specified index.
   */
  def float(index: Int)(implicit ev: A <:< Float): Float =
    ev(apply(index))

  /**
   * Folds over the elements in this chunk from the left.
   */
  override def foldLeft[S](s0: S)(f: (S, A) => S): S = {
    val iterator = self.chunkIterator
    var index    = 0
    var s        = s0
    while (iterator.hasNextAt(index)) {
      val a = iterator.nextAt(index)
      index += 1
      s = f(s, a)
    }
    s
  }

  /**
   * Effectfully folds over the elements in this chunk from the left.
   */
  final def foldZIO[R, E, S](s: S)(f: (S, A) => ZIO[R, E, S])(implicit trace: Trace): ZIO[R, E, S] =
    foldLeft[ZIO[R, E, S]](ZIO.succeedNow(s))((s, a) => s.flatMap(f(_, a)))

  /**
   * Folds over the elements in this chunk from the right.
   */
  override def foldRight[S](s0: S)(f: (A, S) => S): S = {
    val iterator = self.reverseIterator
    var s        = s0
    while (iterator.hasNext) {
      val a = iterator.next()
      s = f(a, s)
    }
    s
  }

  /**
   * Folds over the elements in this chunk from the left. Stops the fold early
   * when the condition is not fulfilled.
   */
  final def foldWhile[S](s0: S)(pred: S => Boolean)(f: (S, A) => S): S = {
    val iterator = self.chunkIterator
    var index    = 0
    var s        = s0
    var continue = pred(s)
    while (continue && iterator.hasNextAt(index)) {
      val a = iterator.nextAt(index)
      index += 1
      s = f(s, a)
      continue = pred(s)
    }
    s
  }

  final def foldWhileZIO[R, E, S](
    z: S
  )(pred: S => Boolean)(f: (S, A) => ZIO[R, E, S])(implicit trace: Trace): ZIO[R, E, S] = {
    val iterator = self.chunkIterator

    def loop(s: S, iterator: Chunk.ChunkIterator[A], index: Int): ZIO[R, E, S] =
      if (iterator.hasNextAt(index)) {
        if (pred(s)) f(s, iterator.nextAt(index)).flatMap(loop(_, iterator, index + 1))
        else ZIO.succeedNow(s)
      } else {
        ZIO.succeedNow(s)
      }

    loop(z, iterator, 0)
  }

  /**
   * Determines whether a predicate is satisfied for all elements of this chunk.
   */
  override final def forall(f: A => Boolean): Boolean = {
    val iterator = self.chunkIterator
    var index    = 0
    var exists   = true
    while (exists && iterator.hasNextAt(index)) {
      val a = iterator.nextAt(index)
      index += 1
      exists = f(a)
    }
    exists
  }

  override final def hashCode: Int = toArrayOption match {
    case None        => Seq.empty[A].hashCode
    case Some(array) => array.toSeq.hashCode
  }

  /**
   * Returns the first element of this chunk. Note that this method is partial
   * in that it will throw an exception if the chunk is empty. Consider using
   * `headOption` to explicitly handle the possibility that the chunk is empty
   * or iterating over the elements of the chunk in lower level, performance
   * sensitive code unless you really only need the first element of the chunk.
   */
  override def head: A =
    self(0)

  /**
   * Returns the first element of this chunk if it exists.
   */
  override final def headOption: Option[A] =
    if (isEmpty) None else Some(self(0))

  /**
   * Returns the first index for which the given predicate is satisfied after or
   * at some given index.
   */
  override final def indexWhere(f: A => Boolean, from: Int): Int = {
    val iterator = self.chunkIterator
    var i        = 0
    var result   = -1
    while (result < 0 && iterator.hasNextAt(i)) {
      val a = iterator.nextAt(i)
      if (i >= from && f(a)) {
        result = i
      }
      i += 1
    }
    result
  }

  /**
   * Get the element at the specified index.
   */
  def int(index: Int)(implicit ev: A <:< Int): Int =
    ev(apply(index))

  /**
   * Determines if the chunk is empty.
   */
  override final def isEmpty: Boolean =
    length == 0

  /**
   * Returns the last element of this chunk if it exists.
   */
  override final def lastOption: Option[A] =
    if (isEmpty) None else Some(self(self.length - 1))

  /**
   * Get the element at the specified index.
   */
  def long(index: Int)(implicit ev: A <:< Long): Long =
    ev(apply(index))

  /**
   * Statefully maps over the chunk, producing new elements of type `B`.
   */
  final def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): (S1, Chunk[B]) = {
    val iterator = self.chunkIterator
    var index    = 0
    val builder  = ChunkBuilder.make[B]()
    builder.sizeHint(length)
    var s = s1
    while (iterator.hasNextAt(index)) {
      val a = iterator.nextAt(index)
      index += 1
      val tuple = f1(s, a)
      s = tuple._1
      builder += tuple._2
    }
    (s, builder.result())
  }

  /**
   * Statefully and effectfully maps over the elements of this chunk to produce
   * new elements.
   */
  final def mapAccumZIO[R, E, S1, B](
    s1: S1
  )(f1: (S1, A) => ZIO[R, E, (S1, B)])(implicit trace: Trace): ZIO[R, E, (S1, Chunk[B])] =
    ZIO.suspendSucceed {
      val iterator = self.chunkIterator
      var index    = 0
      val builder  = ChunkBuilder.make[B]()
      builder.sizeHint(length)
      var dest: ZIO[R, E, S1] = ZIO.succeedNow(s1)
      while (iterator.hasNextAt(index)) {
        val a = iterator.nextAt(index)
        index += 1
        dest = dest.flatMap { state =>
          f1(state, a).map { case (state2, b) =>
            builder += b
            state2
          }
        }
      }
      dest.map((_, builder.result()))
    }

  /**
   * Effectfully maps the elements of this chunk.
   */
  final def mapZIO[R, E, B](f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, Chunk[B]] =
    ZIO.foreach(self)(f)

  /**
   * Effectfully maps the elements of this chunk purely for the effects.
   */
  final def mapZIODiscard[R, E](f: A => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.foreachDiscard(self)(f)

  /**
   * Effectfully maps the elements of this chunk in parallel.
   */
  final def mapZIOPar[R, E, B](f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, Chunk[B]] =
    ZIO.foreachPar(self)(f)

  /**
   * Effectfully maps the elements of this chunk in parallel purely for the
   * effects.
   */
  final def mapZIOParDiscard[R, E](f: A => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.foreachParDiscard(self)(f)

  /**
   * Materializes a chunk into a chunk backed by an array. This method can
   * improve the performance of bulk operations.
   */
  def materialize[A1 >: A]: Chunk[A1] =
    self.toArrayOption[A1] match {
      case None        => Chunk.Empty
      case Some(array) => Chunk.fromArray(array)
    }

  /**
   * Runs `fn` if a `chunk` is not empty or returns default value
   */
  def nonEmptyOrElse[B](ifEmpty: => B)(fn: NonEmptyChunk[A] => B): B =
    if (isEmpty) ifEmpty else fn(NonEmptyChunk.nonEmpty(self))

  /**
   * Partitions the elements of this chunk into two chunks using the specified
   * function.
   */
  override final def partitionMap[B, C](f: A => Either[B, C]): (Chunk[B], Chunk[C]) = {
    val bs = ChunkBuilder.make[B]()
    val cs = ChunkBuilder.make[C]()
    foreach { a =>
      f(a) match {
        case Left(b)  => bs += b
        case Right(c) => cs += c
      }
    }
    (bs.result(), cs.result())
  }

  /**
   * Get the element at the specified index.
   */
  def short(index: Int)(implicit ev: A <:< Short): Short =
    ev(apply(index))

  /**
   * Splits this chunk into `n` equally sized chunks.
   */
  final def split(n: Int): Chunk[Chunk[A]] = {
    val length    = self.length
    val quotient  = length / n
    val remainder = length % n
    val iterator  = self.chunkIterator
    var index     = 0
    val chunks    = ChunkBuilder.make[Chunk[A]]()
    var i         = 0
    while (i < remainder) {
      val chunk = ChunkBuilder.make[A]()
      var j     = 0
      while (j <= quotient) {
        chunk += iterator.nextAt(index)
        index += 1
        j += 1
      }
      chunks += chunk.result()
      i += 1
    }
    if (quotient > 0) {
      while (i < n) {
        val chunk = ChunkBuilder.make[A]()
        var j     = 0
        while (j < quotient) {
          chunk += iterator.nextAt(index)
          index += 1
          j += 1
        }
        chunks += chunk.result()
        i += 1
      }
    }
    chunks.result()
  }

  /**
   * Returns two splits of this chunk at the specified index.
   */
  override final def splitAt(n: Int): (Chunk[A], Chunk[A]) =
    (take(n), drop(n))

  /**
   * Splits this chunk on the first element that matches this predicate.
   */
  final def splitWhere(f: A => Boolean): (Chunk[A], Chunk[A]) = {
    val iterator = self.chunkIterator
    var continue = true
    var i        = 0
    while (continue && iterator.hasNextAt(i)) {
      val a = iterator.nextAt(i)
      if (f(a)) {
        continue = false
      } else {
        i += 1
      }
    }
    splitAt(i)
  }

  /**
   * Takes the first `n` elements of the chunk.
   */
  override def take(n: Int): Chunk[A] =
    if (n <= 0) Chunk.Empty
    else if (n >= length) this
    else
      self match {
        case Chunk.Slice(c, o, _) => Chunk.Slice(c, o, n)
        case Chunk.Concat(l, r) =>
          if (n > l.length) Chunk.Concat(l, r.take(n - l.length))
          else l.take(n)
        case _ => Chunk.Slice(self, 0, n)
      }

  /**
   * Takes the last `n` elements of the chunk.
   */
  override def takeRight(n: Int): Chunk[A] =
    if (n <= 0) Chunk.Empty
    else if (n >= length) this
    else
      self match {
        case Chunk.Slice(c, o, l) => Chunk.Slice(c, o + l - n, n)
        case Chunk.Concat(l, r) =>
          if (n > r.length) Chunk.Concat(l.takeRight(n - r.length), r)
          else r.takeRight(n)
        case _ => Chunk.Slice(self, length - n, n)
      }

  /**
   * Takes all elements so long as the predicate returns true.
   */
  override def takeWhile(f: A => Boolean): Chunk[A] = {
    val iterator = self.chunkIterator
    var continue = true
    var i        = 0
    while (continue && iterator.hasNextAt(i)) {
      val a = iterator.nextAt(i)
      if (!f(a)) {
        continue = false
      } else {
        i += 1
      }
    }
    take(i)
  }

  /**
   * Takes all elements so long as the effectual predicate returns true.
   */
  def takeWhileZIO[R, E](p: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Chunk[A]] =
    ZIO.suspendSucceed {
      val length  = self.length
      val builder = ChunkBuilder.make[A]()
      builder.sizeHint(length)
      var taking: ZIO[R, E, Boolean] = ZIO.succeedNow(true)
      val iterator                   = self.chunkIterator
      var index                      = 0
      while (iterator.hasNextAt(index)) {
        val a = iterator.nextAt(index)
        index += 1
        taking = taking.flatMap { b =>
          (if (b) p(a) else ZIO.succeed(false)).map {
            case true =>
              builder += a
              true
            case false =>
              false
          }
        }
      }
      taking as builder.result()
    }

  /**
   * Converts the chunk into an array.
   */
  override def toArray[A1 >: A: ClassTag]: Array[A1] = {
    val dest = Array.ofDim[A1](self.length)

    self.toArray(0, dest)

    dest
  }

  /**
   * Renders this chunk of bits as a binary string.
   */
  final def toBinaryString(implicit ev: A <:< Boolean): String = {
    val bits    = self.asInstanceOf[Chunk[Boolean]]
    val builder = new scala.collection.mutable.StringBuilder
    bits.foreach(bit => if (bit) builder.append("1") else builder.append("0"))
    builder.toString
  }

  override final def toList: List[A] = {
    val listBuilder = List.newBuilder[A]
    fromBuilder(listBuilder)
  }

  override final def toVector: Vector[A] = {
    val vectorBuilder = Vector.newBuilder[A]
    fromBuilder(vectorBuilder)
  }

  override final def toString: String =
    toArrayOption.fold("Chunk()")(_.mkString("Chunk(", ",", ")"))

  /**
   * Zips this chunk with the specified chunk to produce a new chunk with pairs
   * of elements from each chunk. The returned chunk will have the length of the
   * shorter chunk.
   */
  final def zip[B](that: Chunk[B])(implicit zippable: Zippable[A, B]): Chunk[zippable.Out] =
    zipWith(that)(zippable.zip(_, _))

  /**
   * Zips this chunk with the specified chunk to produce a new chunk with pairs
   * of elements from each chunk, filling in missing values from the shorter
   * chunk with `None`. The returned chunk will have the length of the longer
   * chunk.
   */
  final def zipAll[B](that: Chunk[B]): Chunk[(Option[A], Option[B])] =
    zipAllWith(that)(a => (Some(a), None), b => (None, Some(b)))((a, b) => (Some(a), Some(b)))

  /**
   * Zips with chunk with the specified chunk to produce a new chunk with pairs
   * of elements from each chunk combined using the specified function `both`.
   * If one chunk is shorter than the other uses the specified function `left`
   * or `right` to map the element that does exist to the result type.
   */
  final def zipAllWith[B, C](
    that: Chunk[B]
  )(left: A => C, right: B => C)(both: (A, B) => C): Chunk[C] = {
    val length = self.length.max(that.length)
    if (length == 0) Chunk.empty
    else {
      val leftIterator  = self.chunkIterator
      val rightIterator = that.chunkIterator
      var index         = 0
      val builder       = ChunkBuilder.make[C]()
      builder.sizeHint(length)
      while (leftIterator.hasNextAt(index) && rightIterator.hasNextAt(index)) {
        val a = leftIterator.nextAt(index)
        val b = rightIterator.nextAt(index)
        index += 1
        val c = both(a, b)
        builder += c
      }
      while (leftIterator.hasNextAt(index)) {
        val a = leftIterator.nextAt(index)
        index += 1
        val c = left(a)
        builder += c
      }
      while (rightIterator.hasNextAt(index)) {
        val b = rightIterator.nextAt(index)
        index += 1
        val c = right(b)
        builder += c
      }
      builder.result()
    }
  }

  /**
   * Zips this chunk with the specified chunk using the specified combiner.
   */
  final def zipWith[B, C](that: Chunk[B])(f: (A, B) => C): Chunk[C] = {
    val length = self.length.min(that.length)
    if (length == 0) Chunk.empty
    else {
      val leftIterator  = self.chunkIterator
      val rightIterator = that.chunkIterator
      var index         = 0
      val builder       = ChunkBuilder.make[C]()
      builder.sizeHint(length)
      while (leftIterator.hasNextAt(index) && rightIterator.hasNextAt(index)) {
        val a = leftIterator.nextAt(index)
        val b = rightIterator.nextAt(index)
        index += 1
        val c = f(a, b)
        builder += c
      }
      builder.result()
    }
  }

  /**
   * Zips this chunk with the index of every element, starting from the initial
   * index value.
   */
  final def zipWithIndexFrom(indexOffset: Int): Chunk[(A, Int)] = {
    val iterator = self.chunkIterator
    var index    = 0
    val builder  = ChunkBuilder.make[(A, Int)]()
    builder.sizeHint(length)
    var i = indexOffset
    while (iterator.hasNextAt(index)) {
      val a = iterator.nextAt(index)
      index += 1
      builder += ((a, i))
      i += 1
    }
    builder.result()
  }

  //noinspection AccessorLikeMethodIsUnit
  protected[zio] def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit =
    if (isEmpty) () else materialize.toArray(n, dest)

  /**
   * Appends an element to the chunk.
   */
  protected def append[A1 >: A](a1: A1): Chunk[A1] = {
    val buffer = Array.ofDim[AnyRef](Chunk.BufferSize)
    buffer(0) = a1.asInstanceOf[AnyRef]
    Chunk.AppendN(self, buffer, 1, new AtomicInteger(1))
  }

  /**
   * Returns a filtered, mapped subset of the elements of this chunk.
   */
  protected def collectChunk[B](pf: PartialFunction[A, B]): Chunk[B] =
    if (isEmpty) Chunk.empty else self.materialize.collectChunk(pf)

  protected def depth: Int =
    0

  protected def left: Chunk[A] =
    Chunk.empty

  /**
   * Returns a chunk with the elements mapped by the specified function.
   */
  protected def mapChunk[B](f: A => B): Chunk[B] = {
    val iterator = self.chunkIterator
    var index    = 0
    val builder  = ChunkBuilder.make[B]()
    builder.sizeHint(length)
    while (iterator.hasNextAt(index)) {
      val a = iterator.nextAt(index)
      index += 1
      val b = f(a)
      builder += b
    }
    builder.result()
  }

  /**
   * Prepends an element to the chunk.
   */
  protected def prepend[A1 >: A](a1: A1): Chunk[A1] = {
    val buffer = Array.ofDim[AnyRef](Chunk.BufferSize)
    buffer(Chunk.BufferSize - 1) = a1.asInstanceOf[AnyRef]
    Chunk.PrependN(self, buffer, 1, new AtomicInteger(1))
  }

  protected def right: Chunk[A] =
    Chunk.empty

  /**
   * Updates an element at the specified index of the chunk.
   */
  protected def update[A1 >: A](index: Int, a1: A1): Chunk[A1] =
    if (index < 0 || index >= length) throw new IndexOutOfBoundsException(s"Update chunk access to $index")
    else {
      val bufferIndices = Array.ofDim[Int](Chunk.UpdateBufferSize)
      val bufferValues  = Array.ofDim[AnyRef](Chunk.UpdateBufferSize)
      bufferIndices(0) = index
      bufferValues(0) = a1.asInstanceOf[AnyRef]
      Chunk.Update(self, bufferIndices, bufferValues, 1, new AtomicInteger(1))
    }

  private final def fromBuilder[A1 >: A, B[_]](builder: Builder[A1, B[A1]]): B[A1] = {
    val c   = materialize
    var i   = 0
    val len = c.length
    builder.sizeHint(len)
    while (i < len) {
      builder += c(i)
      i += 1
    }
    builder.result()
  }

  /**
   * A helper function that converts the chunk into an array if it is not empty.
   */
  private final def toArrayOption[A1 >: A]: Option[Array[A1]] =
    self match {
      case Chunk.Empty => None
      case chunk       => Some(chunk.toArray(Chunk.classTagOf(self)))
    }
}

object Chunk extends ChunkFactory with ChunkPlatformSpecific {

  sealed trait Endianness
  object Endianness {
    case object BigEndian    extends Endianness
    case object LittleEndian extends Endianness
  }

  /**
   * Returns a chunk from a number of values.
   */
  override def apply[A](as: A*): Chunk[A] =
    fromIterable(as)

  /**
   * Returns the empty chunk.
   */
  override def empty[A]: Chunk[A] =
    Empty

  /**
   * Returns a chunk backed by an array.
   *
   * WARNING: The array must not be mutated after creating the chunk.
   */
  def fromArray[A](array: Array[A]): Chunk[A] =
    (if (array.isEmpty) Empty
     else
       (array.asInstanceOf[AnyRef]: @unchecked) match {
         case x: Array[AnyRef]  => AnyRefArray(x, 0, array.length)
         case x: Array[Int]     => IntArray(x, 0, array.length)
         case x: Array[Double]  => DoubleArray(x, 0, array.length)
         case x: Array[Long]    => LongArray(x, 0, array.length)
         case x: Array[Float]   => FloatArray(x, 0, array.length)
         case x: Array[Char]    => CharArray(x, 0, array.length)
         case x: Array[Byte]    => ByteArray(x, 0, array.length)
         case x: Array[Short]   => ShortArray(x, 0, array.length)
         case x: Array[Boolean] => BooleanArray(x, 0, array.length)
       }).asInstanceOf[Chunk[A]]

  /**
   * Returns a chunk backed by a [[java.nio.ByteBuffer]].
   */
  def fromByteBuffer(buffer: ByteBuffer): Chunk[Byte] = {
    val dest = Array.ofDim[Byte](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.CharBuffer]].
   */
  def fromCharBuffer(buffer: CharBuffer): Chunk[Char] = {
    val dest = Array.ofDim[Char](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.DoubleBuffer]].
   */
  def fromDoubleBuffer(buffer: DoubleBuffer): Chunk[Double] = {
    val dest = Array.ofDim[Double](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.FloatBuffer]].
   */
  def fromFloatBuffer(buffer: FloatBuffer): Chunk[Float] = {
    val dest = Array.ofDim[Float](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.IntBuffer]].
   */
  def fromIntBuffer(buffer: IntBuffer): Chunk[Int] = {
    val dest = Array.ofDim[Int](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.LongBuffer]].
   */
  def fromLongBuffer(buffer: LongBuffer): Chunk[Long] = {
    val dest = Array.ofDim[Long](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.ShortBuffer]].
   */
  def fromShortBuffer(buffer: ShortBuffer): Chunk[Short] = {
    val dest = Array.ofDim[Short](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by an iterable.
   */
  def fromIterable[A](it: Iterable[A]): Chunk[A] =
    it match {
      case chunk: Chunk[A]              => chunk
      case iterable if iterable.isEmpty => Empty
      case vector: Vector[A]            => VectorChunk(vector)
      case iterable =>
        val builder = ChunkBuilder.make[A]()
        builder.sizeHint(iterable)
        builder ++= iterable
        builder.result()
    }

  /**
   * Creates a chunk from an iterator.
   */
  def fromIterator[A](iterator: Iterator[A]): Chunk[A] = {
    val builder = ChunkBuilder.make[A]()
    builder ++= iterator
    builder.result()
  }

  /**
   * Returns a chunk backed by a Java iterable.
   */
  def fromJavaIterable[A](iterable: java.lang.Iterable[A]): Chunk[A] =
    fromJavaIterator(iterable.iterator)

  /**
   * Creates a chunk from a Java iterator.
   */
  def fromJavaIterator[A](iterator: java.util.Iterator[A]): Chunk[A] = {
    val builder = ChunkBuilder.make[A]()
    while (iterator.hasNext()) {
      val a = iterator.next()
      builder += a
    }
    builder.result()
  }

  override def fill[A](n: Int)(elem: => A): Chunk[A] =
    if (n <= 0) Chunk.empty
    else {
      val builder = ChunkBuilder.make[A]()
      builder.sizeHint(n)

      var i = 0
      while (i < n) {
        builder += elem
        i += 1
      }
      builder.result()
    }

  def newBuilder[A]: ChunkBuilder[A] =
    ChunkBuilder.make()

  /**
   * Returns a singleton chunk, eagerly evaluated.
   */
  def single[A](a: A): Chunk[A] =
    Singleton(a)

  /**
   * Alias for [[Chunk.single]].
   */
  def succeed[A](a: A): Chunk[A] =
    single(a)

  /**
   * Constructs a `Chunk` by repeatedly applying the function `f` as long as it
   * returns `Some`.
   */
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Chunk[A] = {

    @tailrec
    def go(s: S, builder: ChunkBuilder[A]): Chunk[A] =
      f(s) match {
        case Some((a, s)) => go(s, builder += a)
        case None         => builder.result()
      }

    go(s, ChunkBuilder.make[A]())
  }

  /**
   * Constructs a `Chunk` by repeatedly applying the effectual function `f` as
   * long as it returns `Some`.
   */
  def unfoldZIO[R, E, A, S](
    s: S
  )(f: S => ZIO[R, E, Option[(A, S)]])(implicit trace: Trace): ZIO[R, E, Chunk[A]] =
    ZIO.suspendSucceed {

      def go(s: S, builder: ChunkBuilder[A]): ZIO[R, E, Chunk[A]] =
        f(s).flatMap {
          case Some((a, s)) => go(s, builder += a)
          case None         => ZIO.succeedNow(builder.result())
        }

      go(s, ChunkBuilder.make[A]())
    }

  /**
   * The unit chunk
   */
  val unit: Chunk[Unit] = single(())

  /**
   * Returns the `ClassTag` for the element type of the chunk.
   */
  private[zio] def classTagOf[A](chunk: Chunk[A]): ClassTag[A] =
    chunk match {
      case x: AppendN[_]     => x.classTag.asInstanceOf[ClassTag[A]]
      case x: Arr[_]         => x.classTag.asInstanceOf[ClassTag[A]]
      case x: Concat[_]      => x.classTag.asInstanceOf[ClassTag[A]]
      case Empty             => classTag[java.lang.Object].asInstanceOf[ClassTag[A]]
      case x: PrependN[_]    => x.classTag.asInstanceOf[ClassTag[A]]
      case x: Singleton[_]   => x.classTag.asInstanceOf[ClassTag[A]]
      case x: Slice[_]       => x.classTag.asInstanceOf[ClassTag[A]]
      case x: Update[_]      => x.classTag.asInstanceOf[ClassTag[A]]
      case x: VectorChunk[_] => x.classTag.asInstanceOf[ClassTag[A]]
      case _: BitChunk       => ClassTag.Boolean.asInstanceOf[ClassTag[A]]
      case _: BitChunkInt    => ClassTag.Int.asInstanceOf[ClassTag[A]]
      case _: BitChunkLong   => ClassTag.Long.asInstanceOf[ClassTag[A]]
    }

  /**
   * The maximum number of elements in the buffer for fast append.
   */
  private val BufferSize: Int =
    64

  /**
   * The maximum number of elements in the buffer for fast update.
   */
  private val UpdateBufferSize: Int =
    256

  private final case class AppendN[A](start: Chunk[A], buffer: Array[AnyRef], bufferUsed: Int, chain: AtomicInteger)
      extends Chunk[A] { self =>

    def chunkIterator: ChunkIterator[A] =
      start.chunkIterator ++ ChunkIterator.fromArray(buffer.asInstanceOf[Array[A]]).sliceIterator(0, bufferUsed)

    implicit val classTag: ClassTag[A] = classTagOf(start)

    val length: Int =
      start.length + bufferUsed

    override protected def append[A1 >: A](a1: A1): Chunk[A1] =
      if (bufferUsed < buffer.length && chain.compareAndSet(bufferUsed, bufferUsed + 1)) {
        buffer(bufferUsed) = a1.asInstanceOf[AnyRef]
        AppendN(start, buffer, bufferUsed + 1, chain)
      } else {
        val buffer = Array.ofDim[AnyRef](BufferSize)
        buffer(0) = a1.asInstanceOf[AnyRef]
        val chunk = Chunk.fromArray(self.buffer.asInstanceOf[Array[A1]]).take(bufferUsed)
        AppendN(start ++ chunk, buffer, 1, new AtomicInteger(1))
      }

    def apply(n: Int): A =
      if (n < 0 || n >= length) throw new IndexOutOfBoundsException(s"Append chunk access to $n")
      else if (n < start.length) start(n)
      else buffer(n - start.length).asInstanceOf[A]

    override protected[zio] def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = {
      start.toArray(n, dest)
      val _ = buffer.asInstanceOf[Array[A]].copyToArray(dest, n + start.length, bufferUsed)
    }
  }

  private final case class PrependN[A](end: Chunk[A], buffer: Array[AnyRef], bufferUsed: Int, chain: AtomicInteger)
      extends Chunk[A] { self =>

    def chunkIterator: ChunkIterator[A] =
      ChunkIterator
        .fromArray(buffer.asInstanceOf[Array[A]])
        .sliceIterator(BufferSize - bufferUsed, bufferUsed) ++ end.chunkIterator

    implicit val classTag: ClassTag[A] = classTagOf(end)

    val length: Int =
      end.length + bufferUsed

    override protected def prepend[A1 >: A](a1: A1): Chunk[A1] =
      if (bufferUsed < buffer.length && chain.compareAndSet(bufferUsed, bufferUsed + 1)) {
        buffer(BufferSize - bufferUsed - 1) = a1.asInstanceOf[AnyRef]
        PrependN(end, buffer, bufferUsed + 1, chain)
      } else {
        val buffer = Array.ofDim[AnyRef](BufferSize)
        buffer(BufferSize - 1) = a1.asInstanceOf[AnyRef]
        val chunk = Chunk.fromArray(self.buffer.asInstanceOf[Array[A1]]).takeRight(bufferUsed)
        PrependN(chunk ++ end, buffer, 1, new AtomicInteger(1))
      }

    def apply(n: Int): A =
      if (n < 0 || n >= length) throw new IndexOutOfBoundsException(s"Prepend chunk access to $n")
      else if (n < bufferUsed) buffer(BufferSize - bufferUsed + n).asInstanceOf[A]
      else end(n - bufferUsed)

    override protected[zio] def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = {
      val length = math.min(bufferUsed, math.max(dest.length - n, 0))
      Array.copy(buffer, BufferSize - bufferUsed, dest, n, length)
      val _ = end.toArray(n + length, dest)
    }
  }

  private final case class Update[A](
    chunk: Chunk[A],
    bufferIndices: Array[Int],
    bufferValues: Array[AnyRef],
    used: Int,
    chain: AtomicInteger
  ) extends Chunk[A] { self =>

    def chunkIterator: ChunkIterator[A] =
      ChunkIterator.fromArray(self.toArray)

    implicit val classTag: ClassTag[A] = Chunk.classTagOf(chunk)

    val length: Int =
      chunk.length

    def apply(i: Int): A = {
      var j = used - 1
      var a = null.asInstanceOf[A]
      while (j >= 0) {
        if (bufferIndices(j) == i) {
          a = bufferValues(j).asInstanceOf[A]
          j = -1
        } else {
          j -= 1
        }
      }
      if (a != null) a else chunk(i)
    }

    override protected def update[A1 >: A](i: Int, a: A1): Chunk[A1] =
      if (i < 0 || i >= length) throw new IndexOutOfBoundsException(s"Update chunk access to $i")
      else if (used < UpdateBufferSize && chain.compareAndSet(used, used + 1)) {
        bufferIndices(used) = i
        bufferValues(used) = a.asInstanceOf[AnyRef]
        Update(chunk, bufferIndices, bufferValues, used + 1, chain)
      } else {
        val bufferIndices = Array.ofDim[Int](UpdateBufferSize)
        val bufferValues  = Array.ofDim[AnyRef](UpdateBufferSize)
        bufferIndices(0) = i
        bufferValues(0) = a.asInstanceOf[AnyRef]
        val array = self.asInstanceOf[Chunk[AnyRef]].toArray
        Update(Chunk.fromArray(array.asInstanceOf[Array[A1]]), bufferIndices, bufferValues, 1, new AtomicInteger(1))
      }

    override protected[zio] def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = {
      chunk.toArray(n, dest)
      var i = 0
      while (i < used) {
        val index = bufferIndices(i)
        val value = self.bufferValues(i)
        dest(index) = value.asInstanceOf[A1]
        i += 1
      }
    }
  }

  private[zio] sealed abstract class Arr[A] extends Chunk[A] with Serializable { self =>

    val array: Array[A]

    implicit val classTag: ClassTag[A] =
      ClassTag(array.getClass.getComponentType)

    override def collectZIO[R, E, B](
      pf: PartialFunction[A, ZIO[R, E, B]]
    )(implicit trace: Trace): ZIO[R, E, Chunk[B]] = ZIO.suspendSucceed {
      val len     = array.length
      val builder = ChunkBuilder.make[B]()
      builder.sizeHint(len)
      val orElse                           = (_: A) => ZIO.succeedNow(null.asInstanceOf[B])
      var dest: ZIO[R, E, ChunkBuilder[B]] = ZIO.succeedNow(builder)

      var i = 0
      while (i < len) {
        // `zipWith` is lazy in the RHS, so we need to capture to evaluate the
        // `pf.applyOrElse` strictly to make sure we use the right value of `i`.
        val rhs = pf.applyOrElse(array(i), orElse)

        dest = dest.zipWith(rhs)((builder, b) => if (b != null) (builder += b) else builder)

        i += 1
      }

      dest.map(_.result())
    }

    override def collectWhile[B](pf: PartialFunction[A, B]): Chunk[B] = {
      val self    = array
      val len     = self.length
      val builder = ChunkBuilder.make[B]()
      builder.sizeHint(len)

      var i    = 0
      var done = false
      while (!done && i < len) {
        val b = pf.applyOrElse(self(i), (_: A) => null.asInstanceOf[B])

        if (b != null) {
          builder += b
        } else {
          done = true
        }

        i += 1
      }

      builder.result()
    }

    override def collectWhileZIO[R, E, B](
      pf: PartialFunction[A, ZIO[R, E, B]]
    )(implicit trace: Trace): ZIO[R, E, Chunk[B]] =
      ZIO.suspendSucceed {
        val len     = self.length
        val builder = ChunkBuilder.make[B]()
        builder.sizeHint(len)
        var dest: ZIO[R, E, ChunkBuilder[B]] = ZIO.succeedNow(builder)

        var i    = 0
        var done = false
        val orElse = (_: A) => {
          done = true
          ZIO.succeedNow(null.asInstanceOf[B])
        }

        while (!done && i < len) {
          val j = i
          // `zipWith` is lazy in the RHS, and we rely on the side-effects of `orElse` here.
          val rhs = pf.applyOrElse(self(j), orElse)
          dest = dest.zipWith(rhs) { case (builder, b) =>
            if (b != null) (builder += b) else builder
          }
          i += 1
        }

        dest.map(_.result())
      }

    override def dropWhile(f: A => Boolean): Chunk[A] = {
      val self = array
      val len  = self.length

      var i = 0
      while (i < len && f(self(i))) {
        i += 1
      }

      drop(i)
    }

    override def filter(f: A => Boolean): Chunk[A] = {
      val len     = self.length
      val builder = ChunkBuilder.make[A]()
      builder.sizeHint(len)

      var i = 0
      while (i < len) {
        val elem = self(i)

        if (f(elem)) {
          builder += elem
        }

        i += 1
      }

      builder.result()
    }

    override def foldLeft[S](s0: S)(f: (S, A) => S): S = {
      val len = self.length
      var s   = s0

      var i = 0
      while (i < len) {
        s = f(s, self(i))
        i += 1
      }

      s
    }

    override def foldRight[S](s0: S)(f: (A, S) => S): S = {
      val self = array
      val len  = self.length
      var s    = s0

      var i = len - 1
      while (i >= 0) {
        s = f(self(i), s)
        i -= 1
      }

      s
    }

    override def foreach[B](f: A => B): Unit =
      array.foreach(f)

    override def iterator: Iterator[A] =
      array.iterator

    override def materialize[A1 >: A]: Chunk[A1] =
      self

    /**
     * Takes all elements so long as the predicate returns true.
     */
    override def takeWhile(f: A => Boolean): Chunk[A] = {
      val self = array
      val len  = length

      var i = 0
      while (i < len && f(self(i))) {
        i += 1
      }

      take(i)
    }

    override protected[zio] def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit =
      Array.copy(array, 0, dest, n, length)

    override protected def collectChunk[B](pf: PartialFunction[A, B]): Chunk[B] = {
      val len     = self.length
      val builder = ChunkBuilder.make[B]()
      builder.sizeHint(len)

      var i = 0
      while (i < len) {
        val b = pf.applyOrElse(self(i), (_: A) => null.asInstanceOf[B])
        if (b != null) {
          builder += b
        }

        i += 1
      }
      builder.result()
    }

    override protected def mapChunk[B](f: A => B): Chunk[B] = {
      val len     = self.length
      val builder = ChunkBuilder.make[B]()
      builder.sizeHint(len)

      var i = 0
      while (i < len) {
        builder += f(self(i))
        i += 1
      }

      builder.result()
    }
  }

  private final case class Concat[A](override protected val left: Chunk[A], override protected val right: Chunk[A])
      extends Chunk[A] {
    self =>

    def chunkIterator: ChunkIterator[A] =
      left.chunkIterator ++ right.chunkIterator

    implicit val classTag: ClassTag[A] =
      left match {
        case Empty => classTagOf(right)
        case _     => classTagOf(left)
      }

    override val depth: Int =
      1 + math.max(left.depth, right.depth)

    override val length: Int =
      left.length + right.length

    override def apply(n: Int): A =
      if (n < left.length) left(n) else right(n - left.length)

    override def foreach[B](f: A => B): Unit = {
      left.foreach(f)
      right.foreach(f)
    }

    override def iterator: Iterator[A] =
      left.iterator ++ right.iterator

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = {
      left.toArray(n, dest)
      right.toArray(n + left.length, dest)
    }
  }

  private final case class Singleton[A](a: A) extends Chunk[A] with ChunkIterator[A] { self =>

    implicit val classTag: ClassTag[A] =
      Tags.fromValue(a)

    override val length =
      1

    override def apply(n: Int): A =
      if (n == 0) a
      else throw new ArrayIndexOutOfBoundsException(s"Singleton chunk access to $n")

    override def foreach[B](f: A => B): Unit = {
      val _ = f(a)
    }

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit =
      dest(n) = a

    def chunkIterator: ChunkIterator[A] =
      self

    def hasNextAt(index: Int): Boolean =
      index == 0

    def nextAt(index: Int): A =
      if (index == 0) a
      else throw new ArrayIndexOutOfBoundsException(s"Singleton chunk access to $index")

    def sliceIterator(offset: Int, length: Int): ChunkIterator[A] =
      if (offset <= 0 && length >= 1) self
      else ChunkIterator.empty
  }

  private final case class Slice[A](private val chunk: Chunk[A], offset: Int, l: Int) extends Chunk[A] {

    def chunkIterator: ChunkIterator[A] =
      chunk.chunkIterator.sliceIterator(offset, l)

    implicit val classTag: ClassTag[A] =
      classTagOf(chunk)

    override val length: Int =
      l

    override def apply(n: Int): A =
      chunk.apply(offset + n)

    override def foreach[B](f: A => B): Unit = {
      var i = 0
      while (i < length) {
        f(apply(i))
        i += 1
      }
    }

    override def iterator: Iterator[A] =
      chunk.iterator.slice(offset, offset + l)

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = {
      var i = 0
      var j = n

      while (i < length) {
        dest(j) = apply(i)

        i += 1
        j += 1
      }
    }
  }

  private final case class VectorChunk[A](private val vector: Vector[A]) extends Chunk[A] {

    def chunkIterator: ChunkIterator[A] =
      ChunkIterator.fromVector(vector)

    implicit val classTag: ClassTag[A] =
      Tags.fromValue(vector(0))

    override val length: Int =
      vector.length

    override def apply(n: Int): A =
      vector(n)

    override def foreach[B](f: A => B): Unit =
      vector.foreach(f)

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = {
      val _ = vector.copyToArray(dest, n, length)
    }
  }

  private[zio] final case class BitChunk(bytes: Chunk[Byte], minBitIndex: Int, maxBitIndex: Int)
      extends Chunk[Boolean]
      with ChunkIterator[Boolean] {
    self =>

    override val length: Int =
      maxBitIndex - minBitIndex

    override def apply(n: Int): Boolean =
      (bytes(n >> 3) & (1 << (7 - (n & 7)))) != 0

    override def drop(n: Int): BitChunk = {
      val index  = (minBitIndex + n) min maxBitIndex
      val toDrop = index >> 3
      val min    = index & 7
      val max    = maxBitIndex - index + min
      BitChunk(bytes.drop(toDrop), min, max)
    }

    override def foreach[A](f: Boolean => A): Unit = {
      val minByteIndex    = (minBitIndex + 7) >> 3
      val maxByteIndex    = maxBitIndex >> 3
      val minFullBitIndex = (minByteIndex << 3) min maxBitIndex
      val maxFullBitIndex = (maxByteIndex << 3) max minFullBitIndex
      var i               = minBitIndex
      while (i < minFullBitIndex) {
        f(apply(i))
        i += 1
      }
      i = minByteIndex
      while (i < maxByteIndex) {
        val byte = bytes(i)
        f((byte & 128) != 0)
        f((byte & 64) != 0)
        f((byte & 32) != 0)
        f((byte & 16) != 0)
        f((byte & 8) != 0)
        f((byte & 4) != 0)
        f((byte & 2) != 0)
        f((byte & 1) != 0)
        i += 1
      }
      i = maxFullBitIndex
      while (i < maxBitIndex) {
        f(apply(i))
        i += 1
      }
    }

    override def take(n: Int): BitChunk = {
      val index  = (minBitIndex + n) min maxBitIndex
      val toTake = (index + 7) >> 3
      BitChunk(bytes.take(toTake), minBitIndex, index)
    }

    override def toArray[A1 >: Boolean](n: Int, dest: Array[A1]): Unit = {
      var i = n
      while (i < length) {
        dest(i + n) = apply(i)
        i += 1
      }
    }

    def chunkIterator: ChunkIterator[Boolean] =
      self

    def hasNextAt(index: Int): Boolean =
      index < length

    def nextAt(index: Int): Boolean =
      self(index)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[Boolean] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else BitChunk(bytes, self.minBitIndex + offset, self.minBitIndex + offset + length min self.maxBitIndex)
  }

  private[zio] final case class BitChunkInt(
    chunk: Chunk[Int],
    endianness: Endianness,
    minBitIndex: Int,
    maxBitIndex: Int
  ) extends Chunk[Boolean] {
    self =>

    override val length: Int =
      maxBitIndex - minBitIndex

    private def elementAt(n: Int): Int =
      if(endianness == Chunk.Endianness.BigEndian) chunk(n) else Integer.reverse(chunk(n))

    override def apply(n: Int): Boolean =
      (elementAt(n >> 5) & (1 << (31 - (n & 31)))) != 0

    override def drop(n: Int): BitChunkInt = {
      val index  = (minBitIndex + n) min maxBitIndex
      val toDrop = index >> 5
      val min    = index & 31
      val max    = maxBitIndex - index + min
      BitChunkInt(chunk.drop(toDrop), endianness, min, max)
    }

    override def foreach[A](f: Boolean => A): Unit = {
      val minIntIndex     = (minBitIndex + 31) >> 5
      val maxIntIndex     = maxBitIndex >> 5
      val minFullBitIndex = (minIntIndex << 5) min maxBitIndex
      val maxFullBitIndex = (maxIntIndex << 5) max minFullBitIndex
      var i               = minBitIndex
      while (i < minFullBitIndex) {
        f(apply(i))
        i += 1
      }
      i = minIntIndex
      while (i < maxIntIndex) {
        val int = elementAt(i)
        f((int & 0x80000000) != 0)
        f((int & 0x40000000) != 0)
        f((int & 0x20000000) != 0)
        f((int & 0x10000000) != 0)
        f((int & 0x8000000) != 0)
        f((int & 0x4000000) != 0)
        f((int & 0x2000000) != 0)
        f((int & 0x1000000) != 0)
        f((int & 0x800000) != 0)
        f((int & 0x400000) != 0)
        f((int & 0x200000) != 0)
        f((int & 0x100000) != 0)
        f((int & 0x80000) != 0)
        f((int & 0x40000) != 0)
        f((int & 0x20000) != 0)
        f((int & 0x10000) != 0)
        f((int & 0x8000) != 0)
        f((int & 0x4000) != 0)
        f((int & 0x2000) != 0)
        f((int & 0x1000) != 0)
        f((int & 0x800) != 0)
        f((int & 0x400) != 0)
        f((int & 0x200) != 0)
        f((int & 0x100) != 0)
        f((int & 0x80) != 0)
        f((int & 0x40) != 0)
        f((int & 0x20) != 0)
        f((int & 0x10) != 0)
        f((int & 0x8) != 0)
        f((int & 0x4) != 0)
        f((int & 0x2) != 0)
        f((int & 0x1) != 0)
        i += 1
      }
      i = maxFullBitIndex
      while (i < maxBitIndex) {
        f(apply(i))
        i += 1
      }
    }

    override def take(n: Int): BitChunkInt = {
      val index  = (minBitIndex + n) min maxBitIndex
      val toTake = (index + 31) >> 5
      BitChunkInt(chunk.take(toTake), endianness, minBitIndex, index)
    }

    override def toArray[A1 >: Boolean](n: Int, dest: Array[A1]): Unit = {
      var i = n
      while (i < length) {
        dest(i + n) = apply(i)
        i += 1
      }
    }

    override private[zio] def arrayIterator[A1 >: Boolean]: Iterator[Array[A1]] = {
      val array = Array.ofDim[Boolean](length)
      toArray(0, array)
      Iterator.single(array.asInstanceOf[Array[A1]])
    }

    override private[zio] def reverseArrayIterator[A1 >: Boolean]: Iterator[Array[A1]] =
      arrayIterator

  }

  private[zio] final case class BitChunkLong(
    chunk: Chunk[Long],
    endianness: Endianness,
    minBitIndex: Int,
    maxBitIndex: Int
  ) extends Chunk[Boolean] {
    self =>

    override val length: Int =
      maxBitIndex - minBitIndex

    private def elementAt(n: Int): Long =
      if(endianness == Chunk.Endianness.BigEndian) chunk(n) else java.lang.Long.reverse(chunk(n))

    override def apply(n: Int): Boolean =
      (elementAt(n >> 6) & (1L << (63 - (n & 63)))) != 0

    override def drop(n: Int): BitChunkLong = {
      val index  = (minBitIndex + n) min maxBitIndex
      val toDrop = index >> 6
      val min    = index & 63
      val max    = maxBitIndex - index + min
      BitChunkLong(chunk.drop(toDrop), endianness, min, max)
    }

    override def foreach[A](f: Boolean => A): Unit = {
      if (this.isEmpty) return
      val minLongIndex    = (minBitIndex + 63) >> 6
      val maxLongIndex    = maxBitIndex >> 6
      val minFullBitIndex = (minLongIndex << 6) min maxBitIndex
      val maxFullBitIndex = (maxLongIndex << 6) max minFullBitIndex
      var i               = minBitIndex
      while (i < minFullBitIndex) {
        f(apply(i))
        i += 1
      }
      i = minLongIndex
      while (i < maxLongIndex) {
        val long = elementAt(i)
        f((long & 0x8000000000000000L) != 0)
        f((long & 0x4000000000000000L) != 0)
        f((long & 0x2000000000000000L) != 0)
        f((long & 0x1000000000000000L) != 0)
        f((long & 0x800000000000000L) != 0)
        f((long & 0x400000000000000L) != 0)
        f((long & 0x200000000000000L) != 0)
        f((long & 0x100000000000000L) != 0)
        f((long & 0x80000000000000L) != 0)
        f((long & 0x40000000000000L) != 0)
        f((long & 0x20000000000000L) != 0)
        f((long & 0x10000000000000L) != 0)
        f((long & 0x8000000000000L) != 0)
        f((long & 0x4000000000000L) != 0)
        f((long & 0x2000000000000L) != 0)
        f((long & 0x1000000000000L) != 0)
        f((long & 0x800000000000L) != 0)
        f((long & 0x400000000000L) != 0)
        f((long & 0x200000000000L) != 0)
        f((long & 0x100000000000L) != 0)
        f((long & 0x80000000000L) != 0)
        f((long & 0x40000000000L) != 0)
        f((long & 0x20000000000L) != 0)
        f((long & 0x10000000000L) != 0)
        f((long & 0x8000000000L) != 0)
        f((long & 0x4000000000L) != 0)
        f((long & 0x2000000000L) != 0)
        f((long & 0x1000000000L) != 0)
        f((long & 0x800000000L) != 0)
        f((long & 0x400000000L) != 0)
        f((long & 0x200000000L) != 0)
        f((long & 0x100000000L) != 0)
        f((long & 0x80000000L) != 0)
        f((long & 0x40000000L) != 0)
        f((long & 0x20000000L) != 0)
        f((long & 0x10000000L) != 0)
        f((long & 0x8000000L) != 0)
        f((long & 0x4000000L) != 0)
        f((long & 0x2000000L) != 0)
        f((long & 0x1000000L) != 0)
        f((long & 0x800000L) != 0)
        f((long & 0x400000L) != 0)
        f((long & 0x200000L) != 0)
        f((long & 0x100000L) != 0)
        f((long & 0x80000L) != 0)
        f((long & 0x40000L) != 0)
        f((long & 0x20000L) != 0)
        f((long & 0x10000L) != 0)
        f((long & 0x8000L) != 0)
        f((long & 0x4000L) != 0)
        f((long & 0x2000L) != 0)
        f((long & 0x1000L) != 0)
        f((long & 0x800L) != 0)
        f((long & 0x400L) != 0)
        f((long & 0x200L) != 0)
        f((long & 0x100L) != 0)
        f((long & 0x80L) != 0)
        f((long & 0x40L) != 0)
        f((long & 0x20L) != 0)
        f((long & 0x10L) != 0)
        f((long & 0x8L) != 0)
        f((long & 0x4L) != 0)
        f((long & 0x2L) != 0)
        f((long & 0x1L) != 0)
        i += 1
      }
      i = maxFullBitIndex
      while (i < maxBitIndex) {
        f(apply(i))
        i += 1
      }
    }

    override def take(n: Int): BitChunkLong = {
      val index  = (minBitIndex + n) min maxBitIndex
      val toTake = (index + 63) >> 6
      BitChunkLong(chunk.take(toTake), endianness, minBitIndex, index)
    }

    override def toArray[A1 >: Boolean](n: Int, dest: Array[A1]): Unit = {
      var i = n
      while (i < length) {
        dest(i + n) = apply(i)
        i += 1
      }
    }

    override private[zio] def arrayIterator[A1 >: Boolean]: Iterator[Array[A1]] = {
      val array = Array.ofDim[Boolean](length)
      toArray(0, array)
      Iterator.single(array.asInstanceOf[Array[A1]])
    }

    override private[zio] def reverseArrayIterator[A1 >: Boolean]: Iterator[Array[A1]] =
      arrayIterator

  }
  private case object Empty extends Chunk[Nothing] { self =>

    def chunkIterator: ChunkIterator[Nothing] =
      ChunkIterator.empty

    override val length: Int =
      0

    override def apply(n: Int): Nothing =
      throw new ArrayIndexOutOfBoundsException(s"Empty chunk access to $n")

    override def foreach[B](f: Nothing => B): Unit = {
      val _ = f
    }

    /**
     * Materializes a chunk into a chunk backed by an array. This method can
     * improve the performance of bulk operations.
     */
    override def materialize[A1]: Chunk[A1] =
      Empty

    override def toArray[A1: ClassTag]: Array[A1] =
      Array.empty
  }

  final case class AnyRefArray[A <: AnyRef](array: Array[A], offset: Int, override val length: Int)
      extends Arr[A]
      with ChunkIterator[A] { self =>
    def apply(index: Int): A =
      array(index + offset)
    def chunkIterator: ChunkIterator[A] =
      self
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): A =
      array(index + offset)
    def sliceIterator(offset: Int, length: Int): ChunkIterator[A] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else AnyRefArray(array, self.offset + offset, self.length - offset min length)
  }

  final case class ByteArray(array: Array[Byte], offset: Int, override val length: Int)
      extends Arr[Byte]
      with ChunkIterator[Byte] { self =>
    def apply(index: Int): Byte =
      array(index + offset)
    override def byte(index: Int)(implicit ev: Byte <:< Byte): Byte =
      array(index + offset)
    def chunkIterator: ChunkIterator[Byte] =
      self
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Byte =
      array(index + offset)
    def sliceIterator(offset: Int, length: Int): ChunkIterator[Byte] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else ByteArray(array, self.offset + offset, self.length - offset min length)
  }

  final case class CharArray(array: Array[Char], offset: Int, override val length: Int)
      extends Arr[Char]
      with ChunkIterator[Char] { self =>
    def apply(index: Int): Char =
      array(index + offset)
    override def char(index: Int)(implicit ev: Char <:< Char): Char =
      array(index + offset)
    def chunkIterator: ChunkIterator[Char] =
      self
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Char =
      array(index + offset)
    def sliceIterator(offset: Int, length: Int): ChunkIterator[Char] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else CharArray(array, self.offset + offset, self.length - offset min length)
  }

  final case class IntArray(array: Array[Int], offset: Int, override val length: Int)
      extends Arr[Int]
      with ChunkIterator[Int] { self =>
    def apply(index: Int): Int =
      array(index + offset)
    override def int(index: Int)(implicit ev: Int <:< Int): Int =
      array(index + offset)
    def chunkIterator: ChunkIterator[Int] =
      self
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Int =
      array(index + offset)
    def sliceIterator(offset: Int, length: Int): ChunkIterator[Int] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else IntArray(array, self.offset + offset, self.length - offset min length)
  }

  final case class LongArray(array: Array[Long], offset: Int, override val length: Int)
      extends Arr[Long]
      with ChunkIterator[Long] { self =>
    def apply(index: Int): Long =
      array(index + offset)
    override def long(index: Int)(implicit ev: Long <:< Long): Long =
      array(index + offset)
    def chunkIterator: ChunkIterator[Long] =
      self
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Long =
      array(index + offset)
    def sliceIterator(offset: Int, length: Int): ChunkIterator[Long] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else LongArray(array, self.offset + offset, self.length - offset min length)
  }

  final case class DoubleArray(array: Array[Double], offset: Int, override val length: Int)
      extends Arr[Double]
      with ChunkIterator[Double] { self =>
    def apply(index: Int): Double =
      array(index + offset)
    override def double(index: Int)(implicit ev: Double <:< Double): Double =
      array(index + offset)
    def chunkIterator: ChunkIterator[Double] =
      self
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Double =
      array(index + offset)
    def sliceIterator(offset: Int, length: Int): ChunkIterator[Double] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else DoubleArray(array, self.offset + offset, self.length - offset min length)
  }

  final case class FloatArray(array: Array[Float], offset: Int, override val length: Int)
      extends Arr[Float]
      with ChunkIterator[Float] { self =>
    def apply(index: Int): Float =
      array(index + offset)
    override def float(index: Int)(implicit ev: Float <:< Float): Float =
      array(index + offset)
    def chunkIterator: ChunkIterator[Float] =
      self
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Float =
      array(index + offset)
    def sliceIterator(offset: Int, length: Int): ChunkIterator[Float] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else FloatArray(array, self.offset + offset, self.length - offset min length)
  }

  final case class ShortArray(array: Array[Short], offset: Int, override val length: Int)
      extends Arr[Short]
      with ChunkIterator[Short] { self =>
    def apply(index: Int): Short =
      array(index + offset)
    override def short(index: Int)(implicit ev: Short <:< Short): Short =
      array(index + offset)
    def chunkIterator: ChunkIterator[Short] =
      self
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Short =
      array(index + offset)
    def sliceIterator(offset: Int, length: Int): ChunkIterator[Short] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else ShortArray(array, self.offset + offset, self.length - offset min length)
  }

  final case class BooleanArray(array: Array[Boolean], offset: Int, length: Int)
      extends Arr[Boolean]
      with ChunkIterator[Boolean] { self =>
    def apply(index: Int): Boolean =
      array(index + offset)
    override def boolean(index: Int)(implicit ev: Boolean <:< Boolean): Boolean =
      array(index + offset)
    def chunkIterator: ChunkIterator[Boolean] =
      self
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Boolean =
      array(index + offset)
    def sliceIterator(offset: Int, length: Int): ChunkIterator[Boolean] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else BooleanArray(array, self.offset + offset, self.length - offset min length)
  }

  /**
   * A `ChunkIterator` is a specialized iterator that supports efficient
   * iteration over chunks. Unlike a normal iterator, the caller is responsible
   * for providing an `index` with each call to `hasNextAt` and `nextAt`. By
   * contract this should be `0` initially and incremented by `1` each time
   * `nextAt` is called. This allows the caller to maintain the current index in
   * local memory rather than the iterator having to do it on the heap for array
   * backed chunks.
   */
  sealed trait ChunkIterator[+A] { self =>

    /**
     * Checks if the chunk iterator has another element.
     */
    def hasNextAt(index: Int): Boolean

    /**
     * The length of the iterator.
     */
    def length: Int

    /**
     * Gets the next element from the chunk iterator.
     */
    def nextAt(index: Int): A

    /**
     * Returns a new iterator that is a slice of this iterator.
     */
    def sliceIterator(offset: Int, length: Int): ChunkIterator[A]

    /**
     * Concatenates this chunk iterator with the specified chunk iterator.
     */
    final def ++[A1 >: A](that: ChunkIterator[A1]): ChunkIterator[A1] =
      ChunkIterator.Concat(self, that)
  }

  object ChunkIterator {

    /**
     * The empty iterator.
     */
    val empty: ChunkIterator[Nothing] =
      Empty

    /**
     * Constructs an iterator from an array of arbitrary values.
     */
    def fromArray[A](array: Array[A]): ChunkIterator[A] =
      array match {
        case array: Array[AnyRef]  => Chunk.AnyRefArray(array, 0, array.length)
        case array: Array[Boolean] => Chunk.BooleanArray(array, 0, array.length)
        case array: Array[Byte]    => Chunk.ByteArray(array, 0, array.length)
        case array: Array[Char]    => Chunk.CharArray(array, 0, array.length)
        case array: Array[Double]  => Chunk.DoubleArray(array, 0, array.length)
        case array: Array[Float]   => Chunk.FloatArray(array, 0, array.length)
        case array: Array[Int]     => Chunk.IntArray(array, 0, array.length)
        case array: Array[Long]    => Chunk.LongArray(array, 0, array.length)
        case array: Array[Short]   => Chunk.ShortArray(array, 0, array.length)
      }

    /**
     * Constructs an iterator from a `Vector`.
     */
    def fromVector[A](vector: Vector[A]): ChunkIterator[A] =
      if (vector.length <= 0) Empty
      else Iterator(vector.iterator, vector.length)

    private final case class Concat[A](left: ChunkIterator[A], right: ChunkIterator[A]) extends ChunkIterator[A] {
      self =>
      def hasNextAt(index: Int): Boolean =
        index < length
      val length: Int =
        left.length + right.length
      def nextAt(index: Int): A =
        if (left.hasNextAt(index)) left.nextAt(index)
        else right.nextAt(index - left.length)
      def sliceIterator(offset: Int, length: Int): ChunkIterator[A] =
        if (offset <= 0 && length >= self.length) self
        else if (offset >= self.length || length <= 0) Empty
        else if (offset >= left.length) right.sliceIterator(offset - left.length, length)
        else if (length <= left.length - offset) left.sliceIterator(offset, length)
        else
          Concat(
            left.sliceIterator(offset, left.length - offset),
            right.sliceIterator(0, offset + length - left.length)
          )
    }

    private case object Empty extends ChunkIterator[Nothing] { self =>
      def hasNextAt(index: Int): Boolean =
        false
      val length: Int =
        0
      def nextAt(index: Int): Nothing =
        throw new ArrayIndexOutOfBoundsException(s"Empty chunk access to $index")
      def sliceIterator(offset: Int, length: Int): ChunkIterator[Nothing] =
        self
    }

    private final case class Iterator[A](iterator: scala.Iterator[A], length: Int) extends ChunkIterator[A] { self =>
      def hasNextAt(index: Int): Boolean =
        iterator.hasNext
      def nextAt(index: Int): A =
        iterator.next()
      def sliceIterator(offset: Int, length: Int): ChunkIterator[A] =
        if (offset <= 0 && length >= self.length) self
        else if (offset >= self.length || length <= 0) Empty
        else Iterator(iterator.slice(offset, offset + length), self.length - offset min length)
    }
  }
}
