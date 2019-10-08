package zio.test

case class TestArgs(testSearchTerm: Option[String])

object TestArgs {
  def empty: TestArgs = TestArgs(Option.empty[String])

  def parse(args: Array[String]): TestArgs = {
    // TODO: Add a proper command-line parser
    val parsedArgs = args
      .sliding(2, 2)
      .collect {
        case Array("-t", term) => Map("testSearchTerm" -> term)
      }
      .foldLeft(Map.empty[String, String])(_ ++ _)

    TestArgs(parsedArgs.get("testSearchTerm"))
  }
}
