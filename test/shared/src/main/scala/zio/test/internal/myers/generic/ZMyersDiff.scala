/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

package zio.test.internal.myers.generic

import zio.test.ConsoleUtils
import zio.{Chunk, ChunkBuilder}

object Example extends App {
  val a =
    Chunk.fromIterable("""
Nice
Shoot
Beans
Hello
Clark
      """.trim.split("\n"))
  val b =
    Chunk.fromIterable("""
Nice
Cool
Howdy
Hello
Claude
      """.trim.split("\n"))
  val result = ZMyersDiff.diff(a, b)

  println(result)

}

sealed trait Action[A]

object Action {

  final case class Delete[A](s: A) extends Action[A] {
    override def toString: String = s"-$s"
  }

  final case class Insert[A](s: A) extends Action[A] {
    override def toString: String = s"+$s"
  }

  final case class Keep[A](s: A) extends Action[A] {
    override def toString: String = s.toString
  }
}

final case class DiffResult[A](actions: Chunk[Action[A]]) {
  override def toString: String =
    actions
      .foldLeft(new StringBuilder()) { case (b, a) =>
        a match {
          case Action.Delete(s) =>
            b append ConsoleUtils.red(s"\n-$s")
          case Action.Insert(s) =>
            b append ConsoleUtils.green(s"\n+$s")
          case Action.Keep(s) =>
            b append s"\n $s"
        }
      }
      .toString()
}

object ZMyersDiff {
  def diff[A](original: Chunk[A], modified: Chunk[A]): DiffResult[A] = {

    var varOriginal = original
    var varModified = modified
    var longestCommonSubstring: Chunk[A] =
      getLongestCommonSubsequence(original, modified)

    var actions: Chunk[Action[A]] = Chunk.empty

    while (longestCommonSubstring.nonEmpty) {
      val headOfLongestCommonSubstring = longestCommonSubstring(0)
      longestCommonSubstring = longestCommonSubstring.drop(1)

      var headOfModified = varModified(0)
      var loop           = true

      while (loop) {
        headOfModified = varModified(0)
        varModified = varModified.drop(1)
        if (headOfModified != headOfLongestCommonSubstring)
          actions = actions :+ Action.Insert(headOfModified)

        loop = varModified.nonEmpty && headOfModified != headOfLongestCommonSubstring
      }

      var headOfOriginal = varOriginal(0)
      loop = true

      while (loop) {
        headOfOriginal = varOriginal(0)
        varOriginal = varOriginal.drop(1)
        if (headOfOriginal != headOfLongestCommonSubstring)
          actions = actions :+ Action.Delete(headOfOriginal)

        loop = varOriginal.nonEmpty && headOfOriginal != headOfLongestCommonSubstring
      }

      actions = actions :+ Action.Keep(headOfLongestCommonSubstring)
    }

    while (varModified.nonEmpty) {
      val headOfModified = varModified(0)
      varModified = varModified.drop(1)
      actions = actions :+ Action.Insert(headOfModified)
    }

    while (varOriginal.nonEmpty) {
      val headOfOriginal = varOriginal(0)
      varOriginal = varOriginal.drop(1)
      actions = actions :+ Action.Delete(headOfOriginal)
    }

    DiffResult(actions)
  }

  def getLongestCommonSubsequence[A](original: Chunk[A], modified: Chunk[A]): Chunk[A] =
    if (original == null || original.isEmpty || modified == null || modified.isEmpty) Chunk.empty
    else if (original == modified) original
    else {

      val myersMatrix: Array[Array[Int]] = initializeMyersMatrix(original, modified)
      val longestCommonSubsequence       = ChunkBuilder.make[A]()

      var originalPosition = original.length
      var modifiedPosition = modified.length

      var loop = true

      while (loop) {
        if (myersMatrix(originalPosition)(modifiedPosition) == myersMatrix(originalPosition - 1)(modifiedPosition)) {
          originalPosition -= 1
        } else if (
          myersMatrix(originalPosition)(modifiedPosition) == myersMatrix(originalPosition)(modifiedPosition - 1)
        ) {
          modifiedPosition -= 1
        } else {
          longestCommonSubsequence += original(originalPosition - 1)
          originalPosition -= 1
          modifiedPosition -= 1
        }

        loop = originalPosition > 0 && modifiedPosition > 0
      }

      longestCommonSubsequence.result().reverse
    }

  private def initializeMyersMatrix[A](original: Chunk[A], modified: Chunk[A]): Array[Array[Int]] = {
    val originalLength = original.length
    val modifiedLength = modified.length

    val myersMatrix = Array.fill[Int](originalLength + 1, modifiedLength + 1)(0)

    for (i <- 0 until originalLength) {
      for (j <- 0 until modifiedLength) {
        if (original(i) == modified(j)) {
          myersMatrix(i + 1)(j + 1) = myersMatrix(i)(j) + 1
        } else {
          if (myersMatrix(i)(j + 1) >= myersMatrix(i + 1)(j)) {
            myersMatrix(i + 1)(j + 1) = myersMatrix(i)(j + 1)
          } else {
            myersMatrix(i + 1)(j + 1) = myersMatrix(i + 1)(j)
          }
        }
      }
    }

    myersMatrix
  }
}
