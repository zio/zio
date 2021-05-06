//import zio._
//
//object LayerAppendSyntax {
//  type ++[A,B] = (A,B) match {
//    case (_ <: Has[_], _ <: Has[_])  => A & B
//    case (a, b & Has[_]) => Has[a] & B
//    case (a & Has[_], b) => a & Has[b]
////    case (_,_) => Has[A] & Has[B]
////    case _ => String & Int
//  }
//
//
//  type +++[A,B] = (A,B) match {
//    case (Has[a], Has[b])  => 1
//    case (_, Has[b]) => 2
//    case (Has[a], _) => 3
//    case (_,_) => 4
//  }
//
//  trait Nice
//  val two = ZIO.environment[++[++[Has[String], Has[Boolean]] Int].inject()
//
//
//  //  val three = ZIO.service[Has[Int] +++ Boolean].inject()
////  val four = ZIO.service[Int +++ Boolean].inject()
//}