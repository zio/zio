package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

import java.util

/**
 * Temporary storage to be used during encoding and decoding for schema-based
 * data structures. These are mutable and should be cached with thread locals,
 * fiber locals, or pools, to ensure zero-allocation during encoding / decoding.
 */
class Registers private (userRegister: RegisterOffset) {
  import RegisterOffset.RegisterOffset

  private[this] var primitives: Array[Byte] = new Array[Byte](RegisterOffset.getBytes(userRegister))
  private[this] var objects: Array[AnyRef]  = new Array[AnyRef](RegisterOffset.getObjects(userRegister))

  def getBoolean(baseOffset: RegisterOffset, relativeIndex: Int): Boolean =
    primitives(RegisterOffset.getBytes(baseOffset) + relativeIndex) != 0

  def getByte(baseOffset: RegisterOffset, relativeIndex: Int): Byte =
    primitives(RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getShort(baseOffset: RegisterOffset, relativeIndex: Int): Short = {
    val index = RegisterOffset.getBytes(baseOffset) + relativeIndex
    (primitives(index) & 0xff |
      (primitives(index + 1) & 0xff) << 8).toShort
  }

  def getInt(baseOffset: RegisterOffset, relativeIndex: Int): Int = {
    val index = RegisterOffset.getBytes(baseOffset) + relativeIndex
    primitives(index) & 0xff |
      (primitives(index + 1) & 0xff) << 8 |
      (primitives(index + 2) & 0xff) << 16 |
      (primitives(index + 3) << 24)
  }

  def getLong(baseOffset: RegisterOffset, relativeIndex: Int): Long = {
    val index = RegisterOffset.getBytes(baseOffset) + relativeIndex
    (primitives(index) & 0xff |
      (primitives(index + 1) & 0xff) << 8 |
      (primitives(index + 2) & 0xff) << 16).toLong |
      (primitives(index + 3) & 0xff).toLong << 24 |
      (primitives(index + 4) & 0xff).toLong << 32 |
      (primitives(index + 5) & 0xff).toLong << 40 |
      (primitives(index + 6) & 0xff).toLong << 48 |
      (primitives(index + 7) & 0xff).toLong << 56
  }

  def getFloat(baseOffset: RegisterOffset, relativeIndex: Int): Float = {
    val index = RegisterOffset.getBytes(baseOffset) + relativeIndex
    java.lang.Float.intBitsToFloat(
      primitives(index) & 0xff |
        (primitives(index + 1) & 0xff) << 8 |
        (primitives(index + 2) & 0xff) << 16 |
        (primitives(index + 3) << 24)
    )
  }

  def getDouble(baseOffset: RegisterOffset, relativeIndex: Int): Double = {
    val index = RegisterOffset.getBytes(baseOffset) + relativeIndex
    java.lang.Double.longBitsToDouble(
      (primitives(index) & 0xff |
        (primitives(index + 1) & 0xff) << 8 |
        (primitives(index + 2) & 0xff) << 16).toLong |
        (primitives(index + 3) & 0xff).toLong << 24 |
        (primitives(index + 4) & 0xff).toLong << 32 |
        (primitives(index + 5) & 0xff).toLong << 40 |
        (primitives(index + 6) & 0xff).toLong << 48 |
        (primitives(index + 7) & 0xff).toLong << 56
    )
  }

  def getChar(baseOffset: RegisterOffset, relativeIndex: Int): Char = {
    val index = RegisterOffset.getBytes(baseOffset) + relativeIndex
    (primitives(index) & 0xff |
      (primitives(index + 1) & 0xff) << 8).toChar
  }

  def getObject(baseOffset: RegisterOffset, relativeIndex: Int): AnyRef =
    objects(RegisterOffset.getObjects(baseOffset) + relativeIndex)

  def setBoolean(baseOffset: RegisterOffset, relativeIndex: Int, value: Boolean): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 1))
    }
    primitives(absoluteIndex) = if (value) (1: Byte) else (0: Byte)
  }

  def setByte(baseOffset: RegisterOffset, relativeIndex: Int, value: Byte): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 1))
    }
    primitives(absoluteIndex) = value
  }

  def setShort(baseOffset: RegisterOffset, relativeIndex: Int, value: Short): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 1 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 2))
    }
    primitives(absoluteIndex) = value.toByte
    primitives(absoluteIndex + 1) = (value >> 8).toByte
  }

  def setInt(baseOffset: RegisterOffset, relativeIndex: Int, value: Int): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 3 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 4))
    }
    primitives(absoluteIndex) = value.toByte
    primitives(absoluteIndex + 1) = (value >> 8).toByte
    primitives(absoluteIndex + 2) = (value >> 16).toByte
    primitives(absoluteIndex + 3) = (value >> 24).toByte
  }

  def setLong(baseOffset: RegisterOffset, relativeIndex: Int, value: Long): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 7 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 8))
    }
    primitives(absoluteIndex) = value.toByte
    primitives(absoluteIndex + 1) = (value >> 8).toByte
    primitives(absoluteIndex + 2) = (value >> 16).toByte
    primitives(absoluteIndex + 3) = (value >> 24).toByte
    primitives(absoluteIndex + 4) = (value >> 32).toByte
    primitives(absoluteIndex + 5) = (value >> 40).toByte
    primitives(absoluteIndex + 6) = (value >> 48).toByte
    primitives(absoluteIndex + 7) = (value >> 56).toByte
  }

  def setFloat(baseOffset: RegisterOffset, relativeIndex: Int, value: Float): Unit = {
    val bits          = java.lang.Float.floatToIntBits(value)
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 3 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 4))
    }
    primitives(absoluteIndex) = bits.toByte
    primitives(absoluteIndex + 1) = (bits >> 8).toByte
    primitives(absoluteIndex + 2) = (bits >> 16).toByte
    primitives(absoluteIndex + 3) = (bits >> 24).toByte
  }

  def setDouble(baseOffset: RegisterOffset, relativeIndex: Int, value: Double): Unit = {
    val bits          = java.lang.Double.doubleToLongBits(value)
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 7 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 8))
    }
    primitives(absoluteIndex) = bits.toByte
    primitives(absoluteIndex + 1) = (bits >> 8).toByte
    primitives(absoluteIndex + 2) = (bits >> 16).toByte
    primitives(absoluteIndex + 3) = (bits >> 24).toByte
    primitives(absoluteIndex + 4) = (bits >> 32).toByte
    primitives(absoluteIndex + 5) = (bits >> 40).toByte
    primitives(absoluteIndex + 6) = (bits >> 48).toByte
    primitives(absoluteIndex + 7) = (bits >> 56).toByte
  }

  def setChar(baseOffset: RegisterOffset, relativeIndex: Int, value: Char): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 1 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 2))
    }
    primitives(absoluteIndex) = value.toByte
    primitives(absoluteIndex + 1) = (value >> 8).toByte
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
  def apply(usedRegisters: RegisterOffset): Registers = new Registers(usedRegisters)
}
