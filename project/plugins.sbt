addSbtPlugin("ch.epfl.scala"                     % "sbt-scalafix"                  % "0.14.7")
addSbtPlugin("com.eed3si9n"                      % "sbt-assembly"                  % "2.4.1")
addSbtPlugin("com.eed3si9n"                      % "sbt-buildinfo"                 % "0.13.1")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"                    % "0.6.1")
addSbtPlugin("com.github.sbt"                    % "sbt-ci-release"                % "1.12.0")
addSbtPlugin("com.github.cb372"                  % "sbt-explicit-dependencies"     % "0.3.1")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings"              % "3.0.3")
addSbtPlugin("com.typesafe"                      % "sbt-mima-plugin"               % "1.1.6")
addSbtPlugin("com.github.sbt"                    % "sbt-header"                    % "5.11.0")
addSbtPlugin("org.portable-scala"                % "sbt-scala-native-crossproject" % "1.4.0")
addSbtPlugin("org.portable-scala"                % "sbt-scalajs-crossproject"      % "1.4.0")
addSbtPlugin("org.scala-js"                      % "sbt-scalajs"                   % "1.22.0")
addSbtPlugin("org.scalameta"                     % "sbt-mdoc"                      % "2.9.1")
addSbtPlugin("org.scala-native"                  % "sbt-scala-native"              % "0.5.12")
addSbtPlugin("org.scalameta"                     % "sbt-scalafmt"                  % "2.6.2")
addSbtPlugin("pl.project13.scala"                % "sbt-jcstress"                  % "0.2.0")
addSbtPlugin("pl.project13.scala"                % "sbt-jmh"                       % "0.4.8")

addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.4")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "3.1.1"

addDependencyTreePlugin
