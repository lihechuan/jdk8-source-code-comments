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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.LockSupport;

public class StampedLock implements java.io.Serializable {

    private static final long serialVersionUID = -6001602636862214147L;

    /** 处理器数量 */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /** 入队前最大的自旋次数  */
    private static final int SPINS = (NCPU > 1) ? 1 << 6 : 0;

    /** 第一次入队时自旋次数 */
    private static final int HEAD_SPINS = (NCPU > 1) ? 1 << 10 : 0;

    /** 第一次入队时最大的自旋次数 */
    private static final int MAX_HEAD_SPINS = (NCPU > 1) ? 1 << 16 : 0;

    /** 线程让步概率 */
    private static final int OVERFLOW_YIELD_RATE = 7;

    /**
     * state中的低7位二进制代表读锁加锁次数
     * 第8位二进制代表写锁
     * */
    private static final int LG_READERS = 7;

    //读锁加锁时在原有锁状态上加的值,这样就能获取到读锁的版本号已经读锁的状态
    //读锁加锁的时候每次在锁状态上的基础值加1
    //写锁加锁的时候每次在锁状态上的基础值加256
    private static final long RUNIT = 1L;

    //写锁所在的二进制位 1000 0000
    //1代表写锁
    private static final long WBIT  = 1L << LG_READERS;

    //读锁所在的二进制位 0111 1111
    //1代表读锁
    private static final long RBITS = WBIT - 1L;

    //读锁的二进制位最大加锁次数,因为7个二进制位表达的数值只有这么多
    //并不是说读锁只能加锁这么多,溢出的加锁次数会用readerOverflow变量来存放
    private static final long RFULL = RBITS - 1L;

    //读锁和写锁所代表的二进制位 1111 1111
    private static final long ABITS = RBITS | WBIT;

    //对读锁的二进制位进行取反
    //获取到的是-128,读锁的二进制位为0以外其余的二进制位都为1
    //1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1000 0000
    //该数为最大的写锁版本号
    private static final long SBITS = ~RBITS;

    //版本号的初始值 256
    private static final long ORIGIN = WBIT << 1;

    // Special value from cancelled acquire methods so caller can throw IE
    private static final long INTERRUPTED = 1L;

    //等待加锁
    private static final int WAITING   = -1;

    //取消加锁
    private static final int CANCELLED =  1;

    //读模式
    private static final int RMODE = 0;

    //写模式
    private static final int WMODE = 1;

    /** Wait nodes */
    static final class WNode {
        //上一个节点
        volatile WNode prev;
        //下一个节点
        volatile WNode next;
        //挂载的读节点
        volatile WNode cowait;
        //节点线程
        volatile Thread thread;
        //节点状态
        volatile int status;
        //节点模式 读/写
        final int mode;
        WNode(int m, WNode p) {
            mode = m;
            prev = p;
        }
    }

    /** 头节点 */
    private transient volatile WNode whead;
    /** 尾节点 */
    private transient volatile WNode wtail;

    // views
    transient ReadLockView readLockView;
    transient WriteLockView writeLockView;
    transient ReadWriteLockView readWriteLockView;

    /** 锁的状态以及版本号 默认256*/
    private transient volatile long state;
    /** 溢出的读锁加锁次数 */
    private transient int readerOverflow;

    /**
     * 创建一个带有标记的锁
     */
    public StampedLock() {
        //锁默认状态为256 二进制为0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0001 0000 0000
        //前56位为锁的版本号,版本号从256开始
        //而后面的8位分别为读写锁标识
        //第8位为写锁  1000 0000
        //后面7位为读锁
        state = ORIGIN;
    }

