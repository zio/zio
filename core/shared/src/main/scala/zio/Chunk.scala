/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

import scala.collection.mutable.Builder
import scala.reflect.{ classTag, ClassTag }
import java.nio._

/**
 * A `Chunk[A]` represents a chunk of values of type `A`. Chunks are designed
 * are usually backed by arrays, but expose a purely functional, safe interface
 * to the underlying elements, and they become lazy on operations that would be
 * costly with arrays, such as repeated concatenation.
 */
sealed trait Chunk[+A] { self =>

  /**
   * The number of elements in the chunk.
   */
  val length: Int

  /**
   * Returns the concatenation of this chunk with the specified chunk.
   */
  final def ++[A1 >: A](that: Chunk[A1]): Chunk[A1] =
    if (self.length == 0) that
    else if (that.length == 0) self
    else Chunk.Concat(self, that)

  /**
   * Appends an element to the chunk
   */
  final def +[A1 >: A](a: A1): Chunk[A1] =
    if (self.length == 0) Chunk.single(a)
    else Chunk.Concat(self, Chunk.single(a))

  /**
   * Returns a filtered, mapped subset of the elements of this chunk.
   */
  def collect[B](pf: PartialFunction[A, B]): Chunk[B] = self.materialize.collect(pf)

  /**
   * Returns a filtered, mapped subset of the elements of this chunk based on a .
   */
  def collectM[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] = self.materialize.collectM(pf)

  /**
   * Transforms all elements of the chunk for as long as the specified partial function is defined.
   */
  def collectWhile[B](pf: PartialFunction[A, B]): Chunk[B] = self.materialize.collectWhile(pf)

  def collectWhileM[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] =
    self.materialize.collectWhileM(pf)

  /**
   * Drops the first `n` elements of the chunk.
   */
  final def drop(n: Int): Chunk[A] = {
    val len = self.length

    if (n <= 0) self
    else if (n >= len) Chunk.empty
    else
      self match {
        case Chunk.Slice(c, o, l)        => Chunk.Slice(c, o + n, l - n)
        case Chunk.Singleton(_) if n > 0 => Chunk.empty
        case c @ Chunk.Singleton(_)      => c
        case Chunk.Empty                 => Chunk.empty
        case _                           => Chunk.Slice(self, n, len - n)
      }
  }

  /**
   * Drops all elements so long as the predicate returns true.
   */
  def dropWhile(f: A => Boolean): Chunk[A] = {
    val len = self.length

    var i = 0
    while (i < len && f(self(i))) {
      i += 1
    }

    drop(i)
  }

  override def equals(that: Any): Boolean = that match {
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
    case _ => false
  }

  /**
   * Returns a filtered subset of this chunk.
   */
  def filter(f: A => Boolean): Chunk[A] = {
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
    else Chunk.Slice(Chunk.Arr(dest), 0, j)
  }

  /**
   * Filters this chunk by the specified effectful predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  final def filterM[R, E](f: A => ZIO[R, E, Boolean]): ZIO[R, E, Chunk[A]] = {
    implicit val A: ClassTag[A] = Chunk.classTagOf(this)

    val len                              = self.length
    var dest: ZIO[R, E, (Array[A], Int)] = ZIO.succeed((Array.ofDim[A](len), 0))

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
        else Chunk.Slice(Chunk.Arr(array), 0, arrLen)
    }
  }

  /**
   * Flattens a chunk of chunks into a single chunk by concatenating all chunks.
   */
  def flatten[B](implicit ev: A <:< Chunk[B]): Chunk[B] =
    flatMap(ev(_))

  /**
   * Returns the concatenation of mapping every element into a new chunk using
   * the specified function.
   */
  def flatMap[B](f: A => Chunk[B]): Chunk[B] = {
    val len                    = self.length
    var chunks: List[Chunk[B]] = Nil

    var i               = 0
    var total           = 0
    var B0: ClassTag[B] = null.asInstanceOf[ClassTag[B]]
    while (i < len) {
      val chunk = f(self(i))

      if (chunk.length > 0) {
        if (B0 == null)
          B0 = Chunk.classTagOf(chunk)

        chunks ::= chunk
        total += chunk.length
      }

      i += 1
    }

    if (B0 == null) Chunk.empty
    else {
      implicit val B: ClassTag[B] = B0

      val dest: Array[B] = Array.ofDim(total)

      val it = chunks.iterator
      var n  = total
      while (it.hasNext) {
        val chunk = it.next
        n -= chunk.length
        chunk.toArray(n, dest)
      }

      Chunk.Arr(dest)
    }
  }

