/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;

import java.util.function.LongBinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A package-local class holding common representation and mechanics
 * for classes supporting dynamic striping on 64bit values. The class
 * extends Number so that concrete subclasses must publicly do so.
 */
@SuppressWarnings("serial")
abstract class Striped64 extends Number {
    /*
     * This class maintains a lazily-initialized table of atomically
     * updated variables, plus an extra "base" field. The table size
     * is a power of two. Indexing uses masked per-thread hash codes.
     * Nearly all declarations in this class are package-private,
     * accessed directly by subclasses.
     *
     * Table entries are of class Cell; a variant of AtomicLong padded
     * (via @sun.misc.Contended) to reduce cache contention. Padding
     * is overkill for most Atomics because they are usually
     * irregularly scattered in memory and thus don't interfere much
     * with each other. But Atomic objects residing in arrays will
     * tend to be placed adjacent to each other, and so will most
     * often share cache lines (with a huge negative performance
     * impact) without this precaution.
     *
     * In part because Cells are relatively large, we avoid creating
     * them until they are needed.  When there is no contention, all
     * updates are made to the base field.  Upon first contention (a
     * failed CAS on base update), the table is initialized to size 2.
     * The table size is doubled upon further contention until
     * reaching the nearest power of two greater than or equal to the
     * number of CPUS. Table slots remain empty (null) until they are
     * needed.
     *
     * A single spinlock ("cellsBusy") is used for initializing and
     * resizing the table, as well as populating slots with new Cells.
     * There is no need for a blocking lock; when the lock is not
     * available, threads try other slots (or the base).  During these
     * retries, there is increased contention and reduced locality,
     * which is still better than alternatives.
     *
     * The Thread probe fields maintained via ThreadLocalRandom serve
     * as per-thread hash codes. We let them remain uninitialized as
     * zero (if they come in this way) until they contend at slot
     * 0. They are then initialized to values that typically do not
     * often conflict with others.  Contention and/or table collisions
     * are indicated by failed CASes when performing an update
     * operation. Upon a collision, if the table size is less than
     * the capacity, it is doubled in size unless some other thread
     * holds the lock. If a hashed slot is empty, and lock is
     * available, a new Cell is created. Otherwise, if the slot
     * exists, a CAS is tried.  Retries proceed by "double hashing",
     * using a secondary hash (Marsaglia XorShift) to try to find a
     * free slot.
     *
     * The table size is capped because, when there are more threads
     * than CPUs, supposing that each thread were bound to a CPU,
     * there would exist a perfect hash function mapping threads to
     * slots that eliminates collisions. When we reach capacity, we
     * search for this mapping by randomly varying the hash codes of
     * colliding threads.  Because search is random, and collisions
     * only become known via CAS failures, convergence can be slow,
     * and because threads are typically not bound to CPUS forever,
     * may not occur at all. However, despite these limitations,
     * observed contention rates are typically low in these cases.
     *
     * It is possible for a Cell to become unused when threads that
     * once hashed to it terminate, as well as in the case where
     * doubling the table causes no thread to hash to it under
     * expanded mask.  We do not try to detect or remove such cells,
     * under the assumption that for long-running instances, observed
     * contention levels will recur, so the cells will eventually be
     * needed again; and for short-lived ones, it does not matter.
     */

    /**
     * Padded variant of AtomicLong supporting only raw accesses plus CAS.
     * <p>
     * JVM intrinsics note: It would be possible to use a release-only
     * form of CAS here, if it were provided.
     */
    @sun.misc.Contended
    static final class Cell {
        volatile long value;

        Cell(long x) {
            value = x;
        }

