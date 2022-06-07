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
  def +(flag: RuntimeFlag): RuntimeFlags = self ++ RuntimeFlags(flag)

  def -(flag: RuntimeFlag): RuntimeFlags = self -- RuntimeFlags(flag)

  def ++(that: RuntimeFlags): RuntimeFlags = RuntimeFlags(self.packed | that.packed)

  def --(that: RuntimeFlags): RuntimeFlags = RuntimeFlags(self.packed & ~that.packed)

  def currentFiber: Boolean = isEnabled(RuntimeFlag.CurrentFiber)

  def diff(newValue: RuntimeFlags): RuntimeFlags.Patch =
    RuntimeFlags.Patch(packed ^ newValue.packed, newValue.packed)

  def disable(flag: RuntimeFlag): RuntimeFlags = RuntimeFlags(packed & flag.notMask)

  def isDisabled(flag: RuntimeFlag): Boolean = !isEnabled(flag)

  def enable(flag: RuntimeFlag): RuntimeFlags = RuntimeFlags(packed | flag.mask)

  def isEnabled(flag: RuntimeFlag): Boolean = (packed & flag.mask) != 0

  def fiberRoots: Boolean = isEnabled(RuntimeFlag.FiberRoots)

  def interruption: Boolean = isEnabled(RuntimeFlag.Interruption)

  /**
   * This method returns true only if the flag `Interruption` is ENABLED, and
   * also the flag `WindDown` is DISABLED.
   *
   * A fiber is said to be interruptible if the feature of Interruption is
   * turned on, and the fiber is not in its wind-down phase, in which it takes
   * care of cleanup activities related to fiber shutdown.
   */
  def interruptible: Boolean = interruption && !windDown

  def opLog: Boolean = isEnabled(RuntimeFlag.OpLog)

  def opSupervision: Boolean = isEnabled(RuntimeFlag.OpSupervision)

  def patch(p: RuntimeFlags.Patch): RuntimeFlags = p(self)

  def runtimeMetrics: Boolean = isEnabled(RuntimeFlag.RuntimeMetrics)

  def toSet: Set[RuntimeFlag] = RuntimeFlag.all.filter(isEnabled(_))

  override def toString(): String =
    toSet.mkString("RuntimeFlags(", ", ", ")")

  def windDown: Boolean = isEnabled(RuntimeFlag.WindDown)
}
object RuntimeFlags {
  final case class Patch(packed: Long) extends AnyVal { self =>
    def &(that: Patch): Patch =
      Patch(active | that.active, enabled & that.enabled)

    def |(that: Patch): Patch =
      Patch(active | that.active, enabled | that.enabled)

    def <>(that: Patch): Patch = self | that

    def apply(flag: RuntimeFlags): RuntimeFlags =
      RuntimeFlags((flag.packed & (~active | enabled)) | (active & enabled))

    def without(flag: RuntimeFlag): RuntimeFlags.Patch = RuntimeFlags.Patch(active & flag.notMask, enabled)

    def isDisabled(flag: RuntimeFlag): Boolean = ((active & flag.mask) != 0) && ((enabled & flag.mask) == 0)

    def isEnabled(flag: RuntimeFlag): Boolean = ((active & flag.mask) != 0) && ((enabled & flag.mask) != 0)

    def inverse: Patch = Patch(active, ~enabled)

    def isEmpty: Boolean = active == 0L

    def enabledSet: Set[RuntimeFlag] = RuntimeFlags(active & enabled).toSet

    def disabledSet: Set[RuntimeFlag] = RuntimeFlags(active & ~enabled).toSet

    override def toString(): String = {
      val enabledS =
        enabledSet.mkString("(", ", ", ")")

      val disabledS =
        disabledSet.mkString("(", ", ", ")")

      s"RuntimeFlags.Patch(enabled = ${enabledS}, isDisabled = ${disabledS})"
    }

    private def active: Int  = ((packed >> 0) & 0xffffffff).toInt
    private def enabled: Int = ((packed >> 32) & 0xffffffff).toInt
  }
  object Patch {
    def apply(active: Int, enabled: Int): Patch =
      Patch((active.toLong << 0) + ((enabled & active).toLong << 32))

    val empty: Patch = Patch(0, 0)
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
