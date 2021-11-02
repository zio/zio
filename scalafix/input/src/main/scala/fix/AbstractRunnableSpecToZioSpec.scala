/*
rule = ZIOSpecMigration
 */
package fix

import zio.{Has, ZLayer}
import zio.test._

class AbstractRunnableSpecToZioSpec {
  object HasSpec extends DefaultRunnableSpec {
    42
    override def spec: ZSpec[Environment, Failure] = ???
    
  }
}
