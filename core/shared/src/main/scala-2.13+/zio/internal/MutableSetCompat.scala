package zio.internal

import scala.collection.mutable

trait MutableSetCompat[V] extends mutable.Set[V] {

  override def addOne(element: V): this.type = {
    add(element)
    this
  }

  override def subtractOne(element: V): this.type = {
    remove(element)
    this
  }

}
