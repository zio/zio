scalaVersion := "2.13.18"
publish / skip := true

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"            % "2.1.26",
  "dev.zio" %% "zio-concurrent" % "2.1.26"
)
