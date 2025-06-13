package zio.app

import zio._
import zio.test._

/**
 * Main test suite for ZIOApp.
 * This suite combines all the individual test specs for ZIOApp functionality.
 */
object ZIOAppSuite extends ZIOBaseSpec {
  def spec = 
    suite("ZIOApp Suite")(
      // Core ZIOApp functionality tests that work across platforms
      ZIOAppSpec.spec,
      
      // Signal handling tests that verify graceful degradation across platforms
      ZIOAppSignalHandlingSpec.spec
      
      // Process-based tests are included automatically when running on JVM
      // via ZIOAppProcessSpec which is tagged with jvmOnly
    )
} 