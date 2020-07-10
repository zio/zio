package zio

import java.{ util => ju }

import zio.test.Assertion.equalTo
import zio.test._

object ZSerializerSpec extends ZIOBaseSpec {
  import ZSerializer._

  def spec = suite("ZSerializerSpec")(
    suite("Primitive Serializers")(
      testM("StringSerializer")(
        checkSerializer(Gen.anyString)
      ),
      // testM("ByteArraySerializer")(
      //   //checkRoundtrip(Gen.)
      //   ???
      // ),
      testM("Char")(
        checkSerializer(Gen.anyChar)
      ),
      testM("TruncatedShortSerializer")(
        checkSerializer(Gen.anyShort)
      ),
      testM("TruncatedIntSerializer")(
        checkSerializer(Gen.anyInt)
      ),
      testM("TruncatedLongSerializer")(
        checkSerializer(Gen.anyLong)
      )
    ),
    suite("Serializable Serializers")(
      testM("UUID") {
        implicit val ser = SerializableZSerializer.forClass(classOf[ju.UUID])
        checkSerializer(Gen.anyUUID)
      }
    )
  )

  private def checkSerializer[E, V](gen: Gen[E, V])(implicit ser: ZSerializer[Any, V]) =
    checkM(gen) { (expected: V) =>
      for {
        bytes: Array[Byte]      <- ser.serialize(expected)
        fromBytes: V @unchecked <- ser.deserialize(bytes)
      } yield assert(fromBytes)(equalTo(expected))
    }
}
