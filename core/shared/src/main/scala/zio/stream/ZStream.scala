package zio.stream

import zio._
import zio.internal.{MutableConcurrentQueue, OneElementConcurrentQueue}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.IOException
import scala.collection.immutable.{Queue => ScalaQueue}

private[stream] trait ZStreamPlatformSpecificConstructors {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators2 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors2 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors2 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun2 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators3 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors3 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors3 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun3 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators4 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors4 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors4 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun4 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators5 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors5 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors5 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun5 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators6 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors6 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors6 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun6 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators7 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors7 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors7 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun7 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators8 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors8 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors8 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun8 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators9 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors9 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors9 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun9 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators10 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors10 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors10 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun10 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators11 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors11 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors11 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun11 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators12 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors12 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors12 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun12 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators13 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors13 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors13 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun13 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators14 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors14 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors14 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun14 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators15 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors15 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors15 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun15 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators16 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors16 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors16 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun16 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators17 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors17 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors17 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun17 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators18 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors18 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors18 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun18 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators19 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors19 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors19 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun19 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators20 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors20 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors20 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun20 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators21 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors21 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors21 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun21 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators22 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors22 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors22 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun22 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators23 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors23 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors23 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun23 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators24 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors24 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors24 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun24 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators25 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors25 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors25 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun25 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators26 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors26 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors26 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun26 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators27 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors27 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors27 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun27 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators28 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors28 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors28 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun28 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators29 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors29 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors29 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun29 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators30 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors30 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors30 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun30 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators31 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors31 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors31 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun31 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators32 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors32 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors32 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun32 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators33 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors33 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors33 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun33 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators34 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors34 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors34 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun34 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators35 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors35 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors35 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun35 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators36 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors36 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors36 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun36 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators37 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors37 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors37 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun37 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators38 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors38 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors38 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun38 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators39 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors39 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors39 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun39 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators40 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors40 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors40 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun40 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators41 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors41 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors41 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun41 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators42 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors42 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors42 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun42 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators43 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors43 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors43 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun43 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators44 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors44 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors44 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun44 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators45 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors45 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors45 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificRun45 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificCombinators46 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificConstructors46 {
  self: ZStream.type =>
}

private[stream] trait ZStreamPlatformSpecificDestructors46 {