  /**
   * Folds over the elements in this chunk from the left.
   */
  def fold[S](s0: S)(f: (S, A) => S): S = {
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
    fold[ZIO[R, E, S]](IO.succeed(s)) { (s, a) =>
      s.flatMap(f(_, a))
    }

  /**
   * Folds over the elements in this chunk from the right.
   */
  def foldRight[S](s0: S)(f: (A, S) => S): S = {
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
      if (i >= len) IO.succeed(s)
      else {
        if (pred(s)) f(s, self(i)).flatMap(loop(_, i + 1))
        else IO.succeed(s)
      }

    loop(z, 0)
  }

  override final def hashCode: Int = toArrayOption match {
    case None        => Seq.empty[A].hashCode
    case Some(array) => array.toSeq.hashCode
  }

  /**
   * Determines if the chunk is empty.
   */
  final def isEmpty: Boolean = length == 0

  /**
   * Returns a chunk with the elements mapped by the specified function.
   */
  def map[B](f: A => B): Chunk[B] = {
    val len  = self.length
    var dest = null.asInstanceOf[Array[B]]

    var i = 0
    while (i < len) {
      val b = f(self(i))

      if (dest == null) {
        implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)

        dest = Array.ofDim[B](len)
      }

      dest(i) = b

      i = i + 1
    }

    if (dest != null) Chunk.Arr(dest)
    else Chunk.Empty
  }

