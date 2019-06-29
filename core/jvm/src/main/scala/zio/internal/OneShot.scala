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

package zio.internal

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
   *
   * @param timeout The maximum amount of time the thread will be blocked, in milliseconds.
   * @throws Error if the timeout is reached without the value being set.
   */
  final def get(timeout: Long): A = {
    var remainingNano = math.min(timeout, Long.MaxValue/1000000L)*1000000L
    while (value == null && remainingNano > 0L) {
      val waitMilli = remainingNano / 1000000L
      val waitNano = (remainingNano % 1000000L).toInt
      val start = System.nanoTime()
      this.synchronized {
        if (value == null) this.wait(waitMilli, waitNano)
      }
      remainingNano -= System.nanoTime() - start
    }

    if (value == null) throw new Error("Timed out waiting for variable to be set")

    value
  }

  /**
   * Retrieves the value of the variable, blocking if necessary.
   *
   * This will block until the value is set or the thread is interrupted.
   */
  final def get(): A = {
    while (value == null) {
      this.synchronized {
        if (value == null) this.wait()
      }
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
