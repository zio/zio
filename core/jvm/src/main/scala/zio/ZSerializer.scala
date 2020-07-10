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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Comparator

/**
 * A `ZSerializer[E, A]` represents a serializer of type `A` that can
 * serialize or deserialize `A` to and from `Array[Byte]`.
 */
trait ZSerializer[+E, A] {
  def serialize(value: A): IO[E, Array[Byte]]
  def deserialize(bytes: Array[Byte]): IO[E, A]
}

/**
 * Implicits that go along with the Serializer trait
 */
object ZSerializer extends ZSerializerLowPriorityImplicits {
  implicit object StringZSerializer extends ZSerializer[Nothing, String] {
    final def serialize(value: String): UIO[Array[Byte]] = UIO.effectTotal(value.getBytes(UTF_8))

    final def deserialize(bytes: Array[Byte]): UIO[String] = UIO.effectTotal(new String(bytes, UTF_8))
  }

  implicit object ByteArrayZSerializer extends ZSerializer[Nothing, Array[Byte]] {
    final def serialize(value: Array[Byte]): UIO[Array[Byte]] = UIO.effectTotal(value)

    final def deserialize(bytes: Array[Byte]): UIO[Array[Byte]] = UIO.effectTotal(bytes)
  }

  implicit object BooleanZSerializer extends ZSerializer[Nothing, Boolean] {
    final def serialize(value: Boolean): UIO[Array[Byte]] = UIO.effectTotal(Array[Byte](if (value) 1 else 0))

    final def deserialize(bytes: Array[Byte]): UIO[Boolean] = UIO.effectTotal(if (bytes(0) == 0) false else true)
  }

  implicit object CharZSerializer extends ZSerializer[Nothing, Char] with Comparator[Array[Byte]] {
    final def serialize(value: Char): UIO[Array[Byte]]   = UIO.effectTotal(truncatedBytes(value.toShort))
    final def deserialize(bytes: Array[Byte]): UIO[Char] = UIO.effectTotal(truncatedShort(bytes).toChar)

    final def compare(l: Array[Byte], r: Array[Byte]): Int = {
      val li = truncatedShort(l).toChar
      val ri = truncatedShort(r).toChar

      if (li < ri) -1
      else if (li > ri) 1
      else 0
    }
  }

  implicit object TruncatedShortZSerializer extends ZSerializer[Nothing, Short] with Comparator[Array[Byte]] {
    final def serialize(value: Short): UIO[Array[Byte]]   = UIO.effectTotal(truncatedBytes(value))
    final def deserialize(bytes: Array[Byte]): UIO[Short] = UIO.effectTotal(truncatedShort(bytes))

    final def compare(l: Array[Byte], r: Array[Byte]): Int = {
      val li = truncatedInt(l)
      val ri = truncatedInt(r)

      if (li < ri) -1
      else if (li > ri) 1
      else 0
    }
  }

  implicit object TruncatedIntZSerializer extends ZSerializer[Nothing, Int] with Comparator[Array[Byte]] {
    final def serialize(value: Int): UIO[Array[Byte]]   = UIO.effectTotal(truncatedBytes(value))
    final def deserialize(bytes: Array[Byte]): UIO[Int] = UIO.effectTotal(truncatedInt(bytes))

    final def compare(l: Array[Byte], r: Array[Byte]): Int = {
      val li = truncatedInt(l)
      val ri = truncatedInt(r)

      if (li < ri) -1
      else if (li > ri) 1
      else 0
    }
  }

  implicit object TruncatedLongZSerializer extends ZSerializer[Nothing, Long] with Comparator[Array[Byte]] {
    final def serialize(value: Long): UIO[Array[Byte]]   = UIO.effectTotal(truncatedBytes(value))
    final def deserialize(bytes: Array[Byte]): UIO[Long] = UIO.effectTotal(truncatedLong(bytes))

    final def compare(l: Array[Byte], r: Array[Byte]): Int = {
      val li = truncatedLong(l)
      val ri = truncatedLong(r)

      if (li < ri) -1
      else if (li > ri) 1
      else 0
    }
  }

