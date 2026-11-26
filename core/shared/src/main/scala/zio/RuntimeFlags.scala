/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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
 * Operations on `RuntimeFlags` values, which are sets of runtime flags stored
 * as the bits of an `Int`, and on `RuntimeFlags.Patch` values, which describe
 * changes to them. Runtime flags affect the operation of the ZIO runtime
 * system. They are exposed to application-level code because they affect the
 * behavior and performance of application code.
 *
 * For more information on individual flags, see [[zio.RuntimeFlag]].
 */
object RuntimeFlags {

  /** Returns `true` when `CooperativeYielding` is enabled in `flags`. */
  def cooperativeYielding(flags: RuntimeFlags): Boolean =
    isEnabled(flags, RuntimeFlag.CooperativeYielding.mask)

  /** Returns `true` when `CurrentFiber` is enabled in `flags`. */
  def currentFiber(flags: RuntimeFlags): Boolean =
    isEnabled(flags, RuntimeFlag.CurrentFiber.mask)

  /**
   * Returns the patch that turns `oldValue` into `newValue`: it enables the
   * flags that only `newValue` has and disables the flags that only `oldValue`
   * has.
   */
  def diff(oldValue: RuntimeFlags, newValue: RuntimeFlags): RuntimeFlags.Patch =
    RuntimeFlags.Patch(oldValue ^ newValue, newValue)

  /** Returns `flags` with `flag` disabled. */
  def disable(flags: RuntimeFlags)(flag: RuntimeFlag): RuntimeFlags =
    flags & flag.notMask

  /** Returns `self` with the flags enabled in `that` disabled. */
  def disableAll(self: RuntimeFlags)(that: RuntimeFlags): RuntimeFlags =
    self & ~that

  /** Returns `true` when `EagerShiftBack` is enabled in `flags`. */
  def eagerShiftBack(flags: RuntimeFlags): Boolean =
    isEnabled(flags, RuntimeFlag.EagerShiftBack.mask)

  /** Returns `flags` with `flag` enabled. */
  def enable(flags: RuntimeFlags)(flag: RuntimeFlag): RuntimeFlags =
    flags | flag.mask

  /** Returns `self` with the flags enabled in `that` enabled too. */
  def enableAll(self: RuntimeFlags)(that: RuntimeFlags): RuntimeFlags =
    self | that

  /** Returns `true` when `FiberRoots` is enabled in `flags`. */
  def fiberRoots(flags: RuntimeFlags): Boolean =
    isEnabled(flags, RuntimeFlag.FiberRoots.mask)

  /**
   * This method returns true only if the flag `Interruption` is ENABLED, and
   * also the flag `WindDown` is DISABLED.
   *
   * A fiber is said to be interruptible if the feature of Interruption is
   * turned on, and the fiber is not in its wind-down phase, in which it takes
   * care of cleanup activities related to fiber shutdown.
   */
  def interruptible(flags: RuntimeFlags): Boolean =
    interruption(flags) && !windDown(flags)

  /** Returns `true` when `Interruption` is enabled in `flags`. */
  def interruption(flags: RuntimeFlags): Boolean =
    isEnabled(flags, RuntimeFlag.Interruption.mask)

  /** Returns `true` when `flag` is disabled in `flags`. */
  def isDisabled(flags: RuntimeFlags)(flag: RuntimeFlag): Boolean =
    !isEnabled(flags, flag.mask)

  /** Returns `true` when `flag` is enabled in `flags`. */
  def isEnabled(flags: RuntimeFlags)(flag: RuntimeFlag): Boolean =
    isEnabled(flags, flag.mask)

  /**
   * Optimized variant which doesn't rely on the megamorphic call to `.mask`.
   * Prefer using this method when the RuntimeFlag being tested is statically
   * known
   */
  private def isEnabled(flags: RuntimeFlags, mask: Int): Boolean =
    (flags & mask) != 0

  /** Returns `true` when `OpLog` is enabled in `flags`. */
  def opLog(flags: RuntimeFlags): Boolean =
    isEnabled(flags, RuntimeFlag.OpLog.mask)

