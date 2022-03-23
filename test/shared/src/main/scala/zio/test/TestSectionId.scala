package zio.test

import zio.{Random, ZIO}

import java.util.UUID

/**
 * @param id
 *   Level of the spec nesting that you are at. Suites get new values, test
 *   cases inherit their suite's
 */
// TODO rename
// TODO use int instead of UUID
case class TestSectionId(id: UUID)

object TestSectionId {
  val newRandom: ZIO[Random, Nothing, TestSectionId] =
    for {
      random <- zio.Random.nextUUID
    } yield TestSectionId(random)
}
