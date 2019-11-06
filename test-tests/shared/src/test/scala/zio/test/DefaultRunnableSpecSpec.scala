package zio.test

object DefaultRunnableSpecSpec extends DefaultRunnableSpec {

  override def spec = suite("DefaultRunnableSpecSpec")(
    test("inheritance style specification of spec is allowed") {
      assertCompletes
    }
  )
}