  /**
   * Statefully maps over the chunk, producing new elements of type `B`.
   */
  final def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): (S1, Chunk[B]) = {
    var s: S1          = s1
    var i              = 0
    var dest: Array[B] = null.asInstanceOf[Array[B]]
    val len            = self.length

    while (i < len) {
      val a = self(i)
      val t = f1(s, a)

      s = t._1
      val b = t._2

      if (dest == null) {
        implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)

        dest = Array.ofDim(len)
      }

      dest(i) = b

      i += 1
    }

    s ->
      (if (dest == null) Chunk.empty
       else Chunk.Arr(dest))
  }

  /**
   * Materializes a chunk into a chunk backed by an array. This method can
   * improve the performance of bulk operations.
   */
  def materialize[A1 >: A]: Chunk[A1] = self.toArrayOption[A1] match {
    case None        => Chunk.Empty
    case Some(array) => Chunk.Arr(array)
  }

  /**
   * Generates a readable string representation of this chunk using the
   * specified start, separator, and end strings.
   */
  final def mkString(start: String, sep: String, end: String): String = {
    val builder = new scala.collection.mutable.StringBuilder()

    builder.append(start)

    var i   = 0
    val len = self.length

    while (i < len) {
      if (i != 0) builder.append(sep)
      builder.append(self(i).toString)
      i += 1
    }

    builder.append(end)

    builder.toString
  }

  /**
   * Generates a readable string representation of this chunk using the
   * specified separator string.
   */
  final def mkString(sep: String): String = mkString("", sep, "")

  /**
   * Generates a readable string representation of this chunk.
   */
  final def mkString: String = mkString("")

  /**
   * Determines if the chunk is not empty.
   */
  final def nonEmpty: Boolean = length > 0

  /**
   * The number of elements in the chunk.
   */
  final def size: Int = length

  /**
   * Returns two splits of this chunk at the specified index.
   */
  final def splitAt(n: Int): (Chunk[A], Chunk[A]) =
    (take(n), drop(n))

  /**
   * Takes the first `n` elements of the chunk.
   */
  final def take(n: Int): Chunk[A] =
    if (n <= 0) Chunk.Empty
    else if (n >= length) this
    else
      self match {
        case Chunk.Empty => Chunk.Empty
        case Chunk.Slice(c, o, l) =>
          if (n >= l) this
          else Chunk.Slice(c, o, n)
        case c @ Chunk.Singleton(_) => c
        case _                      => Chunk.Slice(self, 0, n)
      }

  /**
   * Takes all elements so long as the predicate returns true.
   */
  def takeWhile(f: A => Boolean): Chunk[A] = {
    val len = self.length

    var i = 0
    while (i < len && f(self(i))) {
      i += 1
    }

    take(i)
  }

  /**
   * A helper function that converts the chunk into an array if it is not empty.
   */
  private def toArrayOption[A1 >: A]: Option[Array[A1]] = self match {
    case Chunk.Empty => None
    case chunk       => Some(chunk.toArray(Chunk.classTagOf(self)))
  }

  /**
   * Converts the chunk into an array.
   */
  def toArray[A1 >: A](implicit tag: ClassTag[A1]): Array[A1] = {
    val dest = Array.ofDim[A1](self.length)

    self.toArray(0, dest)

    dest
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

  def toSeq: Seq[A] = {
    val seqBuilder = Seq.newBuilder[A]
    fromBuilder(seqBuilder)
  }

  def toList: List[A] = {
    val listBuilder = List.newBuilder[A]
    fromBuilder(listBuilder)
  }

  def toVector: Vector[A] = {
    val vectorBuilder = Vector.newBuilder[A]
    fromBuilder(vectorBuilder)
  }

  override def toString: String =
    toArrayOption.fold(s"${self.getClass.getSimpleName}()")(_.mkString(s"${self.getClass.getSimpleName}(", ",", ")"))

  /**
   * Statefully and effectfully maps over the elements of this chunk to produce
   * new elements.
   */
  final def mapAccumM[R, E, S1, B](s1: S1)(f1: (S1, A) => ZIO[R, E, (S1, B)]): ZIO[R, E, (S1, Chunk[B])] = {
    val len                             = self.length
    var dest: ZIO[R, E, (S1, Array[B])] = UIO.succeed((s1, null.asInstanceOf[Array[B]]))

    var i = 0
    while (i < len) {
      val j = i
      dest = dest.flatMap {
        case (state, array) =>
          f1(state, self(j)).map {
            case (state2, b) =>
              val array2 = if (array == null) {
                implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)
                Array.ofDim[B](len)
              } else array

              array2(j) = b
              (state2, array2)
          }
      }

      i += 1
    }

    dest.map {
      case (state, array) =>
        if (array == null) (state, Chunk.empty)
        else (state, Chunk.fromArray(array))
    }
  }

  /**
   * Effectfully maps the elements of this chunk.
   */
  final def mapM[R, E, B](f: A => ZIO[R, E, B]): ZIO[R, E, Chunk[B]] = {
    val len                        = self.length
    var array: ZIO[R, E, Array[B]] = IO.succeed(null.asInstanceOf[Array[B]])
    var i                          = 0

    while (i < len) {
      val j = i
      array = array.zipWith(f(self(j))) { (array, b) =>
        val array2 = if (array == null) {
          implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)
          Array.ofDim[B](len)
        } else array

        array2(j) = b
        array2
      }

      i += 1
    }

    array.map(
      array =>
        if (array == null) Chunk.empty
        else Chunk.fromArray(array)
    )
  }

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
   * Zips this chunk with the specified chunk using the specified combiner.
   */
  final def zipWith[B, C](that: Chunk[B])(f: (A, B) => C): Chunk[C] = {
    val size = self.length.min(that.length)

    if (size == 0) Chunk.empty
    else {
      var dest = null.asInstanceOf[Array[C]]

      var i = 0
      while (i < size) {
        val c = f(self(i), that(i))
        if (dest == null) {
          implicit val C: ClassTag[C] = Chunk.Tags.fromValue(c)

          dest = Array.ofDim[C](size)
        }

        dest(i) = c

        i = i + 1
      }

      Chunk.Arr(dest)
    }
  }

  def zipAllWith[B, C](
    that: Chunk[B]
  )(left: A => C, right: B => C)(both: (A, B) => C): Chunk[C] = {

    val size = self.length.max(that.length)

    if (size == 0) Chunk.empty
    else {
      var j                       = 0
      implicit val C: ClassTag[C] = Chunk.Tags.fromValue(if (self.length > 0) left(self(0)) else right(that(0)))
      val dest                    = Array.ofDim[C](size)

      while (j < size) {
        val c =
          if (j < self.length) {
            if (j < that.length) both(self(j), that(j))
            else (left(self(j)))
          } else right(that(j))

        dest(j) = c

        j = j + 1

      }

      Chunk.Arr(dest)

    }
  }

  /**
   * Zips this chunk with the index of every element.
   */
  final def zipWithIndex: Chunk[(A, Int)] = zipWithIndexFrom(0)

  /**
   * Zips this chunk with the index of every element, starting from the initial
   * index value.
   */
  final def zipWithIndexFrom(indexOffset: Int): Chunk[(A, Int)] = {
    val len  = self.length
    val dest = Array.ofDim[(A, Int)](len)

    var i = 0

    while (i < len) {
      dest(i) = (self(i), i + indexOffset)

      i += 1
    }

    Chunk.Arr(dest)
  }

  protected[zio] def apply(n: Int): A
  protected[zio] def foreach(f: A => Unit): Unit

  //noinspection AccessorLikeMethodIsUnit
  protected[zio] def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit
}

