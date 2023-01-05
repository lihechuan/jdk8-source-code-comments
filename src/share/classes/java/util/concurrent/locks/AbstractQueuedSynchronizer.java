package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import sun.misc.Unsafe;


public abstract class AbstractQueuedSynchronizer
    extends AbstractOwnableSynchronizer
    implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() { }


    static final class Node {
        /** 共享标记 */
        static final Node SHARED = new Node();
        /** 独占标记 */
        static final Node EXCLUSIVE = null;
        /** 当前需要取消加锁 */
        static final int CANCELLED =  1;
        /** 当前节点的后一个节点需要解决挂起 */
        static final int SIGNAL    = -1;
        /** 当前线程正在等待 */
        static final int CONDITION = -2;
        /** 共享锁传播 */
        static final int PROPAGATE = -3;

        /**
         * 节点的等待状态
         */
        volatile int waitStatus;

        /**
         *  当前节点的上一个节点
         */
        volatile Node prev;

        /**
         *  当前节点的下一个节点
         */
        volatile Node next;

        /**
         * 当前节点对应的线程
         */
        volatile Thread thread;


        Node nextWaiter;

        /**
         * 校验线程节点是否是共享模式
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 获取当前节点的上一个节点
         * @return
         * @throws NullPointerException
         */
        final Node predecessor() throws NullPointerException {
            //当前节点的上一个节点
            Node p = prev;
            if (p == null)
                //节点为空则抛出空指针异常
                throw new NullPointerException();
            else
                //返回上一个节点
                return p;
        }

        Node() {
        }

        Node(Thread thread, Node mode) {
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) {
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * 头节点
     */
    private transient volatile Node head;

    /**
     * 尾节点
     */
    private transient volatile Node tail;

    /**
     * 当前锁的状态
     * 0:未加锁
     * 1:已加锁
     * >1:一个线程多次加锁(重入锁)
     */
    private volatile int state;

    /**
     * 获取锁状态
     * @return
     */
    protected final int getState() {
        return state;
    }

    /**
     * 设置锁状态
     * @return
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * 执行CAS操作将state修改成指定的值
     * @param expect 预期state的值
     * @param update 待修改的state值
     * @return
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // 根据state的偏移量从当前this对象中获取state的值并校验state的值是否与预期的值相同
        // 如果与预期的值相同则将state的值修改成新的值
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices
     * to improve responsiveness with very short timeouts.
     */
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * 等待队列中没有节点则先创建头节点
     * 再将当前线程所在节点设置为尾节点
     * 并将头节点和尾节点进行指针关联
     * @param node
     * @return
     */
    private Node enq(final Node node) {
        for (;;) {
            //获取尾节点
            Node t = tail;
            if (t == null) {
                //通过CAS操作修改头节点
                if (compareAndSetHead(new Node()))
                    //将尾节点的指针指向头节点
                    tail = head;
            } else {
                //将当前线程的节点的上一个节点的指针指向尾节点
                node.prev = t;
                //通过CAS操作将当前线程的节点设置为尾节点
                if (compareAndSetTail(t, node)) {
                    //将头节点的下一个节点的指针指向当前线程的节点
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * 为当前线程创建给定模式的节点并入队
     * @param mode Node.EXCLUSIVE 独占模式, Node.SHARED 共享模式
     * @return the new node
     */
    private Node addWaiter(Node mode) {
        //使用当前线程与指定的模式创建一个新的节点
        Node node = new Node(Thread.currentThread(), mode);
        //获取尾节点
        Node pred = tail;
        if (pred != null) {
            //如果尾节点不为空则将新创建的节点的上一个节点的指针指向尾节点
            node.prev = pred;
            //通过CAS操作将新创建的节点设置为尾节点
            if (compareAndSetTail(pred, node)) {
                //新节点设置为了尾节点则将原尾节点的下一个节点的指针指向新尾节点
                pred.next = node;
                //返回新创建的节点
                return node;
            }
        }
        //等待队列中没有节点
        //则通过enq方法创建头节点并将当前线程所在的节点设置为尾节点
        enq(node);
        return node;
    }

    /**
     * 将node设置为头节点并使原头节点出队
     */
    private void setHead(Node node) {
        //将当前线程所在的节点设置为头节点
        head = node;
        //将节点中的线程置为空
        node.thread = null;
        //将节点中的上一个节点指针置为空
        node.prev = null;
    }

    /**
     * 唤醒节点的后续节点
     * 前提是后续节点存在
     * 如果后续节点已被取消加锁则继续获取后续节点直到节点未被取消加锁
     */
    private void unparkSuccessor(Node node) {
        //获取当前节点的等待状态
        int ws = node.waitStatus;
        if (ws < 0)
            //将当前节点的等待状态修改为0
            compareAndSetWaitStatus(node, ws, 0);

        //获取下一个节点
        Node s = node.next;
        //校验下一个节点是否为空或者等待状态为1
        //如果等待状态为1则说明线程已取消加锁
        //则从尾节点向当前节点所在的位置开始遍历
        //获取当前节点的后续节点并且该节点的未被取消加锁
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        if (s != null)
            //唤醒下一个节点
            LockSupport.unpark(s.thread);
    }

    /**
     * 唤醒后继节点
     */
    private void doReleaseShared() {
        for (;;) {
            //头节点
            Node h = head;
            //校验头节点是否不为空并且头节点不等于尾节点
            if (h != null && h != tail) {
                //获取头节点的等待状态
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    //如果头节点的等待状态为SIGNAL则将等待状态修改为0
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;
                    //状态修改成功则唤醒下一个节点中的线程
                    unparkSuccessor(h);
                } else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;
            }
            if (h == head)
                break;
        }
    }

    /**
     * 更新头节点 并且检查后继节点是否在等待
     * 在共享模式下等待则唤醒节点线程
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        //获取头节点
        Node h = head;
        //将当前获取到锁的节点设置为头节点
        setHead(node);
        if (propagate > 0 || h == null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0) {
            //获取当前节点的下一个节点
            Node s = node.next;
            if (s == null || s.isShared())
                //唤醒后继节点
                doReleaseShared();
        }
    }


    /**
     * 将出现异常的节点取消获取锁
     * 如果出现异常的节点的前一个节点是头节点
     * 则唤醒异常节点的后续的一个节点
     * 唤醒的后续节点的等待状态必须是SIGNAL
     * 如果后续的节点不是SIGNAL则继续获取后续节点
     * 直到后续节点为SIGNAL
     */
    private void cancelAcquire(Node node) {
        if (node == null)
            return;
        //将节点中的线程置空
        node.thread = null;
        //获取上一个节点
        Node pred = node.prev;
        //如果节点中的等待状态大于0则说明已经被取消加锁
        //则获取已被取消加锁的节点的上一个节点
        //直到上一个节点未被取消加锁
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;
        //上一个节点的下一个节点
        //如果等待状态大于0该节点则是与node节点相关联并连续被取消加锁的节点的最靠前的一个节点
        //如果等待状态不大于0该节点则是当前node节点
        Node predNext = pred.next;
        //将当前节点的等待状态设置为取消加锁状态
        node.waitStatus = Node.CANCELLED;
        //如果当前节点是尾节点则通过CAS操作将尾节点的上一个节点设置为尾节点
        if (node == tail && compareAndSetTail(node, pred)) {
            //将新的尾节点与原尾节点取消关联
            compareAndSetNext(pred, predNext, null);
        } else {
            //当前节点不是尾节点或通过CAS操作将节点设置成尾节点失败
            //上一个节点的等待状态
            int ws;
            //校验pred节点是否是头节点并且pred节点的等待状态为-1(pred状态小于0并且CAS操作成功修改等待状态为-1)
            if (pred != head &&
                ((ws = pred.waitStatus) == Node.SIGNAL ||
                 (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                pred.thread != null) {
                //条件成立获取当前节点的下一个节点
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    /**        1                    2                     3                        4
                     *      +------+     prev    +-----+      prev    +--------+      prev      +-----+
                     * head |      |    <----   | pred |     <----   |  node   |     <----     | next |  tail
                     *      |SIGNAL|    ---->   |SIGNAL|             |CANCELLED|               |SIGNAL|
                     *      +------+    next    +-----+              +--------+                +-----+ <---
                     *                             |                                                      |
                     *                             |                                                      |
                     *                             ——————————————————————next——————————————————————-——————
                     */
                    //下一个节点不为null并且等待状态小于等于0
                    //则通过CAS操作将当前节点的上一个节点的next指针指向当前节点的下一个节点
                    //从上面的图可以看出如果当前节点3为取消加锁状态则会将节点2的next指针指向节点4
                    //在节点2获取到锁并释放锁的时候则会唤醒节点4
                    //在acquireQueued方法中节点4要获取锁的时候只有节点4的上一个节点为头节点才能获取锁
                    //此时节点4的上一个节点3并不是头节点
                    //此时会获取锁失败并调用shouldParkAfterFailedAcquire方法校验上一个节点的等待状态
                    //此时会发现节点3的等待状态为1则会走到do while中将节点4的prev指针指向节点
                    //此时会继续校验节点4的上一个节点是否是头节点,此时节点2则是头节点
                    //节点4则会获取锁成功,则会将节点4设置为头节点并取消与前面节点的关联
                    compareAndSetNext(pred, predNext, next);
            } else {
                //pred节点为头节点或等待状态不为-1
                unparkSuccessor(node);
            }
            //将当前节点的next指针指向自己
            node.next = node;
        }
    }

    /**
     * 如果当前线程所在的节点的上一个节点的等待状态为0
     * 则需要将上一个节点的等待状态设置为-1
     * 设置为-1说明当前线程需要在后续解除线程挂起状态
     * 如果上一个节点的状态大于0,从当前类中的常量来看只有1是大于0的
     * 如果大于0则说明该节点已被取消加锁并从等待队列中将节点删除
     * @param pred 当前节点的上一个节点
     * @param node 当前节点
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        //获取上一个节点的等待状态 默认为0
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL)
            //上一个节点的等待状态为-1则说明当前线程后续需要解除挂起
            return true;
        if (ws > 0) {
            //上一个节点的等待状态大于0则说明上一个节点线程已被取消加锁
            //则需要从等待队列中将节点删除
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            //上一个节点的等待状态为0则需要通过CAS操作将上一个节点的等待状态更新为-1
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * 挂起当前线程并返回当前线程是否被中断
     */
    private final boolean parkAndCheckInterrupt() {
        //挂起当前线程
        LockSupport.park(this);
        //是否被中断
        return Thread.interrupted();
    }

    /**
     * 将线程挂起并等待其它线程释放锁的时候唤醒
     * 并再次尝试获取锁,如果获取锁失败则会继挂起线程
     * @param node 当前线程的节点
     * @param arg the acquire argument
     */
    final boolean acquireQueued(final Node node, int arg) {
        //是否出现故障
        boolean failed = true;
        try {
            //默认当前线程未被打断
            boolean interrupted = false;
            for (;;) {
                //获取当前线程的节点的上一个节点
                final Node p = node.predecessor();
                //校验当前线程的节点的上一个节点是否是头节点
                //如果是头节点则说明当前线程在等待队列中是最靠前的一个线程节点
                //则调用tryAcquire方法再次尝试获取锁
                if (p == head && tryAcquire(arg)) {
                    //尝试获取锁成功将node设置为头节点
                    //并使原头节点出队并将node中的线程置为null
                    setHead(node);
                    //与原头节点取消关联
                    p.next = null;
                    failed = false;
                    return interrupted;
                }
                //当前线程所在的节点不是头节点
                //或当前所在的节点是头节点但是尝试加锁失败,被其它线程获取到了锁
                //为什么是头节点的时候还是会加锁失败被其它线程获取到锁？
                //是因为在非公平锁中不是说在头节点中的线程是百分百加锁成功的
                //当头节点准备去获取锁的时候,此时来了一个新线程,该线程不会进入等待队列中而是直接获取锁
                //获取锁失败的时候才会进入等待队列中,如果这个新的线程直接获取锁就可能被这个线程拿到锁
                //头节点的线程就会获取锁失败
                //shouldParkAfterFailedAcquire方法如果返回true则说明当前线程后续需要解除挂起状态
                //此时则会调用parkAndCheckInterrupt方法将当前线程挂起,等待后续解除挂起状态
                //shouldParkAfterFailedAcquire方法如果返回false则说明将上一个节点的等待状态设置为了-1
                //或者说上一个节点的线程已被取消加锁并已从等待队列中删除该节点
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                    //线程已被中断
                    interrupted = true;
            }
        } finally {
            if (failed)
                //只有出现异常的情况下才会执行该方法
                //当出现异常则会将异常的节点取消加锁
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
        throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 尝试获取锁,如果获取锁失败则将线程挂起指定时间
     * 当超过指定时间则会自动唤醒当前线程再次重新尝试获取锁
     * 如果获取锁失败则直接返回false
     * @param arg 加锁次数1
     * @param nanosTimeout 等待时间
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L)
            //如果等待时间小于等于0则返回false
            return false;
        //获取锁的截至时间
        final long deadline = System.nanoTime() + nanosTimeout;
        //为当前线程创建独占模式的节点并入队
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                //获取上一个节点
                final Node p = node.predecessor();
                //如果上一个节点是头节点则会尝试获取锁
                if (p == head && tryAcquire(arg)) {
                    //将当前线程所在的节点设置为头节点
                    setHead(node);
                    //取消原头节点的关联
                    p.next = null;
                    failed = false;
                    return true;
                }
                //获取剩余时间
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    //小于等于0则说明获取锁超时则返回false不再尝试获取锁
                    return false;
                //校验当前线程是否需要挂起并且剩余时间是否大于1000
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold)
                    //如果线程需要挂起并且剩余时间大于1000则将当前线程挂起指定时间等待唤醒
                    //如果超出指定时间当前线程则自动唤醒尝试获取锁
                    //获取锁失败则不再尝试获取锁
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    //线程已被中断
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                //出现异常的时候取消加锁
                cancelAcquire(node);
        }
    }

    private void doAcquireShared(int arg) {
        //为当前线程创建共享模式的节点
        final Node node = addWaiter(Node.SHARED);
        //是否执行失败
        boolean failed = true;
        try {
            //是否被中断
            boolean interrupted = false;
            for (;;) {
                //获取当前节点的上一个节点
                final Node p = node.predecessor();
                if (p == head) {
                    //如果上一个节点是头节点则尝试获取读锁
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        //r大于0则说明获取锁成功
                        //更新头节点并唤醒后继节点为共享模式的节点
                        setHeadAndPropagate(node, r);
                        p.next = null;
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                //修改节点中的等待状态并将线程挂起
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                //出现异常的时候取消加锁
                cancelAcquire(node);
        }
    }

    private void doAcquireSharedInterruptibly(int arg) throws InterruptedException {
        //为当前线程创建一个共享模式的节点
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                //获取当前线程的上一个节点
                final Node p = node.predecessor();
                if (p == head) {
                    //如果上一个节点是头节点则尝试获取读锁
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        //r大于0则说明获取锁成功
                        //更新头节点并唤醒后继节点为共享模式的节点
                        setHeadAndPropagate(node, r);
                        p.next = null;
                        failed = false;
                        return;
                    }
                }
                //修改节点中的等待状态并将线程挂起
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                //出现异常的时候取消加锁
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    // Main exported methods

    /**
     * Attempts to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread. This can be used
     * to implement method {@link Lock#tryLock()}.
     *
     * <p>The default
     * implementation throws {@link UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return {@code true} if successful. Upon success, this object has
     *         been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in exclusive
     * mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this object is now in a fully released
     *         state, so that any waiting threads may attempt to acquire;
     *         and {@code false} otherwise.
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread.
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return a negative value on failure; zero if acquisition in shared
     *         mode succeeded but no subsequent shared-mode acquire can
     *         succeed; and a positive value if acquisition in shared
     *         mode succeeded and subsequent shared-mode acquires might
     *         also succeed, in which case a subsequent waiting thread
     *         must check availability. (Support for three different
     *         return values enables this method to be used in contexts
     *         where acquires only sometimes act exclusively.)  Upon
     *         success, this object has been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in shared mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this release of shared mode may permit a
     *         waiting acquire (shared or exclusive) to succeed; and
     *         {@code false} otherwise
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code true} if synchronization is held exclusively with
     * respect to the current (calling) thread.  This method is invoked
     * upon each call to a non-waiting {@link ConditionObject} method.
     * (Waiting methods instead invoke {@link #release}.)
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}. This method is invoked
     * internally only within {@link ConditionObject} methods, so need
     * not be defined if conditions are not used.
     *
     * @return {@code true} if synchronization is held exclusively;
     *         {@code false} otherwise
     * @throws UnsupportedOperationException if conditions are not supported
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试获取锁
     * 如果获取失败则将当前线程挂起并放入等待队列中
     * 等待锁的持有者释放锁并唤醒队列中的头节点获取锁
     * @param arg
     */
    public final void acquire(int arg) {
        /**
         * tryAcquire 根据调用方自己实现的方法来重试获取锁
         * 在ReentrantLock中的非公平锁中返回false则说明再次尝试获取锁失败
         * 返回true分为两种情况:
         * 1.其它线程释放了锁,当前线程尝试获取锁成功
         * 2.锁已经被线程持有并持有的线程是当前线程则state加1,说明当前线程多次加锁
         *
         * 在ReentrantLock中的公平锁中返回false则说明尝试获取锁失败
         * 返回true分为两种情况:
         * 1.
         *  1.1等待队列中没有在等待获取锁的线程,当前线程直接获取锁成功
         *  1.2等待队列中有在等待获取锁的线程并且等待队列中下一个获取锁的线程就是当前线程
         * 2.锁已经被线程持有并持有的线程是当前线程则state加1,说明当前线程多次加锁
         *
         * 在ReentrantReadWriteLock中的返回的几种情况：
         * false
         * 1.有线程已经获取到了读锁,此时就不能加写锁,则需要入队等待
         * 2.有线程已经获取到了写锁,此时就不能加写锁,则需要入队等待
         * 3.锁状态为空闲状态,此时是公平锁,队列中有线程节点在等待,当前线程则需要入队等待
         *
         * true
         * 1.有线程加了写锁并且加锁的线程是当前线程则重入加锁
         * 2.锁状态为空闲状态,此时是公平锁并且队列中没有线程节点在等待则会尝试获取锁
         * 3.锁状态为空闲状态,此时是非公平锁,当前线程会尝试加锁
         *
         * 在tryAcquire方法中尝试加锁失败时
         * 则会调用addWaiter方法为当前线程创建一个独占模式的节点并将该节点添加到等待队列中
         * 如果等待队列中为空则先创建一个头节点并将当前线程的节点设置为尾节点
         *
         */
        if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

    /**
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once {@link #tryAcquire}, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquire}
     * until success or the thread is interrupted.  This method can be
     * used to implement method {@link Lock#lockInterruptibly}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            doAcquireInterruptibly(arg);
    }


    /**
     * 在指定时间内尝试获取锁
     * @param arg
     * @param nanosTimeout
     * @return
     * @throws InterruptedException
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted())
            //如果当前线程已被中断则返回中断异常
            throw new InterruptedException();
        /**
         * tryAcquire 尝试获取锁
         * 非公平锁中如果其它线程释放了锁则会直接获取锁
         * 不会加入到等待队列中等待前面的线程获取完锁再获取锁
         * 只有在获取锁失败的时候才会加入到等待队列中
         * 而公平锁上来不会直接获取锁
         * 而是先查看等待队列中是否有线程在等待获取锁
         * 如果有并且下一个获取锁的线程不是当前线程则会将当前线程添加到等待队列中依次获取锁
         * 如果等待队列中没有等待的线程,当前线程则会尝试获取锁
         *
         * 调用tryAcquire方法尝试获取锁失败则会调用doAcquireNanos方法将线程挂起指定时间
         * 到达指定时候则自动唤醒线程尝试获取锁
         * 如果获取锁失败则不再获取锁
         */
        return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 尝试释放锁
     * 如果释放锁成功并且锁的状态是空闲的
     * 则会唤醒等待队列中当前节点的后续节点
     * 如果后续节点的等待状态为1则会继续获取后续节点
     * 直到后续节点为-1才进行唤醒
     */
    public final boolean release(int arg) {
        /**
         * tryRelease 尝试释放锁,由调用方实现具体的逻辑
         * 只有锁的持有者线程是当前线程才能释放锁
         */
        if (tryRelease(arg)) {
            //获取头节点
            Node h = head;
            //校验头节点是否为空并且头节点的等待状态是否不等于0
            if (h != null && h.waitStatus != 0)
                //如果头节点不为空并且头节点的等待状态不等于0则说明后续节点需要加锁
                //调用unparkSuccessor方法唤醒下一个节点
                //如果下一个节点中的等待状态为-1才进行唤醒
                //如果等待状态为1则不会进行唤醒
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    /**
     * 获取共享锁
     * @param arg
     */
    public final void acquireShared(int arg) {
        //1 加锁成功  -1 加锁失败
        if (tryAcquireShared(arg) < 0)
            //为当前线程创建共享模式下的节点并入队
            //等待前面的线程获取了锁并释放锁之后当前线程再去尝试获取锁
            doAcquireShared(arg);
    }

    public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted())
            //如果当前线程中断则抛出异常
            throw new InterruptedException();
        //校验是否加锁成功 1 加锁成功  -1 加锁失败
        if (tryAcquireShared(arg) < 0)
            //为当前线程创建共享模式下的节点并入队
            //等待前面的线程获取了锁并释放锁之后当前线程再去尝试获取锁
            doAcquireSharedInterruptibly(arg);
    }

    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 || doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * 释放读锁
     * @param arg
     * @return
     */
    public final boolean releaseShared(int arg) {
        //校验当前线程加的读锁是否都释放了
        if (tryReleaseShared(arg)) {
            //如果当前线程加的读锁都释放了则唤醒后续节点中的线程
            doReleaseShared();
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a {@code true} return does not guarantee that any
     * other thread will ever acquire.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there may be other threads waiting to acquire
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is if an acquire method has ever blocked.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there has ever been contention
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * Returns the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued.
     *
     * <p>In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     *         {@code null} if no threads are currently queued
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null) ||
            ((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null))
            return st;

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * Returns true if the given thread is currently queued.
     *
     * <p>This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is on the queue
     * @throws NullPointerException if the thread is null
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    final boolean apparentlyFirstQueuedIsExclusive() {
        //h 头节点
        //s 头节点的下一个节点
        Node h, s;
        //节点是否是在独占模式下等待
        return (h = head) != null &&
            (s = h.next)  != null &&
            !s.isShared()         &&
            s.thread != null;
    }

    /**
     * 查看等待队列中是否还有线程在等待获取锁
     * 如果有则比较下一个获取锁的线程是否是当前线程
     * @return
     */
    public final boolean hasQueuedPredecessors() {
        //尾节点
        Node t = tail;
        //头节点
        Node h = head;
        //头节点的下一个节点
        Node s;
        //头节点是否不等于尾节点并且头节点的下一个节点的线程不等于当前线程
        //如果头节点等于尾节点则说明等待队列中并没有节点在等待获取锁
        //如果头节点不等于尾节点则说明等待队列中有节点在等待获取锁
        //此时则需要校验下一个获取锁的线程是否是当前线程
        return h != t && ((s = h.next) == null || s.thread != Thread.currentThread());
    }


    // Instrumentation and monitoring methods

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring system state, not for synchronization
     * control.
     *
     * @return the estimated number of threads waiting to acquire
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        int s = getState();
        String q  = hasQueuedThreads() ? "non" : "";
        return super.toString() +
            "[State = " + s + ", " + q + "empty queue]";
    }


    // Internal support methods for Conditions

    final boolean isOnSyncQueue(Node node) {
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            //指定节点还在等待队列中此时就需要继续等待
            return false;
        if (node.next != null)
            //指定节点已经不在等待队列中了
            return true;
        //从等待队列中的尾节点开始向头节点遍历,校验指定的节点是否在其中
        return findNodeFromTail(node);
    }

    /**
     * 从等待队列中的尾节点向头节点遍历
     * 校验指定的节点是否在队列中
     */
    private boolean findNodeFromTail(Node node) {
        //尾节点
        Node t = tail;
        //从尾节点开始向头节点遍历并校验指定的节点是否在其中
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    /**
     * 将指定的节点添加到同步等待队列中
     * 并根据前一个节点的等待状态来决定是否需要立刻唤醒指定节点
     */
    final boolean transferForSignal(Node node) {
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            //更改节点状态失败说明该节点已经被取消等待或已经被唤醒了
            return false;
        //将要唤醒的节点添加到同步等待队列中
        //并返回前一个节点
        Node p = enq(node);
        //前一个节点的等待状态
        int ws = p.waitStatus;
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            //如果前一个节点的等待状态大于0则说明已经被取消加锁,此时就需要唤醒后续的节点,就是当前节点
            //前一个节点的等待状态不大于0但是更改前一个节点的等待状态时失败则说明前一个节点已经被唤醒了并更改了状态
            //此时就需要尝试将当前节点中的线程唤醒
            LockSupport.unpark(node.thread);
        return true;
    }

    final boolean transferAfterCancelledWait(Node node) {
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            //节点取消了等待则将节点添加到同步等待队列中
            enq(node);
            return true;
        }
        /*
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         */
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }

    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            //获取当前锁状态
            int savedState = getState();
            //释放锁
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed)
                node.waitStatus = Node.CANCELLED;
        }
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /** 条件队列中的头节点 */
        private transient Node firstWaiter;
        /** 条件队列中的尾节点 */
        private transient Node lastWaiter;

        public ConditionObject() { }

        // Internal methods

        /**
         * 添加新的节点到队列中
         */
        private Node addConditionWaiter() {
            //尾节点
            Node t = lastWaiter;
            if (t != null && t.waitStatus != Node.CONDITION) {
                //尾节点已取消等待则需要从队列中移除
                //从头节点依次遍历将队列中已经取消等待的节点移除
                unlinkCancelledWaiters();
                //更新尾节点,因为在移除队列中取消等待的节点的时候可能尾节点已经发生了变化
                t = lastWaiter;
            }
            //为当前线程创建一个等待模式的节点
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null)
                //尾节点为空则说明队列中没有节点在等待
                //此时需要将新创建的节点设置为头节点
                firstWaiter = node;
            else
                //尾节点不为空此时需要将尾节点的下一个节点的指针指向新创建的节点
                t.nextWaiter = node;
            //将新创建的节点设置为尾节点
            lastWaiter = node;
            //返回新创建的节点
            return node;
        }

        /**
         * 唤醒等待队列中的头节点
         * 如果等待队列中的头节点被取消等待或已经被唤醒了
         * 此时就需要唤醒头节点的后续的一个节点
         * 直到成功的唤醒一个节点中的线程
         */
        private void doSignal(Node first) {
            do {
                if ( (firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;
                first.nextWaiter = null;
            } while (!transferForSignal(first) && (first = firstWaiter) != null);
        }

        /**
         * Removes and transfers all nodes.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * 移除队列中取消等待的节点
         */
        private void unlinkCancelledWaiters() {
            //头节点
            Node t = firstWaiter;
            Node trail = null;
            //从头节点开始遍历依次将队列中已经取消等待的节点从队列中移除
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    if (next == null)
                        lastWaiter = trail;
                }
                else
                    trail = t;
                t = next;
            }
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signal() {
            if (!isHeldExclusively())
                //加锁的线程不是当前线程则抛出异常
                throw new IllegalMonitorStateException();
            //头节点
            Node first = firstWaiter;
            if (first != null)
                //唤醒头节点
                doSignal(first);
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signalAll() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted())
                    interrupted = true;
            }
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /** Mode meaning to reinterrupt on exit from wait */
        private static final int REINTERRUPT =  1;
        /** Mode meaning to throw InterruptedException on exit from wait */
        private static final int THROW_IE    = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */
        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ? (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : 0;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode) throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        public final void await() throws InterruptedException {
            if (Thread.interrupted())
                //线程被中断抛出异常
                throw new InterruptedException();
            //为当前线程创建一个等待模式的节点并入队,并将等待队列中已经取消等待的节点移除掉
            Node node = addConditionWaiter();
            //释放当前线程的锁,防止当前线程加了锁,导致其它在等待的线程被唤醒之后不能获取到锁从而导致一直阻塞
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            //如果指定节点还在等待队列中等待则挂起
            //如果指定节点被中断了则会将指定节点添加到同步等待队列中
            //如果指定节点被唤醒了则会将指定节点添加到同步等待队列中
            while (!isOnSyncQueue(node)) {
                //节点在等待队列中则挂起
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            //acquireQueued 指定节点中的线程被中断了或者被唤醒了则会尝试去获取锁
            //如果还未到指定节点中的线程获取锁的时候则会继续挂起
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                //指定节点的线程已经获取到了锁并且节点关联的下一个节点不为空
                //此时就需要将已经获取到锁的节点从等待队列中移除
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return deadline - System.nanoTime();
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
         *
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("next"));

        } catch (Exception ex) { throw new Error(ex); }
    }

    /**
     * 通过CAS操作修改头节点
     * 该方法由enq方法使用
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * 通过CAS操作修改尾节点
     * @param expect 预期的节点
     * @param update 待更新的节点
     * @return
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        //根据tail偏移量从当前this对象中获取最新的尾节点
        //并校验预期的尾节点是否与最新的尾节点相同
        //如果相同则使用update节点替换尾节点
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * 通过CAS操作修改线程等待状态
     */
    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                                        expect, update);
    }

    /**
     * 通过CAS操作修改当前节点node节点关联的下一个节点
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
