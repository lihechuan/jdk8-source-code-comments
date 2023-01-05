/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.util.function.Consumer;

public class LinkedList<E>
    extends AbstractSequentialList<E>
    implements List<E>, Deque<E>, Cloneable, java.io.Serializable
{
    //双向链表长度
    transient int size = 0;

    //双向链表头节点
    transient Node<E> first;

    //双向链表尾节点
    transient Node<E> last;

    //创建一个空的双向链表
    public LinkedList() {
    }

    /**
     * 根据集合中的元素创建一个双向链表
     * @param c 集合元素
     */
    public LinkedList(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    //将指定元素添加到双向链表头节点
    private void linkFirst(E e) {
        //原头节点
        final Node<E> f = first;
        //创建新的节点将指定元素放置节点中并将节点中的下一个节点指针指向原头节点
        final Node<E> newNode = new Node<>(null, e, f);
        //将当前指定元素所在的节点设置为新的头节点
        first = newNode;
        if (f == null)
            //原头节点为null则说明双向链表中没有节点元素
            //将当前指定元素所在的节点设置为新的尾节点
            last = newNode;
        else
            //将原头节点中的上一个节点指针指向新的头节点
            f.prev = newNode;
        //双向链表长度加1
        size++;
        //双向链表修改次数加1
        modCount++;
    }

    //将指定元素添加到双向链表尾节点
    void linkLast(E e) {
        //原尾节点
        final Node<E> l = last;
        //创建新的节点将指定元素放置节点中并将节点中的上一个节点指针指向原尾节点
        final Node<E> newNode = new Node<>(l, e, null);
        //将当前指定元素所在的节点设置为新的尾节点
        last = newNode;
        if (l == null)
            //原尾节点为null则说明双向链表中没有节点元素
            //将当前指定元素所在的节点设置为新的头节点
            first = newNode;
        else
            //将原尾节点中的下一个节点指针指向新的尾节点
            l.next = newNode;
        size++;
        modCount++;
    }

    /**
     * 在succ节点前添加元素
     * @param e 待添加的元素
     * @param succ 待添加的元素所在的索引位置的节点
     */
    void linkBefore(E e, Node<E> succ) {
        // assert succ != null;
        //获取succ节点的上一个节点
        final Node<E> pred = succ.prev;
        //创建节点将添加的元素放置节点中
        //将该节点中的next指针指向succ
        //将该节点中的prev指针指向pred
        //该节点插入到succ节点和pred节点的中间
        final Node<E> newNode = new Node<>(pred, e, succ);
        //将succ的prev指针指向新添加的节点
        succ.prev = newNode;
        if (pred == null)
            //如果succ的上一个节点为空则说明succ原先是头节点
            //将新添加的节点设置为头节点
            first = newNode;
        else
            //将原succ的下一个节点的指针指向新添加的节点
            pred.next = newNode;
        //更新双向链表的长度和修改次数
        size++;
        modCount++;
    }

    /**
     * 删除头节点并修改指针
     * @param f 头节点
     * @return
     */
    private E unlinkFirst(Node<E> f) {
        // assert f == first && f != null;
        //头节点中的元素
        final E element = f.item;
        //获取头节点next指针指向的下一个节点
        final Node<E> next = f.next;
        //将头节点中的元素置为null
        f.item = null;
        //将头节点next指针置为null
        f.next = null;
        //将被删除的头节点中next指针指向的下一个节点置为新的头节点
        first = next;
        if (next == null)
            //如果被删除的头节点中next指针指向的下一个节点为null
            //则说明该双向链表中将头节点删除之后则没有别的节点
            //将尾节点置为null
            last = null;
        else
            //如果被删除的头节点中next指针指向的下一个节点不为null
            //则将该节点的prev指针置为null
            next.prev = null;
        //更新双向链表的长度和修改次数
        size--;
        modCount++;
        //返回被删除的元素
        return element;
    }

    /**
     * 删除尾节点并修改指针
     */
    private E unlinkLast(Node<E> l) {
        // assert l == last && l != null;
        final E element = l.item;
        final Node<E> prev = l.prev;
        l.item = null;
        l.prev = null;
        last = prev;
        if (prev == null)
            first = null;
        else
            prev.next = null;
        size--;
        modCount++;
        return element;
    }

    /**
     * 删除节点并且删除相关联的指针
     * @param x
     * @return
     */
    E unlink(Node<E> x) {
        // assert x != null;
        //待删除的节点中的元素
        final E element = x.item;
        //待删除节点的右边节点
        //待删除的节点next指针指向的节点
        final Node<E> next = x.next;
        //待删除节点的左边节点
        //待删除的节点prev指针指向的节点
        final Node<E> prev = x.prev;

        if (prev == null) {
            //如果待删除节点的左边节点为null则说明待删除节点是头节点
            //将待删除的节点next指针指向的节点设置为头节点(将待删除节点右边的节点设置为头节点)
            first = next;
        } else {
            //如果待删除节点的左边节点不为null
            //则将待删除节点的上一个节点的next指针指向待删除节点的下一个节点(将待删除节点的左边节点与待删除节点的右边节点关联)
            prev.next = next;
            //将待删除节点与左边节点取消关联
            x.prev = null;
        }

        if (next == null) {
            //如果待删除节点的右边节点为null则说明待删除节点是尾节点
            //将待删除的节点prev指针指向的节点设置为尾节点(将待删除节点左边的节点设置为尾节点)
            last = prev;
        } else {
            //如果待删除节点的右边节点不为null
            //则将待删除节点的下一个节点的prev指针指向待删除节点的上一个节点(将待删除节点的右边节点与待删除节点的左边节点关联)
            next.prev = prev;
            //将待删除节点与右边节点取消关联
            x.next = null;
        }
        //将待删除节点中的元素置为null
        x.item = null;
        //更新双向链表长度和修改次数
        size--;
        modCount++;
        return element;
    }

    /**
     * 获取双向链表中的头节点元素
     * @return
     */
    public E getFirst() {
        final Node<E> f = first;
        if (f == null)
            throw new NoSuchElementException();
        return f.item;
    }

    /**
     * 获取双向链表中的尾节点
     * @return
     */
    public E getLast() {
        final Node<E> l = last;
        if (l == null)
            throw new NoSuchElementException();
        return l.item;
    }

    /**
     * 删除双向链表中的头节点
     * @return
     */
    public E removeFirst() {
        //头节点
        final Node<E> f = first;
        if (f == null)
            throw new NoSuchElementException();
        //删除头节点并修改指针并返回被删除的元素
        return unlinkFirst(f);
    }

    /**
     * 删除双向链表中的尾节点
     * @return
     */
    public E removeLast() {
        //尾节点
        final Node<E> l = last;
        if (l == null)
            throw new NoSuchElementException();
        //删除尾节点并修改指针并返回被删除的元素
        return unlinkLast(l);
    }

    /**
     * 将元素添加到双向链表的头部
     * @param e 元素
     */
    public void addFirst(E e) {
        linkFirst(e);
    }

    /**
     * 将元素添加到双向链表的尾部
     * @param e
     */
    public void addLast(E e) {
        linkLast(e);
    }

    /**
     * Returns {@code true} if this list contains the specified element.
     * More formally, returns {@code true} if and only if this list contains
     * at least one element {@code e} such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this list is to be tested
     * @return {@code true} if this list contains the specified element
     */
    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list
     */
    public int size() {
        return size;
    }

    /**
     * 将指定元素追加到双向链表的末尾
     * @param e 元素
     * @return
     */
    public boolean add(E e) {
        //添加元素
        linkLast(e);
        return true;
    }

    /**
     * 删除指定元素在双向链表中第一次匹配到的节点
     * @param o 待删除元素
     * @return
     */
    public boolean remove(Object o) {
        if (o == null) {
            //元素为null
            //从头节点开始遍历
            for (Node<E> x = first; x != null; x = x.next) {
                //如果被遍历到的节点中的元素与待删除的元素不相同
                //则将被遍历到的节点中的next指针指向的节点设置为下次待遍历的节点
                if (x.item == null) {
                    //与指定的元素匹配
                    //删除该元素匹配的节点并且删除相关联的指针
                    unlink(x);
                    return true;
                }
            }
        } else {
            //元素不为null
            //从头节点开始遍历
            for (Node<E> x = first; x != null; x = x.next) {
                //如果被遍历到的节点中的元素与待删除的元素不相同
                //则将被遍历到的节点中的next指针指向的节点设置为下次待遍历的节点
                if (o.equals(x.item)) {
                    //与指定的元素匹配
                    //删除该元素匹配的节点并且删除相关联的指针
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 将指定集合中的元素追加到当前双向链表的尾部
     * @param c 集合元素
     * @return
     */
    public boolean addAll(Collection<? extends E> c) {
        return addAll(size, c);
    }

    /**
     * 在指定的索引位置开始添加集合中的所有元素
     * @param index 索引位置
     * @param c 元素集合
     * @return
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        //检查指定的索引是否在有效的索引位置
        checkPositionIndex(index);
        //获取集合中的数组对象
        Object[] a = c.toArray();
        //待添加的元素数量
        int numNew = a.length;
        if (numNew == 0)
            //元素数量等于0直接返回
            return false;
        //定义上一个节点和当前指定的索引在双向链表中存在的节点
        Node<E> pred, succ;
        if (index == size) {
            //index等于size则说明
            //当前指定的索引在双向链表中不存在节点
            succ = null;
            //当前指定的索引不存在则从双向链表中的尾节点开始添加元素
            pred = last;
        } else {
            //index不等于size
            //当前指定的索引在双向链表中存在,根据指定的索引在双向链表中获取该节点
            succ = node(index);
            //获取指定索引的节点的上一个节点
            pred = succ.prev;
        }
        //遍历待添加的元素集合
        for (Object o : a) {
            //强转成泛型
            @SuppressWarnings("unchecked") E e = (E) o;
            //创建新的节点并将元素添加到节点中
            //并设置该节点的prev指针
            //index等于size的时候,如果当前元素是集合中的第一个元素
            //prev指针则指向原双向链表中的尾节点
            //如果当前元素不是集合中的第一个元素则prev指针指向集合中上一个元素所在的节点
            //index不等于size的时候,如果当前元素是集合中的第一个元素
            //prev指针则指向双向链表中index索引所在的节点指向的上一个节点
            //如果当前元素不是集合中的第一个元素则prev指针指向集合中上一个元素所在的节点
            Node<E> newNode = new Node<>(pred, e, null);
            if (pred == null)
                //上一个节点为null
                //说明双向链表中没有节点则将新创建的节点设置为头节点
                first = newNode;
            else
                //上一个节点不为null
                //将上一个节点中的next指针指向新创建的节点
                pred.next = newNode;
            //将新创建的节点更新为上一个节点
            //下一次循环创建的新节点中的prev指针则指向该节点
            pred = newNode;
        }

        if (succ == null) {
            //指定索引(index)的节点在双向链表中不存在
            //则说明指定的元素是从双向链表的尾部开始添加的
            //将指定的元素集合中的末尾元素所在的节点设置为尾节点
            last = pred;
        } else {
            //指定索引的节点在双向链表中存在
            //将指定的元素集合中的末尾元素所在的节点中的next指针指向指定的索引(index)的原节点
            pred.next = succ;
            //将指定的索引(index)的原节点中的prev指针指向元素集合中的末尾元素所在的节点
            succ.prev = pred;
        }
        //更新双向链表的长度和修改次数
        size += numNew;
        modCount++;
        return true;
    }

    /**
     * 清空双向链表中的所有节点
     */
    public void clear() {
        //从头节点开始遍历依次将所有节点中的元素、指针置为null
        for (Node<E> x = first; x != null; ) {
            //获取当前遍历到的节点的下一个节点
            Node<E> next = x.next;
            //将当前节点中的元素置为null
            x.item = null;
            //将当前节点中的next指针(下一个节点)指向null
            x.next = null;
            //将当前节点中的prev指针(上一个节点)指向null
            x.prev = null;
            //将下一个节点设置为下个循环待处理的节点
            x = next;
        }
        //将头和尾节点置为null
        first = last = null;
        //更新双向链表长度和修改次数
        size = 0;
        modCount++;
    }


    /**
     * 获取指定索引为的元素
     * @param index
     * @return
     */
    public E get(int index) {
        //校验索引是否是现有节点的索引
        checkElementIndex(index);
        //获取指定索引位置的节点并返回该节点中的元素
        return node(index).item;
    }

    /**
     * 替换指定索引位置的元素
     * @param index 索引
     * @param element 元素
     * @return
     */
    public E set(int index, E element) {
        //校验索引是否是现有节点的索引
        checkElementIndex(index);
        //获取指定索引位置的节点
        Node<E> x = node(index);
        //获取节点中旧元素
        E oldVal = x.item;
        //将新元素替换旧元素
        x.item = element;
        //返回旧元素
        return oldVal;
    }

    /**
     * 将元素添加到指定的索引位置
     * @param index 索引
     * @param element 元素
     */
    public void add(int index, E element) {
        //校验索引是否在有效的索引位置
        checkPositionIndex(index);
        if (index == size)
            //添加元素的索引位置等于双向链表的长度则往双向链表尾节点插入
            linkLast(element);
        else
            //node(index) 获取当前指定的索引的节点
            //在指定的索引的节点前添加元素并修改双向链表中原先的一些节点中的next和prev指针引用
            linkBefore(element, node(index));
    }

    /**
     * 删除指定索引位置的节点
     * @param index 索引
     * @return
     */
    public E remove(int index) {
        //校验索引是否是现有节点的索引
        checkElementIndex(index);
        //node(index) 获取指定索引位置的节点
        //unlink 删除节点并且删除相关联的指针
        return unlink(node(index));
    }

    /**
     * 校验索引是否是现有节点的索引
     * @param index 索引
     * @return
     */
    private boolean isElementIndex(int index) {
        return index >= 0 && index < size;
    }

    /**
     * 校验索引是否在有效的索引位置
     * @param index 索引
     * @return
     */
    private boolean isPositionIndex(int index) {
        return index >= 0 && index <= size;
    }

    /**
     * Constructs an IndexOutOfBoundsException detail message.
     * Of the many possible refactorings of the error handling code,
     * this "outlining" performs best with both server and client VMs.
     */
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }

    private void checkElementIndex(int index) {
        //校验索引是否是现有节点的索引
        if (!isElementIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    private void checkPositionIndex(int index) {
        //校验索引是否在有效的索引位置
        if (!isPositionIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * 遍历获取到指定索引位置的节点
     * @param index 索引
     * @return 索引位置的节点
     */
    Node<E> node(int index) {
         //assert isElementIndex(index);
        //对双向链表的长度右移一位并判断指定的索引位置是在双向链表的前半部分还是后半部分
        if (index < (size >> 1)) {
            //双向链表的前半部分
            //双向链表头节点,当前遍历到的节点
            Node<E> x = first;
            //从双向链表的头节点开始遍历直到找到指定的索引所在的节点
            for (int i = 0; i < index; i++)
                //从双向链表的头节点开始获取当前遍历到的节点的下一个节点
                //并将下一个节点赋予为当前遍历到的节点,以此类推直到获取到指定索引所在的节点并返回
                x = x.next;
            return x;
        } else {
            //双向链表的后半部分
            //双向链表尾节点,当前遍历到的节点
            Node<E> x = last;
            //从双向链表的尾节点开始遍历直到找到指定的索引所在的节点
            for (int i = size - 1; i > index; i--)
                //从双向链表的尾节点开始获取当前遍历到的节点的上一个节点
                //并将上一个节点赋予为当前遍历到的节点,以此类推直到获取到指定索引所在的节点并返回
                x = x.prev;
            return x;
        }
    }

    /**
     * 获取指定元素所在的节点索引位置
     * @param o 元素
     * @return
     */
    public int indexOf(Object o) {
        //索引
        int index = 0;
        if (o == null) {
            //元素为null
            //从头节点开始遍历
            //对每一个节点中的元素进行匹配
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null)
                    //匹配成功则返回将节点所在的索引位置
                    return index;
                //匹配失败,索引自增
                index++;
            }
        } else {
            //元素不为null
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item))
                    return index;
                index++;
            }
        }
        //双向链表中不存在该元素的节点
        return -1;
    }

    /**
     * 获取指定元素所在的节点的索引位置
     * @param o 元素
     * @return
     */
    public int lastIndexOf(Object o) {
        //索引
        int index = size;
        if (o == null) {
            //元素为null
            //从尾节点开始遍历
            //对每一个节点中的元素进行匹配
            for (Node<E> x = last; x != null; x = x.prev) {
                //-1,当前元素所在节点的索引位置
                index--;
                if (x.item == null)
                    //匹配成功则返回将节点所在的索引位置
                    return index;
            }
        } else {
            //元素不为null
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (o.equals(x.item))
                    return index;
            }
        }
        //双向链表中不存在该元素的节点
        return -1;
    }

    // Queue operations.

    /**
     * 获取头节点元素
     * @return
     */
    public E peek() {
        //头节点
        final Node<E> f = first;
        //如果头节点为null则返回null,反之则返回头节点中的元素
        return (f == null) ? null : f.item;
    }

    /**
     * 获取头节点的元素
     * @return
     */
    public E element() {
        return getFirst();
    }

    /**
     * 获取头节点中的元素,并将头节点移除
     * @return
     */
    public E poll() {
        //头节点
        final Node<E> f = first;
        //头节点为null则返回null
        //不为null则返回头节点中的元素并将头节点移除
        return (f == null) ? null : unlinkFirst(f);
    }

    /**
     * 删除双向链表头节点
     * @return
     */
    public E remove() {
        return removeFirst();
    }

    /**
     * 将指定元素添加到末尾
     * @param e 元素
     * @return
     */
    public boolean offer(E e) {
        return add(e);
    }

    // Deque operations

    /**
     * 添加指定元素到头部
     * @param e 元素
     * @return
     */
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    /**
     * 添加指定元素到末尾
     * @param e
     * @return
     */
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    /**
     * 获取头节点中的元素
     * @return
     */
    public E peekFirst() {
        final Node<E> f = first;
        return (f == null) ? null : f.item;
     }

    /**
     * 获取尾节点中的元素
     * @return
     */
    public E peekLast() {
        final Node<E> l = last;
        return (l == null) ? null : l.item;
    }

    /**
     * 获取头节点中的元素并将该头节点移除
     * @return
     */
    public E pollFirst() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    /**
     * 获取尾节点中的元素并将该尾节点移除
     * @return
     */
    public E pollLast() {
        final Node<E> l = last;
        return (l == null) ? null : unlinkLast(l);
    }

    /**
     * 将元素添加到头节点中
     * @param e 元素
     */
    public void push(E e) {
        addFirst(e);
    }

    /**
     * 删除头节点
     * @return
     */
    public E pop() {
        return removeFirst();
    }

    /**
     * 从头节点开始查找指定元素第一个匹配到的元素的节点并删除
     * @param o 元素
     * @return
     */
    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    /**
     * 从尾节点开始查找指定元素第一个匹配到的元素的节点并删除
     * @param o
     * @return
     */
    public boolean removeLastOccurrence(Object o) {
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a list-iterator of the elements in this list (in proper
     * sequence), starting at the specified position in the list.
     * Obeys the general contract of {@code List.listIterator(int)}.<p>
     *
     * The list-iterator is <i>fail-fast</i>: if the list is structurally
     * modified at any time after the Iterator is created, in any way except
     * through the list-iterator's own {@code remove} or {@code add}
     * methods, the list-iterator will throw a
     * {@code ConcurrentModificationException}.  Thus, in the face of
     * concurrent modification, the iterator fails quickly and cleanly, rather
     * than risking arbitrary, non-deterministic behavior at an undetermined
     * time in the future.
     *
     * @param index index of the first element to be returned from the
     *              list-iterator (by a call to {@code next})
     * @return a ListIterator of the elements in this list (in proper
     *         sequence), starting at the specified position in the list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @see List#listIterator(int)
     */
    public ListIterator<E> listIterator(int index) {
        checkPositionIndex(index);
        return new ListItr(index);
    }

    private class ListItr implements ListIterator<E> {
        private Node<E> lastReturned;
        private Node<E> next;
        private int nextIndex;
        private int expectedModCount = modCount;

        ListItr(int index) {
            // assert isPositionIndex(index);
            next = (index == size) ? null : node(index);
            nextIndex = index;
        }

        public boolean hasNext() {
            return nextIndex < size;
        }

        public E next() {
            checkForComodification();
            if (!hasNext())
                throw new NoSuchElementException();

            lastReturned = next;
            next = next.next;
            nextIndex++;
            return lastReturned.item;
        }

        public boolean hasPrevious() {
            return nextIndex > 0;
        }

        public E previous() {
            checkForComodification();
            if (!hasPrevious())
                throw new NoSuchElementException();

            lastReturned = next = (next == null) ? last : next.prev;
            nextIndex--;
            return lastReturned.item;
        }

        public int nextIndex() {
            return nextIndex;
        }

        public int previousIndex() {
            return nextIndex - 1;
        }

        public void remove() {
            checkForComodification();
            if (lastReturned == null)
                throw new IllegalStateException();

            Node<E> lastNext = lastReturned.next;
            unlink(lastReturned);
            if (next == lastReturned)
                next = lastNext;
            else
                nextIndex--;
            lastReturned = null;
            expectedModCount++;
        }

        public void set(E e) {
            if (lastReturned == null)
                throw new IllegalStateException();
            checkForComodification();
            lastReturned.item = e;
        }

        public void add(E e) {
            checkForComodification();
            lastReturned = null;
            if (next == null)
                linkLast(e);
            else
                linkBefore(e, next);
            nextIndex++;
            expectedModCount++;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            while (modCount == expectedModCount && nextIndex < size) {
                action.accept(next.item);
                lastReturned = next;
                next = next.next;
                nextIndex++;
            }
            checkForComodification();
        }

        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    private static class Node<E> {
        //节点元素
        E item;
        //下一个节点
        Node<E> next;
        //上一个节点
        Node<E> prev;

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }

    /**
     * @since 1.6
     */
    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    /**
     * Adapter to provide descending iterators via ListItr.previous
     */
    private class DescendingIterator implements Iterator<E> {
        private final ListItr itr = new ListItr(size());
        public boolean hasNext() {
            return itr.hasPrevious();
        }
        public E next() {
            return itr.previous();
        }
        public void remove() {
            itr.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedList<E> superClone() {
        try {
            return (LinkedList<E>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    /**
     * 获取当前集合的副本
     * @return
     */
    public Object clone() {
        //返回当前集合的副本
        LinkedList<E> clone = superClone();
        //将副本置为原始状态
        clone.first = clone.last = null;
        clone.size = 0;
        clone.modCount = 0;
        //从头节点开始遍历将元素添加到副本中
        for (Node<E> x = first; x != null; x = x.next)
            //调用add方法将元素添加到节点并设置节点指针
            clone.add(x.item);
        //返回副本
        return clone;
    }

    /**
     * 获取当前集合的数组
     * @return
     */
    public Object[] toArray() {
        //创建当前集合大小的object对象
        Object[] result = new Object[size];
        //数组索引
        int i = 0;
        //从双向链表的头节点开始遍历
        for (Node<E> x = first; x != null; x = x.next)
            //将双向链表中的每一个节点元素添加到数组中
            result[i++] = x.item;
        //返回数组
        return result;
    }

    /**
     * 返回指定类型的数组
     * @param a
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        //校验指定数组的长度是小于当前集合的长度
        if (a.length < size)
            //小于则创建一个集合大小的数组
            a = (T[])java.lang.reflect.Array.newInstance(
                                a.getClass().getComponentType(), size);
        //数组索引
        int i = 0;
        Object[] result = a;
        //从头节点开始遍历
        for (Node<E> x = first; x != null; x = x.next)
            //将双向链表中的每一个节点中的元素添加到数组中
            result[i++] = x.item;
        if (a.length > size)
            //如果a数组长度大于当前集合的长度则在a[size]处放置一个null
            //这个null值可以判断出null后面已经没有当前集合中的元素了
            a[size] = null;

        return a;
    }

    private static final long serialVersionUID = 876323262645176354L;

    /**
     * Saves the state of this {@code LinkedList} instance to a stream
     * (that is, serializes it).
     *
     * @serialData The size of the list (the number of elements it
     *             contains) is emitted (int), followed by all of its
     *             elements (each an Object) in the proper order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        // Write out any hidden serialization magic
        s.defaultWriteObject();

        // Write out size
        s.writeInt(size);

        // Write out all elements in the proper order.
        for (Node<E> x = first; x != null; x = x.next)
            s.writeObject(x.item);
    }

    /**
     * Reconstitutes this {@code LinkedList} instance from a stream
     * (that is, deserializes it).
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in any hidden serialization magic
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++)
            linkLast((E)s.readObject());
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * list.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED} and
     * {@link Spliterator#ORDERED}.  Overriding implementations should document
     * the reporting of additional characteristic values.
     *
     * @implNote
     * The {@code Spliterator} additionally reports {@link Spliterator#SUBSIZED}
     * and implements {@code trySplit} to permit limited parallelism..
     *
     * @return a {@code Spliterator} over the elements in this list
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return new LLSpliterator<E>(this, -1, 0);
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    static final class LLSpliterator<E> implements Spliterator<E> {
        static final int BATCH_UNIT = 1 << 10;  // batch array size increment
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final LinkedList<E> list; // null OK unless traversed
        Node<E> current;      // current node; null until initialized
        int est;              // size estimate; -1 until first needed
        int expectedModCount; // initialized when est set
        int batch;            // batch size for splits

        LLSpliterator(LinkedList<E> list, int est, int expectedModCount) {
            this.list = list;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getEst() {
            int s; // force initialization
            final LinkedList<E> lst;
            if ((s = est) < 0) {
                if ((lst = list) == null)
                    s = est = 0;
                else {
                    expectedModCount = lst.modCount;
                    current = lst.first;
                    s = est = lst.size;
                }
            }
            return s;
        }

        public long estimateSize() { return (long) getEst(); }

        public Spliterator<E> trySplit() {
            Node<E> p;
            int s = getEst();
            if (s > 1 && (p = current) != null) {
                int n = batch + BATCH_UNIT;
                if (n > s)
                    n = s;
                if (n > MAX_BATCH)
                    n = MAX_BATCH;
                Object[] a = new Object[n];
                int j = 0;
                do { a[j++] = p.item; } while ((p = p.next) != null && j < n);
                current = p;
                batch = j;
                est = s - j;
                return Spliterators.spliterator(a, 0, j, Spliterator.ORDERED);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Node<E> p; int n;
            if (action == null) throw new NullPointerException();
            if ((n = getEst()) > 0 && (p = current) != null) {
                current = null;
                est = 0;
                do {
                    E e = p.item;
                    p = p.next;
                    action.accept(e);
                } while (p != null && --n > 0);
            }
            if (list.modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Node<E> p;
            if (action == null) throw new NullPointerException();
            if (getEst() > 0 && (p = current) != null) {
                --est;
                E e = p.item;
                current = p.next;
                action.accept(e);
                if (list.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

}
