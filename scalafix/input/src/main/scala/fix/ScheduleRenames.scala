/*
rule = Zio2Upgrade
 */
package fix

import zio.Schedule.once

object ScheduleRenames {
  once.addDelayM _
  once.checkM _
  once.contramapM _
  once.delayedM _
  once.dimapM _
  once.foldM _
  once.mapM _
  once.modifyDelayM _
  once.reconsiderM _
  once.untilInputM _
  once.untilOutputM _
  once.whileInputM _
  once.whileOutputM _
}
