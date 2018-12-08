package scalaz.zio.java.data;

public final class Pair<A, B> {
    public final A first;
    public final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public static <L, R> Pair<L, R> fromScala(scala.Tuple2<L, R> tuple) {
        return new Pair<>(tuple._1, tuple._2);
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Pair) {
            Pair t = (Pair) obj;
            return t.first == first && t.second == second;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // TODO proper hashcode
        return 31 * (31 + first.hashCode()) + second.hashCode();
    }


}
