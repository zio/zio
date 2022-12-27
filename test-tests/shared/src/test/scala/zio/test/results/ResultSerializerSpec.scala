package zio.test.results

import zio.test.{ExecutionEvent, SuiteId, TestAnnotationMap, TestSuccess, assertTrue}

object ResultSerializerSpec extends zio.test.ZIOSpecDefault {
  override def spec =
    suite("ResultSerializerSpec")(test("test") {
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
}
