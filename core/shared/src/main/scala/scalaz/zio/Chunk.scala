package scalaz.zio

import scala.reflect._

/**
 * A `Chunk[A]` represents a chunk of values of type `A`. Chunks are designed
 * are usually backed by arrays, but expose a purely functional, safe interface
 * to the underlying elements, and they become lazy on operations that would be
 * costly with arrays, such as repeated concatenation.
 */
sealed trait Chunk[@specialized +A] { self =>

  /**
   * The number of elements in the chunk.
   */
  def length: Int

  /**
   * Determines if the chunk is empty.
   */
  final def isEmpty: Boolean = length == 0

  /**
   * Determines if the chunk is not empty.
   */
  final def notEmpty: Boolean = length > 0

  /**
   * Returns the concatenation of this chunk with the specified chunk.
   */
  final def ++[A1 >: A](that: Chunk[A1]): Chunk[A1] =
    if (self.length == 0) that
    else if (that.length == 0) self
    else Chunk.Concat(self, that)

  /**
   * Returns a filtered, mapped subset of the elements of this chunk.
   */
  def collect[@specialized B](p: PartialFunction[A, B]): Chunk[B] =
    self.materialize.collect(p)

  /**
   * Drops the first `n` elements of the chunk.
   */
  final def drop(n: Int): Chunk[A] = {
    val len = self.length

    if (n <= 0) self
    else if (n == len) Chunk.empty
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
   * Returns the concatenation of mapping every element into a new chunk using
   * the specified function.
   */
  def flatMap[@specialized B](f: A => Chunk[B]): Chunk[B] = {
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
    chunks = chunks.reverse

    if (B0 == null) Chunk.empty
    else {
      implicit val B: ClassTag[B] = B0

      val dest: Array[B] = Array.ofDim(total)

      val it = chunks.iterator
      var n  = 0
      while (it.hasNext) {
        val chunk = it.next

        chunk.toArray(n, dest)
        n += chunk.length
      }

      Chunk.Arr(dest)
    }
  }

  final def foldMLazy[R, E, S](z: S)(pred: S => Boolean)(f: (S, A) => ZIO[R, E, S]): ZIO[R, E, S] = {
    val len = length

    def loop(s: S, i: Int): ZIO[R, E, S] =
      if (i >= len) IO.succeed(s)
      else {
        if (pred(s)) f(s, self(i)).flatMap(loop(_, i + 1))
        else IO.succeed(s)
      }

    loop(z, 0)
  }

  final def foldLeftLazy[S](z: S)(pred: S => Boolean)(f: (S, A) => S): S = {
    val len = length
    var s   = z
    var i   = 0
    while (i < len && pred(s)) {
      s = f(s, self(i))
      i += 1
    }
    s
  }

  /**
   * Folds over the elements in this chunk from the left.
   */
  def foldLeft[@specialized S](s0: S)(f: (S, A) => S): S = {
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
    foldLeft[ZIO[R, E, S]](IO.succeed(s)) { (s, a) =>
      s.flatMap(f(_, a))
    }

  /**
   * Folds over the elements in this chunk from the right.
   */
  def foldRight[@specialized S](s0: S)(f: (A, S) => S): S = {
    val len = self.length
    var s   = s0

    var i = len - 1
    while (i >= 0) {
      s = f(self(i), s)
      i -= 1
    }

    s
  }

  override final def hashCode: Int = toArray.toSeq.hashCode

  /**
   * Returns a chunk with the elements mapped by the specified function.
   */
  def map[@specialized B](f: A => B): Chunk[B] = {
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
   * Materializes a chunk into a chunk backed by an array. This method can
   * improve the performance of bulk operations.
   */
  def materialize[A1 >: A]: Chunk[A1] = Chunk.Arr(toArray)

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
   * Statefully maps over the chunk, producing new elements of type `B`.
   */
  final def mapAccum[@specialized S1, @specialized B](s1: S1)(f1: (S1, A) => (S1, B)): (S1, Chunk[B]) = {
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
   * Converts the chunk into an array.
   */
  def toArray[A1 >: A]: Array[A1] = {
    implicit val A1: ClassTag[A1] = Chunk.classTagOf(self)

    val dest = Array.ofDim[A1](self.length)

    self.toArray(0, dest)

    dest
  }

  def toSeq: Seq[A] = {
    val seqBuilder = Seq.newBuilder[A]
    var i          = 0
    val len        = length
    seqBuilder.sizeHint(len)
    while (i < len) {
      seqBuilder += apply(i)
      i += 1
    }
    seqBuilder.result()
  }

  override def toString: String =
    toArray.mkString(s"${self.getClass.getSimpleName}(", ",", ")")

  /**
   * Effectfully traverses the elements of this chunk.
   */
  final def traverse[E, B](f: A => IO[E, B]): IO[E, Chunk[B]] = {
    val len                    = self.length
    var array: IO[E, Array[B]] = IO.succeed(null.asInstanceOf[Array[B]])
    var i                      = 0

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
   * Effectfully traverses the elements of this chunk purely for the effects.
   */
  final def traverse_[R, E](f: A => ZIO[R, E, _]): ZIO[R, E, Unit] = {
    val len               = self.length
    var zio: ZIO[R, E, _] = ZIO.unit
    var i                 = 0

    while (i < len) {
      val a = self(i)
      zio = zio *> f(a)
      i += 1
    }

    zio.void
  }

  /**
   * Returns two splits of this chunk at the specified index.
   */
  final def splitAt(n: Int): (Chunk[A], Chunk[A]) =
    (take(n), drop(n))

  /**
   * Zips this chunk with the specified chunk using the specified combiner.
   */
  final def zipWith[@specialized B, @specialized C](that: Chunk[B])(f: (A, B) => C): Chunk[C] = {
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

  /**
   * Zips this chunk with the index of every element, starting from the initial
   * index value.
   */
  final def zipWithIndex0(indexOffset: Int): Chunk[(A, Int)] = {
    val len  = self.length
    val dest = Array.ofDim[(A, Int)](len)

    var i = 0

    while (i < len) {
      dest(i) = (self(i), i + indexOffset)

      i += 1
    }

    Chunk.Arr(dest)
  }

  /**
   * Zips this chunk with the index of every element.
   */
  final def zipWithIndex: Chunk[(A, Int)] = zipWithIndex0(0)

  protected[zio] def apply(n: Int): A
  protected[zio] def foreach(f: A => Unit): Unit

  //noinspection AccessorLikeMethodIsUnit
  protected[zio] def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit
}

object Chunk {

  /**
   * Returns a chunk from a number of values.
   */
  final def apply[A](as: A*): Chunk[A] = fromIterable(as)

  /**
   * Returns the empty chunk.
   */
  final val empty: Chunk[Nothing] = Empty

  /**
   * Returns a chunk backed by an array.
   */
  final def fromArray[@specialized A](array: Array[A]): Chunk[A] = Arr(array)

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
   * Returns a singleton chunk.
   */
  final def succeedLazy[@specialized A](a: A): Chunk[A] = Singleton(a)

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

  private case class Arr[@specialized A](private val array: Array[A]) extends Chunk[A] {
    implicit lazy val classTag: ClassTag[A] = ClassTag(array.getClass.getComponentType)

    override def collect[@specialized B](p: PartialFunction[A, B]): Chunk[B] = {
      val self = array
      val len  = self.length
      var dest = null.asInstanceOf[Array[B]]

      var i = 0
      var j = 0
      while (i < len) {
        val a = self(i)

        if (p.isDefinedAt(a)) {
          val b = p(a)

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

    override def flatMap[@specialized B](f: A => Chunk[B]): Chunk[B] = {
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
      chunks = chunks.reverse

      if (B0 == null) Chunk.empty
      else {
        implicit val B: ClassTag[B] = B0

        val dest: Array[B] = Array.ofDim(total)

        val it = chunks.iterator
        var n  = 0
        while (it.hasNext) {
          val chunk = it.next

          chunk.toArray(n, dest)
          n += chunk.length
        }

        Arr(dest)
      }
    }

    override def foldLeft[@specialized S](s0: S)(f: (S, A) => S): S = {
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

    override def foldRight[@specialized S](s0: S)(f: (A, S) => S): S = {
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

    override def map[@specialized B](f: A => B): Chunk[B] = {
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

    override def toArray[A1 >: A]: Array[A1] = array.asInstanceOf[Array[A1]]

    override def length: Int = array.length

    override def apply(n: Int): A = array(n)

    override def foreach(f: A => Unit): Unit = array.foreach(f)

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit =
      Array.copy(array, 0, dest, n, length)
  }

  private case class Concat[@specialized A](l: Chunk[A], r: Chunk[A]) extends Chunk[A] {
    self =>
    implicit lazy val classTag: ClassTag[A] =
      l match {
        case Empty => classTagOf(r)
        case _     => classTagOf(l)
      }

    override def length: Int = l.length + r.length

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
    override def length: Int = 0

    protected[zio] def apply(n: Int): Nothing = throw new ArrayIndexOutOfBoundsException(s"Empty chunk access to $n")

    protected[zio] def foreach(f: Nothing => Unit): Unit = ()

    protected[zio] def toArray[A1 >: Nothing](n: Int, dest: Array[A1]): Unit = ()

    override def toArray[A1]: Array[A1] = {
      implicit val A1: ClassTag[A1] = Chunk.classTagOf(self)
      Array.empty
    }
  }

  private case class Singleton[A](a: A) extends Chunk[A] {
    implicit lazy val classTag: ClassTag[A] = Tags.fromValue(a)

    def length = 1

    override def apply(n: Int): A =
      if (n == 0) a
      else throw new ArrayIndexOutOfBoundsException(s"Singleton chunk access to $n")

    override def foreach(f: A => Unit): Unit = f(a)

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit =
      dest(n) = a
  }

  private case class Slice[@specialized A](private val chunk: Chunk[A], offset: Int, l: Int) extends Chunk[A] {
    implicit lazy val classTag: ClassTag[A] = classTagOf(chunk)

    override def apply(n: Int): A = chunk.apply(offset + n)

    override def length: Int = l

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

  private case class VectorChunk[@specialized A](private val vector: Vector[A]) extends Chunk[A] {
    implicit lazy val classTag: ClassTag[A] = Tags.fromValue(vector(0))

    final def length: Int = vector.length

    override def apply(n: Int): A = vector(n)

    override def foreach(f: A => Unit): Unit = vector.foreach(f)

    override def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = { val _ = vector.copyToArray(dest, n, length) }
  }

  private object Tags {
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
      else classTag[java.lang.Object].asInstanceOf[ClassTag[A]]

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
