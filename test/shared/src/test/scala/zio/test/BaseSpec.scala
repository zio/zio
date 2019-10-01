package zio.test

import zio.DefaultRuntime

trait BaseSpec extends DefaultRuntime {
  def run: List[Async[(Boolean, String)]]
}
