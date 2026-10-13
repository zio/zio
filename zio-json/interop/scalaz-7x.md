# Scalaz 7.x Interop

> ```scala
> libraryDependencies ++= Seq(
>   "dev.zio" % "zio-json-interop-scalaz" % "0.8.0"
> )
> ```

## Installation

```scala
libraryDependencies ++= Seq(
  "dev.zio" % "zio-json-interop-scalaz" % "0.8.0"
)
```

## Usage

```scala
import zio.json._
import zio.json.interop.scalaz7x._

import scalaz._

IList(1, 2, 3).toJson
// res0: String = "[1,2,3]"
```
