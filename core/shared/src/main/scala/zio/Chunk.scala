/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import java.nio._

import scala.collection.mutable.Builder
import scala.reflect.{ classTag, ClassTag }

/**
 * A `Chunk[A]` represents a chunk of values of type `A`. Chunks are designed
 * are usually backed by arrays, but expose a purely functional, safe interface
 * to the underlying elements, and they become lazy on operations that would be
 * costly with arrays, such as repeated concatenation.
 *
 * NOTE: For performance reasons `Chunk` does not box primitive types. As a
 * result, it is not safe to construct chunks from heteregenous primitive
 * types.
 */
sealed trait Chunk[+A] extends ChunkLike[A] { self =>

  /**
   * Appends an element to the chunk
   */
  final def +[A1 >: A](a: A1): NonEmptyChunk[A1] =
    self match {
      case Chunk.Empty          => Chunk.single(a)
      case ne: NonEmptyChunk[A] => Chunk.concat(ne, Chunk.single(a))
    }

  /**
   * Returns the concatenation of this chunk with the specified chunk.
   */
  def ++[A1 >: A](chunk: Chunk[A1]): Chunk[A1] =
    Chunk.concat(self, chunk)

  /**
   * Converts a chunk of bytes to a chunk of bits.
   */
  final def asBits(implicit ev: A <:< Byte): Chunk[Boolean] =
    self match {
      case Chunk.Empty                => Chunk.Empty
      case nonEmpty: NonEmptyChunk[A] => Chunk.BitChunk(nonEmpty.mapNonEmpty(ev), 0, length << 3)
    }

  /**
   * Returns a filtered, mapped subset of the elements of this chunk based on a .
   */
  def collectM[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] =
    self.materialize.collectM(pf)

  /**
   * Transforms all elements of the chunk for as long as the specified partial function is defined.
   */
  def collectWhile[B](pf: PartialFunction[A, B]): Chunk[B] =
    self.materialize.collectWhile(pf)

  def collectWhileM[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] =
    self.materialize.collectWhileM(pf)

  /**
   * Determines whether this chunk and the specified chunk have the same length
   * and every pair of corresponding elements of this chunk and the specified
   * chunk satisfy the specified predicate.
   */
  final def corresponds[B](that: Chunk[B])(f: (A, B) => Boolean): Boolean =
    if (self.length != that.length) false
    else {
      var i           = 0
      var corresponds = true
      while (corresponds && i < length) {
        if (!f(self(i), that(i))) {
          corresponds = false
        }
        i += 1
      }
      corresponds
    }

  /**
   * Drops the first `n` elements of the chunk.
   */
  override final def drop(n: Int): Chunk[A] = {
    val len = self.length

    if (n <= 0) self
    else if (n >= len) Chunk.empty
    else
      self match {
        case Chunk.Slice(c, o, l)        => Chunk.Slice(c, o + n, l - n)
        case Chunk.Singleton(_) if n > 0 => Chunk.empty
        case c @ Chunk.Singleton(_)      => c
        case Chunk.Empty                 => Chunk.empty
        case ne: NonEmptyChunk[A]        => Chunk.Slice(ne, n, len - n)
      }
  }

  /**
   * Drops all elements so long as the predicate returns true.
   */
  override def dropWhile(f: A => Boolean): Chunk[A] = {
    val len = self.length

    var i = 0
    while (i < len && f(self(i))) {
      i += 1
    }

    drop(i)
  }

  override final def equals(that: Any): Boolean = that match {
    case that: Chunk[_] =>
      if (self.length != that.length) false
      else {
        var i     = 0
        var equal = true
        val len   = self.length

        while (equal && i < len) {
          equal = self(i) == that(i)
          i += 1
        }

        equal
      }
    case that: Seq[_] =>
      self.corresponds(that)(_ == _)
    case _ => false
  }

  /**
   * Determines whether a predicate is satisfied for at least one element of this chunk.
   */
  override final def exists(f: A => Boolean): Boolean = {
    val len    = self.length
    var exists = false
    var i      = 0
    while (!exists && i < len) {
      if (f(self(i))) exists = true
      i += 1
    }
    exists
  }

  /**
   * Returns a filtered subset of this chunk.
   */
  override def filter(f: A => Boolean): Chunk[A] = {
    implicit val B: ClassTag[A] = Chunk.classTagOf(this)

    val len  = self.length
    val dest = Array.ofDim[A](len)

    var i = 0
    var j = 0
    while (i < len) {
      val elem = self(i)

      if (f(elem)) {
        dest(j) = elem
        j += 1
      }

      i += 1
    }

    if (j == 0) Chunk.Empty
    else Chunk.Slice(Chunk.arr(dest), 0, j)
  }

