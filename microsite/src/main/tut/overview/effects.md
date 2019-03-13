---
layout: docs
section: overview
title:  "Effects"
---

# {{page.title}}
`IO` describes the following effects:

### Errors

```scala
val failedIO: IO[Exception, Nothing] =

IO.fail(new Exception("error"))
```
In order to describe Errors you can call fail, that will compute a value of `IO[Exception, Nothing]`

### Pure values

```scala
val number: Int= 1

val number: IO[Nothing, Int] = IO.succeed(1)
```
If you want to describe  pure values you can use `IO.succeeed`.

### Synchronous Effect

```scala

println("hello")

val toInt: IO[Nothing, Unit] = IO.effectTotal(println("hello"))

```
If you want to describe an effectful program that will never fail, use `effectTotal`.

### Asynchronous Effects

```scala

Future(4)

IO.effectAsync[Nothing, Int](_(Completed(4)))

```
And in order to describe an asynchronous effect, you can call `asyncEffect`.

### Concurrent Effects

```scala

val service: ExecutorService =
   Executors.newFixedThreadPool(2)
service.submit(t1)
service.submit(t2)

def task(i: Int) = new Thread(
 new Runnable {
   def run() {
     ???
   }
 })

 val task: IO[Throwable, (A, B)] = t1.zipPar(t2)

```
And if you want to describe concurrent tasks like in this case
Call `zipPar` that executes both effects  in parallel and combining their results into a tuple.

### Resource Effects

```scala

val f: File = openFile()
try {
 compute(f)
}
finally {
 closeFile(f)
}


IO.bracket(openFile)(closeFile(_)) { file =>
  compute(f)
}

```
try, finally is used to manage resources in this example we’ll open the file, do some computations and at the end we will close it, but try catch finally doesn’t work for asynchronous code
Using ZIO you can describe resource effect, with `IO.bracket` where you can define the acquire and the release and you can do your computations safely.

### Defects

```scala

Throw new FatalError

IO.die(FatalError)

```
In ZIO you can also describe defects, using `die` for example, your program will be terminated with FataError.

### Contextual

```scala
def program[F[_]: Console: Monad] =  
  Console[F].println("Hello World")

val sayHello: ZIO[Console, Nothing, Unit] =
  console.putStrLn("Hello World")

  ```
  And there is a new effect that you can describe using ZIO, which is Contextual effect.
  Instead of using Final tagless you can define the environments that you’re going to use in an immutable data structure ZIO.
