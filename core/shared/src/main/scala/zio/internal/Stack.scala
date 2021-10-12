/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A very fast, growable/shrinkable, mutable stack.
 */
private[zio] final class Stack[A <: AnyRef]() extends Iterable[A] { self =>
  private[this] var array   = new Array[AnyRef](13)
  private[this] var _used   = 0
  private[this] var nesting = 0

  override def size: Int = _used + (12 * nesting)

  def iterator: Iterator[A] =
    new Iterator[A] {
      var currentArray   = self.array
      var currentIndex   = _used - 1
      var iterated       = 0
      var currentNesting = self.nesting

      def hasNext: Boolean = iterated < self.size

      def next(): A =
        if (!hasNext) throw new NoSuchElementException("There are no elements to iterate")
        else {
          if (currentIndex == 0 && currentNesting > 0) {
            currentArray = currentArray(0).asInstanceOf[Array[AnyRef]]
            currentIndex = 12
            currentNesting = currentNesting - 1
          }

          val a = currentArray(currentIndex).asInstanceOf[A]

          iterated = iterated + 1
          currentIndex = currentIndex - 1

          a
        }
    }

  /**
   * Determines if the stack is empty.
   */
  override def isEmpty: Boolean = size == 0

  /**
   * Pushes an item onto the stack.
   */
  def push(a: A): Unit =
    if (_used == 13) {
      array = Array(array, a, null, null, null, null, null, null, null, null, null, null, null)
      _used = 2
      nesting += 1
    } else {
      array(_used) = a
      _used += 1
    }

  /**
   * Pops an item off the stack, or returns `null` if the stack is empty.
   */
  def pop(): A =
    if (_used <= 0) {
      null.asInstanceOf[A]
    } else {
      val idx = _used - 1
      var a   = array(idx)
      if (idx == 0 && nesting > 0) {
        array = a.asInstanceOf[Array[AnyRef]]
        a = array(12)
        array(12) = null // GC
        _used = 12
        nesting -= 1
      } else {
        array(idx) = null // GC
        _used = idx
      }
      a.asInstanceOf[A]
    }

  def popOrElse(that: A): A = {
    val a = pop()

    if (a eq null) that else a
  }

  /**
   * Peeks the item on the head of the stack, or returns `null` if empty.
   */
  def peek(): A =
    if (_used <= 0) {
      null.asInstanceOf[A]
    } else {
      val idx = _used - 1
      var a   = array(idx)
      if (idx == 0 && nesting > 0) a = (a.asInstanceOf[Array[AnyRef]])(12)
      a.asInstanceOf[A]
    }

  def peekOrElse(a: A): A = if (_used <= 0) a else peek()
}

private[zio] object Stack {
  def apply[A <: AnyRef](): Stack[A] = new Stack[A]
  def apply[A <: AnyRef](a: A): Stack[A] = {
    val stack = new Stack[A]

    stack.push(a)

    stack
  }

  def fromIterable[A <: AnyRef](as: Iterable[A]): Stack[A] = {
    val stack = Stack[A]()

    as.toList.reverse.foreach(stack.push(_))

    stack
  }
}
