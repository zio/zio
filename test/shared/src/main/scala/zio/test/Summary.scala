package zio.test

final case class Summary(success: Int, fail: Int, ignore: Int, summary: String) {
  def total: Int = success + fail + ignore
}