  /** Returns `true` when `OpSupervision` is enabled in `flags`. */
  def opSupervision(flags: RuntimeFlags): Boolean =
    isEnabled(flags, RuntimeFlag.OpSupervision.mask)

  /** Applies `patch` to `flags`, as `RuntimeFlags.Patch.patch` does. */
  def patch(patch: RuntimeFlags.Patch)(flags: RuntimeFlags): RuntimeFlags =
    Patch.patch(patch)(flags)

  /**
   * Renders the flags of `RuntimeFlag.all` that are enabled in `flags`, for
   * example `RuntimeFlags(Interruption, CooperativeYielding, FiberRoots)`.
   */
  def render(flags: RuntimeFlags): String =
    toSet(flags).mkString("RuntimeFlags(", ", ", ")")

  /** Returns `true` when `RuntimeMetrics` is enabled in `flags`. */
  def runtimeMetrics(flags: RuntimeFlags): Boolean =
    isEnabled(flags, RuntimeFlag.RuntimeMetrics.mask)

  /**
   * Returns the flags of `RuntimeFlag.all` that are enabled in `flags`. The
   * deprecated `WorkStealing` flag is not in `RuntimeFlag.all`.
   */
  def toSet(flags: RuntimeFlags): Set[RuntimeFlag] =
    RuntimeFlag.all.filter(isEnabled(flags))

  /** Returns `true` when `WindDown` is enabled in `flags`. */
  def windDown(flags: RuntimeFlags): Boolean =
    isEnabled(flags, RuntimeFlag.WindDown.mask)

  /** Returns `true` when the unused `WorkStealing` flag is set in `flags`. */
  @deprecated("Unused + unimplemented: using this flag will have no effect", "2.1.19")
  def workStealing(flags: RuntimeFlags): Boolean =
    isEnabled(flags, RuntimeFlag.WorkStealing.mask)

  /**
   * A change to `RuntimeFlags` values, stored in a `Long`. The low 32 bits mark
   * the flags that the patch changes, its active flags, and the high 32 bits
   * mark the active flags that it enables; it disables the other active flags.
   */
  type Patch = Long

  /** Operations on `RuntimeFlags.Patch` values. */
  object Patch {

    /**
     * Combines two patches into one that changes the flags that either patch
     * changes and enables the flags that either patch enables. When one patch
     * enables a flag and the other disables it, the combined patch enables it
     * whatever the order: with `on = RuntimeFlags.enable(flag)` and
     * `off = RuntimeFlags.disable(flag)`, `andThen(on, off)` enables `flag`,
     * while applying `on` and then `off` disables it.
     */
    def andThen(first: Patch, second: Patch): Patch =
      first | second

    /**
     * Creates a patch that changes the flags set in `active`, enabling those
     * that are also set in `enabled` and disabling the others. Flags set in
     * `enabled` but not in `active` are left out.
     */
    def apply(active: Int, enabled: Int): Patch =
      (active.toLong << 0) + ((enabled & active).toLong << 32)

    /**
     * Combines two patches into one that changes the flags that either patch
     * changes, and enables a flag only when both patches enable it: a flag that
     * only one of them enables is disabled.
     */
    def both(left: Patch, right: Patch): Patch =
      Patch(active(left) | active(right), enabled(left) & enabled(right))

    /** Returns the flags of `RuntimeFlag.all` that `patch` disables. */
    def disabledSet(patch: Patch): Set[RuntimeFlag] =
      RuntimeFlags.toSet(active(patch) & ~enabled(patch))

    /**
     * Combines two patches into one that changes the flags that either patch
     * changes, and enables a flag when either patch enables it.
     */
    def either(left: Patch, right: Patch): Patch =
      Patch(active(left) | active(right), enabled(left) | enabled(right))

    /** The patch that changes no flag. */
    val empty: Patch = Patch(0, 0)

    /** Returns the flags of `RuntimeFlag.all` that `patch` enables. */
    def enabledSet(patch: Patch): Set[RuntimeFlag] =
      RuntimeFlags.toSet(active(patch) & enabled(patch))

    /** Returns `patch` without its change to `flag`. */
    def exclude(patch: Patch)(flag: RuntimeFlag): Patch =
      exclude(patch, flag.notMask)

