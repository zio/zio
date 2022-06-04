package zio

final case class RuntimeFlags(packed: Int) extends AnyVal { self =>
  def currentFiber: Boolean = enabled(RuntimeFlag.CurrentFiber)

  def diff(newValue: RuntimeFlags): RuntimeFlags.Patch =
    RuntimeFlags.Patch(packed ^ newValue.packed, newValue.packed)

  def disable(flag: RuntimeFlag): RuntimeFlags = RuntimeFlags(packed & flag.notMask)

  def disabled(flag: RuntimeFlag): Boolean =
    !enabled(flag)

  def enable(flag: RuntimeFlag): RuntimeFlags = RuntimeFlags(packed | flag.mask)

  def enabled(flag: RuntimeFlag): Boolean = (packed & flag.mask) != 0

  def fiberRoots: Boolean = enabled(RuntimeFlag.FiberRoots)

  def interruption: Boolean = enabled(RuntimeFlag.Interruption)

  def opLog: Boolean = enabled(RuntimeFlag.OpLog)

  def opSupervision: Boolean = enabled(RuntimeFlag.OpSupervision)

  def patch(p: RuntimeFlags.Patch): RuntimeFlags = p(self)

  def runtimeMetrics: Boolean = enabled(RuntimeFlag.RuntimeMetrics)

  def toSet: Set[RuntimeFlag] = RuntimeFlag.all.filter(enabled)

  override def toString(): String =
    toSet.mkString("RuntimeFlags(", ", ", ")")
}
object RuntimeFlags {
  final case class Patch(packed: Long) extends AnyVal {
    def apply(flag: RuntimeFlags): RuntimeFlags =
      RuntimeFlags(((~active) & flag.packed) | enabled)

    private final def active: Int  = ((packed >> 0) & 0xffffffff).toInt
    private final def enabled: Int = ((packed >> 32) & 0xffffffff).toInt

    def &(that: Patch): Patch =
      Patch(active | that.active, enabled & that.enabled)

    def |(that: Patch): Patch =
      Patch(active | that.active, enabled | that.enabled)

    def disabled(flag: RuntimeFlag): Boolean = ((active & flag.mask) != 0) && ((enabled & flag.mask) == 0)

    def enabled(flag: RuntimeFlag): Boolean = ((active & flag.mask) != 0) && ((enabled & flag.mask) != 0)

    def inverse: Patch = Patch(active, ~enabled)

    def enabledSet: Set[RuntimeFlag] = apply(RuntimeFlags.none).toSet

    def disabledSet: Set[RuntimeFlag] = RuntimeFlags(active & ~enabled).toSet

    override def toString(): String = {
      val enabledS =
        enabledSet.mkString("(", ", ", ")")

      val disabledS =
        disabledSet.mkString("(", ", ", ")")

      s"RuntimeFlags.Patch(enabled = ${enabledS}, disabled = ${disabledS})"
    }
  }
  object Patch {
    def apply(active: Int, enabled: Int): Patch =
      Patch((active.toLong << 0) + (enabled.toLong << 32))
  }

  def apply(flags: RuntimeFlag*): RuntimeFlags =
    RuntimeFlags(flags.foldLeft(0)(_ | _.mask))

  val default: RuntimeFlags =
    RuntimeFlags(RuntimeFlag.FiberRoots, RuntimeFlag.Interruption)

  def disable(flag: RuntimeFlag): RuntimeFlags.Patch =
    RuntimeFlags.Patch(flag.mask, 0)

  def enable(flag: RuntimeFlag): RuntimeFlags.Patch =
    RuntimeFlags.Patch(flag.mask, flag.mask)

  def fromPacked(packed: Int): RuntimeFlags = RuntimeFlags(packed)

  val none: RuntimeFlags = RuntimeFlags(0)
}
