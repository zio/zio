package zio.stream.internal

import zio._

/**
 * A mutable builder used for aggregating values of type `I` into values of type `O`
 * through a [[ZSummary]].
 */
sealed abstract class Builder[-R, +E, -I, +O] { self =>

  /**
   * Wraps this builder as an effectual builder. If it is already
   * an effectual builder, this operation does nothing.
   */
  def effectual: Builder.Effectual[R, E, I, O] =
    self match {
      case effectual: Builder.Effectual[R, E, I, O] => effectual
      case mutable: Builder.Mutable[I, O] =>
        new Builder.Effectual[Any, Nothing, I, O] {
          override def reset: ZIO[Any, Nothing, Unit]            = UIO(mutable.reset())
          override def accepts(i: I): ZIO[Any, Nothing, Boolean] = UIO(mutable.accepts(i))
          override def done: ZIO[Any, Nothing, Boolean]          = UIO(mutable.done())
          override def step(i: I): ZIO[Any, Nothing, Unit]       = UIO(mutable.step(i))
          override def extract: ZIO[Any, Nothing, O]             = UIO(mutable.extract())
        }
    }
}

object Builder {

  /**
   * An impure, mutable builder that summarizes `I` values into `O` values. Values
   * of this type should be considered mutable and unsafe to invoke concurrently.
   */
  abstract class Mutable[-I, +O] extends Builder[Any, Nothing, I, O] {

    /**
     * Resets the state of the builder. This should be equivalent to creating
     * a fresh builder.
     */
    def reset(): Unit

    /**
     * Copies this builder to a new builder. The new builder must not reference any
     * mutable state in this builder.
     *
     * The state of the resulting builder is undefined. It is required to call `reset`
     * before stepping the builder.
     */
    def copy(): Mutable[I, O]

    /**
     * Tests whether the builder will accept the proposed element. The caller
     * must always verify this predicate before calling `step`, and must
     * only call `step` if this method returns `true`.
     */
    def accepts(i: I): Boolean

    /**
     * Tests whether the builder is done aggregating and must be extracted and reset
     * before calling `step` again.
     */
    def done(): Boolean

    /**
     * Adds another input to the builder's summary.
     */
    def step(i: I): Unit

    /**
     * Extracts the summary value maintained by the builder. The builder must remain
     * valid for use after this call.
     */
    def extract(): O
  }

  /**
   * An effectual builder that summarizes `I` values into `O` values, using
   * an environment of type `R` with potential failures of type `E`.
   *
   * It is unsafe to invoke methods of effectual builders concurrently.
   */
  abstract class Effectual[-R, +E, -I, +O] extends Builder[R, E, I, O] {

    /**
     * Resets the state of the builder. This should be equivalent to creating
     * a fresh builder.
     */
    def reset: ZIO[R, E, Unit]

    /**
     * Tests whether the builder will accept the proposed element. The caller
     * must always verify this predicate before calling `step`, and must
     * only call `step` if this method returns `true`.
     */
    def accepts(i: I): ZIO[R, E, Boolean]

    /**
     * Tests whether the builder is done aggregating and must be extracted and reset
     * before calling step again.
     */
    def done: ZIO[R, E, Boolean]

    /**
     * Feeds an element to the builder.
     */
    def step(i: I): ZIO[R, E, Unit]

    /**
     * Extracts a result from the builder.
     */
    def extract: ZIO[R, E, O]
  }
}
