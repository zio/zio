package zio.blocks.schema

import zio.Scope
import zio.test.Assertion.equalTo
import zio.test._

object SchemaSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("SchemaSpec")(
    suite("standard types")(
      test("Schema.byte") {
        assert(Schema.byte == Schema.byte)(equalTo(true))
      }
    )
  )
}