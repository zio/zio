#!/bin/bash

# Run the verification script using SBT
echo "Running ZIO Race Optimization Verification Test"

# Create a temporary build.sbt file
cat > build.sbt << EOF
scalaVersion := "2.13.10"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "2.0.15",
  "org.typelevel" %% "cats-effect" % "3.5.1"
)
EOF

# Create a temporary project directory for SBT
mkdir -p project

# Run the verification script
sbt "runMain VerifyRaceOptimization"

# Clean up temporary files
rm -f build.sbt
rm -rf project