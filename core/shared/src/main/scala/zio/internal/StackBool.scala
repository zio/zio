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

package zio.internal

/**
 * A very fast, hand-optimized stack designed just for booleans.
 * In the common case (size < 64), achieves zero allocations.
 */
private[zio] final class StackBool private () {
  import StackBool.Entry

  private[this] var head  = new Entry(null)
  private[this] var _size = 0L

  def getOrElse(index: Int, b: Boolean): Boolean = {
    val i0   = _size & 63L
    val base = (64L - i0) + index
    val i    = base & 63L
    var ie   = base >> 6
    var cur  = head

    while (ie > 0 && (cur ne null)) {
      ie -= 1
      cur = cur.next
    }
    if (cur eq null) b
    else {
      val mask = 1L << (63L - i)
      (cur.bits & mask) != 0L
    }
  }

  def size: Long = _size

  def push(flag: Boolean): Unit = {
    val index = _size & 0x3fL

    if (flag) head.bits = head.bits | (1L << index)
    else head.bits = head.bits & (~(1L << index))

    if (index == 63L) head = new Entry(head)

    _size += 1L
  }

  def popOrElse(b: Boolean): Boolean =
    if (_size == 0L) b
    else {
      _size -= 1L
      val index = _size & 0x3fL

      if (index == 63L && head.next != null) head = head.next

      ((1L << index) & head.bits) != 0L
    }

  def peekOrElse(b: Boolean): Boolean =
    if (_size == 0L) b
    else {
      val size  = _size - 1L
      val index = size & 0x3fL
      val entry =
        if (index == 63L && head.next != null) head.next else head

      ((1L << index) & entry.bits) != 0L
    }

  def popDrop[A](a: A): A = { popOrElse(false); a }

  def toList: List[Boolean] =
    (0 until _size.toInt).map(getOrElse(_, false)).toList

  override def toString: String =
    "StackBool(" + toList.mkString(", ") + ")"

  override def equals(that: Any): Boolean = (that: @unchecked) match {
    case that: StackBool => toList == that.toList
  }

  override def hashCode = toList.hashCode
}
private[zio] object StackBool {
  def apply(): StackBool = new StackBool

  def apply(bool: Boolean): StackBool = {
    val stack = StackBool()

    stack.push(bool)

    stack
  }

  def fromIterable(it: Iterable[Boolean]): StackBool = {
    val stack = StackBool()
    it.foldRight(stack) { (b, stack) =>
      stack.push(b)
      stack
    }
  }

  private class Entry(val next: Entry) {
    var bits: Long = 0L
  }
}
