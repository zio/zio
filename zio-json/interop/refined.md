# Refined Interop

> ```scala
libraryDependencies ++= Seq(
  "dev.zio" % "zio-json-interop-refined" % "0.8.0"
)
```

## Installation

```scala
libraryDependencies ++= Seq(
  "dev.zio" % "zio-json-interop-refined" % "0.8.0"
)
```

## Usage

```scala

case class Person(name: String Refined NonEmpty)

object Person {
  implicit val decoder: JsonDecoder[Person] = DeriveJsonDecoder.gen
}
```

```scala
"""{ "name": "" }""".fromJson[Person]
// res0: Either[String, Person] = Left(
//   value = ".name(Predicate isEmpty() did not fail.)"
// )
```

```scala
"""{ "name": "Aurora" }""".fromJson[Person]
// res1: Either[String, Person] = Right(value = Person(name = Aurora))
```
