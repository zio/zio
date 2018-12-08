package scalaz.zio.java.data;

import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Either<L, R> {

    public static class Left<L, R> extends Either<L, R> {
        public final L value;

        public Left(L value) {
            this.value = value;
        }


        @Override
        public <T> T fold(Function<? super L, ? extends T> lFunc, Function<? super R, ? extends T> rFunc) {
            return lFunc.apply(value);
        }
    }

    public static class Right<L, R> extends Either<L, R> {
        public final R value;

        public Right(R value) {
            this.value = value;
        }


        @Override
        public <T> T fold(Function<? super L, ? extends T> lFunc, Function<? super R, ? extends T> rFunc) {
            return rFunc.apply(value);
        }
    }

    public static <L, R> Either<L, R> fromScala(scala.util.Either<L, R> either) {
        return either.fold(Left::new, Right::new);
    }

    public abstract <T> T fold(Function<? super L, ? extends T> lFunc, Function<? super R, ? extends T> rFunc);

    public <T> Either<T, R> leftMap(Function<? super L, ? extends T> lFunc) {
        return fold(l -> new Left<>(lFunc.apply(l)), Right::new);
    }

    public <T> Either<L, T> map(Function<? super R, ? extends T> rFunc) {
        return fold(Left::new, r -> new Right<>(rFunc.apply(r)));
    }

    public void apply(Consumer<? super L> lFunc, Consumer<? super R> rFunc) {
        Function<? super L, Void> f = l -> {
            lFunc.accept(l);
            return null;
        };

        Function<? super R, Void> g = r -> {
            rFunc.accept(r);
            return null;
        };

        fold(f, g);
    }

    // TODO equals and hashcode
}