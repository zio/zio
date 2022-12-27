package zio.test.results

import zio.test._

import java.time.Instant

object ResultSerializerSpec extends zio.test.ZIOSpecDefault {
  override def spec =
    suite("ResultSerializerSpec")(suite("full")(test("test") {
      val input = ExecutionEvent.Test(
        List("testName", "suiteName"),
        Right(TestSuccess.Succeeded()),
        TestAnnotationMap.empty,
        List(SuiteId(1)),
        1,
        SuiteId(1),
        "dev.zio"
      )
      assertTrue(
        ResultSerializer.Json.render(input) ==
          """
            |    {
            |       "name" : "dev.zio/suiteName/testName",
            |       "status" : "Success",
            |       "durationMillis" : "1",
            |       "annotations" : "",
            |       "fullyQualifiedClassName" : "dev.zio",
            |       "labels" : ["suiteName", "testName"],
            |    },""".stripMargin
      )
    })
      ,
      suite("annotations map")(
        test("timed") {
          assertTrue(
          ResultSerializer.Json.jsonify(
          TestAnnotationMap.empty.annotate(
            TestAnnotation.timing,
            TestDuration.fromInterval(
              Instant.parse("2020-01-01T00:00:00Z"),
              Instant.parse("2020-01-01T00:00:01Z"))
          )
          ) == "1 s"
          )
        }
      )
    )

}
