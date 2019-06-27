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

package zio
package interop

import com.github.ghik.silencer.silent
import org.specs2.Specification
import org.specs2.execute.Result
import zio.interop.bio.{ Async2, Concurrent2, Errorful2, RunAsync2, RunSync2, Sync2, Temporal2, _ }
import zio.interop.runtime.TestRuntime

final class ZioSyntaxInferenceSpec extends Specification with TestRuntime {

  def is = "ZioSyntaxInferenceSpec".title ^ s2"""
    The type-class syntax:
      can summon Monad[F[E, ?]] when evidence of Errorful2 is provided. $summonErrorful2Syntax
      can summon Monad[F[E, ?]] when evidence of Bracket2 is provided. $summonBracket2Syntax
      can summon Monad[F[E, ?]] when evidence of Concurrent2 is provided. $summonConcurrent2Syntax
      can summon Monad[F[E, ?]] when evidence of Temporal2 is provided. $summonTemporal2Syntax
      can summon Monad[F[E, ?]] when evidence of Sync2 is provided. $summonSync2Syntax
      can summon Monad[F[E, ?]] when evidence of Async2 is provided. $summonAsync2Syntax
      can summon Monad[F[E, ?]] when evidence of RunSync2 is provided. $summonRunSync2Syntax
      can summon Monad[F[E, ?]] when evidence of RunAsync2 is provided. $summonRunAsync2Syntax
      can summon Monad[F[E, ?]] when multiple evidences are provided. $summonMultipleSyntax
  """

  private[this] def summonErrorful2Syntax: Result = {

    @silent def f[F[+_, +_], E, A](fa: F[E, A])(
      implicit A: Errorful2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    success
  }

  private[this] def summonBracket2Syntax: Result = {

    @silent def f[F[+_, +_], E, A](fa: F[E, A])(
      implicit A: Bracket2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    success
  }

  private[this] def summonTemporal2Syntax: Result = {

    @silent def f[F[+_, +_], E, A](fa: F[E, A])(
      implicit A: Temporal2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    success
  }

  private[this] def summonConcurrent2Syntax: Result = {

    @silent def f[F[+_, +_], E, A](fa: F[E, A])(
      implicit A: Concurrent2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    success
  }

  private[this] def summonSync2Syntax: Result = {

    @silent def f[F[+_, +_], E, A](fa: F[E, A])(
      implicit A: Sync2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    success
  }

  private[this] def summonAsync2Syntax: Result = {

    @silent def f[F[+_, +_], E, A](fa: F[E, A])(
      implicit A: Async2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    success
  }

  private[this] def summonRunSync2Syntax: Result = {

    @silent def f[F[+_, +_], E, A](fa: F[E, A])(
      implicit A: RunSync2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    success
  }

  private[this] def summonRunAsync2Syntax: Result = {

    @silent def f[F[+_, +_], E, A](fa: F[E, A])(
      implicit A: RunAsync2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    success
  }

  private[this] def summonMultipleSyntax: Result = {

    @silent def f1[F[+_, +_], E, A](fa: F[E, A])(
      implicit
      A: Bracket2[F],
      B: Sync2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    @silent def f2[F[+_, +_], E, A](fa: F[E, A])(
      implicit
      A: Concurrent2[F],
      B: Sync2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    @silent def f3[F[+_, +_], E, A](fa: F[E, A])(
      implicit
      A: Concurrent2[F],
      B: Async2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    @silent def f4[F[+_, +_], E, A](fa: F[E, A])(
      implicit
      A: Errorful2[F],
      B: Concurrent2[F],
      C: Temporal2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    @silent def f5[F[+_, +_], E, A](fa: F[E, A])(
      implicit
      A: RunAsync2[F],
      B: RunSync2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    success
  }
}