    /**
     * 获取写锁并返回该锁的版本号
     */
    public long writeLock() {
        long s, next;
        //((s = state) & ABITS) 使用当前锁状态与读写锁的标识进行与运算获取到当前是否有线程已经加锁
        //U.compareAndSwapLong 通过cas操作尝试获取写锁，如果获取写锁成功返回next，next则是当前写锁的版本号，释放写锁的时候通过该版本号来释放
        //先通过锁状态与读写锁的二进制位标识进行与运算来校验当前是否有线程已经加了锁
        //如果没有线程加锁当前线程则尝试加写锁，加写锁成功则返回当前线程写锁的版本号
        //写锁的第一个版本号为384，二进制为 0001 1000 0000
        //读锁的第一个版本号为257，二进制为 0001 0000 0001
        //如果加锁失败并且当前线程节点的上一个节点是头节点,那当前线程节点就一直自旋尝试获取锁
        //如果当前线程节点的上一个节点不是头节点,那当前线程节点根据cpu数量来计算自旋的次数
        //如果到达了指定的自旋次数还未获取到锁,那就会将当前线程节点中的线程挂起
        return (( ((s = state) & ABITS) == 0L && U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ?
                next : acquireWrite(false, 0L));
    }

    /**
     * Exclusively acquires the lock if it is immediately available.
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    public long tryWriteLock() {
        long s, next;
        return ((((s = state) & ABITS) == 0L &&
                 U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ?
                next : 0L);
    }

    /**
     * Exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted.
     * Behavior under timeout and interruption matches that specified
     * for method {@link Lock#tryLock(long,TimeUnit)}.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long tryWriteLock(long time, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            long next, deadline;
            if ((next = tryWriteLock()) != 0L)
                return next;
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireWrite(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * Exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     * Behavior under interruption matches that specified
     * for method {@link Lock#lockInterruptibly()}.
     *
     * @return a stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long writeLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() &&
            (next = acquireWrite(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * 获取读锁
     */
    public long readLock() {
        //s 锁状态
        //next 锁版本号
        long s = state, next;
        //whead == wtail 为true则说明队列中没有线程节点在等待加锁
        //(s & ABITS) < RFULL
        // 1.(s & ABITS)校验是否有线程加锁,如果为0则说明没有线程加锁,如果小于126则说明加的是读锁,如果大于126说明加的是写锁
        // 2.< RFULL RFULL为126,读锁的最大加锁次数,如果大于RFULL则说明已经有线程加了写锁那就让当前线程自旋或挂起
        //如果小于RFULL则说明没有线程加写锁,有可能是加了读锁,读读共享,当前线程可以尝试去获取读锁
        return ((whead == wtail && (s & ABITS) < RFULL && U.compareAndSwapLong(this, STATE, s, next = s + RUNIT)) ?
                next : acquireRead(false, 0L));
    }

    /**
     * Non-exclusively acquires the lock if it is immediately available.
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    public long tryReadLock() {
        for (;;) {
            long s, m, next;
            if ((m = (s = state) & ABITS) == WBIT)
                return 0L;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                    return next;
            }
            else if ((next = tryIncReaderOverflow(s)) != 0L)
                return next;
        }
    }

    /**
     * Non-exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted.
     * Behavior under timeout and interruption matches that specified
     * for method {@link Lock#tryLock(long,TimeUnit)}.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long tryReadLock(long time, TimeUnit unit)
        throws InterruptedException {
        long s, m, next, deadline;
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            if ((m = (s = state) & ABITS) != WBIT) {
                if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireRead(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * Non-exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     * Behavior under interruption matches that specified
     * for method {@link Lock#lockInterruptibly()}.
     *
     * @return a stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long readLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() &&
            (next = acquireRead(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * 尝试乐观读
     */
    public long tryOptimisticRead() {
        //锁状态以及版本号
        long s;
        //根据锁状态返回当前是否有线程加了写锁
        //!=0 有线程加了写锁
        //0 没有线程加写锁
        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }

    /**
     * 根据乐观读的版本号与最新的版本号来确定是否有线程加了写锁
     * true: 没有线程加写锁
     * false： 有线程加了写锁
     */
    public boolean validate(long stamp) {
        //加入内存屏障,刷新数据
        U.loadFence();
        //计算传递进来的版本号与最新的版本号是否加了写锁
        return (stamp & SBITS) == (state & SBITS);
    }

    /**
     * 释放指定版本号的写锁
     * @param stamp 版本号
     */
    public void unlockWrite(long stamp) {
        //头节点
        WNode h;
        //校验当前释放锁的版本号是否与加锁时的版本号相同
        //或者版本号加的锁不是写锁
        if (state != stamp || (stamp & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        //释放锁
        //计算下一次加锁的版本号以及修改锁的状态,如果版本号为0则使用初始的版本号256
        state = (stamp += WBIT) == 0L ? ORIGIN : stamp;
        //校验头节点是否为空并且头节点的状态是否不为0
        //如果头节点不为空并且状态不为0则说明头节点的锁释放后需要将后续线程节点唤醒
        if ((h = whead) != null && h.status != 0)
            //唤醒下一个线程节点
            release(h);
    }

    /**
     * 释放指定版本号的读锁
     * @param  stamp 版本号
     */
    public void unlockRead(long stamp) {
        //s 锁的状态
        //m 是否有线程加了锁,加的是读锁还是写锁
        long s, m;
        WNode h;
        for (;;) {
            //先校验锁的版本号是否跟传递进来的版本号相同
            //如果版本号相同校验是否有线程加了锁
            //如果有线程加了锁则校验是否加的是写锁
            if (((s = state) & SBITS) != (stamp & SBITS) ||
                (stamp & ABITS) == 0L || (m = s & ABITS) == 0L || m == WBIT)
                throw new IllegalMonitorStateException();
            //校验加的读锁次数是否溢出
            if (m < RFULL) {
                //读锁次数未溢出则释放锁
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        //只有当前一个线程加了读锁
                        //并且队列中是有线程节点在等待
                        //当前线程释放了读锁之后则需要唤醒队列中等待加锁的线程节点
                        release(h);
                    break;
                }
            }
            //读锁加锁次数溢出则释放锁并修改溢出的变量
            else if (tryDecReaderOverflow(s) != 0L)
                break;
        }
    }

    /**
     * 根据指定的版本号来释放锁
     * 不管是写锁还是读锁都可以释放
     */
    public void unlock(long stamp) {
        //a 传递进来的版本号是否加锁
        //m 共享变量state是否加锁
        //s 锁状态
        long a = stamp & ABITS, m, s;
        //头节点
        WNode h;
        //加锁的版本号是否与传递进来的版本号相同
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            //版本号相同
            //校验是否加了锁,如果为0则说明没有加锁则退出
            //因为state默认为256是没有锁的,如果传递进来的版本号是256
            //此时并没有对应的锁需要释放
            if ((m = s & ABITS) == 0L)
                break;
            //校验是否是加的写锁
            else if (m == WBIT) {
                //校验传递进来的版本号的锁是否与共享变量state的锁相同
                if (a != m)
                    //不相同则退出
                    break;
                //更新最新的版本号,如果版本号等于0则说明已经超出最大的版本号了,则将版本号初始化从256开始
                state = (s += WBIT) == 0L ? ORIGIN : s;
                //如果队列中有线程节点在等待加锁,当前线程释放了锁之后会去尝试唤醒队列中等待的线程节点
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return;
            }
            //传递进来的版本号的锁并没有对应的锁
            else if (a == 0L || a >= WBIT)
                break;
            //校验加的读锁尝试是否溢出
            else if (m < RFULL) {
                //未溢出
                //释放锁,如果只有一个线程加了读锁,当前这个线程加的读锁释放之后,锁变成了空闲状态
                //如果等待队列中有线程节点在等待,那释放了读锁之后会尝试将等待队列中的线程节点唤醒
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return;
                }
            }
            //读锁次数溢出则更新溢出的变量并释放读锁
            else if (tryDecReaderOverflow(s) != 0L)
                return;
        }
        //版本号不相同,没有对应的锁
        throw new IllegalMonitorStateException();
    }

