package scalaz.zio.java;

import scala.runtime.BoxedUnit;
import scalaz.zio.ExitResult;
import scalaz.zio.Fiber;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class IO<E, A> {

    private scalaz.zio.IO<E, A> delegate;

    private IO(scalaz.zio.IO<E, A> delegate) {
        this.delegate = delegate;
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
        return new IO<>(delegate.fork()).leftMap(nothing -> null);
    }

    public IO<Void, Fiber<E, A>> fork0(Function<ExitResult.Cause<Object>, IO<Void, Void>> f) {
        return new IO<>(
                delegate.fork0(f.andThen(io ->
                        io.delegate.<Object, BoxedUnit>bimap(
                                _void -> _void,
                                _void -> BoxedUnit.UNIT
                        ))::apply
                )
        ).bimap(nothing -> null, nothing -> null);
    }

    public <E1 extends E, B, C> IO<E1, C> parWith(IO<E1, B> that, BiFunction<A, B, C> f) {
        return new IO<>(delegate.parWith(that.delegate, f::apply));
    }

    public <E1 extends E, B> IO<E1, scala.Tuple2<A, B>> par(IO<E1, B> that) {
        return new IO<>(delegate.par(that.delegate));
    }

    public IO<E, A> race(IO<E, A> that) {
        return new IO<>(delegate.race(that.delegate));
    }

    // TODO should we use the Scala Either? Maybe create an Either for Java?
    public <B> IO<E, scala.util.Either<A, B>> raceBoth(IO<E, B> that) {
        return new IO<>(delegate.raceBoth(that.delegate));
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
    public <E2, B> IO<E2, scala.util.Either<A, B>> fallbackTo(IO<E2, B> that) {
        return new IO<>(delegate.$less$bar$bar$greater(() -> that.delegate));
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

    public IO<Void, scala.util.Either<E, A>> attempt() {
        return new IO<>(delegate.attempt()).leftMap(nothing -> null);
    }

    public <B> IO<E, B> bracket(Function<A, scalaz.zio.IO<Object, BoxedUnit>> release, Function<A, scalaz.zio.IO<E, B>> use) {
        return new IO<>(delegate.bracket(release::apply, use::apply));
    }

    // TODO find a better name for all method names ending in "0" and "_"
    public <B> IO<E, B> bracket0(BiFunction<A, ExitResult<E, B>, IO<Void, BoxedUnit>> release,
                                 Function<A, IO<E, B>> use) {
        return new IO<>(delegate.bracket0(
                release.andThen(io -> io.leftMap(nothing -> null).delegate)::apply,
                use.andThen(io -> io.delegate)::apply
        ));
    }

    public <B> IO<E, B> bracket_(IO<Void, BoxedUnit> release, IO<E, B> use) {
        return new IO<>(delegate.bracket_(release.leftMap(nothing -> null).delegate, use.delegate));
    }

    public IO<E, A> ensuring(IO<Void, BoxedUnit> finalizer) {
        return new IO<>(delegate.ensuring(finalizer.leftMap(nothing -> null).delegate));
    }

    // TODO other transformation/composition methods...

    public static <A> IO<Void, A> now(A a) {
        return new IO<>(scalaz.zio.IO$.MODULE$.now(a)).leftMap(nothing -> null);
    }

    public static <A> IO<Void, A> point(Supplier<A> a) {
        return new IO<>(scalaz.zio.IO$.MODULE$.point(a::get)).leftMap(nothing -> null);
    }

    public static <E, A> IO<E, A> fail(E error) {
        return new IO(scalaz.zio.IO$.MODULE$.fail(error));
    }

    // TODO other construction methods...
}
