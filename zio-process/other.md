# Other

> If you'd like to run a process and handle its input/output in the current process, you can inherit its I/O. For example, running the Scala REPL:

### Inheriting I/O

If you'd like to run a process and handle its input/output in the current process, you can inherit its I/O. For example, running the Scala REPL:

```scala
Command("scala").inheritIO.exitCode
```

### Providing environment variables

```scala
Command("java", "-version").env(Map("JAVA_HOME" -> javaHome)).string
```

### Specifying the working directory

```scala
Command("ls").workingDirectory(new File("/")).lines
```

### Redirecting output to a file

For example, if you'd like to dump the contents of a PostgreSQL database, you can do the following:

```scala
Command("pg_dump", "my_database").stdout(ProcessOutput.FileRedirect(new File("dump.sql"))).exitCode
```

Alternatively, you can use the bash-like `>` operator if that feels more familiar to you:

```scala
(Command("pg_dump", "my_database") > new File("dump.sql")).exitCode
```
