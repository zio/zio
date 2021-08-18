package zio.test.magnolia

import zio.test.diff.DiffInstances

/**
 * Use `import zio.test.magnolia.diff._` to get automatic case class diffing
 * when using `smartAssert(result == SomeCaseClass("Nice", 123))`.
 */
package object diff extends DiffInstances with DeriveDiff {}
