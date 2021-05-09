package zio.stream

import scala.reflect.ClassTag

trait ZStreamOps {
  implicit class Syntax[R, E, O](self: ZStream[R, E, O]) {
    /*
     * Collect elements of the given type flowing through the stream, and filters out others.
     */
    final def collectType[O1 <: O](implicit tag: ClassTag[O1]): ZStream[R, E, O1] =
      self.collect({ case o if tag.runtimeClass.isInstance(o) => o.asInstanceOf[O1] })
  }
}
