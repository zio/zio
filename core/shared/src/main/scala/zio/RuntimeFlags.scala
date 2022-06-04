package zio

final case class RuntimeFlags(packed: Int) extends AnyVal {
  def diff(newValue: RuntimeFlags): RuntimeFlags.Patch =
    RuntimeFlags.Patch(packed ^ newValue.packed, newValue.packed)

  def disable(flag: RuntimeFlag): RuntimeFlags = RuntimeFlags(packed & flag.notMask)

  def disabled(flag: RuntimeFlag): Boolean =
    !enabled(flag)

  def enable(flag: RuntimeFlag): RuntimeFlags = RuntimeFlags(packed | flag.mask)

  def enabled(flag: RuntimeFlag): Boolean = (packed & flag.mask) != 0
}
object RuntimeFlags {
  final case class Patch(packed: Long) extends AnyVal {
    private final def active: Int  = ((packed >> 0) & 0xffffffff).toInt
    private final def enabled: Int = ((packed >> 32) & 0xffffffff).toInt

    def &(that: Patch): Patch =
      Patch(active | that.active, enabled & that.enabled)

    def |(that: Patch): Patch =
      Patch(active | that.active, enabled | that.enabled)

    def disabled(flag: RuntimeFlag): Boolean = ((active & flag.mask) != 0) && ((enabled & flag.mask) == 0)

    def enabled(flag: RuntimeFlag): Boolean = ((active & flag.mask) != 0) && ((enabled & flag.mask) != 0)

    def inverse: Patch = Patch(active, ~enabled)

    def patch(flag: RuntimeFlags): RuntimeFlags =
      RuntimeFlags(((~active) & flag.packed) | (enabled & flag.packed))
  }
  object Patch {
    def apply(active: Int, enabled: Int): Patch =
      Patch((active << 0) + (enabled << 32))
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
