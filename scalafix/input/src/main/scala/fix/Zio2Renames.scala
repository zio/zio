/*
rule = Zio2Upgrade
*/
package fix

import zio._

object Zio2Renames {

  val effect = ZIO("cool")

  val halt     = ZIO.halt(Cause.fail("fail"))
  val haltWith = ZIO.haltWith(_ => Cause.fail("fail"))

  val toManaged_ = effect.toManaged_
  val toManaged  = effect.toManaged(_ => UIO.unit)
// bimap -> mapBoth
  val bimap = effect.bimap(_ => UIO.unit, _ => UIO.unit)
// bracket -> acquireReleaseWith
// bracket -> acquireReleaseWith
// bracket_ -> acquireRelease
// bracket_ -> acquireRelease
// bracketExit -> acquireReleaseExitWith
// bracketExit -> acquireReleaseExitWith
// bracketOnError -> acquireReleaseOnErrorWith
// collectM -> collectZIO
// filterOrElse_ -> filterOrElse
// foldCauseM -> foldCauseZIO
// foldM -> foldZIO
// foldTraceM -> foldTraceZIO
// mapEffect -> mapAttempt
// optional -> unoption
// rejectM -> rejectZIO
// repeatUntilM -> repeatUntilZIO
// repeatWhileM -> repeatWhileZIO
// replicateM -> replicateZIO
// replicateM_ -> replicateZIODiscard
// retryUntilM -> retryUntilZIO
// retryWhileM -> retryWhileZIO
// run -> exit
// someOrElseM -> someOrElseZIO
// unlessM -> unlessZIO
// whenM -> whenZIO
}