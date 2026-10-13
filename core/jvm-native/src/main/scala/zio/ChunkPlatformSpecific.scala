/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.reflect.ClassTag

private[zio] trait ChunkPlatformSpecific {

  private[zio] object Tags {
    def fromValue[A](a: A): ClassTag[A] = {
      if (a == null) ClassTag.AnyRef
      else {
        val c = a.getClass
        if (isByte(c)) ClassTag.Byte
        else if (isInt(c)) ClassTag.Int
        else if (isBoolean(c)) ClassTag.Boolean
        else if (isChar(c)) ClassTag.Char
        else if (isShort(c)) ClassTag.Short
        else if (isLong(c)) ClassTag.Long
        else if (isFloat(c)) ClassTag.Float
        else if (isDouble(c)) ClassTag.Double
        else ClassTag.AnyRef
      }
    }.asInstanceOf[ClassTag[A]]

    private def isBoolean(c: Class[_]): Boolean =
      (c eq classOf[Boolean]) || (c eq classOf[java.lang.Boolean])
    private def isByte(c: Class[_]): Boolean =
      (c eq classOf[Byte]) || (c eq classOf[java.lang.Byte])
    private def isShort(c: Class[_]): Boolean =
      (c eq classOf[Short]) || (c eq classOf[java.lang.Short])
    private def isInt(c: Class[_]): Boolean =
      (c eq classOf[Int]) || (c eq classOf[java.lang.Integer])
    private def isLong(c: Class[_]): Boolean =
      (c eq classOf[Long]) || (c eq classOf[java.lang.Long])
    private def isFloat(c: Class[_]): Boolean =
      (c eq classOf[Float]) || (c eq classOf[java.lang.Float])
    private def isDouble(c: Class[_]): Boolean =
      (c eq classOf[Double]) || (c eq classOf[java.lang.Double])
    private def isChar(c: Class[_]): Boolean =
      (c eq classOf[Char]) || (c eq classOf[java.lang.Character])
  }
}
