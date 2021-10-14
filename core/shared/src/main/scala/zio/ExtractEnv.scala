/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

sealed trait ExtractEnv[-Whole, +Piece] {
  def extract(whole: Whole): Piece

  final def toLayer: ZLayer[Whole, Nothing, Piece] = 
    ZLayer.fromZIOMany {
      for {
        whole <- ZIO.environment[Whole]
      } yield extract(whole)
    }
}
object ExtractEnv extends ExtractEnvLowPriorityImplicits {
  implicit def extractId[A]: ExtractEnv[A, A] = 
    new ExtractEnv[A, A] {
      def extract(whole: A): A = whole 
    }
}
private[zio] trait ExtractEnvLowPriorityImplicits {
  implicit def extractHas1[A: Tag]: ExtractEnv[Has[A], A] = 
    new ExtractEnv[Has[A], A] {
      def extract(whole: Has[A]): A = whole.get[A]
    }

  implicit def extractHas2[A: Tag]: ExtractEnv[A, Has[A]] = 
    new ExtractEnv[A, Has[A]] {
      def extract(whole: A): Has[A] = Has(whole)
    }
}