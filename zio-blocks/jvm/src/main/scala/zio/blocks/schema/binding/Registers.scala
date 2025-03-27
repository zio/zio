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

  private[this] var bytes: Array[Byte]     = new Array[Byte](RegisterOffset.getBytes(userRegister))
  private[this] var objects: Array[AnyRef] = new Array[AnyRef](RegisterOffset.getObjects(userRegister))

  def getBoolean(baseOffset: RegisterOffset, relativeIndex: Int): Boolean =
    bytes(RegisterOffset.getBytes(baseOffset) + relativeIndex) != 0

  def getByte(baseOffset: RegisterOffset, relativeIndex: Int): Byte =
    bytes(RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getShort(baseOffset: RegisterOffset, relativeIndex: Int): Short =
    ByteArrayAccess.getShort(bytes, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getInt(baseOffset: RegisterOffset, relativeIndex: Int): Int =
    ByteArrayAccess.getInt(bytes, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getLong(baseOffset: RegisterOffset, relativeIndex: Int): Long =
    ByteArrayAccess.getLong(bytes, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getFloat(baseOffset: RegisterOffset, relativeIndex: Int): Float =
    ByteArrayAccess.getFloat(bytes, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getDouble(baseOffset: RegisterOffset, relativeIndex: Int): Double =
    ByteArrayAccess.getDouble(bytes, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getChar(baseOffset: RegisterOffset, relativeIndex: Int): Char =
    ByteArrayAccess.getChar(bytes, RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getObject(baseOffset: RegisterOffset, relativeIndex: Int): AnyRef =
    objects(RegisterOffset.getObjects(baseOffset) + relativeIndex)

  def setBoolean(baseOffset: RegisterOffset, relativeIndex: Int, value: Boolean): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = if (value) (1: Byte) else (0: Byte)
  }

  def setByte(baseOffset: RegisterOffset, relativeIndex: Int, value: Byte): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = value
  }

  def setShort(baseOffset: RegisterOffset, relativeIndex: Int, value: Short): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 1 >= bytes.length) growBytes(absoluteIndex)
    ByteArrayAccess.setShort(bytes, absoluteIndex, value)
  }

  def setInt(baseOffset: RegisterOffset, relativeIndex: Int, value: Int): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 3 >= bytes.length) growBytes(absoluteIndex)
    ByteArrayAccess.setInt(bytes, absoluteIndex, value)
  }

  def setLong(baseOffset: RegisterOffset, relativeIndex: Int, value: Long): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 7 >= bytes.length) growBytes(absoluteIndex)
    ByteArrayAccess.setLong(bytes, absoluteIndex, value)
  }

  def setFloat(baseOffset: RegisterOffset, relativeIndex: Int, value: Float): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 3 >= bytes.length) growBytes(absoluteIndex)
    ByteArrayAccess.setFloat(bytes, absoluteIndex, value)
  }

  def setDouble(baseOffset: RegisterOffset, relativeIndex: Int, value: Double): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 7 >= bytes.length) growBytes(absoluteIndex)
    ByteArrayAccess.setDouble(bytes, absoluteIndex, value)
  }

  def setChar(baseOffset: RegisterOffset, relativeIndex: Int, value: Char): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 1 >= bytes.length) growBytes(absoluteIndex)
    ByteArrayAccess.setChar(bytes, absoluteIndex, value)
  }

  def setObject(baseOffset: RegisterOffset, relativeIndex: Int, value: AnyRef): Unit = {
    val absoluteIndex = RegisterOffset.getObjects(baseOffset) + relativeIndex
    if (absoluteIndex >= objects.length) growObjects(absoluteIndex)
    objects(absoluteIndex) = value
  }

  private[this] def growBytes(absoluteIndex: RegisterOffset): Unit =
    bytes = util.Arrays.copyOf(bytes, Math.max(bytes.length << 1, absoluteIndex + 8))

  private[this] def growObjects(absoluteIndex: RegisterOffset): Unit =
    objects = util.Arrays.copyOf(objects, Math.max(objects.length << 1, absoluteIndex + 1))
}

object Registers {
  def apply(usedRegisters: RegisterOffset): Registers = new Registers(usedRegisters)
}
