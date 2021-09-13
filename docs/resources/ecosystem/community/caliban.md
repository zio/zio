---
id: caliban
title: "Caliban"
---

[Caliban](https://ghostdogpr.github.io/caliban/) is a purely functional library for creating GraphQL servers and clients in Scala.

## Introduction

Key features of Caliban
- **Purely Functional** — All interfaces are pure and types are referentially transparent.
- **Type Safety** — Schemas are type safe and derived at compile time.
- **Minimal Boilerplate** — No need to manually define a schema for every type in your API.
- **Excellent Interoperability** — Out-of-the-box support for major HTTP server libraries, effect types, JSON libraries, and more.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "com.github.ghostdogpr" %% "caliban" % "1.1.0"
```

Caliban also have lots of optional modules to inter-operate with other various libraries:

```scala
libraryDependencies += "com.github.ghostdogpr" %% "caliban-http4s"     % "1.1.0" // routes for http4s
libraryDependencies += "com.github.ghostdogpr" %% "caliban-akka-http"  % "1.1.0" // routes for akka-http
libraryDependencies += "com.github.ghostdogpr" %% "caliban-play"       % "1.1.0" // routes for play
libraryDependencies += "com.github.ghostdogpr" %% "caliban-finch"      % "1.1.0" // routes for finch
libraryDependencies += "com.github.ghostdogpr" %% "caliban-zio-http"   % "1.1.0" // routes for zio-http
libraryDependencies += "com.github.ghostdogpr" %% "caliban-cats"       % "1.1.0" // interop with cats effect
libraryDependencies += "com.github.ghostdogpr" %% "caliban-monix"      % "1.1.0" // interop with monix
libraryDependencies += "com.github.ghostdogpr" %% "caliban-tapir"      % "1.1.0" // interop with tapir
libraryDependencies += "com.github.ghostdogpr" %% "caliban-federation" % "1.1.0" // interop with apollo federation
```

## Example

First, to define Caliban API, we should define data models using case classes and ADTs. Then the Caliban can derive the whole GraphQL schema from these data models:

```scala modc:silent:nest
import caliban.GraphQL.graphQL
import caliban.schema.Annotations.GQLDescription
import caliban.{RootResolver, ZHttpAdapter}
import zhttp.http._
import zhttp.service.Server
import zio.{ExitCode, ZEnv, ZIO}

import scala.language.postfixOps

sealed trait Role

object Role {
  case object SoftwareDeveloper       extends Role
  case object SiteReliabilityEngineer extends Role
  case object DevOps                  extends Role
}

case class Employee(
    name: String,
    role: Role
)

case class EmployeesArgs(role: Role)
case class EmployeeArgs(name: String)

case class Queries(
    @GQLDescription("Return all employees with specific role")
    employees: EmployeesArgs => List[Employee],
    @GQLDescription("Find an employee by its name")
    employee: EmployeeArgs => Option[Employee]
)
object CalibanExample extends zio.App {

  val employees = List(
    Employee("Alex", Role.DevOps),
    Employee("Maria", Role.SoftwareDeveloper),
    Employee("James", Role.SiteReliabilityEngineer),
    Employee("Peter", Role.SoftwareDeveloper),
    Employee("Julia", Role.SiteReliabilityEngineer),
    Employee("Roberta", Role.DevOps)
  )

  val myApp = for {
    interpreter <- graphQL(
      RootResolver(
        Queries(
          args => employees.filter(e => args.role == e.role),
          args => employees.find(e => e.name == args.name)
        )
      )
    ).interpreter
    _ <- Server
      .start(
        port = 8088,
        http = Http.route { case _ -> Root / "api" / "graphql" =>
          ZHttpAdapter.makeHttpService(interpreter)
        }
      )
      .forever
  } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    myApp.exitCode

}
```

Now let's query all software developers using GraphQL query language:

```graphql
query{
  employees(role: SoftwareDeveloper){
    name
    role
  }
}
```

Here is the _curl_ request of this query:

```bash
curl 'http://localhost:8088/api/graphql' --data-binary '{"query":"query{\n employees(role: SoftwareDeveloper){\n name\n role\n}\n}"}'
```

And the response:

```json
{
  "data" : {
    "employees" : [
      {
        "name" : "Maria",
        "role" : "SoftwareDeveloper"
      },
      {
        "name" : "Peter",
        "role" : "SoftwareDeveloper"
      }
    ]
  }
}
```

## Resources

- [Zymposium - Caliban](https://www.youtube.com/watch?v=mzqsXklbmfM&t=2849s) by Adam Fraser, Kit Langton and Pierre Ricadat — We will be building full-stack applications with the special guest international man of mystery Pierre Ricadat. Pierre will be walking us through Caliban, a GraphQL library in Scala that just had its 1.0 release. We'll be seeing how easy it can be to upgrade an existing application to use GraphQL with the power of Caliban!