    /**
     * 将锁转换为写锁
     */
    public long tryConvertToWriteLock(long stamp) {
        //a 是否有线程加了锁
        long a = stamp & ABITS, m, s, next;
        //当前锁的版本号与传递进来的版本号是否相同
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            //校验当前是否有线程加了锁
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    //传递进来的版本号加了锁,但是当前state并没有加锁则返回
                    break;
                //加写锁
                if (U.compareAndSwapLong(this, STATE, s, next = s + WBIT))
                    return next;
            }
            //是否加的写锁
            else if (m == WBIT) {
                if (a != m)
                    //当前有线程加了写锁,但是传递进来的版本号与当前加写锁的版本号不相同
                    break;
                //当前版本号是一个写锁
                return stamp;
            }
            //加了读锁则校验加读锁的线程是否只有一个
            else if (m == RUNIT && a != 0L) {
                //加读锁的线程只有一个则将读锁转换为写锁
                if (U.compareAndSwapLong(this, STATE, s, next = s - RUNIT + WBIT))
                    return next;
            }
            else
                break;
        }
        return 0L;
    }

    /**
     * 将锁转换为读锁
     */
    public long tryConvertToReadLock(long stamp) {
        //a 是否有线程加了锁
        long a = stamp & ABITS, m, s, next; WNode h;
        //当前锁的版本号与传递进来的版本号是否相同
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            //校验当前是否有线程加了锁
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    //传递进来的版本号加了锁,但是当前state并没有加锁则返回
                    break;
                else if (m < RFULL) {
                    //当前state加了读锁但锁的次数并没有溢出则获取锁
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                //加读锁次数溢出,获取锁并将溢出的次数使用变量存放
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            //有线程加了写锁
            else if (m == WBIT) {
                if (a != m)
                    //当前有线程加了写锁,但是传递进来的版本号与当前加写锁的版本号不相同
                    break;
                //释放写锁并获取读锁
                state = next = s + (WBIT + RUNIT);
                if ((h = whead) != null && h.status != 0)
                    //唤醒队列中的线程节点
                    release(h);
                return next;
            }
            else if (a != 0L && a < WBIT)
                //有许多线程加了读锁,所以当前线程不能转为读锁
                return stamp;
            else
                break;
        }
        return 0L;
    }

    /**
     * 将锁转换为乐观读锁
     */
    public long tryConvertToOptimisticRead(long stamp) {
        //a 是否有线程加了锁
        long a = stamp & ABITS, m, s, next; WNode h;
        //加入内存屏障,刷新数据
        U.loadFence();
        for (;;) {
            if (((s = state) & SBITS) != (stamp & SBITS))
                //传递进来的版本号与当前的版本号不相同
                break;
            //校验当前是否有线程加锁
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    //传递进来的版本号加了锁,但是当前state并没有加锁则返回
                    break;
                //没有线程加锁返回最新的版本号
                return s;
            }
            //校验当前加锁的线程是否是加的写锁
            else if (m == WBIT) {
                if (a != m)
                    //当前有线程加了写锁,但是传递进来的版本号与当前加写锁的版本号不相同
                    break;
                //释放写锁并计算下一个加锁的版本号
                //如果版本号超出最大值则使用初始的版本号
                state = next = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0)
                    //唤醒队列中的线程节点
                    release(h);
                return next;
            }
            else if (a == 0L || a >= WBIT)
                //传递进来的版本号没有加锁
                //或版本号是一个错误的版本号
                break;
            //加读锁的次数是否溢出
            else if (m < RFULL) {
                //释放读锁
                if (U.compareAndSwapLong(this, STATE, s, next = s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        //唤醒队列中的线程节点
                        release(h);
                    //下一个加锁的版本号
                    return next & SBITS;
                }
            }
            //加读锁次数溢出,获取锁并将溢出的次数使用变量存放
            else if ((next = tryDecReaderOverflow(s)) != 0L)
                return next & SBITS;
        }
        return 0L;
    }

    /**
     * Releases the write lock if it is held, without requiring a
     * stamp value. This method may be useful for recovery after
     * errors.
     *
     * @return {@code true} if the lock was held, else false
     */
    public boolean tryUnlockWrite() {
        long s; WNode h;
        if (((s = state) & WBIT) != 0L) {
            state = (s += WBIT) == 0L ? ORIGIN : s;
            if ((h = whead) != null && h.status != 0)
                release(h);
            return true;
        }
        return false;
    }

    /**
     * Releases one hold of the read lock if it is held, without
     * requiring a stamp value. This method may be useful for recovery
     * after errors.
     *
     * @return {@code true} if the read lock was held, else false
     */
    public boolean tryUnlockRead() {
        long s, m; WNode h;
        while ((m = (s = state) & ABITS) != 0L && m < WBIT) {
            if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return true;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                return true;
        }
        return false;
    }

    // status monitoring methods

    /**
     * Returns combined state-held and overflow read count for given
     * state s.
     */
    private int getReadLockCount(long s) {
        long readers;
        if ((readers = s & RBITS) >= RFULL)
            readers = RFULL + readerOverflow;
        return (int) readers;
    }

    /**
     * Returns {@code true} if the lock is currently held exclusively.
     *
     * @return {@code true} if the lock is currently held exclusively
     */
    public boolean isWriteLocked() {
        return (state & WBIT) != 0L;
    }

    /**
     * Returns {@code true} if the lock is currently held non-exclusively.
     *
     * @return {@code true} if the lock is currently held non-exclusively
     */
    public boolean isReadLocked() {
        return (state & RBITS) != 0L;
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return getReadLockCount(state);
    }

    /**
     * Returns a string identifying this lock, as well as its lock
     * state.  The state, in brackets, includes the String {@code
     * "Unlocked"} or the String {@code "Write-locked"} or the String
     * {@code "Read-locks:"} followed by the current number of
     * read-locks held.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        long s = state;
        return super.toString() +
            ((s & ABITS) == 0L ? "[Unlocked]" :
             (s & WBIT) != 0L ? "[Write-locked]" :
             "[Read-locks:" + getReadLockCount(s) + "]");
    }

    // views

    /**
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #readLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link
     * Lock#newCondition()} throws {@code
     * UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asReadLock() {
        ReadLockView v;
        return ((v = readLockView) != null ? v :
                (readLockView = new ReadLockView()));
    }

    /**
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #writeLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link
     * Lock#newCondition()} throws {@code
     * UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asWriteLock() {
        WriteLockView v;
        return ((v = writeLockView) != null ? v :
                (writeLockView = new WriteLockView()));
    }

    /**
     * Returns a {@link ReadWriteLock} view of this StampedLock in
     * which the {@link ReadWriteLock#readLock()} method is mapped to
     * {@link #asReadLock()}, and {@link ReadWriteLock#writeLock()} to
     * {@link #asWriteLock()}.
     *
     * @return the lock
     */
    public ReadWriteLock asReadWriteLock() {
        ReadWriteLockView v;
        return ((v = readWriteLockView) != null ? v :
                (readWriteLockView = new ReadWriteLockView()));
    }

    // view classes

    final class ReadLockView implements Lock {
        public void lock() { readLock(); }
        public void lockInterruptibly() throws InterruptedException {
            readLockInterruptibly();
        }
        public boolean tryLock() { return tryReadLock() != 0L; }
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryReadLock(time, unit) != 0L;
        }
        public void unlock() { unstampedUnlockRead(); }
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class WriteLockView implements Lock {
        public void lock() { writeLock(); }
        public void lockInterruptibly() throws InterruptedException {
            writeLockInterruptibly();
        }
        public boolean tryLock() { return tryWriteLock() != 0L; }
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryWriteLock(time, unit) != 0L;
        }
        public void unlock() { unstampedUnlockWrite(); }
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class ReadWriteLockView implements ReadWriteLock {
        public Lock readLock() { return asReadLock(); }
        public Lock writeLock() { return asWriteLock(); }
    }

    // Unlock methods without stamp argument checks for view classes.
    // Needed because view-class lock methods throw away stamps.

    final void unstampedUnlockWrite() {
        WNode h; long s;
        if (((s = state) & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        state = (s += WBIT) == 0L ? ORIGIN : s;
        if ((h = whead) != null && h.status != 0)
            release(h);
    }

    final void unstampedUnlockRead() {
        for (;;) {
            long s, m; WNode h;
            if ((m = (s = state) & ABITS) == 0L || m >= WBIT)
                throw new IllegalMonitorStateException();
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    break;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                break;
        }
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        state = ORIGIN; // reset to unlocked state
    }

    // internals

    /**
     * 读锁的加锁次数溢出,将溢出的加锁次数使用新的变量来存放
     * @param s 当前锁的状态以及版本号
     */
    private long tryIncReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        //校验读锁的加锁次数是否等于读锁定义的最大的加锁次数126
        //此时s中代表读锁的加锁次数为126     0111 1110
        if ((s & ABITS) == RFULL) {
            //读锁加锁次数已到达定义的最大加锁次数
            //继续尝试加读锁,如果继续加读锁成功此时state中代表加读锁的次数为127     0111 1111
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                //将溢出的加读锁次数使用变量存放起来
                ++readerOverflow;
                //为什么读锁定义的最大的加锁次数不是127而是126
                //如果使用127来作为最大的读锁加锁次数
                //你会发现在多线程并发下,每个线程进入到当前方法时s都为127
                //此时线程A执行了cas操作将state中加读锁的次数设置为了127
                //原先state中加读锁的次数就是127,跟没改一样
                //此时线程A将readerOverflow在主内存中的值拷贝到了自己的工作内存中准备执行++操作时
                //此时线程B执行了cas操作并将readerOverflow在主内存中的值拷贝到了自己的工作内存中并对工作内存中的值进行了++操作
                //线程B对工作内存中的值进行了++操作后将操作后的值刷到主内存中
                //此时线程A对工作内存中的值进行了++操作并刷到了主内存中
                //此时线程A修改后的值会将线程B修改后的值覆盖,这样就会导致少一次加锁的次数
                //最大加锁次数为126时,每个线程进入到当前方法时s都为126
                //此时线程A执行了cas操作将state中加读锁的次数设置为了127
                //其它线程执行cas操作时发现state最新的值与自己预期的值不相同
                //则会退出当前加读锁的方法,等待下一次加锁

                //将s中代表读锁的加锁次数126赋予state
                state = s;
                //返回版本号
                return s;
            }
        }
        //有线程在加读锁或释放读锁,此时当前线程生成随机数与线程让步的概率来计算是否需要让出运行机会
        else if ((LockSupport.nextSecondarySeed() & OVERFLOW_YIELD_RATE) == 0)
            //让出运行机会
            Thread.yield();
        return 0L;
    }

    /**
     * 释放读锁的时候,读锁的加锁次数溢出
     * 将溢出的次数减1并释放读锁
     */
    private long tryDecReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        //校验加锁次数是否溢出
        if ((s & ABITS) == RFULL) {
            //释放锁
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                int r;
                long next;
                if ((r = readerOverflow) > 0) {
                    //读锁的溢出次数大于0则将溢出次数减1
                    //而读锁的版本号还是s
                    readerOverflow = r - 1;
                    next = s;
                }
                else
                    //读锁的溢出次数等于0则说明读锁的加锁次数在126次
                    //并没有溢出也没有使用readerOverflow变量
                    //此时只需要在当前锁的版本号上减1
                    next = s - RUNIT;
                 //更新锁状态
                 state = next;
                 return next;
            }
        }
        //有线程在加读锁或释放读锁,此时当前线程生成随机数与线程让步的概率来计算是否需要让出运行机会
        else if ((LockSupport.nextSecondarySeed() & OVERFLOW_YIELD_RATE) == 0)
            //让出运行机会
            Thread.yield();
        return 0L;
    }

    /**
     * 唤醒下一个节点
     */
    private void release(WNode h) {
        //校验头节点是否为空
        if (h != null) {
            //等待唤醒的线程节点
            WNode q;
            //等待唤醒的线程
            Thread w;
            //修改头节点的状态
            U.compareAndSwapInt(h, WSTATUS, WAITING, 0);
            //如果头节点的下一个节点为空或已被取消加锁
            //则从尾节点开始向头节点遍历获取距离头节点最近的节点状态为等待状态的节点
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            //如果节点不为空并且节点中的线程也不为空则唤醒该线程
            if (q != null && (w = q.thread) != null)
                U.unpark(w);
        }
    }

    /**
     * 获取写锁
     * 如果有其它线程已经获取了读锁或写锁
     * 当前线程则根据cpu数量来计算自旋的次数
     * 在自旋期间为当前线程创建一个写模式的节点
     * 如果自旋结束之后还未获取到写锁时
     * 如果当前线程的节点的上一个节点是头节点
     * 那当前线程则会一直自旋尝试获取写锁
     * 因为头节点已经获取到了锁,当头节点的锁释放了之后
     * 当前线程就可以去尝试获取写锁
     * 如果当前线程的节点的上一个节点不是头节点
     * 头节点是一个加读锁的线程并头节点中的读节点链表不为空
     * 那当前节点则会将头节点中的读节点链表中的所有读节点唤醒
     * 唤醒之后当前线程则会进行线程挂起
     * @param interruptible 是否可中断
     * @param deadline  等待时间
     * @return 版本号
     */
    private long acquireWrite(boolean interruptible, long deadline) {
        //p 当前线程节点未入队时的尾节点
        //node 当前线程的节点
        WNode node = null, p;
        for (int spins = -1;;) {
            //s 锁状态
            //m 是否已经有线程加了锁 大于1是 0否
            //ns 线程加锁的版本号
            long m, s, ns;
            //使用锁状态与读写锁的标识进行与运算并校验是否有线程加了锁
            if ((m = (s = state) & ABITS) == 0L) {
                //没有线程加锁,当前线程则尝试加写锁
                if (U.compareAndSwapLong(this, STATE, s, ns = s + WBIT))
                    //加写锁成功,返回写锁的版本号
                    return ns;
            }
            //校验自旋次数是否小于0
            else if (spins < 0)
                //m == WBIT 校验加的锁是否是写锁
                //wtail == whead 队列中的头节点是否与尾节点相同
                //有线程加了写锁并且等待队列中并没有线程节点在等待或队列未被初始化则根据cpu数量来计算自旋次数
                //有线程加了写锁但是队列中有线程节点在等待,那自旋次数为0,当前线程不自旋,直接创建节点并入队进行等待
                //有线程加了读锁,那自旋次数为0,当前线程不自旋,直接创建节点并入队进行等待
                spins = (m == WBIT && wtail == whead) ? SPINS : 0;
            else if (spins > 0) {
                //进入当前判断语句说明有线程加了写锁并且队列中没有线程节点在等待
                //校验生成的随机数是否大于等于0
                if (LockSupport.nextSecondarySeed() >= 0)
                    //自旋次数减1
                    --spins;
            }
            //自旋次数结束还未获取到写锁则校验队列中的尾节点是否为空
            //如果队列中的尾节点为空则会初始化节点
            else if ((p = wtail) == null) {
                //创建一个写模式的节点
                WNode hd = new WNode(WMODE, null);
                //将创建的节点设置为头节点和尾节点
                if (U.compareAndSwapObject(this, WHEAD, null, hd))
                    wtail = hd;
            }
            else if (node == null)
                //创建一个写模式的节点,并将该节点的上一个节点的指针指向尾节点
                node = new WNode(WMODE, p);
            //校验当前节点的上一个节点是否是尾节点
            //如果不是尾节点则说明当前线程在自旋获取写锁的时候,有其它线程来尝试获取锁并将尾节点修改了
            else if (node.prev != p)
                node.prev = p;
            //将新创建的写模式的节点设置为尾节点,并将原尾节点的下一个节点的指针指向node
            else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                p.next = node;
                break;
            }
        }

        for (int spins = -1;;) {
            //h 头节点
            //np 当前线程节点的上一个节点
            WNode h, np, pp;
            //ps 当前线程节点未入队时的尾节点的状态
            int ps;
            //校验当前线程的上一个节点是否是头节点
            if ((h = whead) == p) {
                if (spins < 0)
                    //第一次循环spins肯定是小于0的
                    //根据cpu数量来计算第一次入队自旋的次数
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    //如果自旋之后还未获取到锁则将自旋的次数翻倍继续自旋
                    spins <<= 1;
                //自旋
                for (int k = spins;;) {
                    //s 锁状态
                    //ns 加写锁成功后的版本号
                    long s, ns;
                    //使用锁状态与读写锁的二进制位标识进行与运算获取到是否有线程加了锁
                    if (((s = state) & ABITS) == 0L) {
                        //没有线程加锁当前线程则尝试获取写锁
                        if (U.compareAndSwapLong(this, STATE, s, ns = s + WBIT)) {
                            //获取写锁成功则将当前线程节点设置为头节点
                            whead = node;
                            //取消与上一个节点的关联
                            node.prev = null;
                            //返回版本号
                            return ns;
                        }
                    }
                    //自旋次数自减
                    else if (LockSupport.nextSecondarySeed() >= 0 && --k <= 0)
                        break;
                }
            }
            //校验头节点是否不为空
            else if (h != null) {
                //读节点链表
                WNode c;
                //读节点线程
                Thread w;
                //校验读节点链表是否不为空
                while ((c = h.cowait) != null) {
                    //如果读节点链表不为空则说明头节点是个读节点则需要将挂载在头节点上的所有读节点都唤醒
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) && (w = c.thread) != null)
                        U.unpark(w);
                }
            }
            //校验头节点是否改变
            if (whead == h) {
                if ((np = node.prev) != p) {
                    //原尾节点与当前线程节点的上一个节点不相同
                    //说明之前或后面来了新的线程并创建了新的节点将原尾节点修改了
                    //此时就需要将原尾节点修改成当前线程节点的上一个节点
                    //当前线程修改了原尾节点,等后面线程执行的时候会发现原尾节点与线程节点的上一个节点不相同也会进行修改
                    if (np != null)
                        (p = np).next = node;
                }
                else if ((ps = p.status) == 0)
                    //将上一个节点的状态设置为等待状态
                    U.compareAndSwapInt(p, WSTATUS, 0, WAITING);
                else if (ps == CANCELLED) {
                    //如果上一个节点的状态为取消状态则需要将当前线程节点与上一个节点取消关联
                    //将当前线程的节点与上一个节点的上一个节点进行关联
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    long time;
                    if (deadline == 0L)
                        time = 0L;
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        //已经超时
                        return cancelWaiter(node, node, false);
                    //获取当前线程
                    Thread wt = Thread.currentThread();
                    //将Thread类中的阻塞对象修改为当前类
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;
                    if (p.status < 0 && (p != h || (state & ABITS) != 0L) && whead == h && node.prev == p)
                        //挂起线程
                        U.park(false, time);
                    //清除节点中的线程引用
                    node.thread = null;
                    //将Thread类中的阻塞对象置空
                    U.putObject(wt, PARKBLOCKER, null);
                    //线程是否被中断
                    if (interruptible && Thread.interrupted())
                        return cancelWaiter(node, node, true);
                }
            }
        }
    }

    /**
     * @param interruptible 是否可中断
     * @param deadline      获取锁的超时时间
     * @return
     */
    private long acquireRead(boolean interruptible, long deadline) {
        //node 当前线程所在的节点
        //p 当前线程节点的上一个节点/原尾节点
        WNode node = null, p;
        for (int spins = -1;;) {
            //头节点
            WNode h;
            //校验头节点是否与尾节点相同,如果头节点与尾节点相同则说明等待队列中没有线程节点在等待加锁
            if ((h = whead) == (p = wtail)) {
                //s 锁状态
                //m 等于0没有线程加锁, 大于0小于128有线程加了读锁,大于128有线程加了写锁
                //ns 加锁的版本号
                for (long m, s, ns;;) {
                    //先校验锁状态是否被其它线程获取了锁,大于0则说明被获取了锁
                    //再校验是否小于读锁的最大加锁次数RFULL,如果小于则说明当前线程可以尝试加读锁
                    //如果大于就分为两种情况：1.有其它线程加了写锁 2.读锁加锁的次数已到RFULL的最大次数
                    //如果有其它线程加了写锁,那么m肯定是大于WBIT的,则会执行后面的else语句
                    //如果读锁的加锁次数已到RFULL的最大次数,那么m肯定是小于WBIT的,因为WBIT的值为128
                    //而读锁的二进制标识为0111 1111,该二进制为127
                    //而写锁的二进制标识为1000 0000,该二进制为128
                    //if语句中的三元表达式中如果为true,肯定是没有线程加锁或者是加的读锁
                    //如果为false要么已经是加的写锁,要么是读锁的加锁次数已经到达了用二进制来标识读锁加锁的次数的最大值
                    if ((m = (s = state) & ABITS) < RFULL ?
                        U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) : (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L))
                        //返回加锁的版本号
                        return ns;
                    else if (m >= WBIT) {
                        //有线程加了写锁
                        if (spins > 0) {
                            //自旋
                            if (LockSupport.nextSecondarySeed() >= 0)
                                --spins;
                        }
                        else {
                            if (spins == 0) {
                                //当自旋次数耗尽
                                //nh 头节点
                                //np 尾节点
                                WNode nh = whead, np = wtail;
                                //(头节点没有变更并且尾节点也没有变更)或者(头节点不等于尾节点)则退出循环
                                if ((nh == h && np == p) || (h = nh) != (p = np))
                                    break;
                            }
                            //根据cpu数量计算自旋的次数
                            spins = SPINS;
                        }
                    }
                }
            }
            if (p == null) {
                //p为空则说明等待队列中没有节点此时就需要初始化队列节点
                //创建一个写模式的节点
                WNode hd = new WNode(WMODE, null);
                //将创建的节点设置为头和尾节点
                if (U.compareAndSwapObject(this, WHEAD, null, hd))
                    wtail = hd;
            }
            else if (node == null)
                //为当前线程创建一个读模式的节点
                //并将节点的上一个节点的指针指向原尾节点
                node = new WNode(RMODE, p);
            //校验队列中的节点是否变更
            else if (h == p || p.mode != RMODE) {
                //节点没有变更才会进入当前语句
                //再次校验一下节点是否变更
                if (node.prev != p)
                    //队列中的节点变更了,修改当前节点的上一个节点的指针并重新执行循环方法再次校验队列中的节点是否变更
                    node.prev = p;
                //队列中的节点没有变更,则将当前线程节点设置为尾节点并将原尾节点的next指针指向当前线程节点
                else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                    p.next = node;
                    break;
                }
            }
            //如果队列中的节点变更了则将当前线程节点挂载到新入队的节点上,并将新入队的节点上的被挂载的节点挂载到当前线程节点上
            /**
             *  当前节点
             *  -----        -----                              -----
             * |node| ----> | p  |                             | p  |
             * -----        -----                              -----
             *                | c1挂载在p节点上    ——————————>     |
             *             -----                              -----
             *            | c1 |                             |node|
             *            -----                              -----
             *                                                 |
             *                                               -----
             *                                              | c1 |
             *                                              -----
             * 将当前线程节点node挂载到p节点上,并将p节点上挂载的节点挂载到node节点上
             */
            else if (!U.compareAndSwapObject(p, WCOWAIT, node.cowait = p.cowait, node))
                //挂载失败则清空node节点上挂载的节点
                //此时p节点上挂载的节点不变
                node.cowait = null;
            //节点变更了并且将当前线程节点挂载到了p节点上
            else {
                for (;;) {
                    //c 挂载的节点
                    //w 挂载的节点的线程
                    //pp 上一个节点的上一个节点
                    WNode pp, c; Thread w;
                    //如果头节点中挂载着节点则将挂载的节点线程唤醒
                    if ((h = whead) != null && (c = h.cowait) != null &&
                        U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) &&
                        (w = c.thread) != null)
                        U.unpark(w);
                    //校验当前节点的上一个节点是否为头节点或上一个节点的上一个节点是头节点
                    if (h == (pp = p.prev) || h == p || pp == null) {
                        //m 是否加锁
                        //s 锁状态
                        //ns 锁的版本号
                        long m, s, ns;
                        do {
                            //校验当前是否有线程在加锁,如果m小于RFULL则说明没有线程在加锁或者加的是读锁
                            //此时就可以尝试加读锁,如果m大于等于RFULL则说明线程在加写锁或者是加读锁的次数已经溢出
                            //如果是加读锁的次数溢出则会调用tryIncReaderOverflow方法将溢出的次数记录下来
                            //如果是写锁的话,m < WBIT该条件是不会成立的
                            //如果加的是写锁的话,m是大于WBIT的
                            if ((m = (s = state) & ABITS) < RFULL ?
                                U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) :
                                (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L))
                                return ns;
                        } while (m < WBIT);
                    }
                    //有线程加了写锁或上一个节点不是头节点或上上个节点不是头节点
                    //校验节点是否变更
                    if (whead == h && p.prev == pp) {
                        long time;
                        //pp == null 说明当前节点是头节点或者说上一个节点是头节点
                        //当前加锁的线程节点排在了对头,此时重新走外面的大循环
                        //如果头节点和尾节点相同则说明队列中已经没有在等待加锁的线程节点了,当前线程就可以尝试的去加锁
                        //如果头节点和尾节点不相同,可能是在当前线程准备重新走外面的大循环的时候来了新的线程节点
                        //此时当前线程则需要重新入队进行排队获取锁
                        //h == p 上一个节点是头节点,那当前线程节点从队列中移除,走外面的大循环尝试加锁
                        //p.status > 0 上一个节点已经取消了加锁,当前线程节点可能会成为队列中的第二个节点,此时可以尝试去加锁
                        if (pp == null || h == p || p.status > 0) {
                            node = null;
                            break;
                        }
                        //校验是否设置了超时时间
                        if (deadline == 0L)
                            time = 0L;
                        //如果设置了超时时间则校验是否超时,如果超时则将当前线程节点从队列中移除
                        else if ((time = deadline - System.nanoTime()) <= 0L)
                            return cancelWaiter(node, p, false);
                        //获取当前线程
                        Thread wt = Thread.currentThread();
                        //将Thread类中的阻塞对象修改为当前类
                        U.putObject(wt, PARKBLOCKER, this);
                        node.thread = wt;
                        if ((h != pp || (state & ABITS) == WBIT) && whead == h && p.prev == pp)
                            //挂起线程
                            U.park(false, time);
                        //清除节点中的线程引用
                        node.thread = null;
                        //将Thread类中的阻塞对象置空
                        U.putObject(wt, PARKBLOCKER, null);
                        //线程是否中断
                        if (interruptible && Thread.interrupted())
                            //如果线程被中断了,则将线程节点冲队列中移除
                            return cancelWaiter(node, p, true);
                    }
                }
            }
        }

        for (int spins = -1;;) {
            //h 头节点
            //np 当前线程节点的上一个节点
            //pp 上上个节点
            WNode h, np, pp;
            //ps 上个节点的状态
            int ps;
            //校验上一个节点是否是头节点
            if ((h = whead) == p) {
                if (spins < 0)
                    //更新自旋次数
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    //已到指定的自旋次数时,还未获取到锁并且自旋的次数未到最大自旋次数则将自旋次数翻倍
                    spins <<= 1;
                //根据自旋次数来自旋
                for (int k = spins;;) {
                    //m 是否有线程加锁,锁标识
                    //s 锁状态
                    //ns 锁版本号
                    long m, s, ns;
                    //校验是否有线程加了锁,如果有线程加了锁则判断锁标识m是否小于RFULL
                    //如果锁标识m小于RFULL则说明没有线程加锁或者线程加的是读锁
                    //如果m小于RFULL,当前线程可以尝试去加锁
                    //如果锁标识m大于等于RFULL,分为两种情况：1.有线程加了写锁 2.加读锁的次数已经溢出
                    //如果溢出则会校验m是否小于WBIT,如果加的是写锁,m肯定是大于WBIT的
                    //如果是溢出的读锁那就会调用tryIncReaderOverflow方法尝试获取读锁并将溢出的次数使用新的变量来存放
                    if ((m = (s = state) & ABITS) < RFULL ?
                        U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) :
                        (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                        //加读锁成功
                        //c 挂载的节点
                        WNode c;
                        //w 挂载的节点中的线程
                        Thread w;
                        //将当前线程节点设置为头节点
                        whead = node;
                        node.prev = null;
                        //获取当前线程节点中挂载的节点并校验节点是否为空
                        while ((c = node.cowait) != null) {
                            //不为空则说明当前线程节点中挂载着线程节点
                            //依次将当前线程节点中挂载的线程节点唤醒
                            if (U.compareAndSwapObject(node, WCOWAIT, c, c.cowait) && (w = c.thread) != null)
                                U.unpark(w);
                        }
                        //返回锁的版本号
                        return ns;
                    }
                    //加读锁失败或有线程加了写锁,当前线程自旋次数-1
                    //如果当前线程自旋次数小于等于0则退出自旋
                    else if (m >= WBIT && LockSupport.nextSecondarySeed() >= 0 && --k <= 0)
                        break;
                }
            }
            //校验头节点是否不为空
            else if (h != null) {
                //挂载的节点
                WNode c;
                //挂载的节点中的线程
                Thread w;
                //如果头节点中挂载的线程节点不为空则依次将挂载的线程节点唤醒
                while ((c = h.cowait) != null) {
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) && (w = c.thread) != null)
                        U.unpark(w);
                }
            }
            //校验头节点是否改变
            if (whead == h) {
                //原尾节点与当前线程节点的上一个节点不相同
                //说明之前或后面来了新的线程并创建了新的节点将原尾节点修改了
                //此时就需要将原尾节点修改成当前线程节点的上一个节点
                //当前线程修改了原尾节点,等后面线程执行的时候会发现原尾节点与线程节点的上一个节点不相同也会进行修改
                if ((np = node.prev) != p) {
                    if (np != null)
                        (p = np).next = node;
                }
                else if ((ps = p.status) == 0)
                    //如果上一个节点的状态为0则将上一个节点的状态修改为-1
                    //节点状态为-1则说明后续有节点在等待加锁,后续p节点释放了锁之后需要唤醒后续节点
                    U.compareAndSwapInt(p, WSTATUS, 0, WAITING);
                else if (ps == CANCELLED) {
                    //如果上一个节点的状态为1则说明线程节点已经取消加锁
                    //则需要将上一个节点从队列中移除
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    //尝试加锁失败
                    long time;
                    //校验是否设置了加锁的超时时间
                    if (deadline == 0L)
                        time = 0L;
                    //如果设置了超时时间则校验是否超时
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        //超时则将当前线程节点从队列中移除
                        return cancelWaiter(node, node, false);
                    //获取当前线程
                    Thread wt = Thread.currentThread();
                    //将Thread类中的阻塞对象修改为当前类
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;
                    if (p.status < 0 && (p != h || (state & ABITS) == WBIT) && whead == h && node.prev == p)
                        //挂起线程
                        U.park(false, time);
                    //清除节点中的线程引用
                    node.thread = null;
                    //将Thread类中的阻塞对象置空
                    U.putObject(wt, PARKBLOCKER, null);
                    //线程是否中断
                    if (interruptible && Thread.interrupted())
                        //如果线程中断了则将当前线程节点从队列中清除
                        return cancelWaiter(node, node, true);
                }
            }
        }
    }

    /**
     * @param node 当前线程节点
     * @param group 节点或节点组
     * @param interrupted 是否中断
     * @return
     */
    private long cancelWaiter(WNode node, WNode group, boolean interrupted) {
        if (node != null && group != null) {
            Thread w;
            //将当前线程节点的状态设置为取消加锁状态
            node.status = CANCELLED;
            //遍历挂载着读节点的链表
            for (WNode p = group, q; (q = p.cowait) != null;) {
                //读节点是否已经取消加锁
                if (q.status == CANCELLED) {
                    //如果读节点已经取消加锁就将读节点从挂载链表中移除
                    U.compareAndSwapObject(p, WCOWAIT, q, q.cowait);
                    //继续从当前线程节点获取挂载的读节点
                    p = group;
                }
                else
                    //跳过没有被取消加锁的节点
                    p = q;
            }
            if (group == node) {
                //遍历挂载着读节点的链表并将未取消的读节点线程唤醒
                for (WNode r = group.cowait; r != null; r = r.cowait) {
                    if ((w = r.thread) != null)
                        U.unpark(w);
                }
                //从当前节点的上一个节点开始向头节点遍历
                //如果当前线程节点是尾节点的话则会唤醒队列头部中未被取消加锁的线程节点
                //如果当前线程节点不是尾节点的话则会唤醒当前线程节点后续一个未被取消加锁的线程节点
                //除了唤醒线程节点之外还会将当前线程节点和当前线程节点左右两边并连续的被取消加锁的线程节点移除
                for (WNode pred = node.prev; pred != null; ) {
                    WNode succ, pp;
                    while ((succ = node.next) == null || succ.status == CANCELLED) {
                        //当前线程节点的下一个节点为空或下一个节点已被取消加锁
                        //q 当前线程节点后面最近的一个未被取消加锁的节点/队列头部未被取消加锁的节点
                        WNode q = null;
                        //如果当前线程节点是一个尾节点则获取队列头部中未被取消加锁的节点
                        //如果当前线程节点不是一个尾节点则获取当前线程节点后续的一个未被取消加锁的节点
                        for (WNode t = wtail; t != null && t != node; t = t.prev)
                            if (t.status != CANCELLED)
                                q = t;
                        //如果succ == q则说明当前线程节点是尾节点,那就将当前线程节点的上一个节点设置为尾节点
                        //如果succ != q则说明当前线程节点不是尾节点并当前线程节点的后续节点有未被取消加锁的节点
                        //将当前线程节点的下一个节点的指针指向后续未被取消加锁的节点
                        if (succ == q || U.compareAndSwapObject(node, WNEXT, succ, succ = q)) {
                            if (succ == null && node == wtail)
                                U.compareAndSwapObject(this, WTAIL, node, pred);
                            break;
                        }
                    }
                    if (pred.next == node)
                        //将pred节点的下一个节点指针指向当前线程节点后续未被取消加锁的线程节点
                        //node可能是尾节点,那就是将pred的next指针指向null
                        U.compareAndSwapObject(pred, WNEXT, node, succ);
                    if (succ != null && (w = succ.thread) != null) {
                        succ.thread = null;
                        //如果当前线程节点不是尾节点的话则唤醒当前线程节点后续一个未被取消加锁的线程节点
                        //如果当前线程是尾节点的话则唤醒队列头部中未被取消加锁的线程节点
                        U.unpark(w);
                    }
                    if (pred.status != CANCELLED || (pp = pred.prev) == null)
                        //如果当前线程节点的上一个节点没有被取消加锁或上一个节点是头节点则退出循环
                        break;
                    node.prev = pp;
                    U.compareAndSwapObject(pp, WNEXT, pred, succ);
                    //更改指针继续向头节点遍历
                    pred = pp;
                }
            }
        }
        //头节点
        WNode h;
        while ((h = whead) != null) {
            //锁状态
            long s;
            //距离头节点最近的状态为等待状态的节点
            WNode q;
            //校验头节点的下一个节点是否为空或等待状态为取消状态
            if ((q = h.next) == null || q.status == CANCELLED) {
                //如果头节点的下一个节点为空或等待状态为取消状态
                //则从尾节点开始向头节点遍历获取距离头节点最近的节点状态为等待状态的节点
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            //校验头节点是否变更
            if (h == whead) {
                //q != null 是否有节点在等待加锁
                //h.status == 0 是否释放锁
                //((s = state) & ABITS) != WBIT 是否未加写锁
                // (s == 0L || q.mode == RMODE) 下一个在等待加锁的节点是要加的读锁
                //如果有节点在等待加锁并且头节点已经释放了锁则校验是否有新的线程加了写锁
                //为什么头节点释放了锁还要校验是否有新的线程加了写锁呢？
                //因为头节点在释放锁的时候,队列中后面在排队的线程节点没有拿到锁,而是被新进来的线程拿到了
                //此时就需要校验一下新进来的线程拿到的是不是写锁,如果不是写锁则校验一下在等待加锁的线程节点
                //是否要加的是读锁,如果是读锁的话则会让线程去获取读锁
                if (q != null && h.status == 0 && ((s = state) & ABITS) != WBIT &&  (s == 0L || q.mode == RMODE))
                    //释放头节点的锁
                    release(h);
                break;
            }
        }
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    //unsafe对象
    private static final sun.misc.Unsafe U;
    //锁状态及版本号所在的偏移量
    private static final long STATE;
    //写头节点的偏移量
    private static final long WHEAD;
    //写尾节点的偏移量
    private static final long WTAIL;
    //下一个节点变量的偏移量
    private static final long WNEXT;
    //节点状态的偏移量
    private static final long WSTATUS;
    //读节点链表的偏移量
    private static final long WCOWAIT;
    //阻塞对象的偏移量
    private static final long PARKBLOCKER;

    static {
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> k = StampedLock.class;
            Class<?> wk = WNode.class;
            STATE = U.objectFieldOffset
                (k.getDeclaredField("state"));
            WHEAD = U.objectFieldOffset
                (k.getDeclaredField("whead"));
            WTAIL = U.objectFieldOffset
                (k.getDeclaredField("wtail"));
            WSTATUS = U.objectFieldOffset
                (wk.getDeclaredField("status"));
            WNEXT = U.objectFieldOffset
                (wk.getDeclaredField("next"));
            WCOWAIT = U.objectFieldOffset
                (wk.getDeclaredField("cowait"));
            Class<?> tk = Thread.class;
            PARKBLOCKER = U.objectFieldOffset
                (tk.getDeclaredField("parkBlocker"));

        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
