// Copyright (C) 2018 - 2019 John A. De Goes. All rights reserved.
package scalaz.zio.internal

/**
 * A very fast, growable/shrinkable, mutable stack.
 */
final class Stack[A <: AnyRef]() {
  private[this] var array   = new Array[AnyRef](13)
  private[this] var size    = 0
  private[this] var nesting = 0

  /**
   * Determines if the stack is empty.
   */
  final def isEmpty: Boolean = size == 0

  /**
   * Pushes an item onto the stack.
   */
  final def push(a: A): Unit =
    if (size == 13) {
      array = Array(array, a, null, null, null, null, null, null, null, null, null, null, null)
      size = 2
      nesting += 1
    } else {
      array(size) = a
      size += 1
    }

  /**
   * Pops an item off the stack, or returns `null` if the stack is empty.
   */
  final def pop(): A = {
    val idx = size - 1
    var a   = array(idx)
    if (idx == 0 && nesting > 0) {
      array = a.asInstanceOf[Array[AnyRef]]
      a = array(12)
      array(12) = null // GC
      size = 12
      nesting -= 1
    } else {
      array(idx) = null // GC
      size = idx
    }
    a.asInstanceOf[A]
  }
}
