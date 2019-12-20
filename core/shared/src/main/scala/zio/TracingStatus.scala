package zio

/**
 * Whether ZIO Tracing is enabled for the current fiber in the current region.
 */
sealed abstract class TracingStatus extends Serializable with Product {
  final def isTraced: Boolean   = this match { case TracingStatus.Traced => true; case _ => false }
  final def isUntraced: Boolean = !isTraced

  private[zio] final def toBoolean: Boolean = isTraced
}
object TracingStatus {
  final def traced: TracingStatus   = Traced
  final def untraced: TracingStatus = Untraced

  case object Traced   extends TracingStatus
  case object Untraced extends TracingStatus

  private[zio] def fromBoolean(b: Boolean): TracingStatus = if (b) Traced else Untraced
}
