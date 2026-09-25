scalaVersion   := "3.9.0"
publish / skip := true

libraryDependencies ++= Seq(
  "dev.zio"       %% "zio"            % "2.1.26",
  "dev.zio"       %% "zio-concurrent" % "2.1.26",
  "org.typelevel" %% "cats-effect"    % "3.7.0"
)