  /**
   * Filters this chunk by the specified effectful predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  final def filterM[R, E](f: A => ZIO[R, E, Boolean]): ZIO[R, E, Chunk[A]] = {
    implicit val A: ClassTag[A] = Chunk.classTagOf(this)

    val len                              = self.length
    var dest: ZIO[R, E, (Array[A], Int)] = ZIO.succeedNow((Array.ofDim[A](len), 0))

    var i = 0
    while (i < len) {
      val elem = self(i)

      dest = dest.zipWith(f(elem)) {
        case ((array, idx), res) =>
          var resIdx = idx
          if (res) {
            array(idx) = elem
            resIdx = idx + 1
          }
          (array, resIdx)
      }

      i += 1
    }

    dest.map {
      case (array, arrLen) =>
        if (arrLen == 0) Chunk.empty
        else Chunk.Slice(Chunk.arr(array), 0, arrLen)
    }
  }

  /**
   * Returns the first element that satisfies the predicate.
   */
  override final def find(f: A => Boolean): Option[A] = {
    val len               = self.length
    var result: Option[A] = None
    var i                 = 0
    while (i < len && result.isEmpty) {
      val elem = self(i)
      if (f(elem)) result = Some(elem)
      i += 1
    }
    result
  }

  /**
   * Flattens a chunk of chunks into a single chunk by concatenating all chunks.
   */
  final def flatten[B](implicit ev: A <:< Chunk[B]): Chunk[B] =
    flatMap(ev(_))

  /**
   * Folds over the elements in this chunk from the left.
   */
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

  /**
   * Effectfully folds over the elements in this chunk from the left.
   */
  final def foldM[R, E, S](s: S)(f: (S, A) => ZIO[R, E, S]): ZIO[R, E, S] =
    foldLeft[ZIO[R, E, S]](IO.succeedNow(s))((s, a) => s.flatMap(f(_, a)))

  /**
   * Folds over the elements in this chunk from the right.
   */
  override def foldRight[S](s0: S)(f: (A, S) => S): S = {
    val len = self.length
    var s   = s0

    var i = len - 1
    while (i >= 0) {
      s = f(self(i), s)
      i -= 1
    }

    s
  }

  /**
   * Folds over the elements in this chunk from the left.
   * Stops the fold early when the condition is not fulfilled.
   */
  final def foldWhile[S](s0: S)(pred: S => Boolean)(f: (S, A) => S): S = {
    val len = length
    var s   = s0

    var i = 0
    while (i < len && pred(s)) {
      s = f(s, self(i))
      i += 1
    }

    s
  }

  final def foldWhileM[R, E, S](z: S)(pred: S => Boolean)(f: (S, A) => ZIO[R, E, S]): ZIO[R, E, S] = {
    val len = length

    def loop(s: S, i: Int): ZIO[R, E, S] =
      if (i >= len) IO.succeedNow(s)
      else {
        if (pred(s)) f(s, self(i)).flatMap(loop(_, i + 1))
        else IO.succeedNow(s)
      }

    loop(z, 0)
  }

  /**
   * Determines whether a predicate is satisfied for all elements of this chunk.
   */
  override final def forall(f: A => Boolean): Boolean = {
    val len    = self.length
    var exists = true
    var i      = 0
    while (exists && i < len) {
      exists = f(self(i))
      i += 1
    }
    exists
  }

  override final def hashCode: Int = toArrayOption match {
    case None        => Seq.empty[A].hashCode
    case Some(array) => array.toSeq.hashCode
  }

  /**
   * Returns the first element of this chunk if it exists.
   */
  override final def headOption: Option[A] =
    if (isEmpty) None else Some(self(0))

  /**
   * Returns the first index for which the given predicate is satisfied after or at some given index.
   */
  override final def indexWhere(f: A => Boolean, from: Int): Int = {
    val len    = self.length
    var i      = math.max(from, 0)
    var result = -1

    while (result < 0 && i < len) {
      if (f(self(i))) result = i
      else i += 1
    }

    result
  }

  /**
   * Determines if the chunk is empty.
   */
  override final def isEmpty: Boolean = length == 0

  /**
   * Returns the last element of this chunk if it exists.
   */
  override final def lastOption: Option[A] =
    if (isEmpty) None else Some(self(self.length - 1))

