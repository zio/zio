package zio.test

object RunnableSpecSpec extends RunnableSpec {

  def spec = suite("RunnableSpecSpec")(
    test("inheritance style specification of spec is allowed") {
      assertCompletes
    }
  )
}