  /**
   * Convert an Int to a truncated byte array depending on how many bytes
   * are actually needed to store the value. Any leading bytes that are
   * zero are truncated.
   *
   * Examples:
   *  1 => 0x01
   *  255 => 0xff
   *  1024 => 0x0400
   */
  private def truncatedBytes(value: Int): Array[Byte] = {
    var len: Int  = 4
    var mask: Int = 0xff000000

    // Figure out how many bytes we need to store the int
    while ((value & mask) == 0 && len > 1) {
      len -= 1
      mask = mask >>> 8
    }

    val bytes = new Array[Byte](len)

    var v: Int = value
    var i: Int = len - 1

    while (i >= 0) {
      bytes(i) = (v & 0xff).byteValue
      v = v >>> 8
      i -= 1
    }

    bytes
  }

  private def truncatedInt(bytes: Array[Byte]): Int = {
    val len: Int = bytes.length

    var idx: Int   = 0
    var value: Int = 0

    while (idx < len) {
      value = (value << 8) | (0xff & bytes(idx))
      idx += 1
    }

    value
  }

  private def truncatedBytes[E](value: Long): Array[Byte] = {
    var len: Int   = 8
    var mask: Long = 0xFF00000000000000L

    // Figure out how many bytes we need to store the int
    while ((value & mask) == 0 && len > 1) {
      len -= 1
      mask = mask >>> 8
    }

    val bytes: Array[Byte] = new Array[Byte](len)

    var v: Long = value
    var i: Int  = len - 1

    while (i >= 0) {
      bytes(i) = (v & 0xff).byteValue
      v = v >>> 8
      i -= 1
    }

    bytes
  }

  private def truncatedLong(bytes: Array[Byte]): Long = {
    val len: Int = bytes.length

    var idx: Int    = 0
    var value: Long = 0L

    while (idx < len) {
      value = (value << 8) | (0xff & bytes(idx))
      idx += 1
    }

    value
  }

  private def truncatedBytes[E](value: Short): Array[Byte] = {
    var len: Int    = 2
    var mask: Short = 0xff00.toShort

    // Figure out how many bytes we need to store the int
    while ((value & mask) == 0 && len > 1) {
      len -= 1
      mask = (mask >>> 8).toShort
    }

    val bytes: Array[Byte] = new Array[Byte](len)

    var v: Short = value
    var i: Int   = len - 1

    while (i >= 0) {
      bytes(i) = (v & 0xff).byteValue
      v = (v >>> 8).toShort
      i -= 1
    }

    bytes
  }

  private def truncatedShort(bytes: Array[Byte]): Short =
    truncatedInt(bytes).toShort

}

object SerializableZSerializer {
  private val typedSerializers =
    new scala.collection.mutable.HashMap[Class[_], SerializableZSerializer[_]] {
      override def default(c: Class[_]) =
        getOrElseUpdate(c, new SerializableZSerializer[Serializable])
    }

  def apply[T <: java.io.Serializable: Manifest]: SerializableZSerializer[T] =
    forClass[T](manifest[T].runtimeClass.asInstanceOf[Class[T]])

  def forClass[T <: java.io.Serializable](c: Class[T]) = typedSerializers(c).asInstanceOf[SerializableZSerializer[T]]

  private def serialize[T](obj: T): IO[Throwable, Array[Byte]] =
    Task(new ByteArrayOutputStream(512)).flatMap { (bao: ByteArrayOutputStream) =>
      ZManaged.fromAutoCloseable(Task(new ObjectOutputStream(bao))).use { (oos: ObjectOutputStream) =>
        Task(oos.writeObject(obj))
      } *> Task(bao.toByteArray())
    }

  private def deserialize[T](bytes: Array[Byte]): IO[Throwable, T] =
    Task(new ByteArrayInputStream(bytes)).flatMap { (bai: ByteArrayInputStream) =>
      ZManaged.fromAutoCloseable(Task(new ObjectInputStream(bai))).use { (ois: ObjectInputStream) =>
        Task(ois.readObject().asInstanceOf[T])
      }
    }
}

final class SerializableZSerializer[T <: java.io.Serializable]() extends ZSerializer[Throwable, T] {
  def serialize(value: T): IO[Throwable, Array[Byte]]   = SerializableZSerializer.serialize[T](value)
  def deserialize(bytes: Array[Byte]): IO[Throwable, T] = SerializableZSerializer.deserialize[T](bytes)
}

sealed trait ZSerializerLowPriorityImplicits {
  implicit def serializable[T <: java.io.Serializable: Manifest]: ZSerializer[Throwable, T] =
    SerializableZSerializer.apply
}
