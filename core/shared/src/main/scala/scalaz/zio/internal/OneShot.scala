// Copyright (C) 2018 John A. De Goes
package scalaz.zio.internal

/**
 * A variable that can be set a single time. The synchronous,
 * effectful equivalent of `Promise`.
 */
private[zio] class OneShot[A <: AnyRef] private (@volatile var value: A) {
  /**
   * Sets the variable to the value. The behavior of this function
   * is undefined if the variable has already been set.
   */
  final def set(v: A): Unit =
    this.synchronized {
      if (!(value eq null)) throw new Error("Defect: OneShot being set twice")

      value = v

      this.notifyAll()
    }

  /**
   * Retrieves the value of the variable, blocking if necessary.
   */
  final def get: A = {
    while (value eq null) {
      this.synchronized {
        if (value eq null) this.wait()
      }
    }

    value
  }
}

object OneShot {
  /**
   * Makes a new (unset) variable.
   */
  final def make[A <: AnyRef]: OneShot[A] = new OneShot(null.asInstanceOf[A])
}
