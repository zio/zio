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

import scala.reflect.{ClassTag, classTag}

private[zio] trait ChunkPlatformSpecific {

  private[zio] object Tags {
    def fromValue[A](a: A): ClassTag[A] =
      if (a == null) ClassTag.AnyRef.asInstanceOf[ClassTag[A]]
      else {
        val c = a.getClass

        if (isBoolean(c)) ClassTag.Boolean.asInstanceOf[ClassTag[A]]
        else if (isByte(c)) ClassTag.Byte.asInstanceOf[ClassTag[A]]
        else if (isShort(c)) ClassTag.Short.asInstanceOf[ClassTag[A]]
        else if (isInt(c)) ClassTag.Int.asInstanceOf[ClassTag[A]]
        else if (isLong(c)) ClassTag.Long.asInstanceOf[ClassTag[A]]
        else if (isFloat(c)) ClassTag.Float.asInstanceOf[ClassTag[A]]
        else if (isDouble(c)) ClassTag.Double.asInstanceOf[ClassTag[A]]
        else if (isChar(c)) ClassTag.Char.asInstanceOf[ClassTag[A]]
        else ClassTag.AnyRef.asInstanceOf[ClassTag[A]]
      }

    private def isBoolean(c: Class[_]): Boolean =
      c == BooleanClass || c == BooleanClassBox
    private def isByte(c: Class[_]): Boolean =
      c == ByteClass || c == ByteClassBox
    private def isShort(c: Class[_]): Boolean =
      c == ShortClass || c == ShortClassBox
    private def isInt(c: Class[_]): Boolean =
      c == IntClass || c == IntClassBox
    private def isLong(c: Class[_]): Boolean =
      c == LongClass || c == LongClassBox
    private def isFloat(c: Class[_]): Boolean =
      c == FloatClass || c == FloatClassBox
    private def isDouble(c: Class[_]): Boolean =
      c == DoubleClass || c == DoubleClassBox
    private def isChar(c: Class[_]): Boolean =
      c == CharClass || c == CharClassBox

    private val BooleanClass    = classOf[Boolean]
    private val BooleanClassBox = classOf[java.lang.Boolean]
    private val ByteClass       = classOf[Byte]
    private val ByteClassBox    = classOf[java.lang.Byte]
    private val ShortClass      = classOf[Short]
    private val ShortClassBox   = classOf[java.lang.Short]
    private val IntClass        = classOf[Int]
    private val IntClassBox     = classOf[java.lang.Integer]
    private val LongClass       = classOf[Long]
    private val LongClassBox    = classOf[java.lang.Long]
    private val FloatClass      = classOf[Float]
    private val FloatClassBox   = classOf[java.lang.Float]
    private val DoubleClass     = classOf[Double]
    private val DoubleClassBox  = classOf[java.lang.Double]
    private val CharClass       = classOf[Char]
    private val CharClassBox    = classOf[java.lang.Character]
  }
}