object Chunk {

  /**
   * Returns the empty chunk.
   */
  final val empty: Chunk[Nothing] = Empty

  /**
   * Returns a chunk from a number of values.
   */
  final def apply[A](as: A*): Chunk[A] = fromIterable(as)

  /**
   * Returns a chunk backed by an array.
   */
  final def fromArray[A](array: Array[A]): Chunk[A] = Arr(array)

  /**
   * Returns a chunk backed by a [[java.nio.ByteBuffer]].
   */
  final def fromByteBuffer(buffer: ByteBuffer): Chunk[Byte] =
    Buff.arrayBacked(buffer).getOrElse(Buff.manualByteBuffer(buffer))

  /**
   * Returns a chunk backed by a [[java.nio.CharBuffer]].
   */
  final def fromCharBuffer(buffer: CharBuffer): Chunk[Char] =
    Buff.arrayBacked(buffer).getOrElse(Buff.manualCharBuffer(buffer))

  /**
   * Returns a chunk backed by an iterable.
   */
  final def fromIterable[A](it: Iterable[A]): Chunk[A] =
    if (it.size <= 0) Empty
    else if (it.size == 1) Singleton(it.head)
    else {
      it match {
        case l: Vector[A] => VectorChunk(l)
        case _ =>
          val first = it.head

          implicit val A: ClassTag[A] = Tags.fromValue(first)

          fromArray(it.toArray)
      }
    }

  /**
   * Returns a singleton chunk, eagerly evaluated.
   */
  final def single[A](a: A): Chunk[A] = Singleton(a)

  /**
   * Alias for [[Chunk.single]].
   */
  final def succeed[A](a: A): Chunk[A] = single(a)

  /**
   * Returns the `ClassTag` for the element type of the chunk.
   */
  private final def classTagOf[A](chunk: Chunk[A]): ClassTag[A] = chunk match {
    case x: Arr[A]         => x.classTag
    case x: Concat[A]      => x.classTag
    case Empty             => classTag[java.lang.Object].asInstanceOf[ClassTag[A]]
    case x: Singleton[A]   => x.classTag
    case x: Slice[A]       => x.classTag
    case x: VectorChunk[A] => x.classTag
  }

