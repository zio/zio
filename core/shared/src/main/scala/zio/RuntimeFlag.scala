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
 * A [[RuntimeFlag]] is a flag that can be set to enable or disable a particular
 * feature of the ZIO runtime. Each flag is one bit of a `RuntimeFlags` value.
 * The `Runtime.enableFlags` and `Runtime.disableFlags` layers change flags for
 * the effects they are provided to, and `ZIO.runtimeFlags` returns the flags
 * of the current fiber.
 */
sealed trait RuntimeFlag {
  /** The position of this flag's bit in a `RuntimeFlags` value. */
  def index: Int

  /** The `Int` in which only this flag's bit is set (`1 << index`). */
  def mask: Int

  /** The `Int` in which every bit except this flag's bit is set (`~mask`). */
  def notMask: Int
}

/**
 * The runtime flags. `Interruption` and `WindDown`, which the runtime manages
 * itself, are visible only inside the `zio` package.
 */
object RuntimeFlag {
  /**
   * The flags that `RuntimeFlags.toSet` and `RuntimeFlags.render` look for:
   * every flag except the deprecated `WorkStealing`.
   */
  val all: Set[RuntimeFlag] =
    Set(
      Interruption,
      CurrentFiber,
      OpLog,
      OpSupervision,
      RuntimeMetrics,
      FiberRoots,
      WindDown,
      CooperativeYielding,
      EagerShiftBack
    )

  /**
   * The interruption flag determines whether or not the ZIO runtime system will
   * interrupt a fiber. While it is disabled, as inside `ZIO.uninterruptible`,
   * an interruption waits until the flag is enabled again. Enabled in
   * `RuntimeFlags.default`.
   *
   * '''Note''': Modification of this flag is not recommended. It may cause
   * unexpected behavior.
   */
  private[zio] case object Interruption extends RuntimeFlag {
    final val index   = 0
    final val mask    = 1 << index
    final val notMask = ~mask
  }

  /**
   * The current fiber flag determines whether or not the ZIO runtime system
   * will store the current fiber inside a `ThreadLocal` whenever a fiber begins
   * executing on a thread. `Fiber.currentFiber()` reads that `ThreadLocal`, so
   * it returns `None` while this flag is disabled. Setting the `ThreadLocal`
   * each time costs some performance. Disabled in `RuntimeFlags.default`.
   */
  case object CurrentFiber extends RuntimeFlag {
    /** This flag's bit position: 1. */
    final val index   = 1
    /** This flag's bit: `1 << 1`, which is 2. */
    final val mask    = 1 << index
    /** Every bit except this flag's: `~2`, which is -3. */
    final val notMask = ~mask
  }

  /**
   * The op log flag. The ZIO runtime does not read it: enabling it changes
   * only what `ZIO.runtimeFlags` reports, and no operations are logged.
   * Disabled in `RuntimeFlags.default`.
   */
  case object OpLog extends RuntimeFlag {
    /** This flag's bit position: 2. */
    final val index   = 2
    /** This flag's bit: `1 << 2`, which is 4. */
    final val mask    = 1 << index
    /** Every bit except this flag's: `~4`, which is -5. */
    final val notMask = ~mask
  }

  /**
   * The op supervision flag determines whether the ZIO runtime calls the
   * `onEffect` method of the fiber's supervisor for each operation that the
   * fiber runs. It slows every operation down, and supervisors that observe
   * each operation, such as profilers, need it. Disabled in
   * `RuntimeFlags.default`.
   */
  case object OpSupervision extends RuntimeFlag {
    /** This flag's bit position: 3. */
    final val index   = 3
    /** This flag's bit: `1 << 3`, which is 8. */
    final val mask    = 1 << index
    /** Every bit except this flag's: `~8`, which is -9. */
    final val notMask = ~mask
  }