  /**
   * Statefully maps over the chunk, producing new elements of type `B`.
   */
  def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): (S1, Chunk[B])

  /**
   * Statefully and effectfully maps over the elements of this chunk to produce
   * new elements.
   */
  def mapAccumM[R, E, S1, B](s1: S1)(f1: (S1, A) => ZIO[R, E, (S1, B)]): ZIO[R, E, (S1, Chunk[B])]

  /**
   * Effectfully maps the elements of this chunk.
   */
  def mapM[R, E, B](f: A => ZIO[R, E, B]): ZIO[R, E, Chunk[B]]

  /**
   * Effectfully maps the elements of this chunk in parallel.
   */
  def mapMPar[R, E, B](f: A => ZIO[R, E, B]): ZIO[R, E, Chunk[B]]

  /**
   * Effectfully maps the elements of this chunk in parallel purely for the effects.
   */
  final def mapMPar_[R, E](f: A => ZIO[R, E, Any]): ZIO[R, E, Unit] =
    foldLeft[ZIO[R, E, Unit]](IO.unit)((io, a) => f(a).zipParRight(io))

  /**
   * Effectfully maps the elements of this chunk purely for the effects.
   */
  final def mapM_[R, E](f: A => ZIO[R, E, Any]): ZIO[R, E, Unit] = {
    val len                 = self.length
    var zio: ZIO[R, E, Any] = ZIO.unit
    var i                   = 0

    while (i < len) {
      val a = self(i)
      zio = zio *> f(a)
      i += 1
    }

    zio.unit
  }

  /**
   * Materializes a chunk into a chunk backed by an array. This method can
   * improve the performance of bulk operations.
   */
  def materialize[A1 >: A]: Chunk[A1]

  /**
   * Returns two splits of this chunk at the specified index.
   */
  override final def splitAt(n: Int): (Chunk[A], Chunk[A]) =
    (take(n), drop(n))

  /**
   * Takes the first `n` elements of the chunk.
   */
  override final def take(n: Int): Chunk[A] =
    if (n <= 0) Chunk.Empty
    else if (n >= length) this
    else
      self match {
        case Chunk.Empty => Chunk.Empty
        case Chunk.Slice(c, o, l) =>
          if (n >= l) this
          else Chunk.Slice(c, o, n)
        case c @ Chunk.Singleton(_) => c
        case ne: NonEmptyChunk[A]   => Chunk.Slice(ne, 0, n)
      }

  /**
   * Takes all elements so long as the predicate returns true.
   */
  override def takeWhile(f: A => Boolean): Chunk[A] = {
    val len = self.length

    var i = 0
    while (i < len && f(self(i))) {
      i += 1
    }

    take(i)
  }

  /**
   * Converts the chunk into an array.
   */
  override def toArray[A1 >: A](implicit tag: ClassTag[A1]): Array[A1] = {
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
   * Zips this chunk with the specified chunk using the specified combiner.
   */
  def zipWith[B, C](that: Chunk[B])(f: (A, B) => C): Chunk[C]

  /**
   * Zips this chunk with the index of every element.
   */
  def zipWithIndex: Chunk[(A, Int)]

  def zipAllWith[B, C](
    that: Chunk[B]
  )(left: A => C, right: B => C)(both: (A, B) => C): Chunk[C]

  /**
   * Zips this chunk with the index of every element, starting from the initial
   * index value.
   */
  def zipWithIndexFrom(indexOffset: Int): Chunk[(A, Int)]

  //noinspection AccessorLikeMethodIsUnit
  protected[zio] def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit

  /**
   * Returns a filtered, mapped subset of the elements of this chunk.
   */
  protected def collectChunk[B](pf: PartialFunction[A, B]): Chunk[B] =
    self.materialize.collectChunk(pf)

  /**
   * Returns a chunk with the elements mapped by the specified function.
   */
  protected def mapChunk[B](f: A => B): Chunk[B]

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
  private final def toArrayOption[A1 >: A]: Option[Array[A1]] = self match {
    case Chunk.Empty => None
    case chunk       => Some(chunk.toArray(Chunk.classTagOf(self)))
  }
}

object Chunk {

  implicit class ConcatNonEmptySyntax[A](private val self: NonEmptyChunk[A]) extends AnyVal {

    /**
     * Concatenates this `Chunk` with the specified `NonEmptyChunk`, returning a
     * `NonEmptyChunk`.
     */
    def concatNonEmpty[A1 >: A](that: NonEmptyChunk[A1]): NonEmptyChunk[A1] =
      Chunk.concat(self, that)
  }

  /**
   * Returns the empty chunk.
   */
  val empty: Chunk[Nothing] = Empty

  def apply[A](): Chunk[A] = Empty

  /**
   * Returns a chunk from a number of values.
   */
  def apply[A](a: A, as: A*): NonEmptyChunk[A] =
    as match {
      case Nil =>
        Singleton(a)
      case wa: scala.collection.mutable.WrappedArray[A] =>
        val len: Int        = wa.size + 1
        val in: Array[A]    = wa.array.asInstanceOf[Array[A]]
        val ct: ClassTag[A] = wa.elemTag.asInstanceOf[ClassTag[A]]

        if (len <= 16) {

          val dest: Array[A] = Array.ofDim[A](len)(ct)
          dest(0) = a
          var i: Int = 1
          while (i < len) {
            dest(i) = in(i - 1)
            i += 1
          }

          new Arr(dest, ct)

        } else Concat(Singleton(a), new Arr(in, ct))

      case _ =>
        val ct: ClassTag[A] = Tags.fromValue(a)
        val des: Array[A]   = Array.ofDim[A](as.length + 1)(ct)
        des(0) = a
        as.copyToArray(des, 1)
        arr(des)
    }

