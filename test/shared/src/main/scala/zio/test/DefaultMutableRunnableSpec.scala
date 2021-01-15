package zio.test

import zio.ZLayer
import zio.test.environment.TestEnvironment

/**
 * Syntax for writing test like
 * {{{
 * object MySpec extends DefaultMutableRunnableSpec {
 *   suite("foo") {
 *     testM("name") {
 *     } @@ ignore
 *
 *     test("name 2")
 *   }
 *   suite("another suite") {
 *     test("name 3")
 *   }
 * }
 * }}}
 */
class DefaultMutableRunnableSpec extends MutableRunnableSpec(ZLayer.identity[TestEnvironment])