    /**
     * Optimized variant of [[exclude]] that doesn't rely on the megamorphic
     * call to `.notMask`
     */
    private[zio] def exclude(patch: Patch, notMask: Int): Patch =
      Patch(active(patch) & notMask, enabled(patch))

    /**
     * Returns `true` when `patch` changes `flag`, by enabling or disabling it.
     * Same as `isActive`.
     */
    def includes(patch: Patch)(flag: RuntimeFlag): Boolean =
      ((active(patch) & flag.mask) != 0)

    /**
     * Returns the patch that changes the same flags in the other direction: it
     * disables the flags that `patch` enables and enables those it disables.
     */
    def inverse(patch: Patch): Patch =
      Patch(active(patch), ~enabled(patch))

    /**
     * Returns `true` when `patch` changes `flag`, by enabling or disabling it.
     * Same as `includes`.
     */
    def isActive(patch: Patch)(flag: RuntimeFlag): Boolean =
      isActive(patch, flag.mask)

    /**
     * Optimized variant of `isActive` that doesn't rely on the megamorphic call
     * to `.mask`
     */
    private def isActive(patch: Patch, mask: Int): Boolean =
      (active(patch) & mask) != 0

    /** Returns `true` when `patch` disables `flag`. */
    def isDisabled(patch: Patch)(flag: RuntimeFlag): Boolean =
      isDisabled(patch, flag.mask)

    /**
     * Optimized variant of [[isDisabled]] that doesn't rely on the megamorphic
     * call to `.mask`
     */
    private[zio] def isDisabled(patch: Patch, mask: Int): Boolean =
      isActive(patch, mask) && ((enabled(patch) & mask) == 0)

    /** Returns `true` when `patch` changes no flag. */
    def isEmpty(patch: Patch): Boolean =
      active(patch) == 0L

    /** Returns `true` when `patch` enables `flag`. */
    def isEnabled(patch: Patch)(flag: RuntimeFlag): Boolean =
      isEnabled(patch, flag.mask)

    /**
     * Optimized variant of [[isEnabled]] that doesn't rely on the megamorphic
     * call to `.mask`
     */
    private[zio] def isEnabled(patch: Patch, mask: Int): Boolean =
      isActive(patch, mask) && ((enabled(patch) & mask) != 0)

    /**
     * Applies `patch` to `flags`: enables the flags that it enables, disables
     * the flags that it disables and leaves the other flags as they are.
     */
    def patch(patch: Patch)(flags: RuntimeFlags): RuntimeFlags =
      (flags & (~active(patch) | enabled(patch))) | (active(patch) & enabled(patch))

    /**
     * Renders the patch, for example as
     * `RuntimeFlags.Patch(enabled = (CurrentFiber), disabled = (OpLog))`.
     */
    def render(patch: Patch): String = {
      val enabledS =
        enabledSet(patch).mkString("(", ", ", ")")

      val disabledS =
        disabledSet(patch).mkString("(", ", ", ")")

      s"RuntimeFlags.Patch(enabled = ${enabledS}, disabled = ${disabledS})"
    }

    private def active(patch: Patch): Int  = ((patch >> 0) & 0xffffffff).toInt
    private def enabled(patch: Patch): Int = ((patch >> 32) & 0xffffffff).toInt
  }

  /** Creates a `RuntimeFlags` value in which only `flags` are enabled. */
  def apply(flags: RuntimeFlag*): RuntimeFlags =
    flags.foldLeft(0)(_ | _.mask)

  /**
   * The default set of runtime flags, recommended for most applications:
   * `Interruption`, `CooperativeYielding` and `FiberRoots`.
   */
  val default: RuntimeFlags =
    RuntimeFlags(
      RuntimeFlag.FiberRoots,
      RuntimeFlag.Interruption,
      RuntimeFlag.CooperativeYielding
    )

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

  private[zio] val disableInterruption: RuntimeFlags.Patch = disable(RuntimeFlag.Interruption)
  private[zio] val enableInterruption: RuntimeFlags.Patch  = enable(RuntimeFlag.Interruption)

  /**
   * No runtime flags.
   */
  val none: RuntimeFlags = 0
}
