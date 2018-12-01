package scalaz.zio.java.data;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Either<L,R> {
    public static <L,R> Either<L,R> left(L value) {
        return new Either<>(Optional.of(value), Optional.empty());
    }

    public static <L,R> Either<L,R> right(R value) {
        return new Either<>(Optional.empty(), Optional.of(value));
    }

    public static <L, R> Either<L, R> fromScala(scala.util.Either<L, R> either) {
        return either.fold(Either::left, Either::right);
    }

    private final Optional<L> left;
    private final Optional<R> right;

    private Either(Optional<L> l, Optional<R> r) {
        left=l;
        right=r;
    }

    public <T> T fold(
            Function<? super L, ? extends T> lFunc,
            Function<? super R, ? extends T> rFunc) {
        return left.<T>map(lFunc).orElseGet(()->right.map(rFunc).get());
    }

    public <T> Either<T,R> leftMap(Function<? super L, ? extends T> lFunc) {
        return new Either<>(left.map(lFunc),right);
    }

    public <T> Either<L,T> map(Function<? super R, ? extends T> rFunc) {
        return new Either<>(left, right.map(rFunc));
    }
    public void apply(Consumer<? super L> lFunc, Consumer<? super R> rFunc) {
        left.ifPresent(lFunc);
        right.ifPresent(rFunc);
    }


}