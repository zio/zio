package zio.blocks.schema.binding

import java.util

/**
 * Temporary storage to be used during encoding and decoding for schema-based
 * data structures. These are mutable and should be cached with thread locals,
 * fiber locals, or pools, to ensure zero-allocation during encoding / decoding.
 */
class Registers private {
  import RegisterOffset.RegisterOffset

  private[this] var booleans: Array[Boolean] = new Array[Boolean](4)
  private[this] var bytes: Array[Byte]       = new Array[Byte](4)
  private[this] var shorts: Array[Short]     = new Array[Short](4)
  private[this] var ints: Array[Int]         = new Array[Int](4)
  private[this] var longs: Array[Long]       = new Array[Long](4)
  private[this] var floats: Array[Float]     = new Array[Float](4)
  private[this] var doubles: Array[Double]   = new Array[Double](4)
  private[this] var chars: Array[Char]       = new Array[Char](4)
  private[this] var objects: Array[AnyRef]   = new Array[AnyRef](4)

  def getBoolean(baseOffset: RegisterOffset, relativeIndex: Int): Boolean =
    booleans(RegisterOffset.getBooleans(baseOffset) + relativeIndex)

  def getByte(baseOffset: RegisterOffset, relativeIndex: Int): Byte =
    bytes(RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getShort(baseOffset: RegisterOffset, relativeIndex: Int): Short =
    shorts(RegisterOffset.getShorts(baseOffset) + relativeIndex)

  def getInt(baseOffset: RegisterOffset, relativeIndex: Int): Int =
    ints(RegisterOffset.getInts(baseOffset) + relativeIndex)

  def getLong(baseOffset: RegisterOffset, relativeIndex: Int): Long =
    longs(RegisterOffset.getLongs(baseOffset) + relativeIndex)

  def getFloat(baseOffset: RegisterOffset, relativeIndex: Int): Float =
    floats(RegisterOffset.getFloats(baseOffset) + relativeIndex)

  def getDouble(baseOffset: RegisterOffset, relativeIndex: Int): Double =
    doubles(RegisterOffset.getDoubles(baseOffset) + relativeIndex)

  def getChar(baseOffset: RegisterOffset, relativeIndex: Int): Char =
    chars(RegisterOffset.getChars(baseOffset) + relativeIndex)

  def getObject(baseOffset: RegisterOffset, relativeIndex: Int): AnyRef =
    objects(RegisterOffset.getObjects(baseOffset) + relativeIndex)

  def setBoolean(baseOffset: RegisterOffset, relativeIndex: Int, value: Boolean): Unit = {
    val absoluteIndex = RegisterOffset.getBooleans(baseOffset) + relativeIndex
    if (absoluteIndex >= booleans.length) {
      booleans = util.Arrays.copyOf(booleans, Math.max(booleans.length << 1, absoluteIndex + 1))
    }
    booleans(absoluteIndex) = value
  }

  def setByte(baseOffset: RegisterOffset, relativeIndex: Int, value: Byte): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex >= bytes.length) {
      bytes = util.Arrays.copyOf(bytes, Math.max(bytes.length << 1, absoluteIndex + 1))
    }
    bytes(absoluteIndex) = value
  }

  def setShort(baseOffset: RegisterOffset, relativeIndex: Int, value: Short): Unit = {
    val absoluteIndex = RegisterOffset.getShorts(baseOffset) + relativeIndex
    if (absoluteIndex >= shorts.length) {
      shorts = util.Arrays.copyOf(shorts, Math.max(shorts.length << 1, absoluteIndex + 1))
    }
    shorts(absoluteIndex) = value
  }

  def setInt(baseOffset: RegisterOffset, relativeIndex: Int, value: Int): Unit = {
    val absoluteIndex = RegisterOffset.getInts(baseOffset) + relativeIndex
    if (absoluteIndex >= ints.length) {
      ints = util.Arrays.copyOf(ints, Math.max(ints.length << 1, absoluteIndex + 1))
    }
    ints(absoluteIndex) = value
  }

  def setLong(baseOffset: RegisterOffset, relativeIndex: Int, value: Long): Unit = {
    val absoluteIndex = RegisterOffset.getLongs(baseOffset) + relativeIndex
    if (absoluteIndex >= longs.length) {
      longs = util.Arrays.copyOf(longs, Math.max(longs.length << 1, absoluteIndex + 1))
    }
    longs(absoluteIndex) = value
  }

  def setFloat(baseOffset: RegisterOffset, relativeIndex: Int, value: Float): Unit = {
    val absoluteIndex = RegisterOffset.getFloats(baseOffset) + relativeIndex
    if (absoluteIndex >= floats.length) {
      floats = util.Arrays.copyOf(floats, Math.max(floats.length << 1, absoluteIndex + 1))
    }
    floats(absoluteIndex) = value
  }

  def setDouble(baseOffset: RegisterOffset, relativeIndex: Int, value: Double): Unit = {
    val absoluteIndex = RegisterOffset.getDoubles(baseOffset) + relativeIndex
    if (absoluteIndex >= doubles.length) {
      doubles = util.Arrays.copyOf(doubles, Math.max(doubles.length << 1, absoluteIndex + 1))
    }
    doubles(absoluteIndex) = value
  }

  def setChar(baseOffset: RegisterOffset, relativeIndex: Int, value: Char): Unit = {
    val absoluteIndex = RegisterOffset.getChars(baseOffset) + relativeIndex
    if (absoluteIndex >= chars.length) {
      chars = util.Arrays.copyOf(chars, Math.max(chars.length << 1, absoluteIndex + 1))
    }
    chars(absoluteIndex) = value
  }

  def setObject(baseOffset: RegisterOffset, relativeIndex: Int, value: AnyRef): Unit = {
    val absoluteIndex = RegisterOffset.getObjects(baseOffset) + relativeIndex
    if (absoluteIndex >= objects.length) {
      objects = util.Arrays.copyOf(objects, Math.max(objects.length << 1, absoluteIndex + 1))
    }
    objects(absoluteIndex) = value
  }
}
object Registers {
  def apply(): Registers = new Registers()
}
