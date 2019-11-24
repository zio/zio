package zio.test

case class Summary(success: Int, fail: Int, ignore: Int, summary: String) {
  def total: Int = success + fail + ignore
}
