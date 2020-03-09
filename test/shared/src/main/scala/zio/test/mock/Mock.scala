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

package zio.test.mock

import zio.{ Has, ZIO }

/**
 * A `Mock` provides the machinery to map mocked invocations to predefined results
 * and check some constraints on the way.
 */
trait Mock {

  def invoke[RIn <: Has[_], ROut, Input, Error, Value](
    method: Method[RIn, Input, Value],
    input: Input
  ): ZIO[ROut, Error, Value]

  final def apply[RIn <: Has[_], ROut, Error, Value](method: Method[RIn, Unit, Value]): ZIO[ROut, Error, Value] =
    invoke(method, ())

  final def apply[RIn <: Has[_], ROut, Error, Value, A](
    method: Method[RIn, A, Value],
    a: A
  ): ZIO[ROut, Error, Value] =
    invoke(method, a)

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B](
    method: Method[RIn, (A, B), Value],
    a: A,
    b: B
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C](
    method: Method[RIn, (A, B, C), Value],
    a: A,
    b: B,
    c: C
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D](
    method: Method[RIn, (A, B, C, D), Value],
    a: A,
    b: B,
    c: C,
    d: D
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E](
    method: Method[RIn, (A, B, C, D, E), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F](
    method: Method[RIn, (A, B, C, D, E, F), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G](
    method: Method[RIn, (A, B, C, D, E, F, G), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H](
    method: Method[RIn, (A, B, C, D, E, F, G, H), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L, M](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L, M), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l, m))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L, M, N](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L, M, N), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l, m, n))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q,
    r: R
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q,
    r: R,
    s: S
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q,
    r: R,
    s: S,
    t: T
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q,
    r: R,
    s: S,
    t: T,
    u: U
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u))

  final def apply[RIn <: Has[_], ROut, Error, Value, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](
    method: Method[RIn, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V), Value],
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q,
    r: R,
    s: S,
    t: T,
    u: U,
    v: V
  ): ZIO[ROut, Error, Value] =
    invoke(method, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
}