  /**
   * The runtime metrics flag determines whether fibers update the metrics in
   * `zio.metrics.Metric.runtime`: fibers started, fork locations, fiber
   * lifetimes, successes, failures and failure causes. A metrics connector can
   * export them, for example to Prometheus, like other ZIO metrics. Disabled
   * in `RuntimeFlags.default`.
   */
  case object RuntimeMetrics extends RuntimeFlag {
    /** This flag's bit position: 4. */
    final val index   = 4
    /** This flag's bit: `1 << 4`, which is 16. */
    final val mask    = 1 << index
    /** Every bit except this flag's: `~16`, which is -17. */
    final val notMask = ~mask
  }

  /**
   * The fiber roots flag determines whether the ZIO runtime records root
   * fibers, the fibers with no parent such as those started with `forkDaemon`
   * or by a `Runtime`, so that `Fiber.roots` and `Fiber.dumpAll` can find
   * them. Root fibers started while it is disabled are not recorded. Enabled
   * in `RuntimeFlags.default`.
   */
  case object FiberRoots extends RuntimeFlag {
    /** This flag's bit position: 5. */
    final val index   = 5
    /** This flag's bit: `1 << 5`, which is 32. */
    final val mask    = 1 << index
    /** Every bit except this flag's: `~32`, which is -33. */
    final val notMask = ~mask
  }

  /**
   * The wind down flag determines whether the ZIO runtime system will execute
   * effects in wind-down mode. In wind-down mode, even if interruption is
   * enabled and a fiber has been interrupted, the fiber will continue its
   * execution uninterrupted.
   *
   * '''Note''': Modification of this flag is not recommended. It may cause
   * unexpected behavior.
   */
  private[zio] case object WindDown extends RuntimeFlag {
    final val index   = 6
    final val mask    = 1 << index
    final val notMask = ~mask
  }

  /**
   * The cooperative yielding flag determines whether the ZIO runtime will yield
   * to another fiber while executing long-running effects or continuously
   * forking fibers.
   *
   * Disabling this flag is highly discouraged but it is necessary for cases
   * where fine-grained control over fiber scheduling is required. Enabled in
   * `RuntimeFlags.default`.
   */
  case object CooperativeYielding extends RuntimeFlag {
    /** This flag's bit position: 7. */
    final val index   = 7
    /** This flag's bit: `1 << 7`, which is 128. */
    final val mask    = 1 << index
    /** Every bit except this flag's: `~128`, which is -129. */
    final val notMask = ~mask
  }

  /**
   * The work stealing flag was meant to determine whether threads running
   * fibers about to asynchronously suspend first attempt to steal work before
   * suspending.
   *
   * '''Note''': this flag is not implemented: the ZIO runtime does not read
   * it, and `RuntimeFlag.all` does not contain it.
   */
  @deprecated("Unused + unimplemented: using this flag will have no effect", "2.1.19")
  case object WorkStealing extends RuntimeFlag {
    /** This flag's bit position: 8. */
    final val index   = 8
    /** This flag's bit: `1 << 8`, which is 256. */
    final val mask    = 1 << index
    /** Every bit except this flag's: `~256`, which is -257. */
    final val notMask = ~mask
  }

  /**
   * Determines whether the ZIO runtime will eagerly shift back execution to the
   * default executor after a blocking effect or a region run on another
   * executor (enabled) or minimize context shifts by continuing execution on
   * the same thread as long as possible (disabled). Disabled in
   * `RuntimeFlags.default`: after `ZIO.succeedBlocking`, for example, the
   * fiber goes on running on the blocking thread.
   *
   * Enabling this flag can positively or negatively affect performance
   * depending on the specific characteristics of the application. For more info
   * on this
   * [[https://blog.pierre-ricadat.com/tuning-zio-for-high-performance#heading-executor-override refer to this blog post.]]
   */
  case object EagerShiftBack extends RuntimeFlag {
    /** This flag's bit position: 9. */
    final val index   = 9
    /** This flag's bit: `1 << 9`, which is 512. */
    final val mask    = 1 << index
    /** Every bit except this flag's: `~512`, which is -513. */
    final val notMask = ~mask
  }
}
