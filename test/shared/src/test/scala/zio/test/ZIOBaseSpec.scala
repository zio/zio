package zio.test

import zio.DefaultRuntime

trait ZIOBaseSpec extends DefaultRuntime {
  def run: List[Async[(Boolean, String)]]
}
