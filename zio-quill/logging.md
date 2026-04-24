# Logging

> To write compile-time queries to a log, use the `-Dquill.log.file=queries.sql` and specify
the file to be written (e.g. `queries.sql`). The path is based on the build root (i.e. the current-working-directory of the Java build).

## Logging to a File at Compile-Time

To write compile-time queries to a log, use the `-Dquill.log.file=queries.sql` and specify
the file to be written (e.g. `queries.sql`). The path is based on the build root (i.e. the current-working-directory of the Java build).

When using SBT, this parameter can be set either in your SBT_OPTS, the project-specific .sbtopts file or directly passed to the SBT command.
In IntelliJ this can be set under settings -> sbt -> VM Parameters.

(Also make sure that `use for: "Builds"` is selected otherwise IntelliJ will not use SBT for the build in the first place.)

![Screenshot from 2022-04-14 23-28-47](https://user-images.githubusercontent.com/1369480/163513653-b5266cd6-1bff-4792-b0d2-936d24b7e0f1.png)

Also note that the `-Dquill.macro.log.pretty=true` parameter works together with `-Dquill.log.file` and will output pretty-printed
queries to the specified file.

For a file that looks like this:
```
// Example.scala
package io.getquill

object Example {
  case class Person(id: Int, name: String, age: Int)
  case class Address(owner:Int, street: String)
  val ctx = new SqlMirrorContext(PostgresDialect, Literal)

  val people = run(query[Person])
  val addresses = run(query[Person])
}
```

The following log will be produced:
```sql

-- file: /home/me/quill-example/src/main/scala/io/getquill/Example.scala:9:19
-- time: 2022-04-14T23:18:19.533

 SELECT
   x.id,
   x.name,
   x.age
 FROM
   Person x

;

-- file: /home/me/quill-example/src/main/scala/io/getquill/Example.scala:10:22
-- time: 2022-04-14T23:18:19.9

 SELECT
   x.id,
   x.name,
   x.age
 FROM
   Person x

;
```

## Disable Compile-Time Console Logging

To disable the console logging of queries during compilation use `quill.macro.log` option:
```
sbt -Dquill.macro.log=false
```
## Runtime

Quill uses SLF4J for logging. Each context logs queries which are currently executed.
It also logs the list of parameters that are bound into a prepared statement if any.
To enable that use `quill.binds.log` option:
```
java -Dquill.binds.log=true -jar myapp.jar
```
The option `quill.query.tooLong` restricts the maximum length of logged SQL statements:
```
java -Dquill.binds.log=true -Dquill.query.tooLong=10000 -jar myapp.jar
```

## Pretty Printing

Quill can pretty print compile-time produced queries by leveraging a great library
produced by [@vertical-blank](https://github.com/vertical-blank) which is compatible
with both Scala and ScalaJS. To enable this feature use the `quill.macro.log.pretty` option:
```
sbt -Dquill.macro.log.pretty=true
```

Before:
```
[info] /home/me/project/src/main/scala/io/getquill/MySqlTestPerson.scala:20:18: SELECT p.id, p.name, p.age, a.ownerFk, a.street, a.state, a.zip FROM Person p INNER JOIN Address a ON a.ownerFk = p.id
```

After:
```
[info] /home/me/project/src/main/scala/io/getquill/MySqlTestPerson.scala:20:18:
[info]   | SELECT
[info]   |   p.id,
[info]   |   p.name,
[info]   |   p.age,
[info]   |   a.ownerFk,
[info]   |   a.street,
[info]   |   a.state,
[info]   |   a.zip
[info]   | FROM
[info]   |   Person p
[info]   |   INNER JOIN Address a ON a.ownerFk = p.id
```
