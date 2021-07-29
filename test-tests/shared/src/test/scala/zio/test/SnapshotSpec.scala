package zio.test

import zio.test.Assertion.equalTo

object SnapshotSpec extends ZIOBaseSpec {

  override def spec: ZSpec[Any, Any] =
    suite("matchSnapshot suite")(
      snapshotTest("first")("goodbye how are you doing on this terrible day?"),
//      snapshotTestM("second")(Task("HEY")),
//      snapshotTestM("no-snap-file")(Task("HEY")),
      //[info]   - failure
      //[info]      did not satisfy No snapshot __snapshots__/failure file found()
      // But should fail that effect failed
      // firstly evaluate test effect then compare it with result
      //      snapshotTestM("failure")(ZIO.fail(new Exception("asdf"))),
      test("not a snapshot")(
        assert(1)(equalTo(1))
      )
    )
}
