package fix

import zio.Schedule.once

object ScheduleRenames {
  once.addDelayZIO _
  once.checkZIO _
  once.contramapZIO _
  once.delayedZIO _
  once.dimapZIO _
  once.foldZIO _
  once.mapZIO _
  once.modifyDelayZIO _
  once.reconsiderZIO _
  once.untilInputZIO _
  once.untilOutputZIO _
  once.whileInputZIO _
  once.whileOutputZIO _
}
