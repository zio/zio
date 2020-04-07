/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.optics

object ZTraversal {

  /**
   * Constructs an traversal from a `get` and a  `set` function.
   */
  def apply[S, T, A, B](get: S => List[A], set: List[B] => S => Option[T]): ZTraversal[S, T, A, B] =
    ZOptic(s => Right(get(s)), b => s => set(b)(s).toRight(()))

  /**
   * A traversal that accesses a filtered subset of a `List`.
   */
  def filter[A](f: A => Boolean): Traversal[List[A], A] =
    Traversal(
      s => s.filter(f),
      as =>
        s => {
          def loop(xs: List[A], ys: List[A], acc: List[A]): Option[List[A]] =
            (xs, ys) match {
              case (h1 :: t1, h2 :: t2) if (f(h2)) => loop(t1, t2, h1 :: acc)
              case (_, h2 :: _) if (f(h2))         => None
              case (a, h2 :: t2)                   => loop(a, t2, h2 :: acc)
              case (_, _)                          => Some(acc.reverse)
              case _                               => None
            }
          loop(as, s, List.empty)
        }
    )

  /**
   * A traversal that accesses a slice of a `Vector`.
   */
  def slice[A](from: Int, until: Int): Traversal[Vector[A], A] =
    Traversal(
      s => s.slice(from, until).toList,
      as =>
        s => {
          val n = ((until min s.length) - from) max 0
          if (as.length < n) None else Some(s.patch(from, as.take(n), n))
        }
    )
}
