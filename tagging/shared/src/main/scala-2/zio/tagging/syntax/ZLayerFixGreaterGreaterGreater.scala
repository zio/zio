package zio.tagging.syntax

import zio.tagging.internal.Minus
import zio._

final class ZLayerFixGreaterGreaterGreater[RIn, E, ROut](private val self: ZLayer[RIn, E, ROut]) extends AnyVal {

  /**
   * Provides the same functionality as ZIO's `>>>`
   *
   * The following code cannot be compiled (Scala 2.13.14, ZIO 2.1.x):
   * {{{
   * trait A
   * trait B
   * trait C
   *
   * val a: TaskLayer[(A @@ "tag")] = ???
   * val abc: URLayer[(A @@ "tag") with B, C] = ???
   *
   * val bc: RLayer[B, C] = a >>> abc // <- this
   * }}}
   *
   * However, it compiles when using `!>>>`.
   *
   * Related: [[https://github.com/scala/scala/pull/10849 scala/scala#10849]]
   */
  def !>>>[U <: ROut, E1 >: E, ROut2, RIn2](
    that: => ZLayer[U, E1, ROut2]
  )(implicit
    tag: EnvironmentTag[ROut],
    minus: Minus.Aux[U, ROut, RIn2],
    trace: Trace
  ): ZLayer[RIn with RIn2, E1, ROut2] = {
    type R[-X] = ZLayer[X, E1, ROut2]
    self.>>>[RIn2, E1, ROut2](minus.evidence.substituteContra[R](that))
  }
}
