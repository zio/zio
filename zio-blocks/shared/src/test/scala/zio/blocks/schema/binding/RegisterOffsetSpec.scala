package zio.blocks.schema.binding

import zio.Scope
import zio.test.Assertion._
import zio.test._

object RegisterOffsetSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("RegisterOffsetSpec")(
    suite("RegisterOffset.apply")(
      test("calculates correct RegisterOffset") {
        assert(
          RegisterOffset(
            booleans = 1,
            bytes = 2,
            chars = 3,
            shorts = 4,
            floats = 5,
            ints = 6,
            doubles = 7,
            longs = 8,
            objects = 9
          )
        )(equalTo(((1 + 2 + (3 + 4) * 2 + (5 + 6) * 4 + (7 + 8) * 8) << 16) | 9))
      },
      test("throws IllegalArgumentException in case of overflow") {
        assert(
          RegisterOffset(
            booleans = 1,
            bytes = 2,
            chars = 3,
            shorts = 4,
            floats = 5,
            ints = 6,
            doubles = 7,
            longs = 8,
            objects = 65536
          )
        )(throwsA[IllegalArgumentException]) &&
        assert(
          RegisterOffset(
            booleans = 1,
            bytes = 2,
            chars = 3,
            shorts = 4,
            floats = 5,
            ints = 6,
            doubles = 4096,
            longs = 4096,
            objects = 9
          )
        )(throwsA[IllegalArgumentException])
      }
    ),
    suite("RegisterOffset.add")(
      test("adds RegisterOffset values") {
        val offset = RegisterOffset(
          booleans = 1,
          bytes = 2,
          chars = 3,
          shorts = 4,
          floats = 5,
          ints = 6,
          doubles = 7,
          longs = 8,
          objects = 9
        )
        assert(RegisterOffset.add(offset, offset))(
          equalTo(
            RegisterOffset(
              booleans = 2,
              bytes = 4,
              chars = 6,
              shorts = 8,
              floats = 10,
              ints = 12,
              doubles = 14,
              longs = 16,
              objects = 18
            )
          )
        )
      },
      test("throws IllegalArgumentException in case of overflow") {
        val offset1 = RegisterOffset(
          booleans = 1,
          bytes = 2,
          chars = 3,
          shorts = 4,
          floats = 5,
          ints = 6,
          doubles = 7,
          longs = 8,
          objects = 32768
        )
        val offset2 = RegisterOffset(
          booleans = 1,
          bytes = 2,
          chars = 3,
          shorts = 4,
          floats = 5,
          ints = 6,
          doubles = 2048,
          longs = 2048,
          objects = 9
        )
        assert(RegisterOffset.add(offset1, offset1))(throwsA[IllegalArgumentException]) &&
        assert(RegisterOffset.add(offset2, offset2))(throwsA[IllegalArgumentException])
      }
    )
  )
}
