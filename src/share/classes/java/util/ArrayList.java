/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import sun.misc.SharedSecrets;

public class ArrayList<E> extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable
{
    private static final long serialVersionUID = 8683452581122892189L;

    /**
     * Default initial capacity.
     * 默认初始容量
     * 创建集合时没有传递集合大小则使用默认大小（10）
     */
    private static final int DEFAULT_CAPACITY = 10;

    /**
     * 空数组对象
     * 该空数组对象则是在创建的时候传递容量大小为0时创建的
     */
    private static final Object[] EMPTY_ELEMENTDATA = {};

    /**
     * 空数组对象
     * 该空数组对象则是在创建的时候未传递容量大小创建的
     * 默认容量大小为10
     */
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    /**
     * 数组对象
     */
    transient Object[] elementData;

    /**
     * 集合长度
     */
    private int size;

    /**
     * 创建一个指定初始容量的集合
     * @param initialCapacity 初始容量
     */
    public ArrayList(int initialCapacity) {
        if (initialCapacity > 0) {
            //初始容量大于0则创建一个指定大小的object数组
            this.elementData = new Object[initialCapacity];
        } else if (initialCapacity == 0) {
            //初始容量等于0则创建一个空的object数组
            this.elementData = EMPTY_ELEMENTDATA;
        } else {
            throw new IllegalArgumentException("Illegal Capacity: "+
                                               initialCapacity);
        }
    }

    /**
     * 创建一个容量大小为10的数组
     */
    public ArrayList() {
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }

    /**
     * 根据一个集合中的元素构造出一个新的集合
     * @param c 构造新的集合元素的集合
     */
    public ArrayList(Collection<? extends E> c) {
        //将集合转换为数组
        Object[] a = c.toArray();
        if ((size = a.length) != 0) {
            //校验集合类型
            if (c.getClass() == ArrayList.class) {
                elementData = a;
            } else {
                //集合不是arrayList类型则使用拷贝方法将转换的数组中的数据拷贝到新的Object数组中
                elementData = Arrays.copyOf(a, size, Object[].class);
            }
        } else {
            //创建一个空的数组对象
            elementData = EMPTY_ELEMENTDATA;
        }
    }

    /**
     * 以集合的长度去掉数组中多余的null来释放空间
     * 该null则是数组在分配空间的时候提前预先分配好的空间默认置为的null
     * 而不是调用方法手动添加的null
     * 该方法不会将调用方法手动添加的null值去掉
     */
    public void trimToSize() {
        //数组修改次数加1
        modCount++;
        //校验当前集合长度是否小于数组长度
        if (size < elementData.length) {
            //集合长度小于数组长度
            //当前集合长度是否等于0,如果集合长度等于0则将空数组对象赋与数组,空数组对象中不包含任何内容
            //如果集合长度不等于0,则使用拷贝方法将数组以集合的长度拷贝到原数组中
            elementData = (size == 0)
              ? EMPTY_ELEMENTDATA
              : Arrays.copyOf(elementData, size);
        }
    }

    /**
     * 手动扩容
     * @param minCapacity 最小扩容容量
     */
    public void ensureCapacity(int minCapacity) {
        //校验当前数组是否在创建的时候未指定初始容量而创建的空数组对象
        //如果创建的时候未指定初始容量则使用默认的初始容量 DEFAULT_CAPACITY
        int minExpand = (elementData != DEFAULTCAPACITY_EMPTY_ELEMENTDATA)
            ? 0
            : DEFAULT_CAPACITY;
        //校验指定的扩容的容量大小是否大于最小默认的初始容量大小
        if (minCapacity > minExpand) {
            //确认数组是否需要扩容,需要扩容则直接进行数组扩容
            ensureExplicitCapacity(minCapacity);
        }
    }

