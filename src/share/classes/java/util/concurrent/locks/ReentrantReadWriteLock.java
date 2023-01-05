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

package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.Collection;

public class ReentrantReadWriteLock
        implements ReadWriteLock, java.io.Serializable {
    private static final long serialVersionUID = -6992448646407690164L;

    private final ReentrantReadWriteLock.ReadLock readerLock;

    private final ReentrantReadWriteLock.WriteLock writerLock;

    final Sync sync;

    /**
     * 初始化读写锁
     * 默认使用的是非公平锁
     */
    public ReentrantReadWriteLock() {
        this(false);
    }

    /**
     * 初始化读写锁
     * 根据指定的参数来决定使用的是公平锁还是非公平锁
     * @param fair
     */
    public ReentrantReadWriteLock(boolean fair) {
        //根据参数来决定锁是公平锁还是非公平锁
        sync = fair ? new FairSync() : new NonfairSync();
        //读锁
        readerLock = new ReadLock(this);
        //写锁
        writerLock = new WriteLock(this);
    }

    public ReentrantReadWriteLock.WriteLock writeLock() { return writerLock; }
    public ReentrantReadWriteLock.ReadLock  readLock()  { return readerLock; }

    /**
     * 读写锁的具体实现
     * 分为公平锁和非公平锁
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 6317671515068378041L;
        //使用锁状态的高低16位来区分读写锁 高16位为读锁 低16位为写锁
        static final int SHARED_SHIFT   = 16;
        //65536 每加一次读锁将在state的基础上加上这个值
        static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
        //65535 加锁的最大次数
        static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
        //16进制的最大值 65535
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /** 获取读锁的加锁次数  */
        static int sharedCount(int c)    {
            //将加锁次数的高16位无符号右移16位获取到读锁的加锁次数
            return c >>> SHARED_SHIFT;
        }
        /** 获取写锁的加锁次数 */
        static int exclusiveCount(int c) {
            //使用当前加锁的次数与65535的二进制进行与运算获取到写锁的加锁次数
            /**
             * 65535的二进制  1111 1111 1111 1111
             * c = 1        0000 0000 0000 0001
             * 重入次数      0000 0000 0000 0001  = 1
             */
            return c & EXCLUSIVE_MASK;
        }

        static final class HoldCounter {
            //加锁次数
            int count = 0;
            //线程id
            final long tid = getThreadId(Thread.currentThread());
        }

        static final class ThreadLocalHoldCounter extends ThreadLocal<HoldCounter> {
            public HoldCounter initialValue() {
                return new HoldCounter();
            }
        }

        //当前线程持有的可重入读锁的数量对象
        private transient ThreadLocalHoldCounter readHolds;
        //线程加读锁时记录加锁次数和线程id的对象(最后一个线程加锁的次数和线程id)
        private transient HoldCounter cachedHoldCounter;
        //第一个加读锁的线程
        private transient Thread firstReader = null;
        //第一个加读锁的线程重入加读锁的次数
        private transient int firstReaderHoldCount;

        Sync() {
            readHolds = new ThreadLocalHoldCounter();
            setState(getState());
        }

        abstract boolean readerShouldBlock();


        abstract boolean writerShouldBlock();

        /**
         * 释放写锁
         * @param releases
         * @return
         */
        protected final boolean tryRelease(int releases) {
            //校验加锁的线程是否是当前线程
            if (!isHeldExclusively())
                //不是当前线程则抛出异常
                throw new IllegalMonitorStateException();
            //加锁次数减去释放锁次数获取到剩余加锁次数
            int nextc = getState() - releases;
            //校验剩余加锁次数是否为0
            boolean free = exclusiveCount(nextc) == 0;
            if (free)
                //如果为0则将持有锁的线程设置为空
                setExclusiveOwnerThread(null);
            //更新锁状态
            setState(nextc);
            return free;
        }

        /**
         * 获取写锁
         * @param acquires
         * @return
         */
        protected final boolean tryAcquire(int acquires) {
            //获取当前线程
            Thread current = Thread.currentThread();
            //获取锁状态
            int c = getState();
            //获取写锁的加锁次数
            int w = exclusiveCount(c);
            //校验锁状态是否不为0
            //如果锁状态不为0说明有线程已经加了锁
            if (c != 0) {
                //如果w等于0则有线程加的是读锁,当前线程则需要入队等待
                //如果w不等于0则有线程加的是写锁此时就会校验加写锁的线程是否是当前线程
                //如果是当前线程则重入,如果不是当前线程则需要入队等待
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                //重入
                if (w + exclusiveCount(acquires) > MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                setState(c + acquires);
                return true;
            }
            //锁状态等于0说明还没有线程加锁
            //writerShouldBlock 在公平锁的模式下会先校验等待队列中是否有线程节点在等待
            //如果有线程节点在等待当前线程则需要入队等待
            //在非公平锁的模式下不管等待队列中是否有线程在等待,都会尝试加锁
            if (writerShouldBlock() || !compareAndSetState(c, c + acquires))
                return false;
            //将当前线程设置为持有锁的线程
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * 释放读锁(共享锁)
         * @param unused
         * @return
         */
        protected final boolean tryReleaseShared(int unused) {
            //获取当前线程
            Thread current = Thread.currentThread();
            //校验当前线程是否是第一个加读锁的线程
            if (firstReader == current) {
                //当前线程是第一个加读锁的线程则校验当前线程加读锁的次数是否等于1
                if (firstReaderHoldCount == 1)
                    //加读锁的次数等于1则将第一次加读锁的线程置为空
                    firstReader = null;
                else
                    //读锁的次数自减
                    firstReaderHoldCount--;
            } else {
                //获取最后一个线程加锁的计数器对象
                HoldCounter rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current))
                    //最后一个加锁的线程不是当前线程或计数器对象为空则从当前线程中获取计数器对象
                    rh = readHolds.get();
                //获取加锁次数
                int count = rh.count;
                if (count <= 1) {
                    //从当前线程中删除计数器对象
                    readHolds.remove();
                    if (count <= 0)
                        throw unmatchedUnlockException();
                }
                //读锁的次数自减
                --rh.count;
            }
            for (;;) {
                //获取锁状态
                int c = getState();
                //减去一次读锁
                int nextc = c - SHARED_UNIT;
                //更新锁状态
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }

        private IllegalMonitorStateException unmatchedUnlockException() {
            return new IllegalMonitorStateException(
                "attempt to unlock read lock, not locked by current thread");
        }

        protected final int tryAcquireShared(int unused) {
            //获取当前线程
            Thread current = Thread.currentThread();
            //获取加锁状态
            int c = getState();
            //校验写锁的加锁次数,如果写锁的加锁次数不等于0则说明已经有线程加了写锁
            //则校验加写锁的线程是否是当前线程,如果不是当前线程则返回-1
            //返回-1后则会为当前线程创建节点并将节点添加到等待队列中
            //等待前面的线程节点获取了锁并释放了锁之后再去获取锁
            //情况1:写锁次数为0则可以尝试获取读锁
            //情况2:写锁次数不为0并且加写锁的线程不是当前线程,此时当前线程加读锁则需要进入等待队列中等待
            //情况3:写锁次数不为0并且加写锁的线程是当前线程,则需要走锁降级将写锁降级成读锁
            if (exclusiveCount(c) != 0 && getExclusiveOwnerThread() != current)
                return -1;
            //获取读锁的加锁次数
            int r = sharedCount(c);
            //readerShouldBlock 队列中的头节点的下一个节点是否是在独占模式下等待 true 独占模式  false 共享模式
            //在非公平锁的模式下队列中的头节点的下一个节点不是在独占模式下等待
            //并且读锁的加锁次数小于最大加锁次数并且当前线程通过CAS操作修改了state的值成功获取到了读锁
            //在公平锁的模式下如果等待队列中有线程节点在等待则需要排队等待获取锁
            if (!readerShouldBlock() && r < MAX_COUNT && compareAndSetState(c, c + SHARED_UNIT)) {
                if (r == 0) {
                    //r等于0则说明当前线程是第一个加读锁的线程
                    //将当前线程设置为第一个加读锁的线程
                    firstReader = current;
                    //重入次数
                    firstReaderHoldCount = 1;
                } else if (firstReader == current) {
                    //第一个加读锁的线程是当前线程则将重入次数自增
                    firstReaderHoldCount++;
                } else {
                    //线程加锁的计数器对象
                    HoldCounter rh = cachedHoldCounter;
                    //校验计数器对象是否为空或者上一次加锁的线程是否是当前线程
                    if (rh == null || rh.tid != getThreadId(current))
                        //如果计数器等于空或计数器对象中的线程不是当前线程
                        //则说明只有一个线程加了读锁之后后续没有线程加读锁
                        //或上一次加读锁的线程不是当前线程
                        //从当前线程中获取计数器对象,如果当前线程中没有计数器对象则会创建
                        cachedHoldCounter = rh = readHolds.get();
                    else if (rh.count == 0)
                        //如果计数器对象不为空并且上一次加锁的线程是当前线程并且加锁次数为0
                        //有可能是加锁的线程进入了等待队列,线程还未获取到锁的时候被取消了加锁
                        readHolds.set(rh);
                    //线程加锁次数自增
                    rh.count++;
                }
                //加锁成功
                return 1;
            }
            //加锁失败
            return fullTryAcquireShared(current);
        }

        final int fullTryAcquireShared(Thread current) {
            HoldCounter rh = null;
            for (;;) {
                //获取加锁状态
                int c = getState();
                //校验写锁的加锁次数是否不为0
                if (exclusiveCount(c) != 0) {
                    //如果写锁的加锁次数不为0则校验加写锁的线程是否是当前线程
                    //已有线程加了写锁并且不是当前线程则返回-1进入等待队列中等待
                    if (getExclusiveOwnerThread() != current)
                        return -1;
                    //在非公平锁的模式下如果等待队列中的头节点的下一个节点是独占模式则当前线程进入等待队列
                    //在公平锁的模式下如果等待队列中有线程节点在等待则当前线程进入等待队列
                    //为什么在非公平锁的模式下等待队列中的头节点的下一个节点是独占模式则当前线程要进入等待队列呢?
                    //在读多写少的情况下大量的线程获取读锁,可能会导致写锁没有获取锁的权限,导致写锁饥饿
                    //在公平锁的模式下只要等待队列中有节点在等待获取锁,那就要依次排队获取锁
                } else if (readerShouldBlock()) {
                    //第一次加读锁的线程是当前线程则可以重入加锁
                    if (firstReader == current) {
                    } else {
                        //当前线程不是第一次加读锁的线程
                        //校验线程加锁的计数器对象是否为空
                        //如果是一次循环的话计数器对象肯定是为空的
                        if (rh == null) {
                            //将最后一次加锁的计数器对象赋值给rh
                            rh = cachedHoldCounter;
                            //校验最后一次加锁的计数器对象是否为空或者最后一次加锁的对象不是当前线程
                            if (rh == null || rh.tid != getThreadId(current)) {
                                //如果计数器等于空或计数器对象中的线程不是当前线程
                                //则说明只有一个线程加了读锁之后后续没有线程加读锁
                                //或上一次加读锁的线程不是当前线程
                                //从当前线程中获取计数器对象,如果当前线程中没有计数器对象则会创建
                                rh = readHolds.get();
                                if (rh.count == 0)
                                    //如果计数器对象中的加锁次数等于0则说明该线程之前没有加过读锁
                                    //则将当前线程中的计数器对象删除
                                    readHolds.remove();
                            }
                        }
                        //如果计数器对象中的加锁次数不等于0则说明当前线程之前加过锁则需要重入锁
                        if (rh.count == 0)
                            //如果计数器对象中的加锁次数等于0则让当前线程入队进行等待
                            return -1;
                    }
                }
                if (sharedCount(c) == MAX_COUNT)
                    //读锁的次数超出最大加锁次数
                    throw new Error("Maximum lock count exceeded");
                //尝试加读锁
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (sharedCount(c) == 0) {
                        //如果当前线程是第一个加读锁的线程则将当前线程记录下来并记录加锁的次数
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        //当前线程是第一个加读锁的线程则将重入次数自增
                        firstReaderHoldCount++;
                    } else {
                        if (rh == null)
                            //获取上一次加读锁的计数器对象
                            rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            //如果计数器等于空或计数器对象中的线程不是当前线程
                            //则说明只有一个线程加了读锁之后后续没有线程加读锁
                            //或上一次加读锁的线程不是当前线程
                            //从当前线程中获取计数器对象,如果当前线程中没有计数器对象则会创建
                            rh = readHolds.get();
                        else if (rh.count == 0)
                            //如果计数器对象不为空并且上一次加锁的线程是当前线程并且加锁次数为0
                            //有可能是加锁的线程进入了等待队列,线程还未获取到锁的时候被取消了加锁
                            readHolds.set(rh);
                        rh.count++;
                        cachedHoldCounter = rh;
                    }
                    return 1;
                }
            }
        }

        /**
         * Performs tryLock for write, enabling barging in both modes.
         * This is identical in effect to tryAcquire except for lack
         * of calls to writerShouldBlock.
         */
        final boolean tryWriteLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            if (c != 0) {
                int w = exclusiveCount(c);
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                if (w == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
            }
            if (!compareAndSetState(c, c + 1))
                return false;
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * Performs tryLock for read, enabling barging in both modes.
         * This is identical in effect to tryAcquireShared except for
         * lack of calls to readerShouldBlock.
         */
        final boolean tryReadLock() {
            Thread current = Thread.currentThread();
            for (;;) {
                int c = getState();
                if (exclusiveCount(c) != 0 &&
                    getExclusiveOwnerThread() != current)
                    return false;
                int r = sharedCount(c);
                if (r == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (r == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        HoldCounter rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            cachedHoldCounter = rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                    }
                    return true;
                }
            }
        }

        /**
         * 校验加锁的线程是否是当前线程
         * @return
         */
        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        // Methods relayed to outer class

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final Thread getOwner() {
            // Must read state before owner to ensure memory consistency
            return ((exclusiveCount(getState()) == 0) ?
                    null :
                    getExclusiveOwnerThread());
        }

        final int getReadLockCount() {
            return sharedCount(getState());
        }

        final boolean isWriteLocked() {
            return exclusiveCount(getState()) != 0;
        }

        final int getWriteHoldCount() {
            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
        }

        final int getReadHoldCount() {
            if (getReadLockCount() == 0)
                return 0;

            Thread current = Thread.currentThread();
            if (firstReader == current)
                return firstReaderHoldCount;

            HoldCounter rh = cachedHoldCounter;
            if (rh != null && rh.tid == getThreadId(current))
                return rh.count;

            int count = readHolds.get().count;
            if (count == 0) readHolds.remove();
            return count;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            readHolds = new ThreadLocalHoldCounter();
            setState(0); // reset to unlocked state
        }

        final int getCount() { return getState(); }
    }

    /**
     * 非公平锁
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -8159625535654395037L;
        final boolean writerShouldBlock() {
            return false;
        }
        final boolean readerShouldBlock() {
            //队列中的头节点的下一个节点是否是在独占模式下等待
            return apparentlyFirstQueuedIsExclusive();
        }
    }

    /**
     * 公平锁
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -2274990926593161451L;
        final boolean writerShouldBlock() {
            //查看等待队列中是否还有线程在等待获取锁
            //如果有则比较下一个获取锁的线程是否是当前线程
            return hasQueuedPredecessors();
        }
        final boolean readerShouldBlock() {
            //查看等待队列中是否还有线程在等待获取锁
            //如果有则比较下一个获取锁的线程是否是当前线程
            return hasQueuedPredecessors();
        }
    }

    /**
     * 读锁
     */
    public static class ReadLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -5992448646407690164L;
        //锁对象 根据外部创建的锁对象来确定当前读锁是公平的还是非公平的
        private final Sync sync;

        /**
         * 根据外部锁对象来构建读锁
         * @param lock 外部锁对象  公平锁或非公平锁
         */
        protected ReadLock(ReentrantReadWriteLock lock) {
            //获取外部锁对象
            sync = lock.sync;
        }

        /**
         * 加读锁
         * 读锁又称共享锁
         * 可以多个线程同时加读锁
         * 加了读锁时,其它线程就不能加写锁
         */
        public void lock() {
            //获取共享锁
            sync.acquireShared(1);
        }

        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }


        public boolean tryLock() {
            return sync.tryReadLock();
        }

        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        /**
         * 释放读锁
         */
        public void unlock() {
            sync.releaseShared(1);
        }

        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() +
                "[Read locks = " + r + "]";
        }
    }

    /**
     * 写锁
     */
    public static class WriteLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -4992448646407690164L;
        //锁对象 根据外部创建的锁对象来确定当前写锁是公平的还是非公平的
        private final Sync sync;

        /**
         * 根据外部锁对象来构建写锁
         * @param lock 外部锁对象  公平锁或非公平锁
         */
        protected WriteLock(ReentrantReadWriteLock lock) {
            //获取外部锁对象
            sync = lock.sync;
        }

        /**
         * 获取写锁
         */
        public void lock() {
            sync.acquire(1);
        }

        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        public boolean tryLock( ) {
            return sync.tryWriteLock();
        }

        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        /**
         * 释放写锁
         */
        public void unlock() {
            sync.release(1);
        }

        public Condition newCondition() {
            return sync.newCondition();
        }

        public String toString() {
            Thread o = sync.getOwner();
            return super.toString() + ((o == null) ?
                                       "[Unlocked]" :
                                       "[Locked by thread " + o.getName() + "]");
        }

        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }

    // Instrumentation and status

    /**
     * Returns {@code true} if this lock has fairness set true.
     *
     * @return {@code true} if this lock has fairness set true
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * Returns the thread that currently owns the write lock, or
     * {@code null} if not owned. When this method is called by a
     * thread that is not the owner, the return value reflects a
     * best-effort approximation of current lock status. For example,
     * the owner may be momentarily {@code null} even if there are
     * threads trying to acquire the lock but have not yet done so.
     * This method is designed to facilitate construction of
     * subclasses that provide more extensive lock monitoring
     * facilities.
     *
     * @return the owner, or {@code null} if not owned
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    /**
     * Queries if the write lock is held by any thread. This method is
     * designed for use in monitoring system state, not for
     * synchronization control.
     *
     * @return {@code true} if any thread holds the write lock and
     *         {@code false} otherwise
     */
    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    /**
     * Queries if the write lock is held by the current thread.
     *
     * @return {@code true} if the current thread holds the write lock and
     *         {@code false} otherwise
     */
    public boolean isWriteLockedByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * Queries the number of reentrant write holds on this lock by the
     * current thread.  A writer thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the write lock by the current thread,
     *         or zero if the write lock is not held by the current thread
     */
    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    /**
     * Queries the number of reentrant read holds on this lock by the
     * current thread.  A reader thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the read lock by the current thread,
     *         or zero if the read lock is not held by the current thread
     * @since 1.6
     */
    public int getReadHoldCount() {
        return sync.getReadHoldCount();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the write lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getExclusiveQueuedThreads();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the read lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        return sync.getSharedQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting to acquire the read or
     * write lock. Note that because cancellations may occur at any
     * time, a {@code true} return does not guarantee that any other
     * thread will ever acquire a lock.  This method is designed
     * primarily for use in monitoring of the system state.
     *
     * @return {@code true} if there may be other threads waiting to
     *         acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Queries whether the given thread is waiting to acquire either
     * the read or write lock. Note that because cancellations may
     * occur at any time, a {@code true} return does not guarantee
     * that this thread will ever acquire a lock.  This method is
     * designed primarily for use in monitoring of the system state.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is queued waiting for this lock
     * @throws NullPointerException if the thread is null
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * Returns an estimate of the number of threads waiting to acquire
     * either the read or write lock.  The value is only an estimate
     * because the number of threads may change dynamically while this
     * method traverses internal data structures.  This method is
     * designed for use in monitoring of the system state, not for
     * synchronization control.
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire either the read or write lock.  Because the actual set
     * of threads may change dynamically while constructing this
     * result, the returned collection is only a best-effort estimate.
     * The elements of the returned collection are in no particular
     * order.  This method is designed to facilitate construction of
     * subclasses that provide more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with the write lock. Note that because timeouts and
     * interrupts may occur at any time, a {@code true} return does
     * not guarantee that a future {@code signal} will awaken any
     * threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with the write lock. Note that because
     * timeouts and interrupts may occur at any time, the estimate
     * serves only as an upper bound on the actual number of waiters.
     * This method is designed for use in monitoring of the system
     * state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with the write lock.
     * Because the actual set of threads may change dynamically while
     * constructing this result, the returned collection is only a
     * best-effort estimate. The elements of the returned collection
     * are in no particular order.  This method is designed to
     * facilitate construction of subclasses that provide more
     * extensive condition monitoring facilities.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a string identifying this lock, as well as its lock state.
     * The state, in brackets, includes the String {@code "Write locks ="}
     * followed by the number of reentrantly held write locks, and the
     * String {@code "Read locks ="} followed by the number of held
     * read locks.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        int c = sync.getCount();
        int w = Sync.exclusiveCount(c);
        int r = Sync.sharedCount(c);

        return super.toString() +
            "[Write locks = " + w + ", Read locks = " + r + "]";
    }

    /**
     * Returns the thread id for the given thread.  We must access
     * this directly rather than via method Thread.getId() because
     * getId() is not final, and has been known to be overridden in
     * ways that do not preserve unique mappings.
     */
    static final long getThreadId(Thread thread) {
        return UNSAFE.getLongVolatile(thread, TID_OFFSET);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long TID_OFFSET;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            TID_OFFSET = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("tid"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
