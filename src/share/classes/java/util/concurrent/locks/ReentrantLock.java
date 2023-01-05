package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.Collection;


public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;
    /**
     * 公平锁和非公平锁的父类对象同步器
     */
    private final Sync sync;

    /**
     * 抽象队列同步器
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        /**
         * 抽象加锁方法
         * 根据公平锁和非公平锁来实现不同的加锁逻辑
         */
        abstract void lock();

        /**
         * 尝试再次获取锁
         * 如果其它线程已经释放了锁当前线程则尝试去获取锁
         * 如果获取失败则返回false
         * 如果锁已经被其它线程持有则校验锁的持有者的线程是否是当前线程
         * 如果是当前线程则更新state,说明当前线程多次加锁
         * @param acquires
         * @return
         */
        final boolean nonfairTryAcquire(int acquires) {
            //获取当前线程
            final Thread current = Thread.currentThread();
            //获取锁状态 0:未加锁 1:已加锁 >1:当前线程多次加锁
            int c = getState();
            //校验锁是否未被其它线程持有
            if (c == 0) {
                //锁未被其它线程持有则再次通过CAS操作修改state获取锁
                if (compareAndSetState(0, acquires)) {
                    //获取锁成功则将当前线程设置为锁的持有者
                    setExclusiveOwnerThread(current);
                    //获取锁成功则返回
                    return true;
                }
            }
            //如果锁已被其它线程持有则校验持有锁的线程是否是当前线程
            else if (current == getExclusiveOwnerThread()) {
                //加锁的是当前线程则重入加锁
                //获取加锁次数
                int nextc = c + acquires;
                //校验加锁次数是否小于0
                if (nextc < 0)
                    //如果小于0则说明加锁次数过多已超出int的最大值
                    throw new Error("Maximum lock count exceeded");
                //更新锁状态
                setState(nextc);
                //重入锁成功则返回true
                return true;
            }
            //如果再次尝试加锁失败并且已经持有锁的线程不是当前线程则返回false
            return false;
        }

        /**
         * 尝试释放锁
         * 只有持有锁的线程是当前线程才能锁放锁
         * 如果一个线程多次加锁则只会释放一次锁
         * @param releases
         * @return
         */
        protected final boolean tryRelease(int releases) {
            //通过getState获取加锁次数
            //使用加锁次数减去1,得到线程剩余加锁次数
            int c = getState() - releases;
            //校验加锁的线程是否是当前线程
            if (Thread.currentThread() != getExclusiveOwnerThread())
                //不是当前线程则抛出异常
                throw new IllegalMonitorStateException();
            //锁是否是空闲的
            boolean free = false;
            if (c == 0) {
                //如果剩余加锁次数为0则说明锁已经被完成释放
                //锁是空闲的
                free = true;
                //将锁的持有者线程置为空
                setExclusiveOwnerThread(null);
            }
            //更新锁的状态
            setState(c);
            return free;
        }

        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        // Methods relayed from outer class

        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
                throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

    /**
     * 非公平锁
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;

        final void lock() {
            //通过CAS操作将state的值0修改成1
            //如果修改成功则说明加锁成功
            if (compareAndSetState(0, 1))
                //加锁成功则将当前线程设置成锁的持有者线程
                setExclusiveOwnerThread(Thread.currentThread());
            else
                //如果加锁失败或者是重入锁则走acquire方法再次获取锁
                //如果是重入锁则对state的值加1
                //如果不是重入锁则再次尝试获取锁
                //如果再次获取锁失败则将当前线程放入等待队列中并挂起
                //等待锁的持有的线程释放锁并唤醒等待队列中的头节点获取锁
                acquire(1);
        }

        /**
         * 再次尝试获取锁
         * @param acquires
         * @return
         */
        protected final boolean tryAcquire(int acquires) {
            //尝试再次获取锁
            //如果其它线程已经释放了锁当前线程则尝试去获取锁
            //如果获取失败则返回false
            //如果锁已经被其它线程持有则校验锁的持有者的线程是否是当前线程
            //如果是当前线程则更新state,说明当前线程多次加锁
            return nonfairTryAcquire(acquires);
        }
    }

    /**
     * 公平锁
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;

        final void lock() {
            //尝试获取锁
            //如果获取锁失败则将线程挂起并放入等待队列中
            //等待其它线程释放锁并唤醒该线程
            //公平锁加锁与非公平锁加锁主要的区别就是非公平锁在调用lock方法的时候会直接尝试获取锁
            //不管等待队列中是否有线程在等待
            //公平锁不会一上来直接获取锁而是会先查看等待队列中是否有线程在等待获取锁
            //如果有并且不是当前线程则会将自己入队列进行等待
            //直到前面的线程都获取完锁之后才会获取锁
            acquire(1);
        }

        /**
         * 尝试获取锁
         */
        protected final boolean tryAcquire(int acquires) {
            //获取当前线程
            final Thread current = Thread.currentThread();
            //获取锁状态
            int c = getState();
            if (c == 0) {
                //如果锁状态为0则锁是空闲状态
                //如果锁是空闲状态则调用hasQueuedPredecessors方法查看等待队列中是否有在等待获取锁的线程
                //如果没有在等待获取锁的线程则当前线程调用compareAndSetState方法获取锁
                //如果有在等待获取锁的线程则会比较一下下一个获取锁的线程是否是当前线程
                //如果是当前线程则会调用compareAndSetState方法获取锁
                //如果不是当前线程则不会去获取锁
                if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
                    //将锁的持有者线程设置为当前线程
                    setExclusiveOwnerThread(current);
                    //加锁成功
                    return true;
                }
                //锁状态不为0则校验锁的持有者线程是否是当前线程
            } else if (current == getExclusiveOwnerThread()) {
                //如果锁的持有者线程是当前线程则加锁的次数加1
                int nextc = c + acquires;
                if (nextc < 0)
                    //校验加锁的次数是否超出int的最大值
                    //如果超出最大值则抛出异常
                    throw new Error("Maximum lock count exceeded");
                //更新锁的状态
                setState(nextc);
                //加锁成功
                return true;
            }
            //加锁失败
            return false;
        }
    }

    /**
     * 创建非公平的锁
     */
    public ReentrantLock() {
        sync = new NonfairSync();
    }

    /**
     * 创建锁
     * 根据参数fair来确定创建的锁类型
     * true 创建公平锁
     * false 创建非公平锁
     *
     * @param fair
     */
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    /**
     * 获取锁
     */
    public void lock() {
        sync.lock();
    }

    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }


    /**
     * 尝试获取锁
     * @return
     */
    public boolean tryLock() {
        //尝试获取非公平锁
        return sync.nonfairTryAcquire(1);
    }

    /**
     * 在指定的时间内获取锁
     * 如果获取锁失败则返回false
     * @param timeout 超时时间
     * @param unit 时间单位
     */
    public boolean tryLock(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    /**
     * 释放锁
     * 当前线程是锁的持有者线程才能释放锁
     */
    public void unlock() {
        sync.release(1);
    }

    /**
     * 创建一个Condition对象
     * 该对象可以实现等待和唤醒
     * 调用该对象的await方法会一次性将锁释放掉
     * 不管当前线程加了多少次锁都会全释放掉
     * 其实await方法就是将线程挂起释放锁资源
     * 调用signal方法可以将线程唤醒重新尝试获取锁
     * @return
     */
    public Condition newCondition() {
        return sync.newCondition();
    }


    public int getHoldCount() {
        return sync.getHoldCount();
    }


    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }


    public boolean isLocked() {
        return sync.isLocked();
    }


    public final boolean isFair() {
        return sync instanceof FairSync;
    }


    protected Thread getOwner() {
        return sync.getOwner();
    }


    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }


    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }


    public final int getQueueLength() {
        return sync.getQueueLength();
    }


    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }


    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject) condition);
    }


    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject) condition);
    }


    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject) condition);
    }


    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ?
                "[Unlocked]" :
                "[Locked by thread " + o.getName() + "]");
    }
}
