package zio.blocks.schema.binding

trait SeqDeconstructor[C[_]] {
  def deconstruct[A](c: C[A]): Iterator[A]
}
object SeqDeconstructor {
  sealed trait SpecializedIndexed[C[_]] extends SeqDeconstructor[C] {
    def elementType[A](c: C[A]): RegisterType[A]

    def length[A](c: C[A]): Int

    def objectAt[A](c: C[A], index: Int): A

    def booleanAt(c: C[Boolean], index: Int): Boolean

    def byteAt(c: C[Byte], index: Int): Byte

    def shortAt(c: C[Short], index: Int): Short

    def intAt(c: C[Int], index: Int): Int

    def longAt(c: C[Long], index: Int): Long

    def floatAt(c: C[Float], index: Int): Float

    def doubleAt(c: C[Double], index: Int): Double

    def charAt(c: C[Char], index: Int): Char
  }

  def apply[C[_]](implicit sd: SeqDeconstructor[C]): SeqDeconstructor[C] = sd

  implicit val setDeconstructor: SeqDeconstructor[Set] = new SeqDeconstructor[Set] {
    def deconstruct[A](c: Set[A]): Iterator[A] = c.iterator
  }

  implicit val listDeconstructor: SeqDeconstructor[List] = new SeqDeconstructor[List] {
    def deconstruct[A](c: List[A]): Iterator[A] = c.iterator
  }

  implicit val vectorDeconstructor: SeqDeconstructor[Vector] = new SeqDeconstructor[Vector] {
    def deconstruct[A](c: Vector[A]): Iterator[A] = c.iterator
  }

  implicit val arrayDeconstructor: SpecializedIndexed[Array] = new SpecializedIndexed[Array] {
    def deconstruct[A](c: Array[A]): Iterator[A] = c.iterator

    def elementType[A](c: Array[A]): RegisterType[A] = c match {
      case _: Array[Boolean] => RegisterType.Boolean
      case _: Array[Byte]    => RegisterType.Byte
      case _: Array[Short]   => RegisterType.Short
      case _: Array[Int]     => RegisterType.Int
      case _: Array[Long]    => RegisterType.Long
      case _: Array[Float]   => RegisterType.Float
      case _: Array[Double]  => RegisterType.Double
      case _: Array[Char]    => RegisterType.Char
      case _                 => RegisterType.Object().asInstanceOf[RegisterType[A]]
    }

    def length[A](c: Array[A]): Int = c.length

    def objectAt[A](c: Array[A], index: Int): A = c(index)

    def booleanAt(c: Array[Boolean], index: Int): Boolean = c(index)

    def byteAt(c: Array[Byte], index: Int): Byte = c(index)

    def shortAt(c: Array[Short], index: Int): Short = c(index)

    def intAt(c: Array[Int], index: Int): Int = c(index)

    def longAt(c: Array[Long], index: Int): Long = c(index)

    def floatAt(c: Array[Float], index: Int): Float = c(index)

    def doubleAt(c: Array[Double], index: Int): Double = c(index)

    def charAt(c: Array[Char], index: Int): Char = c(index)
  }
}
