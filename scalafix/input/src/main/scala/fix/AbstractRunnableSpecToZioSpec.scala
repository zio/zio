/*
rule = Zio2Upgrade
 */
package fix

import zio.test._

class AbstractRunnableSpecToZioSpec {
  object HasSpec extends DefaultRunnableSpec {
    override def spec: ZSpec[Environment, Failure] = ???
  }
}
