/*
 * Copyright 2021-2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio

/**
 * Maintains a set of runtime flags. Runtime flags affect the operation of the
 * ZIO runtime system. They are exposed to application-level code because they
 * affect the behavior and performance of application code.
 *
 * For more information on individual flags, see [[zio.RuntimeFlag]].
 */
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

  /**
   * The default set of runtime flags, recommended for most applications.
   */
  val default: RuntimeFlags =
    RuntimeFlags(RuntimeFlag.FiberRoots, RuntimeFlag.Interruption)

  /**
   * Creates a patch that disables the specified runtime flag.
   */
  def disable(flag: RuntimeFlag): RuntimeFlags.Patch =
    RuntimeFlags.Patch(flag.mask, 0)

  /**
   * Creates a patch that enables the specified runtime flag.
   */
  def enable(flag: RuntimeFlag): RuntimeFlags.Patch =
    RuntimeFlags.Patch(flag.mask, flag.mask)

  /**
   * No runtime flags.
   */
  val none: RuntimeFlags = RuntimeFlags(0)
}