  private case class Arr[A](private val array: Array[A]) extends Chunk[A] {
    implicit val classTag: ClassTag[A] = ClassTag(array.getClass.getComponentType)

    override def collect[B](pf: PartialFunction[A, B]): Chunk[B] = {
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
      else Chunk.Slice(Chunk.Arr(dest), 0, j)
    }

    override def collectM[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] = {
      val len                       = array.length
      val orElse                    = (_: A) => UIO.succeed(null.asInstanceOf[B])
      var dest: ZIO[R, E, Array[B]] = UIO.succeed(null.asInstanceOf[Array[B]])

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

      dest.map(
        array =>
          if (array == null) Chunk.empty
          else Chunk.Slice(Chunk.Arr(array), 0, j)
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
      else Chunk.Slice(Chunk.Arr(dest), 0, j)
    }

    override def collectWhileM[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] = {
      val self                      = array
      val len                       = self.length
      var dest: ZIO[R, E, Array[B]] = UIO.succeed(null.asInstanceOf[Array[B]])

      var i    = 0
      var j    = 0
      var done = false
      val orElse = (_: A) => {
        done = true
        UIO.succeed(null.asInstanceOf[B])
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

      dest.map(
        array =>
          if (array == null) Chunk.empty
          else Chunk.Slice(Chunk.Arr(array), 0, j)
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

      if (dest == null) Chunk.Empty
      else Chunk.Slice(Chunk.Arr(dest), 0, j)
    }

    override def flatMap[B](f: A => Chunk[B]): Chunk[B] = {
      val self                   = array
      val len                    = self.length
      var chunks: List[Chunk[B]] = Nil

      var i               = 0
      var total           = 0
      var B0: ClassTag[B] = null.asInstanceOf[ClassTag[B]]
      while (i < len) {
        val chunk = f(self(i))

        if (chunk.length > 0) {
          if (B0 == null)
            B0 = Chunk.classTagOf(chunk)

          chunks ::= chunk
          total += chunk.length
        }

        i += 1
      }

      if (B0 == null) Chunk.empty
      else {
        implicit val B: ClassTag[B] = B0

        val dest: Array[B] = Array.ofDim(total)

        val it = chunks.iterator
        var n  = total
        while (it.hasNext) {
          val chunk = it.next
          n -= chunk.length
          chunk.toArray(n, dest)
        }

        Arr(dest)
      }
    }

    override def fold[S](s0: S)(f: (S, A) => S): S = {
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

    override def map[B](f: A => B): Chunk[B] = {
      val self = array
      val len  = self.length
      var dest = null.asInstanceOf[Array[B]]

      var i = 0
      while (i < len) {
        val b = f(self(i))

        if (dest == null) {
          implicit val B: ClassTag[B] = Chunk.Tags.fromValue(b)

          dest = Array.ofDim[B](len)
        }

        dest(i) = b

        i = i + 1
      }

      if (dest != null) Chunk.Arr(dest)
      else Chunk.Empty
    }

    override def materialize[A1 >: A]: Chunk[A1] = this

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

    override def toArray[A1 >: A](implicit tag: ClassTag[A1]): Array[A1] = array.asInstanceOf[Array[A1]]

    override val length: Int = array.length

    override def apply(n: Int): A = array(n)

    override def foreach(f: A => Unit): Unit = array.foreach(f)

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit =
      Array.copy(array, 0, dest, n, length)
  }

  private case class Concat[A](l: Chunk[A], r: Chunk[A]) extends Chunk[A] {
    self =>
    implicit val classTag: ClassTag[A] =
      l match {
        case Empty => classTagOf(r)
        case _     => classTagOf(l)
      }

    override val length: Int = l.length + r.length

    override def apply(n: Int): A = if (n < l.length) l(n) else r(n - l.length)

    override def foreach(f: A => Unit): Unit = {
      l.foreach(f)
      r.foreach(f)
    }

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = {
      l.toArray(n, dest)
      r.toArray(n + l.length, dest)
    }
  }

  private case object Empty extends Chunk[Nothing] { self =>
    override val length: Int = 0

    protected[zio] def apply(n: Int): Nothing = throw new ArrayIndexOutOfBoundsException(s"Empty chunk access to $n")

    override def collect[B](pf: PartialFunction[Nothing, B]): Chunk[B] = Empty

    override def collectM[R, E, B](pf: PartialFunction[Nothing, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] = UIO.succeed(Empty)

    override def collectWhile[B](pf: PartialFunction[Nothing, B]): Chunk[B] = Empty

    override def collectWhileM[R, E, B](pf: PartialFunction[Nothing, ZIO[R, E, B]]): ZIO[R, E, Chunk[B]] =
      UIO.succeed(Empty)

    protected[zio] def foreach(f: Nothing => Unit): Unit = ()

    protected[zio] def toArray[A1 >: Nothing](n: Int, dest: Array[A1]): Unit = ()

    override def toArray[A1](implicit tag: ClassTag[A1]): Array[A1] = Array.empty
  }

  private case class Singleton[A](a: A) extends Chunk[A] {
    implicit val classTag: ClassTag[A] = Tags.fromValue(a)

    override val length = 1

    override def apply(n: Int): A =
      if (n == 0) a
      else throw new ArrayIndexOutOfBoundsException(s"Singleton chunk access to $n")

    override def foreach(f: A => Unit): Unit = f(a)

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit =
      dest(n) = a
  }

  private case class Slice[A](private val chunk: Chunk[A], offset: Int, l: Int) extends Chunk[A] {
    implicit val classTag: ClassTag[A] = classTagOf(chunk)

    override def apply(n: Int): A = chunk.apply(offset + n)

    override val length: Int = l

    override def foreach(f: A => Unit): Unit = {
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

  private case class VectorChunk[A](private val vector: Vector[A]) extends Chunk[A] {
    implicit val classTag: ClassTag[A] = Tags.fromValue(vector(0))

    override val length: Int = vector.length

    override def apply(n: Int): A = vector(n)

    override def foreach(f: A => Unit): Unit = vector.foreach(f)

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = { val _ = vector.copyToArray(dest, n, length) }
  }

  private[zio] object Buff {
    final def arrayBacked[A](buffer: Buffer): Option[Chunk[A]] =
      if (!buffer.hasArray()) None
      else {
        val pos = buffer.position()
        val lim = buffer.limit()
        val cap = buffer.capacity()
        val arr = buffer.array().asInstanceOf[Array[A]]
        val off = buffer.arrayOffset()

        val chunk = if (off == 0 && pos == 0 && cap == arr.length && lim == cap) {
          // best case, reuse the underlying array
          fromArray(arr)
        } else {
          implicit val classTag: ClassTag[A] = ClassTag(arr.getClass.getComponentType)
          val len                            = lim - pos
          val dest                           = Array.ofDim[A](len)
          Array.copy(arr, pos, dest, 0, len)
          fromArray(dest)
        }

        Some(chunk.asInstanceOf[Chunk[A]])
      }

    final def manualByteBuffer(buffer: ByteBuffer): Chunk[Byte] = {
      val pos = buffer.position()
      val lim = buffer.limit()
      val len = lim - pos
      val dst = Array.ofDim[Byte](len)
      var i   = pos
      var j   = 0
      while (i < lim) {
        dst(j) = buffer.get(i)
        i += 1
        j += 1
      }
      Chunk.fromArray(dst)
    }

    final def manualCharBuffer(buffer: CharBuffer): Chunk[Char] = {
      val pos = buffer.position()
      val lim = buffer.limit()
      val len = lim - pos
      val dst = Array.ofDim[Char](len)
      var i   = pos
      var j   = 0
      while (i < lim) {
        dst(j) = buffer.get(i)
        i += 1
        j += 1
      }
      Chunk.fromArray(dst)
    }
  }

  private[zio] object Tags {
    final def fromValue[A](a: A): ClassTag[A] =
      unbox(ClassTag(a.getClass))

    private final def unbox[A](c: ClassTag[A]): ClassTag[A] =
      if (isBoolean(c)) BooleanClass.asInstanceOf[ClassTag[A]]
      else if (isByte(c)) ByteClass.asInstanceOf[ClassTag[A]]
      else if (isShort(c)) ShortClass.asInstanceOf[ClassTag[A]]
      else if (isInt(c)) IntClass.asInstanceOf[ClassTag[A]]
      else if (isLong(c)) LongClass.asInstanceOf[ClassTag[A]]
      else if (isFloat(c)) FloatClass.asInstanceOf[ClassTag[A]]
      else if (isDouble(c)) DoubleClass.asInstanceOf[ClassTag[A]]
      else if (isChar(c)) CharClass.asInstanceOf[ClassTag[A]]
      else classTag[AnyRef].asInstanceOf[ClassTag[A]] // TODO: Find a better way

    private final def isBoolean(c: ClassTag[_]): Boolean =
      c == BooleanClass || c == BooleanClassBox
    private final def isByte(c: ClassTag[_]): Boolean =
      c == ByteClass || c == ByteClassBox
    private final def isShort(c: ClassTag[_]): Boolean =
      c == ShortClass || c == ShortClassBox
    private final def isInt(c: ClassTag[_]): Boolean =
      c == IntClass || c == IntClassBox
    private final def isLong(c: ClassTag[_]): Boolean =
      c == LongClass || c == LongClassBox
    private final def isFloat(c: ClassTag[_]): Boolean =
      c == FloatClass || c == FloatClassBox
    private final def isDouble(c: ClassTag[_]): Boolean =
      c == DoubleClass || c == DoubleClassBox
    private final def isChar(c: ClassTag[_]): Boolean =
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
