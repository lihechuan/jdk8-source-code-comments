package java.util.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CyclicBarrier {

    private static class Generation {
        boolean broken = false;
    }

    /** 该锁用于防止共享变量被多线程修改 */
    private final ReentrantLock lock = new ReentrantLock();
    /** Condition to wait on until tripped */
    private final Condition trip = lock.newCondition();
    /** 指定数量的线程等待方 */
    private final int parties;
    /** 执行特定的操作的线程方法 */
    private final Runnable barrierCommand;
    /** 当前屏障的标识 */
    private Generation generation = new Generation();

    /**
     * 剩余数量的线程等待方
     * 线程每次等待的时候都会进行减1
     * 当count等于0的时候会执行特定的操作
     */
    private int count;

    private void nextGeneration() {
        //唤醒所有等待的线程
        trip.signalAll();
        //重新设置剩余数量的线程等待方
        count = parties;
        //重新创建屏障标识
        generation = new Generation();
    }

    /**
     * 修改当前屏障的标识,说明当前执行的屏障已经结束
     * 并重新设置指定数量的线程等待方并唤醒所有等待的线程
     */
    private void breakBarrier() {
        //修改屏障标识
        generation.broken = true;
        //重新设置剩余数量的线程等待方
        count = parties;
        //唤醒所有等待的线程
        trip.signalAll();
    }

    private int dowait(boolean timed, long nanos) throws InterruptedException, BrokenBarrierException, TimeoutException {
        //获取重入锁
        final ReentrantLock lock = this.lock;
        //加锁
        lock.lock();
        try {
            //获取当前屏障的标识
            final Generation g = generation;
            if (g.broken)
                //当前屏障的标识已被修改
                throw new BrokenBarrierException();
            if (Thread.interrupted()) {
                //当前线程已被中断则需要将在等待的线程唤醒
                //并将屏障标识更改并重新设置指定数量的线程等待方
                breakBarrier();
                throw new InterruptedException();
            }
            //剩余等待线程数量减1
            int index = --count;
            //校验剩余等待线程的数量是否已经为0
            if (index == 0) {
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    if (command != null)
                        //剩余等待的线程数量已经为0则需要执行指定的线程操作方法
                        command.run();
                    ranAction = true;
                    //唤醒所有等待的线程并重新设置屏障标识
                    //等待下一次执行
                    nextGeneration();
                    return 0;
                } finally {
                    if (!ranAction)
                        breakBarrier();
                }
            }
            for (;;) {
                try {
                    //校验是否设置了超时时间
                    if (!timed)
                        //未设置超时时间,当前线程则进行等待
                        trip.await();
                    else if (nanos > 0L)
                        //设置了超时时间,当前线程则等待指定的时间
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        Thread.currentThread().interrupt();
                    }
                }
                if (g.broken)
                    throw new BrokenBarrierException();
                if (g != generation)
                    return index;
                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 创建一个指定数量的线程等待方的CyclicBarrier对象
     * 当到达指定的数量的线程等待时执行特定的操作
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0)
            throw new IllegalArgumentException();
        //指定数量的线程等待方
        this.parties = parties;
        //剩余数量的线程等待方
        this.count = parties;
        //到达指定的数量的线程等待时执行的特定的操作的线程方法
        this.barrierCommand = barrierAction;
    }

    /**
     * 创建一个指定数量的线程等待方的CyclicBarrier对象
     * 当到达指定数量的线程等待时并不执行特定的操作
     */
    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    /**
     * Returns the number of parties required to trip this barrier.
     *
     * @return the number of parties required to trip this barrier
     */
    public int getParties() {
        return parties;
    }

    /**
     * 线程执行等待
     * @return
     * @throws InterruptedException
     * @throws BrokenBarrierException
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe);
        }
    }

    /**
     * 线程执行等待指定的时间
     * @param timeout 等待的时间
     * @param unit 等待的时间单位
     * @return
     * @throws InterruptedException
     * @throws BrokenBarrierException
     * @throws TimeoutException
     */
    public int await(long timeout, TimeUnit unit) throws InterruptedException, BrokenBarrierException, TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    /**
     * Queries if this barrier is in a broken state.
     *
     * @return {@code true} if one or more parties broke out of this
     *         barrier due to interruption or timeout since
     *         construction or the last reset, or a barrier action
     *         failed due to an exception; {@code false} otherwise.
     */
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the barrier to its initial state.  If any parties are
     * currently waiting at the barrier, they will return with a
     * {@link BrokenBarrierException}. Note that resets <em>after</em>
     * a breakage has occurred for other reasons can be complicated to
     * carry out; threads need to re-synchronize in some other way,
     * and choose one to perform the reset.  It may be preferable to
     * instead create a new barrier for subsequent use.
     */
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of parties currently waiting at the barrier.
     * This method is primarily useful for debugging and assertions.
     *
     * @return the number of parties currently blocked in {@link #await}
     */
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}
