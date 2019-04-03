package scalaz.zio.delay

import scalaz.zio.duration.Duration

class DelaySyntax(duration: Duration){
  def relative = Delay.relative(duration)
}