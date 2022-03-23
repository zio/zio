package zio.test

import zio.{Random, ZIO}

import java.util.UUID

/**
 * @param id
 *   Level of the spec nesting that you are at. Suites get new values, test
 *   cases inherit their suite's
 */
// TODO use int instead of UUID
case class SuiteId(id: UUID)

object SuiteId {
  val newRandom: ZIO[Random, Nothing, SuiteId] =
    for {
      random <- zio.Random.nextUUID
    } yield SuiteId(random)
}
