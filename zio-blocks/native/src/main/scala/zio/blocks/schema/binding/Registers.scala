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
    ByteArrayAccess.getBoolean(primitives, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getByte(baseOffset: RegisterOffset, relativeIndex: Int): Byte =
    primitives(RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getShort(baseOffset: RegisterOffset, relativeIndex: Int): Short =
    ByteArrayAccess.getShort(primitives, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getInt(baseOffset: RegisterOffset, relativeIndex: Int): Int =
    ByteArrayAccess.getInt(primitives, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getLong(baseOffset: RegisterOffset, relativeIndex: Int): Long =
    ByteArrayAccess.getLong(primitives, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getFloat(baseOffset: RegisterOffset, relativeIndex: Int): Float =
    ByteArrayAccess.getFloat(primitives, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getDouble(baseOffset: RegisterOffset, relativeIndex: Int): Double =
    ByteArrayAccess.getDouble(primitives, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getChar(baseOffset: RegisterOffset, relativeIndex: Int): Char =
    ByteArrayAccess.getChar(primitives, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getObject(baseOffset: RegisterOffset, relativeIndex: Int): AnyRef =
    objects(RegisterOffset.getObjects(baseOffset) + relativeIndex)

  def setBoolean(baseOffset: RegisterOffset, relativeIndex: Int, value: Boolean): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 1))
    }
    ByteArrayAccess.setBoolean(primitives, absoluteIndex, value)
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
    ByteArrayAccess.setShort(primitives, absoluteIndex, value)
  }

  def setInt(baseOffset: RegisterOffset, relativeIndex: Int, value: Int): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 3 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 4))
    }
    ByteArrayAccess.setInt(primitives, absoluteIndex, value)
  }

  def setLong(baseOffset: RegisterOffset, relativeIndex: Int, value: Long): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 7 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 8))
    }
    ByteArrayAccess.setLong(primitives, absoluteIndex, value)
  }

  def setFloat(baseOffset: RegisterOffset, relativeIndex: Int, value: Float): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 3 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 4))
    }
    ByteArrayAccess.setFloat(primitives, absoluteIndex, value)
  }

  def setDouble(baseOffset: RegisterOffset, relativeIndex: Int, value: Double): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 7 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 8))
    }
    ByteArrayAccess.setDouble(primitives, absoluteIndex, value)
  }

  def setChar(baseOffset: RegisterOffset, relativeIndex: Int, value: Char): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 1 >= primitives.length) {
      primitives = util.Arrays.copyOf(primitives, Math.max(primitives.length << 1, absoluteIndex + 2))
    }
    ByteArrayAccess.setChar(primitives, absoluteIndex, value)
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