        final boolean cas(long cmp, long val) {
            return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long valueOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> ak = Cell.class;
                valueOffset = UNSAFE.objectFieldOffset
                        (ak.getDeclaredField("value"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * Number of CPUS, to place bound on table size
     */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Table of cells. When non-null, size is a power of 2.
     */
    transient volatile Cell[] cells;

    /**
     * Base value, used mainly when there is no contention, but also as
     * a fallback during table initialization races. Updated via CAS.
     */
    transient volatile long base;

    /**
     * Spinlock (locked via CAS) used when resizing and/or creating Cells.
     */
    transient volatile int cellsBusy;

    /**
     * Package-private default constructor
     */
    Striped64() {
    }

    /**
     * CASes the base field.
     */
    final boolean casBase(long cmp, long val) {
        return UNSAFE.compareAndSwapLong(this, BASE, cmp, val);
    }

    /**
     * CASes the cellsBusy field from 0 to 1 to acquire lock.
     */
    final boolean casCellsBusy() {
        return UNSAFE.compareAndSwapInt(this, CELLSBUSY, 0, 1);
    }

    /**
     * Returns the probe value for the current thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int getProbe() {
        return UNSAFE.getInt(Thread.currentThread(), PROBE);
    }

    /**
     * Pseudo-randomly advances and records the given probe value for the
     * given thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int advanceProbe(int probe) {
        probe ^= probe << 13;   // xorshift
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        UNSAFE.putInt(Thread.currentThread(), PROBE, probe);
        return probe;
    }

    /**
     * Handles cases of updates involving initialization, resizing,
     * creating new Cells, and/or contention. See above for
     * explanation. This method suffers the usual non-modularity
     * problems of optimistic retry code, relying on rechecked sets of
     * reads.
     *
     * @param x              the value
     * @param fn             the update function, or null for add (this convention
     *                       avoids the need for an extra field or function in LongAdder).
     * @param wasUncontended false if CAS failed before call
     */
    final void longAccumulate(long x, LongBinaryOperator fn, boolean wasUncontended) {
        //存储线程的Probe值
        int h;
        //如果线程的Probe值等于0则强制初始化
        if ((h = getProbe()) == 0) {
            //强制初始化
            ThreadLocalRandom.current();
            //重新获取Probe值
            h = getProbe();
            //线程的Probe值还未初始化说明还没有发生线程竞争
            wasUncontended = true;
        }
        //是否发生竞争
        boolean collide = false;
        for (; ; ) {
            Cell[] as;
            Cell a;
            //as数组长度
            int n;
            long v;
            //校验cells是否不为空
            //如果cells不为空则说明已经初始化过
            if ((as = cells) != null && (n = as.length) > 0) {
                //根据线程的Probe值与cells数组的长度-1进行与运算获取到指定索引位置上的cell对象
                //如果cell对象为null则说明cell对象还没有被其它线程初始化
                if ((a = as[(n - 1) & h]) == null) {
                    //当前线程的cell对象为空
                    //校验当前cell对象的锁是否被其它线程持有
                    if (cellsBusy == 0) {
                        //未被其它线程持有
                        //创建cell对象并将x值赋值给cell对象中的value
                        Cell r = new Cell(x);
                        //再次校验当前cell对象的锁是否被其它线程持有并尝试获取锁
                        if (cellsBusy == 0 && casCellsBusy()) {
                            //是否创建成功
                            boolean created = false;
                            try {
                                Cell[] rs;
                                int m, j;
                                //如果cells数组不为空则根据Probe值重新获取cells数组中指定索引位置上的cell对象
                                //如果数组中指定索引位置上的cell对象为空则将创建的cell对象赋与指定索引位置
                                //如果数组中指定索引位置上的cell对象不为空则有可能发生了扩容
                                if ((rs = cells) != null
                                        && (m = rs.length) > 0
                                        && rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    //创建成功
                                    created = true;
                                }
                            } finally {
                                //释放锁
                                cellsBusy = 0;
                            }
                            if (created)
                                break;
                            continue;
                        }
                    }
                    collide = false;
                    //当前线程所在的cell不为空并且cas更新失败
                    //更新失败的原因可能是因为有其它线程正在对该cell对象进行更新
                    //此时当前线程需要将wasUncontended设置为true说明有其它线程正在竞争
                    //当前线程自旋一次等待下次的更新
                } else if (!wasUncontended)
                    wasUncontended = true;
                    //当线程更新失败的时候则会自旋一次
                    //自旋之后则会执行当前判断语句中的CAS操作
                    //当前CAS操作执行成功则退出循环
                else if (a.cas(v = a.value, ((fn == null) ? v + x : fn.applyAsLong(v, x))))
                    break;
                    //如果当前cells数组的长度到达了cpu的核心数或者cells已经发生了扩容
                    //则将collide设置为false并执行advanceProbe重新获取线程的Probe值
                else if (n >= NCPU || cells != as)
                    collide = false;
                    //如果上上个if语句中执行CAS操作失败了或者上个if语句条件不成立则说明出现了线程竞争
                else if (!collide)
                    //将collide设置为true说明出现了竞争的情况
                    collide = true;
                    //出现了竞争的情况尝试获取锁并扩容
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        //获取锁成功
                        //校验cells是已经被其它线程扩容
                        if (cells == as) {
                            //未被其它线程扩容则当前线程执行扩容
                            //扩容大小与原数组的两倍
                            Cell[] rs = new Cell[n << 1];
                            //循环将旧数组元素拷贝到新的数组中
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            //重新赋值cells为新数组
                            cells = rs;
                        }
                    } finally {
                        //释放锁
                        cellsBusy = 0;
                    }
                    //已竞争问题
                    collide = false;
                    //使用扩容后的新数组重试执行操作
                    continue;
                }
                //重新获取线程的Probe值
                h = advanceProbe(h);
                //其它线程还未加锁并且cells未被初始化
                //则调用casCellsBusy()方法进行CAS操作将cellsBusy设置为1
                //当其它线程看到cellsBusy的值设置为1时则说明已经有线程在初始化
            } else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                //是否初始化成功
                boolean init = false;
                try {
                    //校验as是否与cells相同
                    //如果不相同则说明在获取锁的时候其它线程可能已经对cells数组进行了初始化
                    if (cells == as) {
                        //创建大小为2的cell数组
                        Cell[] rs = new Cell[2];
                        //使用线程的Probe值进行与运算获取到cell数组中的索引位置
                        //并创建指定索引位置上的cell对象
                        //在创建cell对象的时候已经将x的值赋值给了cell对象中的value
                        //相当于已经执行了数值操作
                        rs[h & 1] = new Cell(x);
                        //将创建的数组赋值给cells
                        cells = rs;
                        //初始化成功
                        init = true;
                    }
                } finally {
                    //释放锁
                    cellsBusy = 0;
                }
                //校验是否初始化成功
                //成功则退出当前循环
                if (init)
                    break;
                //当其它线程正在初始化cells数组
                //当前线程则对base执行CAS操作
                //如果CAS操作失败则校验cells是否初始化完成
                //如果未完成则校验其它线程是否在初始化cells数组
                //如果其它线程正在初始化则继续对base执行CAS操作
            } else if (casBase(v = base, ((fn == null) ? v + x : fn.applyAsLong(v, x))))
                break;
        }
    }

