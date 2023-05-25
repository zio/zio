---
id: skywalking
title: "SkyWalking Scala"
---

[SkyWalking Scala](https://github.com/bitlap/skywalking-scala) is a SkyWalking Extension (Agent) Plugins for Scala 3.


## Introduction

Key features of SkyWalking Scala:
- **caliban**
- **zio-grpc**
- **zio-http**
- **zio**

For other Java libraries, SkyWalking Scala can be used in conjunction with other [skywalking-java](https://github.com/apache/skywalking-java) agents.
But don't provide two agents for the same libraries.

## Installation

In order to use this plugin, we need to add the following files in our `skywalking-agent/plugins` folder:

1. Clone code git clone https://github.com/bitlap/skywalking-scala.git
2. Enter the source file directory `cd skywalking-scala`
3. Build plugin `sh packageJars.sh`
4. Put the `dist/*.jar` into skywalking `plugins` folder

Then enable it by using the java agent, pass the parameters to your program to run the script. The following is run according to the sbt assembly:
```
-Dskywalking.agent.service_name=seriveName
-J-javaagent:/data/app/skywalking-agent/skywalking-agent.jar
```
