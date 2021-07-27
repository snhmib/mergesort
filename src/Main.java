import java.util.*;
import java.util.concurrent.*;

/**
 * A parallel version of merge sort that also parallelizes the (usually overlooked) merge phase.
 *
 * @author hj.middendorp@hccnet.nl
 */
public class Main {
    private static final int SIZE = 67108864;
    private static final int CORE = 16;
    private static final int SEED = 0;
    private static final int STOP = 1048576;

    private static void fillArray(int[] array) {
        Random rand = new Random(SEED);
        for (int i = 0; i < SIZE; i++) array[i] = rand.nextInt(SIZE);
    }

    private static void merge(int[] data, int lo, int mi, int hi) {
        int[] temp = Arrays.copyOfRange(data, lo, hi);
        int k = lo, i = 0, j = mi - lo;
        while (i < mi - lo && j < hi - lo)
            if (temp[i] <= temp[j]) data[k++] = temp[i++];
            else data[k++] = temp[j++];
        while (i < mi - lo) data[k++] = temp[i++];
    }

    private static void sequential(int[] data, int lo, int hi) {
        if (hi - lo > 1) {
            int mi = (lo + hi) / 2;
            sequential(data, lo, mi);
            sequential(data, mi, hi);
            merge(data, lo, mi, hi);
        }
    }

    private static int index(int e, int[] data, int lo, int hi) {
        if (hi - lo > 0) {
            int mi = (lo + hi) / 2;
            if (data[mi] < e) return index(e, data, mi + 1, hi);
            else return index(e, data, lo, mi);
        } else return lo;
    }

    private static class Merger extends RecursiveAction {
        private final int[] data, temp;
        private final int ll, lh, rl, rh, to;

        public Merger(int[] data, int ll, int lh, int rl, int rh, int[] temp, int to) {
            this.data = data;
            if (lh - ll < rh - rl) {
                this.ll = rl;
                this.lh = rh;
                this.rl = ll;
                this.rh = lh;
            } else {
                this.ll = ll;
                this.lh = lh;
                this.rl = rl;
                this.rh = rh;
            }
            this.temp = temp;
            this.to = to;
        }

        protected void compute() {
            if (lh - ll > 0) {
                int lm = (ll + lh) / 2;
                int rm = index(data[lm], data, rl, rh);
                if (lh - ll > STOP) {
                    invokeAll(new Merger(data, ll, lm, rl, rm, temp, to),
                            new Merger(data, lm + 1, lh, rm, rh, temp, to + lm - ll + rm - rl + 1));
                    temp[to + lm - ll + rm - rl] = data[lm];
                } else {
                    System.arraycopy(data, ll, temp, to, lh - ll);
                    System.arraycopy(data, rl, temp, to + lh - ll, rh - rl);
                    merge(temp, to, to + lh - ll, to + lh - ll + rh - rl);
                }
            }
        }
    }

    private static class Sorter extends RecursiveAction {
        private final int[] data;
        private final int lo, hi;

        public Sorter(int[] data, int lo, int hi) {
            this.data = data;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            if (hi - lo > 1) {
                int mi = (lo + hi) / 2;
                if (hi - lo > STOP) {
                    invokeAll(new Sorter(data, lo, mi), new Sorter(data, mi, hi));
                    int[] temp = new int[hi - lo];
                    invokeAll(new Merger(data, lo, mi, mi, hi, temp, 0));
                    System.arraycopy(temp, 0, data, lo, hi - lo);
                } else {
                    sequential(data, lo, mi);
                    sequential(data, mi, hi);
                    merge(data, lo, mi, hi);
                }
            }
        }
    }

    public static void main(String[] args) {
        int[] data = new int[SIZE];
        fillArray(data);
        long start = System.nanoTime();
        new ForkJoinPool(CORE).invoke(new Sorter(data, 0, SIZE));
        System.out.println((System.nanoTime() - start) / 1e9);
    }
}
