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

// Copyright (C) 2018 John A. De Goes
package scalaz.zio.internal

/**
 * A variable that can be set a single time. The synchronous,
 * effectful equivalent of `Promise`.
 */
private[zio] class OneShot[A] private (var value: A) {

  /**
   * Sets the variable to the value. The behavior of this function
   * is undefined if the variable has already been set.
   */
  final def set(v: A): Unit = {
    if (v == null) throw new Error("Defect: OneShot variable cannot be set to null value")
    if (value != null) throw new Error("Defect: OneShot variable being set twice")
    value = v
  }

  /**
   * Determines if the variable has been set.
   */
  final def isSet: Boolean = value != null

  /**
   * Retrieves the value of the variable, blocking if necessary.
   */
  final def get(): A = {
    if (value == null) throw new Error("Cannot block for result to be set in Javascript")
    value
  }
}

object OneShot {

  /**
   * Makes a new (unset) variable.
   */
  final def make[A]: OneShot[A] = new OneShot(null.asInstanceOf[A])
}