    /**
     * Same as longAccumulate, but injecting long/double conversions
     * in too many places to sensibly merge with long version, given
     * the low-overhead requirements of this class. So must instead be
     * maintained by copy/paste/adapt.
     */
    final void doubleAccumulate(double x, DoubleBinaryOperator fn,
                                boolean wasUncontended) {
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        for (; ; ) {
            Cell[] as;
            Cell a;
            int n;
            long v;
            if ((as = cells) != null && (n = as.length) > 0) {
                if ((a = as[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        Cell r = new Cell(Double.doubleToRawLongBits(x));
                        if (cellsBusy == 0 && casCellsBusy()) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs;
                                int m, j;
                                if ((rs = cells) != null &&
                                        (m = rs.length) > 0 &&
                                        rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            if (created)
                                break;
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                } else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (a.cas(v = a.value,
                        ((fn == null) ?
                                Double.doubleToRawLongBits
                                        (Double.longBitsToDouble(v) + x) :
                                Double.doubleToRawLongBits
                                        (fn.applyAsDouble
                                                (Double.longBitsToDouble(v), x)))))
                    break;
                else if (n >= NCPU || cells != as)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == as) {      // Expand table unless stale
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            } else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {                           // Initialize table
                    if (cells == as) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    break;
            } else if (casBase(v = base,
                    ((fn == null) ?
                            Double.doubleToRawLongBits
                                    (Double.longBitsToDouble(v) + x) :
                            Double.doubleToRawLongBits
                                    (fn.applyAsDouble
                                            (Double.longBitsToDouble(v), x)))))
                break;                          // Fall back on using base
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long BASE;
    private static final long CELLSBUSY;
    private static final long PROBE;

    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> sk = Striped64.class;
            BASE = UNSAFE.objectFieldOffset
                    (sk.getDeclaredField("base"));
            CELLSBUSY = UNSAFE.objectFieldOffset
                    (sk.getDeclaredField("cellsBusy"));
            Class<?> tk = Thread.class;
            PROBE = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomProbe"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
