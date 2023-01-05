package java.util.concurrent;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * CountDownLatch是一个信号同步器,底层是通过AQS来实现的
 * 在创建CountDownLatch的时候传入一个计数值
 * 当线程调用await的时候,如果计数值不是0的时候则会等待其它线程执行countDown方法对计数值减1
 * 只有当指定的计数值的线程执行了countDown方法后,调通await方法的线程才能继续执行
 *
 * await：当线程调用await方法的时候会先校验一下计数值是否为0,如果为0则返回执行后续的代码
 *        如果不为0,则需要为当前线程创建一个共享模式的节点,并将节点添加到等待队列中
 *        只有当最后一个线程执行完了countDown方法后会将等待队列中的头节点的后一个节点唤醒
 *        当当前线程被唤醒之后则会将当前线程节点设置为头节点,当前线程也会去唤醒等待队列中的线程节点
 *        可能会有多个线程调用了await方法进入到了等待队列中,当前线程执行完之后则会返回执行后续代码
 *
 * countDown：当一个线程执行countDown方法时候会将计数值减1,执行会之后则会校验计数值是否为0
 *            如果计数值不为0则直接返回,如果计数值为0的时候则会唤醒等待队列中头节点的后一个线程节点
 *
 * 计数值其实就是AQS中的state,也可以理解为加锁次数,当一个线程执行countDown方法时则会释放一次读锁
 * 当所有加读锁的线程都释放了读锁之后,调用await方法的线程才能获取写锁并执行后续的代码
 */
public class CountDownLatch {

    private static final class Sync extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = 4982264981922014374L;

        Sync(int count) {
            //设置初始的状态值
            setState(count);
        }

        //获取状态值
        int getCount() {
            return getState();
        }

        /**
         * 校验状态值是否为0
         * 值为0则说明所有的线程都执行完了countDown方法
         * 值不为0则说明部分线程没有执行countDown方法
         * 此时当前线程则需要等待部分线程执行完countDown方法才能继续执行
         * @param acquires
         * @return
         */
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        protected boolean tryReleaseShared(int releases) {
            for (;;) {
                //获取当前状态值
                int c = getState();
                if (c == 0)
                    return false;
                int nextc = c-1;
                //修改state的值
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }
    }

    /**
     * 同步器
     */
    private final Sync sync;

    /**
     * 创建一个指定值的闩锁
     * @param count
     */
    public CountDownLatch(int count) {
        if (count < 0)
            //如果指定值小于0则抛出异常
            throw new IllegalArgumentException("count < 0");
        //创建一个指定值的同步器
        this.sync = new Sync(count);
    }

    /**
     * 等待其它线程执行完countDown方法
     * @throws InterruptedException
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * 等待其它线程执行完countDown方法
     * 如果超出指定时间则直接返回
     * @throws InterruptedException
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    public void countDown() {
        sync.releaseShared(1);
    }

    public long getCount() {
        return sync.getCount();
    }

    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }
}