    /**
     * 计算容量
     * @param elementData 数组对象
     * @param minCapacity 添加数据之后的容量大小
     * @return
     */
    private static int calculateCapacity(Object[] elementData, int minCapacity) {
        //校验数组对象是否是未传递初始容量而创建的
        if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            //创建数组对象时未传递初始容量
            //当前数组对象大小未超出默认容量大小则返回默认容量大小反之返回当前数组对象大小
            return Math.max(DEFAULT_CAPACITY, minCapacity);
        }
        //返回当前数组对象大小
        return minCapacity;
    }

    private void ensureCapacityInternal(int minCapacity) {
        /**
         * calculateCapacity 计算容量
         * 在创建数组对象时未传递初始容量则比较当前数组容量大小和默认容量大小并返回最大的数
         * 在创建数组对象时传递了初始容量大小,则返回当前数组大小
         *
         * ensureExplicitCapacity 确认数组是否需要扩容
         */
        ensureExplicitCapacity(calculateCapacity(elementData, minCapacity));
    }

    private void ensureExplicitCapacity(int minCapacity) {
        //数组修改次数加1
        modCount++;
        //校验数组容量是否已经达到最大
        if (minCapacity - elementData.length > 0)
            //数组扩容
            grow(minCapacity);
    }

    /**
     * The maximum size of array to allocate.
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     * 扩容的数组最大的容量大小
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * 数组扩容
     * @param minCapacity 扩容的最小容量
     */
    private void grow(int minCapacity) {
        //旧数组对象的容量
        int oldCapacity = elementData.length;
        //(oldCapacity >> 1)  右移一位获取到旧数组对象容量的一半容量大小
        //新数组对象的容量
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity < 0)
            //扩容失败
            newCapacity = minCapacity;
        //校验是否超出最大的数组扩容容量大小
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            //超出数组最大扩容容量大小
            //hugeCapacity 获取最大扩容容量大小
            newCapacity = hugeCapacity(minCapacity);
        //将旧数组对象中的数据复制到新扩容的数组对象中
        elementData = Arrays.copyOf(elementData, newCapacity);
    }

    /**
     * 获取最大扩容容量大小
     * @param minCapacity 最小扩容容量
     * @return
     */
    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0)
            throw new OutOfMemoryError();
        //校验最小容量是否大于数组最大扩容容量大小
        //大于则返回int最大值
        //小于则返回数组最大扩容容量大小
        return (minCapacity > MAX_ARRAY_SIZE) ?
            Integer.MAX_VALUE :
            MAX_ARRAY_SIZE;
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
     * Returns <tt>true</tt> if this list contains no elements.
     *
     * @return <tt>true</tt> if this list contains no elements
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns <tt>true</tt> if this list contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this list contains
     * at least one element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this list is to be tested
     * @return <tt>true</tt> if this list contains the specified element
     */
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    /**
     * 返回指定元素从左到右第一次匹配到的索引位置
     * @param o 元素
     * @return 索引
     */
    public int indexOf(Object o) {
        //校验指定元素是否为null
        if (o == null) {
            //指定元素为null
            //遍历数组
            for (int i = 0; i < size; i++)
                //校验数组中每一个索引位置的元素是否为null
                if (elementData[i]==null)
                    //元素为null则返回当前元素所在的索引位置
                    return i;
        } else {
            for (int i = 0; i < size; i++)
                //校验数组中每一个索引位置的元素是否和指定元素相同
                if (o.equals(elementData[i]))
                    //相同则返回元素所在的索引位置
                    return i;
        }
        //指定元素在数组中不存在
        return -1;
    }

    /**
     * 返回指定元素从右到左第一次匹配到的索引位置
     * 该方法和indexOf相同,只是在遍历的时候一个从左到右一个从右到左
     * @param o 元素
     * @return 索引
     */
    public int lastIndexOf(Object o) {
        if (o == null) {
            for (int i = size-1; i >= 0; i--)
                if (elementData[i]==null)
                    return i;
        } else {
            for (int i = size-1; i >= 0; i--)
                if (o.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * 拷贝一个新的集合
     * @return 新集合对象
     */
    public Object clone() {
        try {
            //返回当前集合对象的副本
            ArrayList<?> v = (ArrayList<?>) super.clone();
            //将当前集合对象的元素拷贝到副本中
            v.elementData = Arrays.copyOf(elementData, size);
            //设置副本修改次数
            v.modCount = 0;
            return v;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    /**
     * 获取当前集合的数组对象
     * @return
     */
    public Object[] toArray() {
        //将当前数组对象拷贝到一个新的数组对象中并返回
        return Arrays.copyOf(elementData, size);
    }

    /**
     * 返回指定类型的数组对象
     * @param a 元素存放的数组
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        //校验元素存放的数组长度是否小于当前集合长度
        if (a.length < size)
            //小于,则将当前集合中的元素拷贝到指定类型的数组中
            return (T[]) Arrays.copyOf(elementData, size, a.getClass());
        //大于等于,则将当前集合中的元素拷贝到指定类型的数组中
        System.arraycopy(elementData, 0, a, 0, size);
        if (a.length > size)
            a[size] = null;
        return a;
    }

    /**
     * 获取指定索引位置的元素
     * @param index 索引
     * @return 元素
     */
    @SuppressWarnings("unchecked")
    E elementData(int index) {
        return (E) elementData[index];
    }

    /**
     * 获取指定索引位置的元素
     * @param index 索引位置
     * @return 元素
     */
    public E get(int index) {
        //检查索引是否超出当前数组最大长度
        rangeCheck(index);
        //获取指定索引位置的元素
        return elementData(index);
    }

    /**
     * 替换指定索引位置的元素
     * @param index 要替换的元素的索引
     * @param element 替换的元素
     * @return 被替换的元素
     */
    public E set(int index, E element) {
        //检查索引是否超出当前数组最大长度
        rangeCheck(index);
        //获取被替换的索引元素
        E oldValue = elementData(index);
        //将替换的元素添加到被替换的元素索引位置上
        elementData[index] = element;
        //返回被替换的元素
        return oldValue;
    }

    /**
     * 将元素追加到数组中
     * @param e 添加的元素
     * @return boolean
     */
    public boolean add(E e) {
        //确认数组是否需要扩容,需要扩容则进行扩容
        ensureCapacityInternal(size + 1);
        //将元素添加到数组中
        //size++ 表示元素添加到数组中之后数组的元素长度+1,未加1之前则表示当前元素添加的索引位置
        elementData[size++] = e;
        return true;
    }

    /**
     * 将元素添加到数组中指定索引位置
     * @param index 元素添加的索引位置
     * @param element 添加的元素
     */
    public void add(int index, E element) {
        //检查索引是否超出当前数组最大长度或小于最小索引
        rangeCheckForAdd(index);
        //确认数组是否需要扩容,需要扩容则进行扩容
        ensureCapacityInternal(size + 1);
        /**
         * 数组拷贝
         * src:源数组
         * srcPos:源数组需要拷贝的起始位置
         * dest:目标数组
         * destPos:目标数组放置的起始位置
         * length:拷贝的长度
         */
        System.arraycopy(elementData, index, elementData, index + 1, size - index);
        //将元素添加到指定的索引位置
        elementData[index] = element;
        //数组长度+1
        size++;
    }

    /**
     * 根据指定索引的位置删除对应的元素
     * @param index 索引位置
     * @return 被删除的元素
     */
    public E remove(int index) {
        //检查索引是否超出当前数组最大长度
        rangeCheck(index);
        //数组修改次数加1
        modCount++;
        //获取被删除的索引位置的元素
        E oldValue = elementData(index);
        //获取需要移动的元素数量（被删除的元素后面的所有数据则需要往前移动）
        int numMoved = size - index - 1;
        if (numMoved > 0)
            //如果被删除的索引元素后面的数据数量大于0则需要将元素往前移动
            /**
             * 数组拷贝
             * 将源(elementData)数组中的数据从被删除元素的后一位(index+1)开始复制
             * 复制的元素个数为numMoved
             * 并将复制的元素放置到目标数组(elementData)中的起始位置(index)
             * 例：
             *  [张三,李四,王五,赵六]  源数组
             *  此时要删除index为1的元素(李四)
             *  获取李四后面(index+1)的所有数据个数2个(numMoved) [王五,赵六]
             *  将源数组中李四后面的所有数据放置到目标数组中的index位置
             *  [张三,李四,王五,赵六]
             *       王五,赵六       王五替换索引为1的李四,赵六替换索引为2的王五
             *  [张三,王五,赵六,赵六] 目标数组结果
             */
            System.arraycopy(elementData, index+1, elementData, index, numMoved);
        //将数组拷贝后末尾重复的元素置为空
        elementData[--size] = null;
        //返回被删除的元素
        return oldValue;
    }

    /**
     * 从数组中删除第一个匹配的元素
     * @param o 删除的元素
     * @return
     */
    public boolean remove(Object o) {
        if (o == null) {
            //删除的元素为null
            //遍历数组
            for (int index = 0; index < size; index++)
                //校验index索引的元素是否为null
                if (elementData[index] == null) {
                    //为null则删除并返回
                    //只删除第一个匹配的元素
                    //根据第一个匹配的元素索引进行删除
                    fastRemove(index);
                    return true;
                }
        } else {
            //删除的元素不为null
            //遍历数组
            for (int index = 0; index < size; index++)
                //校验index索引中的元素是否与删除的元素相等
                if (o.equals(elementData[index])) {
                    //相等则删除并返回
                    //只删除第一个匹配的元素
                    //根据第一个匹配的元素索引进行删除
                    fastRemove(index);
                    return true;
                }
        }
        return false;
    }

    /**
     * 根据指定索引删除元素
     * @param index 索引
     */
    private void fastRemove(int index) {
        //数组修改次数加1
        modCount++;
        //获取需要移动的元素数量（被删除的元素后面的所有数据则需要往前移动）
        int numMoved = size - index - 1;
        if (numMoved > 0)
            //数组拷贝
            //将被删除的元素后面的所有元素往前移动一位
            System.arraycopy(elementData, index+1, elementData, index, numMoved);
        //将数组末尾重复的元素置空
        elementData[--size] = null;
    }

    /**
     * 清空数组中的所有元素
     */
    public void clear() {
        //数组修改次数加1
        modCount++;
        //遍历将数组中的所有元素置为null
        for (int i = 0; i < size; i++)
            elementData[i] = null;
        //将集合长度置为0
        size = 0;
    }

    /**
     * 将指定的集合元素追加到当前数组的末尾
     * @param c 集合
     * @return
     */
    public boolean addAll(Collection<? extends E> c) {
        //获取集合中的数组对象
        Object[] a = c.toArray();
        //获取数组长度
        int numNew = a.length;
        //校验将数组中的元素追加到当前集合末尾是否需要扩容,如果需要扩容则直接进行扩容
        ensureCapacityInternal(size + numNew);
        //将数组a中的所有元素追加到elementData中
        System.arraycopy(a, 0, elementData, size, numNew);
        //更新集合长度
        size += numNew;
        return numNew != 0;
    }

    /**
     * 将指定集合中的元素追加到当前集合指定索引位置
     * @param index 追加的索引位置
     * @param c 集合
     * @return
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        //检查元素追加的索引位置是否超出集合长度
        rangeCheckForAdd(index);
        //获取集合中的数组
        Object[] a = c.toArray();
        //获取数组的长度
        int numNew = a.length;
        //校验将数组中的元素追加到当前集合中是否需要扩容,如果需要扩容则直接进行扩容
        ensureCapacityInternal(size + numNew);
        //当前数组中的元素插入到当前集合中,当前集合中需要移动的元素个数
        int numMoved = size - index;
        if (numMoved > 0)
            //elementData中从index索引开始,移动numMoved元素个数到elementData的index+numNew索引位置
            System.arraycopy(elementData, index, elementData, index + numNew, numMoved);
        //将源数组a中的元素从0索引的位置开始拷贝,拷贝长度为numNew,拷贝到目标数组elementData,放置到目标数组中的index索引位置
        System.arraycopy(a, 0, elementData, index, numNew);
        //更新当前集合长度
        size += numNew;
        return numNew != 0;
    }

    /**
     * 删除指定范围中的元素
     * @param fromIndex 开始索引
     * @param toIndex 结束索引
     */
    protected void removeRange(int fromIndex, int toIndex) {
        //数组修改次数加1
        modCount++;
        //集合中需要移动的元素个数
        int numMoved = size - toIndex;
        //将源数组elementData从toIndex索引位置开始拷贝,拷贝元素数量为numMoved
        //并将拷贝的数组元素放置目标数组的fromIndex位置
        System.arraycopy(elementData, toIndex, elementData, fromIndex, numMoved);
        //集合的新长度
        int newSize = size - (toIndex-fromIndex);
        for (int i = newSize; i < size; i++) {
            //将需要删除的范围元素置为null
            elementData[i] = null;
        }
        //更新集合长度
        size = newSize;
    }

    /**
     * 检查索引是否超出集合最大长度
     * @param index 索引
     */
    private void rangeCheck(int index) {
        if (index >= size)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * 检查索引是否超出集合最大长度或是否小于集合最小长度
     * @param index
     */
    private void rangeCheckForAdd(int index) {
        if (index > size || index < 0)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * Constructs an IndexOutOfBoundsException detail message.
     * Of the many possible refactorings of the error handling code,
     * this "outlining" performs best with both server and client VMs.
     */
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }

    /**
     * 删除当前集合中包含指定集合中的值
     * @param c 当前集合删除的值的集合
     * @return
     */
    public boolean removeAll(Collection<?> c) {
        //校验集合是否为空
        //集合为空则直接报空指针异常
        Objects.requireNonNull(c);
        //false表示删除elementData数组中与集合c中相同的元素
        return batchRemove(c, false);
    }

    public boolean retainAll(Collection<?> c) {
        //校验集合是否为空
        //集合为空则直接报空指针异常
        Objects.requireNonNull(c);
        //true表示删除elementData数组中与集合c中不相同的元素
        return batchRemove(c, true);
    }

    private boolean batchRemove(Collection<?> c, boolean complement) {
        //获取当前集合的数组对象
        final Object[] elementData = this.elementData;
        //r 当前集合读取的索引
        //w 当前集合写入的索引
        int r = 0, w = 0;
        //当前集合是否被修改
        boolean modified = false;
        try {
            //遍历集合中的每一个索引
            for (; r < size; r++)
                //校验集合c中是否包含当前索引的元素
                //complement false:删除c集合中包含的元素  true:删除c集合中不包含的元素
                if (c.contains(elementData[r]) == complement)
                    elementData[w++] = elementData[r];
        } finally {
            if (r != size) {
                //r != size 只有在try代码块里面的代码执行异常才会不相等
                System.arraycopy(elementData, r, elementData, w, size - r);
                //更新集合写入索引
                w += size - r;
            }
            if (w != size) {
                //w != size则说明有元素需要删除
                for (int i = w; i < size; i++)
                    //遍历将包含w索引位置后的元素置为null
                    elementData[i] = null;
                //更新集合修改次数
                modCount += size - w;
                //更新集合长度
                size = w;
                modified = true;
            }
        }
        return modified;
    }

    /**
     * Save the state of the <tt>ArrayList</tt> instance to a stream (that
     * is, serialize it).
     *
     * @serialData The length of the array backing the <tt>ArrayList</tt>
     *             instance is emitted (int), followed by all of its elements
     *             (each an <tt>Object</tt>) in the proper order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException{
        // Write out element count, and any hidden stuff
        int expectedModCount = modCount;
        s.defaultWriteObject();

        // Write out size as capacity for behavioural compatibility with clone()
        s.writeInt(size);

        // Write out all elements in the proper order.
        for (int i=0; i<size; i++) {
            s.writeObject(elementData[i]);
        }

        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    /**
     * Reconstitute the <tt>ArrayList</tt> instance from a stream (that is,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        elementData = EMPTY_ELEMENTDATA;

        // Read in size, and any hidden stuff
        s.defaultReadObject();

        // Read in capacity
        s.readInt(); // ignored

        if (size > 0) {
            // be like clone(), allocate array based upon size not capacity
            int capacity = calculateCapacity(elementData, size);
            SharedSecrets.getJavaOISAccess().checkArray(s, Object[].class, capacity);
            ensureCapacityInternal(size);

            Object[] a = elementData;
            // Read in all elements in the proper order.
            for (int i=0; i<size; i++) {
                a[i] = s.readObject();
            }
        }
    }

    /**
     * 返回指定索引后的所有元素的迭代器
     * @param index 指定索引
     * @return
     */
    public ListIterator<E> listIterator(int index) {
        if (index < 0 || index > size)
            throw new IndexOutOfBoundsException("Index: "+index);
        return new ListItr(index);
    }

    /**
     * 返回当前集合的迭代器
     * @return
     */
    public ListIterator<E> listIterator() {
        return new ListItr(0);
    }

    /**
     * 返回当前集合的迭代器
     * @return
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * An optimized version of AbstractList.Itr
     */
    private class Itr implements Iterator<E> {
        //下一个元素的索引
        int cursor;
        //最左元素的索引
        int lastRet = -1;
        //预期修改次数
        int expectedModCount = modCount;

        Itr() {}

        //下一个元素的索引是否不等于集合长度
        public boolean hasNext() {
            return cursor != size;
        }

        //获取元素
        @SuppressWarnings("unchecked")
        public E next() {
            //校验当前集合最近是否被修改过
            checkForComodification();
            //当前获取元素的索引
            int i = cursor;
            //校验当前元素索引是否超出集合最大长度
            if (i >= size)
                throw new NoSuchElementException();
            //获取当前集合的数组对象
            Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length)
                //如果当前获取元素的索引超出数组长度则说明数组元素被修改
                throw new ConcurrentModificationException();
            //索引+1,等待获取下一个索引的元素
            cursor = i + 1;
            //返回最左边索引的元素
            return (E) elementData[lastRet = i];
        }

        //删除集合中的元素
        public void remove() {
            //校验最左元素索引是否小于0
            //remove方法不能在一开始直接调用,必须在next方法调用之后才能调用
            if (lastRet < 0)
                throw new IllegalStateException();
            //校验当前集合最近是否被修改过
            checkForComodification();

            try {
                //调用remove方法删除指定索引位置的元素
                ArrayList.this.remove(lastRet);
                //将指定索引位置的元素删除之后,后续的元素则会往前移动一位,下一个元素的索引位置则是被删除的元素索引位置
                cursor = lastRet;
                lastRet = -1;
                //更新预期修改次数
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        //遍历剩余元素
        @Override
        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super E> consumer) {
            //校验参数是否为null
            Objects.requireNonNull(consumer);
            //集合长度
            final int size = ArrayList.this.size;
            //当前获取的元素索引
            int i = cursor;
            if (i >= size) {
                return;
            }
            //获取集合中的数组对象
            final Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length) {
                //如果当前获取元素的索引超出数组长度则说明数组元素被修改
                throw new ConcurrentModificationException();
            }
            //当前元素索引为超出集合长度并且预期的集合修改次数与集合修改次数相同则继续执行
            while (i != size && modCount == expectedModCount) {
                //调用传递的方法执行
                consumer.accept((E) elementData[i++]);
            }
            // update once at end of iteration to reduce heap write traffic
            cursor = i;
            lastRet = i - 1;
            //校验当前集合最近是否被修改过
            checkForComodification();
        }

        //校验预期的修改次数是否与当前集合的修改次数相同
        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    /**
     * An optimized version of AbstractList.ListItr
     */
    private class ListItr extends Itr implements ListIterator<E> {
        ListItr(int index) {
            super();
            cursor = index;
        }

        //判断当前集合是否有元素
        public boolean hasPrevious() {
            return cursor != 0;
        }

        //下一个元素的索引位置
        public int nextIndex() {
            return cursor;
        }

        //当前元素的索引位置
        public int previousIndex() {
            return cursor - 1;
        }

        @SuppressWarnings("unchecked")
        public E previous() {
            checkForComodification();
            int i = cursor - 1;
            if (i < 0)
                throw new NoSuchElementException();
            Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length)
                throw new ConcurrentModificationException();
            cursor = i;
            return (E) elementData[lastRet = i];
        }

        public void set(E e) {
            if (lastRet < 0)
                throw new IllegalStateException();
            checkForComodification();

            try {
                ArrayList.this.set(lastRet, e);
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        public void add(E e) {
            checkForComodification();

            try {
                int i = cursor;
                ArrayList.this.add(i, e);
                cursor = i + 1;
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * 按指定的起始索引位置获取子集合
     * @param fromIndex 开始索引位置
     * @param toIndex   结束索引位置(不包含结束索引位置)
     * @return
     */
    public List<E> subList(int fromIndex, int toIndex) {
        //检查起始索引位置是否在正确的范围内
        subListRangeCheck(fromIndex, toIndex, size);
        return new SubList(this, 0, fromIndex, toIndex);
    }

    static void subListRangeCheck(int fromIndex, int toIndex, int size) {
        if (fromIndex < 0)
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        if (toIndex > size)
            throw new IndexOutOfBoundsException("toIndex = " + toIndex);
        if (fromIndex > toIndex)
            throw new IllegalArgumentException("fromIndex(" + fromIndex +
                                               ") > toIndex(" + toIndex + ")");
    }

    private class SubList extends AbstractList<E> implements RandomAccess {
        private final AbstractList<E> parent;
        private final int parentOffset;
        private final int offset;
        int size;

        SubList(AbstractList<E> parent,
                int offset, int fromIndex, int toIndex) {
            this.parent = parent;
            this.parentOffset = fromIndex;
            this.offset = offset + fromIndex;
            this.size = toIndex - fromIndex;
            this.modCount = ArrayList.this.modCount;
        }

        public E set(int index, E e) {
            rangeCheck(index);
            checkForComodification();
            E oldValue = ArrayList.this.elementData(offset + index);
            ArrayList.this.elementData[offset + index] = e;
            return oldValue;
        }

        public E get(int index) {
            rangeCheck(index);
            checkForComodification();
            return ArrayList.this.elementData(offset + index);
        }

        public int size() {
            checkForComodification();
            return this.size;
        }

        public void add(int index, E e) {
            rangeCheckForAdd(index);
            checkForComodification();
            parent.add(parentOffset + index, e);
            this.modCount = parent.modCount;
            this.size++;
        }

        public E remove(int index) {
            rangeCheck(index);
            checkForComodification();
            E result = parent.remove(parentOffset + index);
            this.modCount = parent.modCount;
            this.size--;
            return result;
        }

        protected void removeRange(int fromIndex, int toIndex) {
            checkForComodification();
            parent.removeRange(parentOffset + fromIndex,
                               parentOffset + toIndex);
            this.modCount = parent.modCount;
            this.size -= toIndex - fromIndex;
        }

        public boolean addAll(Collection<? extends E> c) {
            return addAll(this.size, c);
        }

        public boolean addAll(int index, Collection<? extends E> c) {
            rangeCheckForAdd(index);
            int cSize = c.size();
            if (cSize==0)
                return false;

            checkForComodification();
            parent.addAll(parentOffset + index, c);
            this.modCount = parent.modCount;
            this.size += cSize;
            return true;
        }

        public Iterator<E> iterator() {
            return listIterator();
        }

        public ListIterator<E> listIterator(final int index) {
            checkForComodification();
            rangeCheckForAdd(index);
            final int offset = this.offset;

            return new ListIterator<E>() {
                int cursor = index;
                int lastRet = -1;
                int expectedModCount = ArrayList.this.modCount;

                public boolean hasNext() {
                    return cursor != SubList.this.size;
                }

                @SuppressWarnings("unchecked")
                public E next() {
                    checkForComodification();
                    int i = cursor;
                    if (i >= SubList.this.size)
                        throw new NoSuchElementException();
                    Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length)
                        throw new ConcurrentModificationException();
                    cursor = i + 1;
                    return (E) elementData[offset + (lastRet = i)];
                }

                public boolean hasPrevious() {
                    return cursor != 0;
                }

                @SuppressWarnings("unchecked")
                public E previous() {
                    checkForComodification();
                    int i = cursor - 1;
                    if (i < 0)
                        throw new NoSuchElementException();
                    Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length)
                        throw new ConcurrentModificationException();
                    cursor = i;
                    return (E) elementData[offset + (lastRet = i)];
                }

                @SuppressWarnings("unchecked")
                public void forEachRemaining(Consumer<? super E> consumer) {
                    Objects.requireNonNull(consumer);
                    final int size = SubList.this.size;
                    int i = cursor;
                    if (i >= size) {
                        return;
                    }
                    final Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length) {
                        throw new ConcurrentModificationException();
                    }
                    while (i != size && modCount == expectedModCount) {
                        consumer.accept((E) elementData[offset + (i++)]);
                    }
                    // update once at end of iteration to reduce heap write traffic
                    lastRet = cursor = i;
                    checkForComodification();
                }

                public int nextIndex() {
                    return cursor;
                }

                public int previousIndex() {
                    return cursor - 1;
                }

                public void remove() {
                    if (lastRet < 0)
                        throw new IllegalStateException();
                    checkForComodification();

                    try {
                        SubList.this.remove(lastRet);
                        cursor = lastRet;
                        lastRet = -1;
                        expectedModCount = ArrayList.this.modCount;
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                public void set(E e) {
                    if (lastRet < 0)
                        throw new IllegalStateException();
                    checkForComodification();

                    try {
                        ArrayList.this.set(offset + lastRet, e);
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                public void add(E e) {
                    checkForComodification();

                    try {
                        int i = cursor;
                        SubList.this.add(i, e);
                        cursor = i + 1;
                        lastRet = -1;
                        expectedModCount = ArrayList.this.modCount;
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                final void checkForComodification() {
                    if (expectedModCount != ArrayList.this.modCount)
                        throw new ConcurrentModificationException();
                }
            };
        }

        public List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size);
            return new SubList(this, offset, fromIndex, toIndex);
        }

        private void rangeCheck(int index) {
            if (index < 0 || index >= this.size)
                throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }

        private void rangeCheckForAdd(int index) {
            if (index < 0 || index > this.size)
                throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }

        private String outOfBoundsMsg(int index) {
            return "Index: "+index+", Size: "+this.size;
        }

        private void checkForComodification() {
            if (ArrayList.this.modCount != this.modCount)
                throw new ConcurrentModificationException();
        }

        public Spliterator<E> spliterator() {
            checkForComodification();
            return new ArrayListSpliterator<E>(ArrayList.this, offset,
                                               offset + this.size, this.modCount);
        }
    }

    /**
     * 对数组中所有的元素按指定的消费行为方法执行
     * @param action 消费行为
     */
    @Override
    public void forEach(Consumer<? super E> action) {
        //校验参数是否为null
        Objects.requireNonNull(action);
        //预期集合的修改次数
        final int expectedModCount = modCount;
        //当前集合的数组
        @SuppressWarnings("unchecked")
        final E[] elementData = (E[]) this.elementData;
        //集合长度
        final int size = this.size;
        //每次循环的时候判断数组有没有被修改并且当前循环的索引是否小于集合长度,一旦大于集合的长度或数组被修改则停止循环
        for (int i=0; modCount == expectedModCount && i < size; i++) {
            //调用消费行为方法执行
            action.accept(elementData[i]);
        }
        //数组被修改则抛出异常
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * list.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, and {@link Spliterator#ORDERED}.
     * Overriding implementations should document the reporting of additional
     * characteristic values.
     *
     * @return a {@code Spliterator} over the elements in this list
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return new ArrayListSpliterator<>(this, 0, -1, 0);
    }

    /** Index-based split-by-two, lazily initialized Spliterator */
    static final class ArrayListSpliterator<E> implements Spliterator<E> {

        /*
         * If ArrayLists were immutable, or structurally immutable (no
         * adds, removes, etc), we could implement their spliterators
         * with Arrays.spliterator. Instead we detect as much
         * interference during traversal as practical without
         * sacrificing much performance. We rely primarily on
         * modCounts. These are not guaranteed to detect concurrency
         * violations, and are sometimes overly conservative about
         * within-thread interference, but detect enough problems to
         * be worthwhile in practice. To carry this out, we (1) lazily
         * initialize fence and expectedModCount until the latest
         * point that we need to commit to the state we are checking
         * against; thus improving precision.  (This doesn't apply to
         * SubLists, that create spliterators with current non-lazy
         * values).  (2) We perform only a single
         * ConcurrentModificationException check at the end of forEach
         * (the most performance-sensitive method). When using forEach
         * (as opposed to iterators), we can normally only detect
         * interference after actions, not before. Further
         * CME-triggering checks apply to all other possible
         * violations of assumptions for example null or too-small
         * elementData array given its size(), that could only have
         * occurred due to interference.  This allows the inner loop
         * of forEach to run without any further checks, and
         * simplifies lambda-resolution. While this does entail a
         * number of checks, note that in the common case of
         * list.stream().forEach(a), no checks or other computation
         * occur anywhere other than inside forEach itself.  The other
         * less-often-used methods cannot take advantage of most of
         * these streamlinings.
         */

        private final ArrayList<E> list;
        private int index; // current index, modified on advance/split
        private int fence; // -1 until used; then one past last index
        private int expectedModCount; // initialized when fence set

        /** Create new spliterator covering the given  range */
        ArrayListSpliterator(ArrayList<E> list, int origin, int fence,
                             int expectedModCount) {
            this.list = list; // OK if null unless traversed
            this.index = origin;
            this.fence = fence;
            this.expectedModCount = expectedModCount;
        }

        private int getFence() { // initialize fence to size on first use
            int hi; // (a specialized variant appears in method forEach)
            ArrayList<E> lst;
            if ((hi = fence) < 0) {
                if ((lst = list) == null)
                    hi = fence = 0;
                else {
                    expectedModCount = lst.modCount;
                    hi = fence = lst.size;
                }
            }
            return hi;
        }

        public ArrayListSpliterator<E> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid) ? null : // divide range in half unless too small
                new ArrayListSpliterator<E>(list, lo, index = mid,
                                            expectedModCount);
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null)
                throw new NullPointerException();
            int hi = getFence(), i = index;
            if (i < hi) {
                index = i + 1;
                @SuppressWarnings("unchecked") E e = (E)list.elementData[i];
                action.accept(e);
                if (list.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            int i, hi, mc; // hoist accesses and checks from loop
            ArrayList<E> lst; Object[] a;
            if (action == null)
                throw new NullPointerException();
            if ((lst = list) != null && (a = lst.elementData) != null) {
                if ((hi = fence) < 0) {
                    mc = lst.modCount;
                    hi = lst.size;
                }
                else
                    mc = expectedModCount;
                if ((i = index) >= 0 && (index = hi) <= a.length) {
                    for (; i < hi; ++i) {
                        @SuppressWarnings("unchecked") E e = (E) a[i];
                        action.accept(e);
                    }
                    if (lst.modCount == mc)
                        return;
                }
            }
            throw new ConcurrentModificationException();
        }

        public long estimateSize() {
            return (long) (getFence() - index);
        }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        //校验参数是否为null
        Objects.requireNonNull(filter);
        //已删除数量
        int removeCount = 0;
        //创建一个当前集合大小的位集,用于存放被删除的元素索引
        final BitSet removeSet = new BitSet(size);
        //数组预期的修改次数
        final int expectedModCount = modCount;
        //集合长度
        final int size = this.size;
        //每次循环的时候判断数组有没有被修改并且当前循环的索引是否小于集合长度,一旦大于集合的长度或数组被修改则停止循环
        for (int i=0; modCount == expectedModCount && i < size; i++) {
            //获取当前遍历到的索引元素
            @SuppressWarnings("unchecked")
            final E element = (E) elementData[i];
            //判断当前索引元素是否满足条件
            if (filter.test(element)) {
                //将元素索引添加到待删除的位集中
                removeSet.set(i);
                //删除数量自增
                removeCount++;
            }
        }
        //数组被修改则抛出异常
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }

        //被删除的元素是否大于0
        final boolean anyToRemove = removeCount > 0;
        if (anyToRemove) {
            //删除之后剩余的元素个数
            final int newSize = size - removeCount;
            for (int i=0, j=0; (i < size) && (j < newSize); i++, j++) {
                //获取下一个比特位为0的索引
                /**
                 * 示例: [0,1,0,1,0]
                 *       0 1 2 3 4
                 * i = 0 获取到的索引0
                 * i = 1 获取到的索引为2
                 * i = 2 获取到的索引为2
                 * i = 3 获取到的索引为4
                 */
                i = removeSet.nextClearBit(i);
                //使用被删除的元素的后一位元素替换删除元素
                elementData[j] = elementData[i];
            }
            for (int k=newSize; k < size; k++) {
                //将数组中末尾重复的元素置为null
                //进了当前循环方法则说明有元素被删除
                //被删除的元素后面的元素则需要向前移动
                //而末尾中的元素向前移动的时候则会多一个重复元素,则需要将末尾中重复的元素置为null
                elementData[k] = null;
            }
            //更新集合长度
            this.size = newSize;
            //数组被修改则抛出异常
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            //数组修改次数加1
            modCount++;
        }

        return anyToRemove;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceAll(UnaryOperator<E> operator) {
        //校验参数是否为null
        Objects.requireNonNull(operator);
        //预期集合的修改次数
        final int expectedModCount = modCount;
        //集合长度
        final int size = this.size;
        //每次循环的时候判断数组有没有被修改并且当前循环的索引是否小于集合长度,一旦大于集合的长度或数组被修改则停止循环
        for (int i=0; modCount == expectedModCount && i < size; i++) {
            //对指定索引中的元素执行运算并将运算的结果替换到当前索引中
            elementData[i] = operator.apply((E) elementData[i]);
        }
        //数组被修改则抛出异常
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
        //集合修改次数加1
        modCount++;
    }

    /**
     * 集合排序,按指定的比较器对集合进行排序
     * @param c 比较器
     */
    @Override
    @SuppressWarnings("unchecked")
    public void sort(Comparator<? super E> c) {
        //预期集合的修改次数
        final int expectedModCount = modCount;
        //对集合进行排序
        Arrays.sort((E[]) elementData, 0, size, c);
        //当前集合修改次数是否与预期的修改次数相同
        if (modCount != expectedModCount) {
            //不相同则说明在排序期间其它线程对当前集合进行了修改
            throw new ConcurrentModificationException();
        }
        //集合修改次数加1
        modCount++;
    }
}
