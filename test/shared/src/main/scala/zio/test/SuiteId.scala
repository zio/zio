package zio.test

import zio.{Random, ZIO}

import java.util.UUID

/**
 * @param id
 *   Level of the spec nesting that you are at. Suites get new values, test
 *   cases inherit their suite's
 */
case class SuiteId(id: Int)

object SuiteId {
  val newRandom: ZIO[Any, Nothing, SuiteId] =
    for {
      // TODO  Consider counting up from 0, rather than completely random ints
      random <- zio.Random.nextInt
    } yield SuiteId(random)
}