  /**
   * Returns the concatenation of this chunk with the specified chunk.
   */
  def concat[A](l: Chunk[A], r: Chunk[A]): Chunk[A] =
    l match {
      case Empty => r
      case neL: NonEmpty[A] =>
        r match {
          case Empty            => neL
          case neR: NonEmpty[A] => concat(neL, neR)
        }
    }

  /**
   * Returns the concatenation of this chunk with the specified chunk.
   */
  def concat[A](l: Chunk[A], r: NonEmpty[A]): NonEmptyChunk[A] = l match {
    case Empty           => r
    case ne: NonEmpty[A] => concat(ne, r)
  }

  /**
   * Returns the concatenation of this chunk with the specified chunk.
   */
  def concat[A](l: NonEmpty[A], r: NonEmpty[A]): NonEmptyChunk[A] = Concat(l, r)

  /**
   * Returns the concatenation of this chunk with the specified chunk.
   */
  def concat[A](l: NonEmpty[A], r: Chunk[A]): NonEmptyChunk[A] = r match {
    case Empty           => l
    case ne: NonEmpty[A] => concat(l, ne)
  }

  /**
   * Returns a chunk backed by an array.
   */
  def fromArray[A](array: Array[A]): Chunk[A] =
    if (array.isEmpty) Empty else arr(array)

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
        val first                   = iterable.head
        implicit val A: ClassTag[A] = Tags.fromValue(first)
        fromArray(it.toArray)
    }

  def fill[A](n: Int)(elem: => A): Chunk[A] =
    if (n <= 0) Chunk.empty
    else {
      val first                     = elem
      implicit val tag: ClassTag[A] = Tags.fromValue(first)
      val array                     = Array.ofDim[A](n)
      array(0) = first
      var i = 1
      while (i < n) {
        array(i) = elem
        i += 1
      }
      arr(array)
    }

  /**
   * Returns a singleton chunk, eagerly evaluated.
   */
  def single[A](a: A): NonEmptyChunk[A] = Singleton(a)

  /**
   * Alias for [[Chunk.single]].
   */
  def succeed[A](a: A): NonEmptyChunk[A] = single(a)

  private[zio] def arr[A](array: Array[A]): NonEmpty[A] =
    new Arr(array, ClassTag(array.getClass.getComponentType))

  /**
   * Returns the `ClassTag` for the element type of the chunk.
   */
  private[zio] def classTagOf[A](chunk: Chunk[A]): ClassTag[A] = chunk match {
    case x: Arr[A]         => x.classTag
    case x: Concat[A]      => x.classTag
    case Empty             => classTag[java.lang.Object].asInstanceOf[ClassTag[A]]
    case x: Singleton[A]   => x.classTag
    case x: Slice[A]       => x.classTag
    case x: VectorChunk[A] => x.classTag
    case _: BitChunk       => ClassTag.Boolean.asInstanceOf[ClassTag[A]]
  }

  private final class Arr[A](private val array: Array[A], implicit val classTag: ClassTag[A])
      extends NonEmpty[A]
      with Serializable {

    override val length: Int =
      array.length

    override def apply(n: Int): A =
      array(n)

    override def collectM[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] = {
      val len                       = array.length
      val orElse                    = (_: A) => UIO.succeedNow(null.asInstanceOf[B])
      var dest: ZIO[R, E, Array[B]] = UIO.succeedNow(null.asInstanceOf[Array[B]])

      var i = 0
      var j = 0
      while (i < len) {
        // `zipWith` is lazy in the RHS, so we need to capture to evaluate the
        // `pf.applyOrElse` strictly to make sure we use the right value of `i`.
        val rhs = pf.applyOrElse(array(i), orElse)

        dest = dest.zipWith(rhs) { (array, b) =>
          var tmp = array
          if (b != null) {
            if (tmp == null) {
              implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)
              tmp = Array.ofDim[B](len)
            }
            tmp(j) = b
            j += 1
          }
          tmp
        }

        i += 1
      }

      dest.map(array =>
        if (array == null) Chunk.empty
        else Chunk.Slice(Chunk.arr(array), 0, j)
      )
    }

    override def collectWhile[B](pf: PartialFunction[A, B]): Chunk[B] = {
      val self = array
      val len  = self.length
      var dest = null.asInstanceOf[Array[B]]

      var i    = 0
      var j    = 0
      var done = false
      while (!done && i < len) {
        val b = pf.applyOrElse(self(i), (_: A) => null.asInstanceOf[B])

        if (b != null) {
          if (dest == null) {
            implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)
            dest = Array.ofDim[B](len)
          }

          dest(j) = b
          j += 1
        } else {
          done = true
        }

        i += 1
      }

      if (dest == null) Chunk.Empty
      else Chunk.Slice(Chunk.arr(dest), 0, j)
    }

    override def collectWhileM[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] = {
      val self                      = array
      val len                       = self.length
      var dest: ZIO[R, E, Array[B]] = UIO.succeedNow(null.asInstanceOf[Array[B]])

      var i    = 0
      var j    = 0
      var done = false
      val orElse = (_: A) => {
        done = true
        UIO.succeedNow(null.asInstanceOf[B])
      }

      while (!done && i < len) {
        // `zipWith` is lazy in the RHS, and we rely on the side-effects of `orElse` here.
        val rhs = pf.applyOrElse(self(i), orElse)

        dest = dest.zipWith(rhs) { (array, b) =>
          var tmp = array
          if (b != null) {
            if (tmp == null) {
              implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)
              tmp = Array.ofDim[B](len)
            }
            tmp(j) = b
            j += 1
          }
          tmp
        }

        i += 1
      }

      dest.map(array =>
        if (array == null) Chunk.empty
        else Chunk.Slice(Chunk.arr(array), 0, j)
      )
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
      val self = array
      val len  = self.length
      val dest = Array.ofDim[A](len)

      var i = 0
      var j = 0
      while (i < len) {
        val elem = self(i)

        if (f(elem)) {
          dest(j) = elem
          j += 1
        }

        i += 1
      }

      if (j == 0) Chunk.Empty
      else Chunk.Slice(Chunk.arr(dest), 0, j)
    }

    override def foldLeft[S](s0: S)(f: (S, A) => S): S = {
      val self = array
      val len  = self.length
      var s    = s0

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

    override def toArray[A1 >: A](implicit tag: ClassTag[A1]): Array[A1] =
      array.asInstanceOf[Array[A1]]

    override protected[zio] def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit =
      Array.copy(array, 0, dest, n, length)

    override protected def collectChunk[B](pf: PartialFunction[A, B]): Chunk[B] = {
      val self = array
      val len  = self.length
      var dest = null.asInstanceOf[Array[B]]

      var i = 0
      var j = 0
      while (i < len) {
        val b = pf.applyOrElse(self(i), (_: A) => null.asInstanceOf[B])

        if (b != null) {
          if (dest == null) {
            implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)
            dest = Array.ofDim[B](len)
          }

          dest(j) = b
          j += 1
        }

        i += 1
      }

      if (dest == null) Chunk.Empty
      else Chunk.Slice(Chunk.arr(dest), 0, j)
    }

    override protected def mapChunk[B](f: A => B): NonEmptyChunk[B] = {
      val self = array
      val len  = self.length

      val b                       = f(first)
      implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)
      val dest                    = Array.ofDim[B](len)

      dest(0) = b

      var i = 1
      while (i < len) {
        dest(i) = f(self(i))
        i += 1
      }

      Chunk.arr(dest)
    }
  }

  private final case class Concat[A](l: NonEmpty[A], r: NonEmpty[A]) extends NonEmpty[A] { self =>

    implicit val classTag: ClassTag[A] = classTagOf(l)

    override val length: Int = l.length + r.length

    override def apply(n: Int): A = if (n < l.length) l(n) else r(n - l.length)

    override def foreach[B](f: A => B): Unit = {
      l.foreach(f)
      r.foreach(f)
    }

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = {
      l.toArray(n, dest)
      r.toArray(n + l.length, dest)
    }
  }

  private final case class Singleton[A](a: A) extends NonEmpty[A] {

    implicit val classTag: ClassTag[A] = Tags.fromValue(a)

    override val length = 1

    override def apply(n: Int): A =
      if (n == 0) a
      else throw new ArrayIndexOutOfBoundsException(s"Singleton chunk access to $n")

    override def foreach[B](f: A => B): Unit = {
      val _ = f(a)
    }

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit =
      dest(n) = a
  }

  private final case class Slice[A](private val chunk: NonEmpty[A], offset: Int, l: Int) extends NonEmpty[A] {

    implicit val classTag: ClassTag[A] = classTagOf(chunk)

    override val length: Int = l

    override def apply(n: Int): A = chunk.apply(offset + n)

    override def foreach[B](f: A => B): Unit = {
      var i = 0
      while (i < length) {
        f(apply(i))
        i += 1
      }
    }

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

  private final case class VectorChunk[A](private val vector: Vector[A]) extends NonEmpty[A] {

    implicit val classTag: ClassTag[A] = Tags.fromValue(vector(0))

    override val length: Int = vector.length

    override def apply(n: Int): A = vector(n)

    override def foreach[B](f: A => B): Unit = vector.foreach(f)

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = { val _ = vector.copyToArray(dest, n, length) }
  }

  private final case class BitChunk(bytes: NonEmptyChunk[Byte], minBitIndex: Int, maxBitIndex: Int)
      extends NonEmpty[Boolean] {
    self =>

    override def apply(n: Int): Boolean =
      (bytes(n >> 3) & (1 << (7 - (n & 7)))) != 0

    override def foreach[A](f: Boolean => A): Unit = {
      var i = 0
      while (i < length) {
        f(apply(i))
        i += 1
      }
    }

    override val length: Int =
      maxBitIndex - minBitIndex

    override def toArray[A1 >: Boolean](n: Int, dest: Array[A1]): Unit = {
      var i = n
      while (i < length) {
        dest(i + n) = apply(i)
        i += 1
      }
    }
  }

  private case object Empty extends Chunk[Nothing] { self =>

    override val length: Int = 0

    override def apply(n: Int): Nothing =
      throw new ArrayIndexOutOfBoundsException(s"Empty chunk access to $n")

    override def collectM[R, E, B](pf: PartialFunction[Nothing, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] =
      UIO.succeedNow(Empty)

    override def collectWhile[B](pf: PartialFunction[Nothing, B]): Chunk[B] =
      Empty

    override def collectWhileM[R, E, B](pf: PartialFunction[Nothing, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] =
      UIO.succeedNow(Empty)

    override def foreach[B](f: Nothing => B): Unit = {
      val _ = f
    }

    /**
     * Statefully maps over the chunk, producing new elements of type `B`.
     */
    override def mapAccum[S1, B](s1: S1)(f1: (S1, Nothing) => (S1, B)): (S1, Chunk[B]) =
      (s1, Empty)

    /**
     * Statefully and effectfully maps over the elements of this chunk to produce
     * new elements.
     */
    override def mapAccumM[R, E, S1, B](s1: S1)(f1: (S1, Nothing) => ZIO[R, E, (S1, B)]): ZIO[R, E, (S1, Chunk[B])] =
      ZIO.succeedNow(s1 -> Empty)

    /**
     * Effectfully maps the elements of this chunk.
     */
    override def mapM[R, E, B](f: Nothing => ZIO[R, E, B]): ZIO[R, E, Chunk[B]] =
      ZIO.succeedNow(Empty)

    /**
     * Effectfully maps the elements of this chunk in parallel.
     */
    override def mapMPar[R, E, B](f: Nothing => ZIO[R, E, B]): ZIO[R, E, Chunk[B]] =
      ZIO.succeedNow(Empty)

    /**
     * Materializes a chunk into a chunk backed by an array. This method can
     * improve the performance of bulk operations.
     */
    override def materialize[A1]: Chunk[A1] =
      Empty

    override def toArray[A1](implicit tag: ClassTag[A1]): Array[A1] =
      Array.empty

    override def zipAllWith[B, C](that: Chunk[B])(left: Nothing => C, right: B => C)(
      both: (Nothing, B) => C
    ): Chunk[C] =
      that.map(right)

    /**
     * Zips this chunk with the specified chunk using the specified combiner.
     */
    override def zipWith[B, C](that: Chunk[B])(f: (Nothing, B) => C): Chunk[C] =
      Empty

    /**
     * Zips this chunk with the index of every element.
     */
    override def zipWithIndex: Chunk[(Nothing, Int)] =
      Empty

    /**
     * Zips this chunk with the index of every element, starting from the initial
     * index value.
     */
    override def zipWithIndexFrom(indexOffset: Int): Chunk[(Nothing, Int)] =
      Empty

    protected[zio] def toArray[A1 >: Nothing](n: Int, dest: Array[A1]): Unit =
      ()

    override protected def collectChunk[B](pf: PartialFunction[Nothing, B]): Chunk[B] =
      Empty

    /**
     * Returns a chunk with the elements mapped by the specified function.
     */
    override protected def mapChunk[B](f: Nothing => B): Chunk[B] =
      Empty
  }

  sealed trait NonEmpty[+A] extends Chunk[A] { self =>

    /**
     * Returns the concatenation of this chunk with the specified chunk,
     * returning a `NonEmptyChunk`.
     */
    final def concatNonEmpty[A1 >: A](that: Chunk[A1]): NonEmptyChunk[A1] =
      Chunk.concat(self, that)

    final def first: A =
      self(0)

    /**
     * Maps each element of this `NonEmptyChunk` to a new `NonEmptyChunk`
     * using the specified function and then concatenates them, returning a
     * `NonEmptyChunk`.
     */
    final def flatMapNonEmpty[B](f: A => NonEmpty[B]): NonEmptyChunk[B] = {
      val len = self.length

      val init: NonEmpty[B]      = f(first)
      var chunks: List[Chunk[B]] = List(init)
      var i                      = 1
      var total                  = init.length
      val B: ClassTag[B]         = classTagOf(init)

      while (i < len) {
        val chunk = f(self(i))
        chunks ::= chunk
        total += chunk.length
        i += 1
      }

      val dest: Array[B] = Array.ofDim(total)(B)

      val it = chunks.iterator
      var n  = total
      while (it.hasNext) {
        val chunk = it.next
        n -= chunk.length
        chunk.toArray(n, dest)
      }

      Chunk.arr(dest)
    }

    /**
     * Flattens a `NonEmptyChunk` of `NonEmptyChunk` values into a single
     * `NonEmptyChunk` by concatenating them..
     */
    final def flatten[B](implicit ev: A <:< NonEmpty[B]): NonEmptyChunk[B] =
      flatMapNonEmpty(ev(_))

    /**
     * Materializes a chunk into a chunk backed by an array. This method can
     * improve the performance of bulk operations.
     */
    override final def materialize[A1 >: A]: NonEmptyChunk[A1] =
      self match {
        case arr: Arr[A] => arr
        case _           => arr(self.toArray(Chunk.classTagOf(self)))
      }

    override final def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): (S1, NonEmpty[B]) = {
      val len                     = self.length
      val init                    = f1(s1, first)
      implicit val B: ClassTag[B] = Chunk.Tags.fromValue(init._2)
      val dest: Array[B]          = Array.ofDim(len)

      dest(0) = init._2
      var i     = 1
      var s: S1 = init._1

      while (i < len) {
        val t = f1(s, self(i))

        s = t._1
        dest(i) = t._2

        i += 1
      }

      (s, Chunk.arr(dest))
    }

    /**
     * Statefully and effectfully maps over the elements of this chunk to produce
     * new elements.
     */
    override final def mapAccumM[R, E, S1, B](
      s1: S1
    )(f1: (S1, A) => ZIO[R, E, (S1, B)]): ZIO[R, E, (S1, NonEmpty[B])] = {

      val len = self.length

      val init: ZIO[R, E, (S1, B)] = f1(s1, first)

      var dest: ZIO[R, E, (S1, Array[B])] = init.map {
        case (s, b) =>
          implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)
          val array                   = Array.ofDim[B](len)
          array(0) = b
          (s, array)
      }

      var i = 1

      while (i < len) {
        val j = i
        dest = dest.flatMap {
          case (state, array) =>
            f1(state, self(j)).map {
              case (state2, b) =>
                array(j) = b
                (state2, array)
            }
        }

        i += 1
      }

      dest.map {
        case (state, array) => (state, Chunk.arr(array))
      }
    }

    override final def mapM[R, E, B](f: A => ZIO[R, E, B]): ZIO[R, E, NonEmpty[B]] = {
      val len = self.length

      val init: ZIO[R, E, B] = f(first)

      var array: ZIO[R, E, Array[B]] = init.map { b =>
        implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)
        val array                   = Array.ofDim[B](len)
        array(0) = b
        array
      }

      var i = 1

      while (i < len) {
        val j = i
        array = array.zipWith(f(self(j))) { (array, b) =>
          array(j) = b
          array
        }

        i += 1
      }

      array.map(array => Chunk.arr(array))
    }

    override final def mapMPar[R, E, B](f: A => ZIO[R, E, B]): ZIO[R, E, NonEmpty[B]] = {
      val len = self.length

      val init: ZIO[R, E, B] = f(first)

      var array: ZIO[R, E, Array[B]] = init.map { b =>
        implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)
        val array                   = Array.ofDim[B](len)
        array(0) = b
        array
      }

      var i = 1

      while (i < len) {
        val j = i
        array = array.zipWithPar(f(self(j))) { (array, b) =>
          array(j) = b
          array
        }

        i += 1
      }

      array.map(array => Chunk.arr(array))
    }

    /**
     * Transforms each element of this `NonEmptyChunk` with the specified
     * function, returning a `NonEmptyChunk`.
     */
    final def mapNonEmpty[B](f: A => B): NonEmptyChunk[B] =
      mapChunk(f)

    override final def reduce[A1 >: A](f: (A1, A1) => A1): A1 = {
      val len     = length
      var res: A1 = first
      var i       = 1
      while (i < len) {
        res = f(res, self(i))
        i += 1
      }
      res
    }

    override final def zipAllWith[B, C](
      that: Chunk[B]
    )(left: A => C, right: B => C)(both: (A, B) => C): NonEmptyChunk[C] =
      that match {
        case Empty => self.mapNonEmpty(left)
        case ne: NonEmpty[B] =>
          val that: NonEmpty[B] = ne
          val size              = self.length.max(that.length)
          val first_out         = both(self.first, that.first)

          implicit val C: ClassTag[C] = Chunk.Tags.fromValue(first_out)
          val dest                    = Array.ofDim[C](size)

          dest(0) = first_out
          var j = 1

          while (j < size) {
            val c =
              if (j >= self.length) right(that(j))
              else if (j >= that.length) left(self(j))
              else both(self(j), that(j))

            dest(j) = c
            j += 1
          }

          Chunk.arr(dest)
      }

    /**
     * Zips this chunk with the specified chunk using the specified combiner.
     */
    override final def zipWith[B, C](that: Chunk[B])(f: (A, B) => C): Chunk[C] =
      that match {
        case Empty => Empty
        case ne: NonEmpty[B] =>
          val that: NonEmpty[B] = ne

          val size     = self.length.min(that.length)
          val first: C = f(self.first, that.first)

          implicit val C: ClassTag[C] = Chunk.Tags.fromValue(first)
          val dest                    = Array.ofDim[C](size)

          var i = 1
          dest(0) = first

          while (i < size) {
            dest(i) = f(self(i), that(i))
            i += 1
          }

          Chunk.arr(dest)
      }

    override final def zipWithIndex: NonEmptyChunk[(A, Int)] = zipWithIndexFrom(0)

    /**
     * Zips this chunk with the index of every element, starting from the initial
     * index value.
     */
    override final def zipWithIndexFrom(indexOffset: Int): NonEmptyChunk[(A, Int)] = {
      val len = self.length

      val dest = Array.ofDim[(A, Int)](len)

      var i = 0

      while (i < len) {
        dest(i) = (self(i), i + indexOffset)
        i += 1
      }

      Chunk.arr(dest)
    }

    override protected def mapChunk[B](f: A => B): NonEmptyChunk[B] = {
      val len                     = self.length
      val init: B                 = f(first)
      implicit val B: ClassTag[B] = Chunk.Tags.fromValue(init)
      val dest                    = Array.ofDim[B](len)

      dest(0) = init
      var i = 1

      while (i < len) {
        dest(i) = f(self(i))
        i += 1
      }
      Chunk.arr(dest)
    }
  }

  private[zio] object Tags {
    def fromValue[A](a: A): ClassTag[A] =
      unbox(ClassTag(a.getClass))

    private def unbox[A](c: ClassTag[A]): ClassTag[A] =
      if (isBoolean(c)) BooleanClass.asInstanceOf[ClassTag[A]]
      else if (isByte(c)) ByteClass.asInstanceOf[ClassTag[A]]
      else if (isShort(c)) ShortClass.asInstanceOf[ClassTag[A]]
      else if (isInt(c)) IntClass.asInstanceOf[ClassTag[A]]
      else if (isLong(c)) LongClass.asInstanceOf[ClassTag[A]]
      else if (isFloat(c)) FloatClass.asInstanceOf[ClassTag[A]]
      else if (isDouble(c)) DoubleClass.asInstanceOf[ClassTag[A]]
      else if (isChar(c)) CharClass.asInstanceOf[ClassTag[A]]
      else classTag[AnyRef].asInstanceOf[ClassTag[A]] // TODO: Find a better way

    private def isBoolean(c: ClassTag[_]): Boolean =
      c == BooleanClass || c == BooleanClassBox
    private def isByte(c: ClassTag[_]): Boolean =
      c == ByteClass || c == ByteClassBox
    private def isShort(c: ClassTag[_]): Boolean =
      c == ShortClass || c == ShortClassBox
    private def isInt(c: ClassTag[_]): Boolean =
      c == IntClass || c == IntClassBox
    private def isLong(c: ClassTag[_]): Boolean =
      c == LongClass || c == LongClassBox
    private def isFloat(c: ClassTag[_]): Boolean =
      c == FloatClass || c == FloatClassBox
    private def isDouble(c: ClassTag[_]): Boolean =
      c == DoubleClass || c == DoubleClassBox
    private def isChar(c: ClassTag[_]): Boolean =
      c == CharClass || c == CharClassBox

    private val BooleanClass    = classTag[Boolean]
    private val BooleanClassBox = classTag[java.lang.Boolean]
    private val ByteClass       = classTag[Byte]
    private val ByteClassBox    = classTag[java.lang.Byte]
    private val ShortClass      = classTag[Short]
    private val ShortClassBox   = classTag[java.lang.Short]
    private val IntClass        = classTag[Int]
    private val IntClassBox     = classTag[java.lang.Integer]
    private val LongClass       = classTag[Long]
    private val LongClassBox    = classTag[java.lang.Long]
    private val FloatClass      = classTag[Float]
    private val FloatClassBox   = classTag[java.lang.Float]
    private val DoubleClass     = classTag[Double]
    private val DoubleClassBox  = classTag[java.lang.Double]
    private val CharClass       = classTag[Char]
    private val CharClassBox    = classTag[java.lang.Character]
  }
}
