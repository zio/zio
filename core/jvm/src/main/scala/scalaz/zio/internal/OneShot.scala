// Copyright (C) 2018 John A. De Goes
package scalaz.zio.internal

/**
 * A variable that can be set a single time. The synchronous,
 * effectful equivalent of `Promise`.
 */
private[zio] class OneShot[A] private (@volatile var value: A) {

  /**
   * Sets the variable to the value. The behavior of this function
   * is undefined if the variable has already been set.
   */
  final def set(v: A): Unit = {
    if (v == null) throw new Error("Defect: OneShot variable cannot be set to null value")

    this.synchronized {
      if (value != null) throw new Error("Defect: OneShot variable being set twice")

      value = v

      this.notifyAll()
    }
  }

  /**
   * Determines if the variable has been set.
   */
  final def isSet: Boolean = value != null

  /**
   * Retrieves the value of the variable, blocking if necessary.
   */
  final def get(timeout: Long = Long.MaxValue): A = {
    if (value == null) {
      this.synchronized {
        if (value == null) this.wait(timeout)
      }

      if (value == null) throw new Error("Timed out waiting for variable to be set")
    }

    value
  }
}

object OneShot {

  /**
   * Makes a new (unset) variable.
   */
  final def make[A]: OneShot[A] = new OneShot(null.asInstanceOf[A])
}
