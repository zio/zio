package scalaz.zio
package interop
package runtime

import org.specs2.Specification
import scalaz.zio.internal.PlatformLive

private[interop] abstract class TestRuntime extends Specification {

  implicit final val testRuntimeInstance: DefaultRuntime =
    new DefaultRuntime {
      override val Platform = PlatformLive.makeDefault().withReportFailure(_ => ())
    }
}
