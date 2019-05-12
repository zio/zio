package scalaz.zio
package interop

import com.github.ghik.silencer.silent
import scalaz.zio.interop.runtime.TestRuntime

final class ZioTestDefaultsSpec extends TestRuntime {

  def is = "ZioTestDefaultsSpec".title ^ s2"""
    The default test type-class instances for Zio:
      Don't give conflict when unified all together. $summonAll
  """

  private[this] def summonAll = {

    import default.testZioInstances._
    import scalaz.zio.interop.bio.{ Async2, Concurrent2, Errorful2, Guaranteed2, RunAsync2, RunSync2, Sync2, Temporal2 }

    @silent def f[F[+ _, + _]](
      implicit
      A: Guaranteed2[F],
      B: Errorful2[F],
      C: Sync2[F],
      D: Temporal2[F],
      E: Concurrent2[F],
      F: RunSync2[F],
      G: Async2[F],
      H: RunAsync2[F]
    ): Unit = ()

    val _ = f[IO]

    success
  }
}
