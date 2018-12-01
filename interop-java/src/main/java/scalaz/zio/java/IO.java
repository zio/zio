package scalaz.zio.java;

import scala.PartialFunction$;
import scala.collection.JavaConverters;
import scala.runtime.BoxedUnit;
import scala.runtime.Nothing$;
import scalaz.zio.ExitResult;
import scalaz.zio.Fiber;
import scalaz.zio.Managed;
import scalaz.zio.java.data.Tuple;
import scalaz.zio.java.data.Either;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class IO<E, A> {

    private scalaz.zio.IO<E, A> delegate;

    private IO(scalaz.zio.IO<E, A> delegate) {
        this.delegate = delegate;
    }

    private static scalaz.zio.IO<Nothing$, BoxedUnit> toScala(IO<Void, Done> io) {
        return io.delegate.bimap(
                _void -> null,
                done -> BoxedUnit.UNIT
        );
    }

    private static <E, A> IO<E, A> fromScalaError(scalaz.zio.IO<E, Nothing$> io) {
        return new IO(io);
    }

    private static <E, A> IO<E, A> fromScalaValue(scalaz.zio.IO<Nothing$, A> io) {
        return new IO(io);
    }

    public <B> IO<E, B> map(Function<A, B> f) {
        return new IO<>(delegate.map(f::apply));
    }

    public <E2, B> IO<E2, B> bimap(Function<E, E2> f, Function<A, B> g) {
        return new IO<>(delegate.bimap(f::apply, g::apply));
    }

    public <E1 extends E, B> IO<E1, B> flatMap(Function<A, IO<E1, B>> f) {
        return new IO<>(delegate.flatMap(f.andThen(io -> io.delegate)::apply));
    }

    // TODO do we want a separate java Fiber?
    public IO<Void, Fiber<E, A>> fork() {
        return IO.fromScalaValue(delegate.fork());
    }

    public IO<Void, Fiber<E, A>> fork0(Function<ExitResult.Cause<Object>, IO<Void, Done>> f) {
        return IO.fromScalaValue(delegate.fork0(f.andThen(IO::toScala)::apply));
    }

    public <E1 extends E, B, C> IO<E1, C> parWith(IO<E1, B> that, BiFunction<A, B, C> f) {
        return new IO<>(delegate.parWith(that.delegate, f::apply));
    }

    public <E1 extends E, B> IO<E1, Tuple<A, B>> par(IO<E1, B> that) {
        return new IO<>(delegate.par(that.delegate).map(Tuple::fromScala));
    }

    public IO<E, A> race(IO<E, A> that) {
        return new IO<>(delegate.race(that.delegate));
    }

    public <B> IO<E, Either<A, B>> raceBoth(IO<E, B> that) {
        return new IO<>(delegate.raceBoth(that.delegate).map(Either::fromScala));
    }

    public <E1, E2, B, C> IO<E2, C> raceWith(IO<E1, B> that,
                                             BiFunction<ExitResult<E, A>, Fiber<E1, B>, IO<E2, C>> leftDone,
                                             BiFunction<ExitResult<E1, B>, Fiber<E, A>, IO<E2, C>> rightDone) {
        return new IO<>(delegate.raceWith(
                that.delegate,
                leftDone.andThen(io -> io.delegate)::apply,
                rightDone.andThen(io -> io.delegate)::apply)
        );
    }

    public <E2> IO<E2, A> orElse(IO<E2, A> that) {
        return new IO<>(delegate.orElse(() -> that.delegate));
    }

    // TODO is this the right name for <||> ?
    public <E2, B> IO<E2, Either<A, B>> fallbackTo(IO<E2, B> that) {
        return new IO<>(delegate.$less$bar$bar$greater(() -> that.delegate).map(Either::fromScala));
    }

    public <E2> IO<E2, A> leftMap(Function<E, E2> f) {
        return new IO<>(delegate.leftMap(f::apply));
    }

    public IO<A, E> flip() {
        return new IO<>(delegate.flip());
    }

    public <E2, B> IO<E2, B> redeem(Function<E, IO<E2, B>> err, Function<A, IO<E2, B>> succ) {
        return new IO<>(delegate.redeem(err.andThen(io -> io.delegate)::apply, succ.andThen(io -> io.delegate)::apply));
    }

    public <E2, B> IO<E2, B> redeem0(Function<ExitResult.Cause<E>, IO<E2, B>> err, Function<A, IO<E2, B>> succ) {
      return new IO<>(delegate.redeem0(err.andThen(io -> io.delegate)::apply, succ.andThen(io -> io.delegate)::apply));
    }

    public <E2, B> IO<E2, B> redeemPure(Function<E, B> err, Function<A, B> succ) {
        return new IO<>(delegate.redeemPure(err::apply, succ::apply));
    }

    public IO<Void, Either<E, A>> attempt() {
        return new IO<>(delegate.attempt().bimap(nothing -> null, Either::fromScala));
    }

    public <B> IO<E, B> bracket(Function<A, IO<Void, Done>> release, Function<A, IO<E, B>> use) {
        return new IO<>(delegate.bracket(
                release.andThen(IO::toScala)::apply,
                use.andThen(io -> io.delegate)::apply
        ));
    }

    // TODO find a better name for all method names ending in "0" and "_"
    public <B> IO<E, B> bracket0(BiFunction<A, ExitResult<E, B>, IO<Void, Done>> release,
                                 Function<A, IO<E, B>> use) {
        return new IO<>(delegate.<E, B>bracket0(
                release.andThen(IO::toScala)::apply,
                use.andThen(io -> io.delegate)::apply
        ));
    }

    public <B> IO<E, B> bracket_(IO<Void, Done> release, IO<E, B> use) {
        return new IO<>(delegate.bracket_(toScala(release), use.delegate));
    }

    public IO<E, A> ensuring(IO<Void, Done> finalizer) {
        return new IO<>(delegate.ensuring(toScala(finalizer)));
    }

    // TODO think about the replacement for ExecutionContext. Maybe ThreadPool?
    // final def on(ec: ExecutionContext): IO[E, A]
    // final def forkOn(ec: ExecutionContext): IO[E, Fiber[E, A]]

    public <B> IO<E, B> bracketOnError(Function<A, IO<Void, Done>> release, Function<A, IO<E, B>> use) {
        return new IO<>(delegate.bracketOnError(
                release.andThen(IO::toScala)::apply,
                use.andThen(io -> io.delegate)::apply
        ));
    }

    // TODO do we want a java Managed ?
    public Managed<E, A> managed(Function<A, IO<Void, Done>> release) {
        return delegate.managed(release.andThen(IO::toScala)::apply);
    }

    public IO<E, A> onError(Function<ExitResult<E, Void>, IO<Void, Done>> cleanup) {
        Function<ExitResult<E, Nothing$>, ExitResult<E, Void>> f = exitResult -> exitResult.map(_nothing -> null);

        return new IO<>(delegate.onError(f.andThen(cleanup).andThen(IO::toScala)::apply));
    }

    public IO<E, A> onInterrupt(IO<Void, Done> cleanup) {
        return new IO<>(delegate.onInterrupt(toScala(cleanup)));
    }

    public IO<E, A> onTermination(Function<ExitResult.Cause<Void>, IO<Void, Done>> cleanup) {
        Function<ExitResult.Cause<Nothing$>, ExitResult.Cause<Void>> f = cause -> cause.map(nothing -> null);

        return new IO<>(delegate.onTermination(f.andThen(cleanup).andThen(IO::toScala)::apply));
    }

    public IO<E, A> supervised() {
        return new IO<>(delegate.supervised());
    }

    public IO<E, A> supervised(Function<Iterable<Fiber<?, ?>>, IO<Void, Done>> supervisor) {
        Function<scala.collection.Iterable<Fiber<?, ?>>, Iterable<Fiber<?, ?>>> asJavaIterable =
                JavaConverters::asJavaIterable;

        return new IO<>(delegate.supervised(
                asJavaIterable
                        .andThen(supervisor)
                        .andThen(IO::toScala)::apply
        ));
    }

    public IO<E, A> uninterruptibly() {
        return new IO<>(delegate.uninterruptibly());
    }

    public <E2> IO<E2, A> catchAll(Function<E, IO<E2, A>> h) {
        return new IO<>(delegate.catchAll(h.andThen(io -> io.delegate)::apply));
    }

    public IO<E, A> forSome(Function<E, Optional<A>> f) {
        scala.PartialFunction<E, IO<E, A>> pf = PartialFunction$.MODULE$.apply(
                e -> f.apply(e)
                        .map(a -> IO.<E, A>safeCast(IO.now(a)))
                        .orElse(safeCastError(IO.fail(e)))
        );

        return new IO<>(delegate.catchSome(pf.andThen(io -> io.delegate)));
    }

    // TODO not sure how to call this from java
    // final def const[B](b: => B): IO[E, B] = self.map(_ => b)

    // TODO right name for *> ?
    public <B> IO<E, B> then(IO<E, B> io) {
        return new IO<>(delegate.$times$greater(() -> io.delegate));
    }

    // TODO right name for <* ?
    public <B> IO<E, A> thenIgnore(IO<E, B> io) {
        return new IO<>(delegate.$less$times(() -> io.delegate));
    }

    public <B, C> IO<E, C> seqWith(IO<E, B> that, BiFunction<A, B, C> f) {
        return new IO<>(delegate.seqWith(that.delegate, f::apply));
    }

    public <B> IO<E, Tuple<A, B>> seq(IO<E, B> that) {
        return new IO<>(delegate.seq(that.delegate).map(Tuple::fromScala));
    }

    public IO<E, Void> forever() {
        return fromScalaError(delegate.forever());
    }

    // TODO other transformation/composition methods...

    public static <A> IO<Void, A> now(A a) {
        return fromScalaValue(scalaz.zio.IO$.MODULE$.now(a));
    }

    public static <A> IO<Void, A> point(Supplier<A> a) {
        return fromScalaValue(scalaz.zio.IO$.MODULE$.point(a::get));
    }

    public static <E, A> IO<E, A> fail(E error) {
        return new IO(scalaz.zio.IO$.MODULE$.fail(error));
    }

    // TODO other construction methods...

    // TODO come up with a better name
    public static class Done {
        private Done() { }

        public static Done instance = new Done();
    }

    public static <E, A> IO<E, A> safeCast(IO<Void, A> io) {
        return (IO<E, A>) io;
    }

    public static <E, A> IO<E, A> safeCastError(IO<E, Void> io) {
        return (IO<E, A>) io;
    }
}
