/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import sun.misc.SharedSecrets;

public class HashMap<K,V> extends AbstractMap<K,V>
    implements Map<K,V>, Cloneable, Serializable {

    private static final long serialVersionUID = 362498820763181265L;

    //默认初始容量大小(16)
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;

    //数组最大容量大小(1073741824)
    static final int MAXIMUM_CAPACITY = 1 << 30;

    //默认的负载因子
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    //当链表中的元素数量为8时则将链表转换为红黑树
    static final int TREEIFY_THRESHOLD = 8;

    //当前红黑树中的元素数量小于等于6时则将红黑树转换为链表
    static final int UNTREEIFY_THRESHOLD = 6;

    /**
     * 最小树化阈值
     * 当链表中的节点数量大于等于8时,如果数组的长度小于最小树化阈值则不会将链表转换为红黑树,而是对数组进行扩容
     * 将链表中的节点分散到别的索引节点中去,只有当数组的长度大于等于最小树化阈值则会将链表转换为红黑树
     */
    static final int MIN_TREEIFY_CAPACITY = 64;

    static class Node<K,V> implements Map.Entry<K,V> {
        //key的hash值
        final int hash;
        //key
        final K key;
        //value
        V value;
        //下一个节点
        Node<K,V> next;

        Node(int hash, K key, V value, Node<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public final K getKey()        { return key; }
        public final V getValue()      { return value; }
        public final String toString() { return key + "=" + value; }

        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public final boolean equals(Object o) {
            if (o == this)
                return true;
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>)o;
                if (Objects.equals(key, e.getKey()) &&
                    Objects.equals(value, e.getValue()))
                    return true;
            }
            return false;
        }
    }

    /* ---------------- Static utilities -------------- */

    /**
     * 获取指定key的hash值
     * @param key
     * @return
     */
    static final int hash(Object key) {
        int h;
        //key如果为null则返回hash值为0
        //key不为null则获取key的hashCode
        //将hashCode二进制无符号右移16位获取到新的二进制
        //将hashCode二进制与hashCode右移16位的二进制做异或运算获取到新的hash值
        //将二进制右移16位的作用主要是让高位与低位做运算减少hash冲突
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    /**
     * Returns x's Class if it is of the form "class C implements
     * Comparable<C>", else null.
     */
    static Class<?> comparableClassFor(Object x) {
        //校验x是否实现了Comparable接口
        if (x instanceof Comparable) {
            //x的Class
            Class<?> c;
            //ts x实现的所有接口的名称
            Type[] ts, as;
            //循环中x实现的单个接口的名称
            Type t;
            ParameterizedType p;
            //校验x是否是String类型
            if ((c = x.getClass()) == String.class)
                //String类型则直接返回
                return c;
            //校验c类实现的所有接口的名称是否不等于null
            if ((ts = c.getGenericInterfaces()) != null) {
                //遍历c类实现的所有接口的名称
                for (int i = 0; i < ts.length; ++i) {
                    if (((t = ts[i]) instanceof ParameterizedType) &&
                        ((p = (ParameterizedType)t).getRawType() == Comparable.class) &&
                        (as = p.getActualTypeArguments()) != null && as.length == 1 && as[0] == c)
                        return c;
                }
            }
        }
        return null;
    }

    /**
     * Returns k.compareTo(x) if x matches kc (k's screened comparable
     * class), else 0.
     */
    @SuppressWarnings({"rawtypes","unchecked"}) // for cast to Comparable
    static int compareComparables(Class<?> kc, Object k, Object x) {
        //根节点的key的class与当前节点的class匹配则进行比较否则返回0
        return (x == null || x.getClass() != kc ? 0 : ((Comparable)k).compareTo(x));
    }

    /**
     * 获取最接近并大于指定容量大小的2的次方
     * @param cap 指定的容量大小
     * @return
     */
    static final int tableSizeFor(int cap) {
        //cap-1可以保证当传入的数是2的次方时,可以返回其本身 例: cap等于16时经过运算最终返回的还是16本身
        //当传入的数不是2的次方时则会返回大于传入的数并且是2的次方的数  例: cap等于17时经过运算最终返回的结果为32
        int n = cap - 1;
        //将n的二进制无符号右移1位并将右移的结果与n进行位或运算获取到新的n的值
        /**
         * 例: n = 16(cap) - 1
         * n的二进制为 1111,将n无符号右移一位获取到新的二进制为0111
         * 将新的二进制与n的二进制进行位或运算,有1则为1,都为0则为0
         * 1111
         * 0111
         * 1111
         */
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        //经过运算n小于0则返回1
        //大于0则判断n是否超出最大容量大小,如果超出最大容量大小则返回最大容量,未超出最大容量大小则返回n+1
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    /* ---------------- Fields -------------- */

    //存放数据的数组
    transient Node<K,V>[] table;

    /**
     * Holds cached entrySet(). Note that AbstractMap fields are used
     * for keySet() and values().
     */
    transient Set<Map.Entry<K,V>> entrySet;

    //数组长度
    transient int size;

    //数组修改次数
    transient int modCount;

    //当数组中的key-value的数量大于threshold则会进行扩容
    int threshold;

    //负载因子
    final float loadFactor;

    /**
     * 根据指定的初始容量大小和指定的负载因子创建一个HashMap
     * @param initialCapacity  初始容量
     * @param loaFactor        负载因子
     */
    public HashMap(int initialCapacity, float loaFactor) {
        if (initialCapacity < 0)
            //指定的初始容量大小小于0则抛出异常
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            //指定的初始容量大小大于最大容量大小则使用最大容量创建
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            //指定的负载因子小于等于0或是非数字则抛出异常
            throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);
        //设置负载因子
        this.loadFactor = loadFactor;
        //获取最接近并大于指定容量大小的2的次方
        this.threshold = tableSizeFor(initialCapacity);
    }

    /**
     * 创建一个指定初始容量大小的HashMap
     * @param initialCapacity  初始容量
     */
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 用默认的初始容量和默认的负载因子创建一个空的HashMap
     */
    public HashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
    }

    /**
     * 使用指定的map中的元素构建一个新的map
     * @param m
     */
    public HashMap(Map<? extends K, ? extends V> m) {
        //使用默认的负载因子
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        //将指定map中的元素添加到当前集合中
        putMapEntries(m, false);
    }

    /**
     * Implements Map.putAll and Map constructor.
     *
     * @param m the map
     * @param evict false when initially constructing this map, else
     * true (relayed to method afterNodeInsertion).
     */
    final void putMapEntries(Map<? extends K, ? extends V> m, boolean evict) {
        //获取集合的长度
        int s = m.size();
        if (s > 0) {
            if (table == null) {
                //当前集合的数组还未初始化时则先对当前集合的阈值进行初始化
                float ft = ((float)s / loadFactor) + 1.0F;
                int t = ((ft < (float)MAXIMUM_CAPACITY) ? (int)ft : MAXIMUM_CAPACITY);
                if (t > threshold)
                    threshold = tableSizeFor(t);
            }
            else if (s > threshold)
                //集合的长度大于当前集合的阈值则对当前集合进行扩容
                resize();
            //循环将集合中的元素添加到当前集合中
            for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
                K key = e.getKey();
                V value = e.getValue();
                putVal(hash(key), key, value, false, evict);
            }
        }
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return size;
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 获取指定key的value
     * @param key
     * @return
     */
    public V get(Object key) {
        Node<K,V> e;
        //先获取key的hash值
        //再根据hash值获取到索引位置上的头节点
        //如果头节点与指定的key和hash相同则返回该节点的value
        //如果不相同则校验头节点是链表还是红黑树
        //如果是红黑树则从红黑树的根节点开始匹配
        //如果是链表则从链表的头节点开始匹配
        //相同则返回该节点的value
        return (e = getNode(hash(key), key)) == null ? null : e.value;
    }

    /**
     * 根据key和hash值获取节点
     * @param hash
     * @param key
     * @return
     */
    final Node<K,V> getNode(int hash, Object key) {
        //存放数据的数组
        Node<K,V>[] tab;
        //first 头节点
        Node<K,V> first, e;
        //n 数组长度
        //k key
        int n; K k;
        //校验数组是否不为空并且根据指定key的hash值获取到的索引位置上的头节点不为空
        if ((tab = table) != null && (n = tab.length) > 0 && (first = tab[(n - 1) & hash]) != null) {
            //校验头节点的hash值是否跟指定key的hash值相同并且key是否也相同
            if (first.hash == hash && ((k = first.key) == key || (key != null && key.equals(k))))
                //相同则返回头节点
                return first;
            //校验头节点中是否还关联着其它节点
            if ((e = first.next) != null) {
                //校验头节点是否是树节点
                if (first instanceof TreeNode)
                    //红黑树
                    //从红黑树的根节点开始匹配
                    //相同则返回该节点
                    return ((TreeNode<K,V>)first).getTreeNode(hash, key);
                //链表
                do {
                    //从链表的头节点开始遍历与指定的key和hash值进行匹配
                    //key和hash值相同则返回该节点
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;
    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the
     * specified key.
     *
     * @param   key   The key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains a mapping for the specified
     * key.
     */
    public boolean containsKey(Object key) {
        return getNode(hash(key), key) != null;
    }

    /**
     * 将指定的key和value相关联并添加到集合中
     * 如果指定的key在集合中存在则将旧的value替换为新的value
     * @param key  键
     * @param value 值
     * @return
     */
    public V put(K key, V value) {
        //hash(key) 获取指定key的hash值
        //将指定的key-value添加到集合中
        return putVal(hash(key), key, value, false, true);
    }

    /**
     *
     * @param hash key的hash值
     * @param key  key
     * @param value value
     * @param onlyIfAbsent 当前位置值已存在,是否替换,false是替换, true不替换
     * @param evict 表是否是在创建模式
     * @return
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
        //存放数据的数组
        Node<K,V>[] tab;
        //待添加元素的原索引位置上的元素
        Node<K,V> p;
        //n 数组长度
        //i 数组索引
        int n, i;
        //校验名称为table的node数组是否为空
        if ((tab = table) == null || (n = tab.length) == 0)
            //node数组为空则调用resize方法初始化
            //将初始化的node数组赋值给tab
            //并将初始化的node数组的长度赋值给n
            n = (tab = resize()).length;
        //使用数组索引长度与hash值进行与运算获取到数组中的一个索引,并获取该索引位置的元素,校验该元素是否为null
        if ((p = tab[i = (n - 1) & hash]) == null)
            //索引位置的元素为null则将待添加的key和value封装成一个节点添加到索引位置上
            tab[i] = newNode(hash, key, value, null);
        else {
            //索引位置上的元素不为null
            Node<K,V> e; K k;
            //校验索引位置上的元素的hash值是否与当前待添加的元素的hash值相同
            //并且索引位置上的key与待添加的key相同
            if (p.hash == hash && ((k = p.key) == key || (key != null && key.equals(k))))
                //hash值相同并且key也相同则根据onlyIfAbsent来决定是否将value进行替换
                e = p;
            //校验索引位置上的元素是否是使用的红黑树
            else if (p instanceof TreeNode)
                //将元素添加到红黑树中
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
            else {
                //将元素添加到链表中
                //binCount 节点数量
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) {
                        //为null则说明已到链表的尾节点
                        //创建一个新的节点并将新的节点添加到链表的尾节点中
                        p.next = newNode(hash, key, value, null);
                        //校验链表的长度是否到达转换红黑树的阈值
                        if (binCount >= TREEIFY_THRESHOLD - 1)
                            //当链表中的节点数量大于等于8时,如果数组的长度小于最小树化阈值则不会将链表转换为红黑树,而是对数组进行扩容
                            //将链表中的节点分散到别的索引节点中去,只有当数组的长度大于等于最小树化阈值则会将链表转换为红黑树
                            treeifyBin(tab, hash);
                        break;
                    }
                    //待添加的元素的key和hash值与链表中每一个节点的key和hash值进行匹配
                    //如果key和hash值相同则退出循环并根据onlyIfAbsent决定是否需要将value替换
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    p = e;
                }
            }
            if (e != null) {
                //旧值
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    //开启了替换元素或者旧值为null并使用新值替换旧值
                    e.value = value;
                afterNodeAccess(e);
                //返回旧值
                return oldValue;
            }
        }
        //数组修改次数加1
        ++modCount;
        //数组长度加1
        //校验数组的长度是否超出扩容阈值,如果超出则调用resize方法进行扩容
        if (++size > threshold)
            //扩容
            resize();
        afterNodeInsertion(evict);
        return null;
    }

    /**
     * 初始化数组
     * @return
     */
    final Node<K,V>[] resize() {
        //旧数组
        Node<K,V>[] oldTab = table;
        //获取旧数组的长度
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        //获取旧数组的扩容阈值
        int oldThr = threshold;
        //新数组的容量大小和阈值
        int newCap, newThr = 0;
        //校验旧数组长度是否大于0
        if (oldCap > 0) {
            //校验旧数组长度是否超出最大容量大小
            if (oldCap >= MAXIMUM_CAPACITY) {
                //旧数组长度超出最大容量大小
                //设置数组的扩容阈值为Integer最大值并返回旧数组不进行扩容
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            //校验旧数组长度扩容两倍之后是否小于最大容量大小
            //并且旧数组长度大于等于默认的初始容量大小16
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY)
                //新数组的阈值
                newThr = oldThr << 1;
        }
        else if (oldThr > 0)
            //进入当前判断语句表示在创建hashMap对象的时候传入了初始容量和负载因子或只传入了初始容量使用了默认的负载因子
            //将阈值赋值给初始容量
            newCap = oldThr;
        else {
            //都为0的情况则是在创建hashMap对象的时候没有传入初始容量和负载因子则使用默认值初始化新的数组
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        if (newThr == 0) {
            //进入当前判断语句表示在创建hashMap对象的时候传入了初始容量和负载因子或只传入了初始容量使用了默认的负载因子或在扩容的时候新数组的长度小于默认初始容量
            //使用新数组的初始容量与传入的负载因子或默认的负载因子计算出新数组的阈值
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                      (int)ft : Integer.MAX_VALUE);
        }
        //更新扩容阈值
        threshold = newThr;
        //创建一个新的node数组
        @SuppressWarnings({"rawtypes","unchecked"})
        Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
        //更新数组对象
        table = newTab;
        //如果旧数组为null则直接返回新创建的数组
        if (oldTab != null) {
            //旧数组不为null则将旧数组中的数据重新索引到新数组中
            for (int j = 0; j < oldCap; ++j) {
                //当前遍历到的节点
                Node<K,V> e;
                //将当前遍历到的节点赋值给e并校验该节点是否不等于null
                if ((e = oldTab[j]) != null) {
                    //将旧数组中当前遍历到的节点置为null
                    oldTab[j] = null;
                    //校验当前遍历到的节点的下一个节点是否为null
                    if (e.next == null)
                        //为null表示当前节点还不是链表或红黑数
                        //使用当前节点的hash值与新数组的索引长度进行与运算获取到当前节点在新数组中的索引
                        //并将当前节点放置新数组中的索引位置
                        //获取到的新数组的索引位置要么与旧数组的索引位置相同要么则是旧数组的索引位置加上旧数组的容量大小
                        newTab[e.hash & (newCap - 1)] = e;
                    else if (e instanceof TreeNode)
                        //当前遍历到的节点是红黑数
                        //将红黑树节点分为低位和高位,如果低位和高位都有节点则将判断节点的长度是否小于等于6
                        //如果小于等于6则将红黑树转换为链表并将链表的头节点添加到数组中
                        //如果不小于等于6则将低位和高位的节点转换为红黑树并添加到数组中
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                    else {
                        //当前遍历到的节点是单向链表
                        //低位头节点和尾节点
                        Node<K,V> loHead = null, loTail = null;
                        //高位头节点和尾节点
                        Node<K,V> hiHead = null, hiTail = null;
                        //下一个节点
                        Node<K,V> next;
                        do {
                            //获取下一个节点
                            next = e.next;
                            //使用当前节点的hash值与旧数组的长度进行与运算
                            //计算结果为0则将节点放入到新数组中与在旧数组中的索引位置相同
                            //计算结果不为0则将节点放入到新数组中的其它索引位置,新数组的索引位置的计算方式为旧数组中的索引位置加上旧数组的容量大小
                            if ((e.hash & oldCap) == 0) {
                                if (loTail == null)
                                    //低位尾节点指针为null则将低位头节点指针指向当前节点
                                    loHead = e;
                                else
                                    //低位尾节点不为null则将低位尾节点的下一个节点的指针指向当前节点
                                    loTail.next = e;
                                //将低位尾节点指针指向当前节点
                                loTail = e;
                            }else {
                                if (hiTail == null)
                                    //高位尾节点指针为null则将高位头节点指针指向当前节点
                                    hiHead = e;
                                else
                                    //高位尾节点不为null则将高位尾节点的下一个节点的指针指向当前节点
                                    hiTail.next = e;
                                //将高位尾节点指针指向当前节点
                                hiTail = e;
                            }
                            //下一个节点为null则说明当前索引位置的链表节点已经处理完毕
                            //下一个节点不为null则继续处理当前索引位置的链表节点
                        } while ((e = next) != null);
                        if (loTail != null) {
                            //将低位尾节点的下一个节点的指针置为null
                            loTail.next = null;
                            //将低位头节点放入到新数组中与在旧数组中的索引位置相同
                            newTab[j] = loHead;
                        }
                        if (hiTail != null) {
                            //将高位尾节点的下一个节点的指针置为null
                            hiTail.next = null;
                            //将高位头节点放入到新数组中在旧数组中的索引位置加上旧数组的容量大小的索引位置
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }

    /**
     * Replaces all linked nodes in bin at index for given hash unless
     * table is too small, in which case resizes instead.
     */
    final void treeifyBin(Node<K,V>[] tab, int hash) {
        //n 数组长度
        //index hash值所在的索引位置
        int n, index;
        Node<K,V> e;
        //校验数组是否为空或者数组的长度小于最小树化阈值
        if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
            //数组为空或者数组的长度小于最小树化阈值则对数组进行扩容
            resize();
        //使用数组索引长度与hash值进行与运算获取到hash值所在的索引位置
        //并根据索引获取到该索引位置的头节点
        else if ((e = tab[index = (n - 1) & hash]) != null) {
            //hd 头节点
            //tl 尾节点
            TreeNode<K,V> hd = null, tl = null;
            do {
                //循环将数组中的index索引位置的单向链表中的所有普通节点转换为双向链表的树节点

                //将普通节点转换为树节点
                TreeNode<K,V> p = replacementTreeNode(e, null);
                if (tl == null)
                    //尾节点为null则将当前树节点设置为头节点
                    hd = p;
                else {
                    //将当前树节点的prev指针指向上一个树节点
                    p.prev = tl;
                    //将上一个树节点的next指针指向当前树节点
                    tl.next = p;
                }
                tl = p;
            } while ((e = e.next) != null);
            //将双向链表中的树节点的头节点添加到index位置
            if ((tab[index] = hd) != null)
                //将双向链表转换为红黑树
                hd.treeify(tab);
        }
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     * These mappings will replace any mappings that this map had for
     * any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     * @throws NullPointerException if the specified map is null
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        putMapEntries(m, true);
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     *         (A <tt>null</tt> return can also indicate that the map
     *         previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    public V remove(Object key) {
        Node<K,V> e;
        return (e = removeNode(hash(key), key, null, false, true)) == null ? null : e.value;
    }

    /**
     * Implements Map.remove and related methods.
     *
     * @param hash hash for key
     * @param key the key
     * @param value the value to match if matchValue, else ignored
     * @param matchValue if true only remove if value is equal
     * @param movable if false do not move other nodes while removing
     * @return the node, or null if none
     */
    final Node<K,V> removeNode(int hash, Object key, Object value, boolean matchValue, boolean movable) {
        Node<K,V>[] tab;
        Node<K,V> p;
        int n, index;
        //校验数组是否不为空并且根据指定key的hash值获取到的索引位置上的节点不为空
        if ((tab = table) != null && (n = tab.length) > 0 && (p = tab[index = (n - 1) & hash]) != null) {
            //待删除节点
            Node<K,V> node = null, e;
            //待删除的key
            K k;
            //待删除的value
            V v;
            //校验删除的key和hash值是否与索引位置上的节点的key和hash值相同
            if (p.hash == hash && ((k = p.key) == key || (key != null && key.equals(k))))
                //相同则将该节点设置为待删除
                node = p;
            //校验该节点是否关联着其它节点
            else if ((e = p.next) != null) {
                //校验该节点是否是树节点
                if (p instanceof TreeNode)
                    //红黑树
                    //从红黑树的根节点开始匹配
                    //key和hash值相同则返回该节点,将该节点设置为待删除
                    node = ((TreeNode<K,V>)p).getTreeNode(hash, key);
                else {
                    //链表
                    do {
                        //从链表的头节点开始匹配
                        if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                            node = e;
                            break;
                        }
                        p = e;
                    } while ((e = e.next) != null);
                }
            }
            //校验待删除的节点是否不为null并且value是否在不相等的情况下删除
            if (node != null && (!matchValue || (v = node.value) == value || (value != null && value.equals(v)))) {
                if (node instanceof TreeNode)
                    //待删除节点为红黑树
                    //从红黑树中删除该节点
                    ((TreeNode<K,V>)node).removeTreeNode(this, tab, movable);
                else if (node == p)
                    //待删除节点为普通节点
                    //将待删除节点的下一个节点设置为头节点
                    //待删除节点的下一个节点为null,所有说则是将该节点所在的索引位置置为null
                    tab[index] = node.next;
                else
                    //待删除节点为链表
                    //将待删除节点的下一个节点设置为待删除节点的父节点的下一个节点
                    p.next = node.next;
                //更新集合修改次数和集合长度
                ++modCount;
                --size;
                afterNodeRemoval(node);
                //返回被删除的节点
                return node;
            }
        }
        return null;
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    public void clear() {
        Node<K,V>[] tab;
        modCount++;
        if ((tab = table) != null && size > 0) {
            //将集合长度置为0
            size = 0;
            //循环将数组中的节点置为null
            for (int i = 0; i < tab.length; ++i)
                tab[i] = null;
        }
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value.
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value
     */
    public boolean containsValue(Object value) {
        Node<K,V>[] tab;
        V v;
        if ((tab = table) != null && size > 0) {
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K,V> e = tab[i]; e != null; e = e.next) {
                    if ((v = e.value) == value || (value != null && value.equals(v)))
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or <tt>addAll</tt>
     * operations.
     *
     * @return a set view of the keys contained in this map
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new KeySet();
            keySet = ks;
        }
        return ks;
    }

    final class KeySet extends AbstractSet<K> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<K> iterator()     { return new KeyIterator(); }
        public final boolean contains(Object o) { return containsKey(o); }
        public final boolean remove(Object key) {
            return removeNode(hash(key), key, null, false, true) != null;
        }
        public final Spliterator<K> spliterator() {
            return new KeySpliterator<>(HashMap.this, 0, -1, 0, 0);
        }
        public final void forEach(Consumer<? super K> action) {
            Node<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (int i = 0; i < tab.length; ++i) {
                    for (Node<K,V> e = tab[i]; e != null; e = e.next)
                        action.accept(e.key);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own <tt>remove</tt> operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt> and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a view of the values contained in this map
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new Values();
            values = vs;
        }
        return vs;
    }

    final class Values extends AbstractCollection<V> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<V> iterator()     { return new ValueIterator(); }
        public final boolean contains(Object o) { return containsValue(o); }
        public final Spliterator<V> spliterator() {
            return new ValueSpliterator<>(HashMap.this, 0, -1, 0, 0);
        }
        public final void forEach(Consumer<? super V> action) {
            Node<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (int i = 0; i < tab.length; ++i) {
                    for (Node<K,V> e = tab[i]; e != null; e = e.next)
                        action.accept(e.value);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation, or through the
     * <tt>setValue</tt> operation on a map entry returned by the
     * iterator) the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt> and
     * <tt>clear</tt> operations.  It does not support the
     * <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es;
        return (es = entrySet) == null ? (entrySet = new EntrySet()) : es;
    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            Object key = e.getKey();
            Node<K,V> candidate = getNode(hash(key), key);
            return candidate != null && candidate.equals(e);
        }
        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return removeNode(hash(key), key, value, true, true) != null;
            }
            return false;
        }
        public final Spliterator<Map.Entry<K,V>> spliterator() {
            return new EntrySpliterator<>(HashMap.this, 0, -1, 0, 0);
        }
        public final void forEach(Consumer<? super Map.Entry<K,V>> action) {
            Node<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (int i = 0; i < tab.length; ++i) {
                    for (Node<K,V> e = tab[i]; e != null; e = e.next)
                        action.accept(e);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    // Overrides of JDK8 Map extension methods

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        Node<K,V> e;
        return (e = getNode(hash(key), key)) == null ? defaultValue : e.value;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return putVal(hash(key), key, value, true, true);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return removeNode(hash(key), key, value, true, true) != null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Node<K,V> e;
        V v;
        //根据key和hash值获取key所在的节点并校验节点是否不为null并且校验指定的oldValue是否与节点中的value相同
        if ((e = getNode(hash(key), key)) != null &&
            ((v = e.value) == oldValue || (v != null && v.equals(oldValue)))) {
            //oldValue与节点中的value相同
            //使用newValue替换节点中的value
            e.value = newValue;
            afterNodeAccess(e);
            return true;
        }
        return false;
    }

    @Override
    public V replace(K key, V value) {
        Node<K,V> e;
        //根据key和hash值获取key所在的节点并校验节点是否不为null
        if ((e = getNode(hash(key), key)) != null) {
            //旧值
            V oldValue = e.value;
            //使用新值替换旧值
            e.value = value;
            //该方法在hashMap中没有具体的实现
            afterNodeAccess(e);
            //返回旧值
            return oldValue;
        }
        return null;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (mappingFunction == null)
            throw new NullPointerException();
        //根据key获取hash值
        int hash = hash(key);
        Node<K,V>[] tab;
        //头节点
        Node<K,V> first;
        int n, i;
        int binCount = 0;
        TreeNode<K,V> t = null;
        Node<K,V> old = null;
        if (size > threshold || (tab = table) == null || (n = tab.length) == 0)
            //数组长度到达了阈值或数组为空则对数组进行初始化或扩容
            n = (tab = resize()).length;
        //根据key的hash值获取索引位置上的节点并校验节点是否不为null
        if ((first = tab[i = (n - 1) & hash]) != null) {
            if (first instanceof TreeNode)
                //节点为红黑树
                //从红黑树根节点开始匹配
                old = (t = (TreeNode<K,V>)first).getTreeNode(hash, key);
            else {
                //节点为链表或单节点
                Node<K,V> e = first;
                K k;
                do {
                    //从头节点开始匹配
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
            V oldValue;
            //节点value已存在则直接返回
            if (old != null && (oldValue = old.value) != null) {
                afterNodeAccess(old);
                return oldValue;
            }
        }
        //根据指定的操作获取到新的value
        V v = mappingFunction.apply(key);
        if (v == null) {
            return null;
        } else if (old != null) {
            //将获取到的新value替换旧value
            old.value = v;
            afterNodeAccess(old);
            return v;
        }
        else if (t != null)
            //红黑树的根节点不为null则将指定的key和新的value封装成树节点添加到红黑树中
            t.putTreeVal(this, tab, hash, key, v);
        else {
            //将key和value封装成普通节点添加到指定的索引位置上
            tab[i] = newNode(hash, key, v, first);
            //如果链表中的节点数量大于等于链表转换为红黑树的阈值则将链表转换为红黑树
            if (binCount >= TREEIFY_THRESHOLD - 1)
                treeifyBin(tab, hash);
        }
        ++modCount;
        ++size;
        afterNodeInsertion(true);
        return v;
    }

    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();
        //节点
        Node<K,V> e;
        //节点中的value
        V oldValue;
        //根据key获取hash值
        int hash = hash(key);
        //根据key和hash值获取到key所在的节点并校验节点是否不为null并且节点的value不为null
        if ((e = getNode(hash, key)) != null && (oldValue = e.value) != null) {
            //对节点中的key和value执行指定的操作获取到新的value
            V v = remappingFunction.apply(key, oldValue);
            if (v != null) {
                //新的value不为null则替换节点中的旧value
                e.value = v;
                afterNodeAccess(e);
                return v;
            }
            else
                //新的value为null则删除该节点
                removeNode(hash, key, null, false, true);
        }
        return null;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();
        //根据key获取hash值
        int hash = hash(key);
        //数组
        Node<K,V>[] tab;
        //头节点
        Node<K,V> first;
        //n 数组长度
        int n, i;
        //链表节点数量
        int binCount = 0;
        //红黑树根节点
        TreeNode<K,V> t = null;
        //指定的key所在的节点
        Node<K,V> old = null;
        if (size > threshold || (tab = table) == null || (n = tab.length) == 0)
            //数组长度到达了阈值或数组为空则对数组进行初始化或扩容
            n = (tab = resize()).length;
        //根据key的hash值获取索引位置上的节点并校验节点是否不为null
        if ((first = tab[i = (n - 1) & hash]) != null) {
            if (first instanceof TreeNode)
                //节点为红黑树
                //从红黑树根节点开始匹配
                old = (t = (TreeNode<K,V>)first).getTreeNode(hash, key);
            else {
                //节点为链表或单节点
                Node<K,V> e = first; K k;
                do {
                    //从头节点开始匹配
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
        }
        //获取节点中的value
        V oldValue = (old == null) ? null : old.value;
        //根据指定的操作获取到新的value
        V v = remappingFunction.apply(key, oldValue);
        if (old != null) {
            if (v != null) {
                //将获取到的新value替换旧value
                old.value = v;
                afterNodeAccess(old);
            }
            else
                //新value为null则删除该节点
                removeNode(hash, key, null, false, true);
        }else if (v != null) {
            if (t != null)
                //新value不为null并且红黑树的根节点不为null则将指定的key和新的value封装成树节点添加到红黑树中
                t.putTreeVal(this, tab, hash, key, v);
            else {
                //将key和value封装成普通节点添加到指定的索引位置上
                tab[i] = newNode(hash, key, v, first);
                if (binCount >= TREEIFY_THRESHOLD - 1)
                    //如果链表中的节点数量大于等于链表转换为红黑树的阈值则将链表转换为红黑树
                    treeifyBin(tab, hash);
            }
            ++modCount;
            ++size;
            afterNodeInsertion(true);
        }
        return v;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (value == null)
            throw new NullPointerException();
        if (remappingFunction == null)
            throw new NullPointerException();
        //获取key的hash值
        int hash = hash(key);
        Node<K,V>[] tab;
        //头节点
        Node<K,V> first;
        //n 数组长度
        //i 索引
        int n, i;
        //链表的节点数量
        int binCount = 0;
        //树节点中的根节点
        TreeNode<K,V> t = null;
        Node<K,V> old = null;
        if (size > threshold || (tab = table) == null || (n = tab.length) == 0)
            //数组长度到达了阈值或数组为空则对数组进行初始化或扩容
            n = (tab = resize()).length;
        //根据key的hash值获取到key所在的索引位置上的头节点
        if ((first = tab[i = (n - 1) & hash]) != null) {
            if (first instanceof TreeNode)
                //索引位置上的节点为红黑树节点
                //根据hash值和key从红黑树根节点开始匹配获取到匹配的节点
                old = (t = (TreeNode<K,V>)first).getTreeNode(hash, key);
            else {
                //索引位置上的节点是链表节点或者是单独的一个节点
                Node<K,V> e = first;
                K k;
                do {
                    //从头节点开始遍历进行匹配
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
        }
        if (old != null) {
            //节点已存在
            V v;
            if (old.value != null)
                //key所在的节点中的value不为null则根据传递的方法执行操作
                v = remappingFunction.apply(old.value, value);
            else
                v = value;
            if (v != null) {
                //将节点中的旧值替换为新值
                old.value = v;
                afterNodeAccess(old);
            }
            else
                //新值为null则将该节点删除
                removeNode(hash, key, null, false, true);
            return v;
        }
        //节点不存在则将节点添加到集合中
        if (value != null) {
            if (t != null)
                //根节点不为null则将key和value封装成树节点添加到红黑树中
                t.putTreeVal(this, tab, hash, key, value);
            else {
                //根节点不存在则将key和value封装成一个普通的节点并添加到数组中指定的索引位置上
                tab[i] = newNode(hash, key, value, first);
                //校验指定的索引位置上的链表节点数量是否大于等于链表转换为红黑树的阈值
                //从代码来看该if判断中的代码是不会执行的
                if (binCount >= TREEIFY_THRESHOLD - 1)
                    //将链表转换为红黑树
                    treeifyBin(tab, hash);
            }
            ++modCount;
            ++size;
            afterNodeInsertion(true);
        }
        return value;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Node<K,V>[] tab;
        if (action == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K,V> e = tab[i]; e != null; e = e.next)
                    action.accept(e.key, e.value);
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Node<K,V>[] tab;
        //校验函数是否为null
        //如果函数为null则抛出异常
        if (function == null)
            throw new NullPointerException();
        //校验集合是否不为null
        if (size > 0 && (tab = table) != null) {
            //预期的修改次数
            int mc = modCount;
            //遍历每一个节点并对节点中value执行操作
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K,V> e = tab[i]; e != null; e = e.next) {
                    e.value = function.apply(e.key, e.value);
                }
            }
            //如果当前集合的修改次数与预期的修改次数不相同则抛出并发修改异常
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    /* ------------------------------------------------------------ */
    // Cloning and serialization

    /**
     * Returns a shallow copy of this <tt>HashMap</tt> instance: the keys and
     * values themselves are not cloned.
     *
     * @return a shallow copy of this map
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object clone() {
        HashMap<K,V> result;
        try {
            result = (HashMap<K,V>)super.clone();
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
        result.reinitialize();
        result.putMapEntries(this, false);
        return result;
    }

    // These methods are also used when serializing HashSets
    final float loadFactor() { return loadFactor; }
    final int capacity() {
        return (table != null) ? table.length :
            (threshold > 0) ? threshold :
            DEFAULT_INITIAL_CAPACITY;
    }

    /**
     * Save the state of the <tt>HashMap</tt> instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <i>capacity</i> of the HashMap (the length of the
     *             bucket array) is emitted (int), followed by the
     *             <i>size</i> (an int, the number of key-value
     *             mappings), followed by the key (Object) and value (Object)
     *             for each key-value mapping.  The key-value mappings are
     *             emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws IOException {
        int buckets = capacity();
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();
        s.writeInt(buckets);
        s.writeInt(size);
        internalWriteEntries(s);
    }

    /**
     * Reconstitutes this map from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws IOException if an I/O error occurs
     */
    private void readObject(ObjectInputStream s)
        throws IOException, ClassNotFoundException {

        ObjectInputStream.GetField fields = s.readFields();

        // Read loadFactor (ignore threshold)
        float lf = fields.get("loadFactor", 0.75f);
        if (lf <= 0 || Float.isNaN(lf))
            throw new InvalidObjectException("Illegal load factor: " + lf);

        lf = Math.min(Math.max(0.25f, lf), 4.0f);
        HashMap.UnsafeHolder.putLoadFactor(this, lf);

        reinitialize();

        s.readInt();                // Read and ignore number of buckets
        int mappings = s.readInt(); // Read number of mappings (size)
        if (mappings < 0) {
            throw new InvalidObjectException("Illegal mappings count: " + mappings);
        } else if (mappings == 0) {
            // use defaults
        } else if (mappings > 0) {
            float fc = (float)mappings / lf + 1.0f;
            int cap = ((fc < DEFAULT_INITIAL_CAPACITY) ?
                       DEFAULT_INITIAL_CAPACITY :
                       (fc >= MAXIMUM_CAPACITY) ?
                       MAXIMUM_CAPACITY :
                       tableSizeFor((int)fc));
            float ft = (float)cap * lf;
            threshold = ((cap < MAXIMUM_CAPACITY && ft < MAXIMUM_CAPACITY) ?
                         (int)ft : Integer.MAX_VALUE);

            // Check Map.Entry[].class since it's the nearest public type to
            // what we're actually creating.
            SharedSecrets.getJavaOISAccess().checkArray(s, Map.Entry[].class, cap);
            @SuppressWarnings({"rawtypes","unchecked"})
            Node<K,V>[] tab = (Node<K,V>[])new Node[cap];
            table = tab;

            // Read the keys and values, and put the mappings in the HashMap
            for (int i = 0; i < mappings; i++) {
                @SuppressWarnings("unchecked")
                    K key = (K) s.readObject();
                @SuppressWarnings("unchecked")
                    V value = (V) s.readObject();
                putVal(hash(key), key, value, false, false);
            }
        }
    }

    // Support for resetting final field during deserializing
    private static final class UnsafeHolder {
        private UnsafeHolder() { throw new InternalError(); }
        private static final sun.misc.Unsafe unsafe
                = sun.misc.Unsafe.getUnsafe();
        private static final long LF_OFFSET;
        static {
            try {
                LF_OFFSET = unsafe.objectFieldOffset(HashMap.class.getDeclaredField("loadFactor"));
            } catch (NoSuchFieldException nfe) {
                throw new InternalError();
            }
        }
        static void putLoadFactor(HashMap<?, ?> map, float lf) {
            unsafe.putFloat(map, LF_OFFSET, lf);
        }
    }

    /* ------------------------------------------------------------ */
    // iterators

    abstract class HashIterator {
        Node<K,V> next;        // next entry to return
        Node<K,V> current;     // current entry
        int expectedModCount;  // for fast-fail
        int index;             // current slot

        HashIterator() {
            expectedModCount = modCount;
            Node<K,V>[] t = table;
            current = next = null;
            index = 0;
            if (t != null && size > 0) { // advance to first entry
                do {} while (index < t.length && (next = t[index++]) == null);
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Node<K,V> nextNode() {
            Node<K,V>[] t;
            Node<K,V> e = next;
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (e == null)
                throw new NoSuchElementException();
            if ((next = (current = e).next) == null && (t = table) != null) {
                do {} while (index < t.length && (next = t[index++]) == null);
            }
            return e;
        }

        public final void remove() {
            Node<K,V> p = current;
            if (p == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            current = null;
            K key = p.key;
            removeNode(hash(key), key, null, false, false);
            expectedModCount = modCount;
        }
    }

    final class KeyIterator extends HashIterator
        implements Iterator<K> {
        public final K next() { return nextNode().key; }
    }

    final class ValueIterator extends HashIterator
        implements Iterator<V> {
        public final V next() { return nextNode().value; }
    }

    final class EntryIterator extends HashIterator
        implements Iterator<Map.Entry<K,V>> {
        public final Map.Entry<K,V> next() { return nextNode(); }
    }

    /* ------------------------------------------------------------ */
    // spliterators

    static class HashMapSpliterator<K,V> {
        final HashMap<K,V> map;
        Node<K,V> current;          // current node
        int index;                  // current index, modified on advance/split
        int fence;                  // one past last index
        int est;                    // size estimate
        int expectedModCount;       // for comodification checks

        HashMapSpliterator(HashMap<K,V> m, int origin,
                           int fence, int est,
                           int expectedModCount) {
            this.map = m;
            this.index = origin;
            this.fence = fence;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getFence() { // initialize fence and size on first use
            int hi;
            if ((hi = fence) < 0) {
                HashMap<K,V> m = map;
                est = m.size;
                expectedModCount = m.modCount;
                Node<K,V>[] tab = m.table;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            return hi;
        }

        public final long estimateSize() {
            getFence(); // force init
            return (long) est;
        }
    }

    static final class KeySpliterator<K,V>
        extends HashMapSpliterator<K,V>
        implements Spliterator<K> {
        KeySpliterator(HashMap<K,V> m, int origin, int fence, int est,
                       int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public KeySpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                new KeySpliterator<>(map, lo, index = mid, est >>>= 1,
                                        expectedModCount);
        }

        public void forEachRemaining(Consumer<? super K> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Node<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K,V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p.key);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K,V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        K k = current.key;
                        current = current.next;
                        action.accept(k);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) |
                Spliterator.DISTINCT;
        }
    }

    static final class ValueSpliterator<K,V>
        extends HashMapSpliterator<K,V>
        implements Spliterator<V> {
        ValueSpliterator(HashMap<K,V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public ValueSpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                new ValueSpliterator<>(map, lo, index = mid, est >>>= 1,
                                          expectedModCount);
        }

        public void forEachRemaining(Consumer<? super V> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Node<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K,V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p.value);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super V> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K,V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        V v = current.value;
                        current = current.next;
                        action.accept(v);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0);
        }
    }

    static final class EntrySpliterator<K,V>
        extends HashMapSpliterator<K,V>
        implements Spliterator<Map.Entry<K,V>> {
        EntrySpliterator(HashMap<K,V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public EntrySpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                new EntrySpliterator<>(map, lo, index = mid, est >>>= 1,
                                          expectedModCount);
        }

        public void forEachRemaining(Consumer<? super Map.Entry<K,V>> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Node<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K,V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<K,V>> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K,V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        Node<K,V> e = current;
                        current = current.next;
                        action.accept(e);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) |
                Spliterator.DISTINCT;
        }
    }

    /* ------------------------------------------------------------ */
    // LinkedHashMap support


    /*
     * The following package-protected methods are designed to be
     * overridden by LinkedHashMap, but not by any other subclass.
     * Nearly all other internal methods are also package-protected
     * but are declared final, so can be used by LinkedHashMap, view
     * classes, and HashSet.
     */

    // Create a regular (non-tree) node
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> next) {
        return new Node<>(hash, key, value, next);
    }

    // For conversion from TreeNodes to plain nodes
    Node<K,V> replacementNode(Node<K,V> p, Node<K,V> next) {
        return new Node<>(p.hash, p.key, p.value, next);
    }

    // Create a tree bin node
    TreeNode<K,V> newTreeNode(int hash, K key, V value, Node<K,V> next) {
        return new TreeNode<>(hash, key, value, next);
    }

    // For treeifyBin
    TreeNode<K,V> replacementTreeNode(Node<K,V> p, Node<K,V> next) {
        return new TreeNode<>(p.hash, p.key, p.value, next);
    }

    /**
     * Reset to initial default state.  Called by clone and readObject.
     */
    void reinitialize() {
        table = null;
        entrySet = null;
        keySet = null;
        values = null;
        modCount = 0;
        threshold = 0;
        size = 0;
    }

    // Callbacks to allow LinkedHashMap post-actions
    void afterNodeAccess(Node<K,V> p) { }
    void afterNodeInsertion(boolean evict) { }
    void afterNodeRemoval(Node<K,V> p) { }

    // Called only from writeObject, to ensure compatible ordering.
    void internalWriteEntries(java.io.ObjectOutputStream s) throws IOException {
        Node<K,V>[] tab;
        if (size > 0 && (tab = table) != null) {
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K,V> e = tab[i]; e != null; e = e.next) {
                    s.writeObject(e.key);
                    s.writeObject(e.value);
                }
            }
        }
    }

    /* ------------------------------------------------------------ */
    // Tree bins

    /**
     * Entry for Tree bins. Extends LinkedHashMap.Entry (which in turn
     * extends Node) so can be used as extension of either regular or
     * linked node.
     */
    static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
        TreeNode<K,V> parent;  // red-black tree links
        TreeNode<K,V> left;
        TreeNode<K,V> right;
        TreeNode<K,V> prev;    // needed to unlink next upon deletion
        boolean red;
        TreeNode(int hash, K key, V val, Node<K,V> next) {
            super(hash, key, val, next);
        }

        /**
         * 获取根节点
         * @return
         */
        final TreeNode<K,V> root() {
            for (TreeNode<K,V> r = this, p;;) {
                if ((p = r.parent) == null)
                    return r;
                r = p;
            }
        }

        /**
         * Ensures that the given root is the first node of its bin.
         */
        static <K,V> void moveRootToFront(Node<K,V>[] tab, TreeNode<K,V> root) {
            //数组长度
            int n;
            //校验根节点是否不为null并且数组不为空
            if (root != null && tab != null && (n = tab.length) > 0) {
                //数组索引长度与根节点的hash进行与运算获取到根节点所在的索引位置
                int index = (n - 1) & root.hash;
                //根据索引获取到该索引位置上的节点
                TreeNode<K,V> first = (TreeNode<K,V>)tab[index];
                //校验根节点是否跟该索引位置上的节点不相同
                if (root != first) {
                    //根节点的下一个节点
                    Node<K,V> rn;
                    //使用根节点替换该索引位置上的节点
                    tab[index] = root;
                    //根节点的上一个节点
                    TreeNode<K,V> rp = root.prev;
                    //校验根节点的下一个节点是否不为null
                    if ((rn = root.next) != null)
                        ((TreeNode<K,V>)rn).prev = rp;
                    if (rp != null)
                        rp.next = rn;
                    if (first != null)
                        first.prev = root;
                    root.next = first;
                    root.prev = null;
                }
                assert checkInvariants(root);
            }
        }

        /**
         * 用给定的key和hash值从根节点开始查找key所在的节点
         */
        final TreeNode<K,V> find(int h, Object k, Class<?> kc) {
            //开始查找的节点
            TreeNode<K,V> p = this;
            do {
                //ph 当前查找到的节点的hash值
                //pk 当前查找到的节点的key
                //dir 方法 -1左子节点 0右子节点
                int ph, dir; K pk;
                //pl 左子节点
                //pr 右子节点
                TreeNode<K,V> pl = p.left, pr = p.right, q;
                if ((ph = p.hash) > h)
                    //当前查找到的节点的hash值大于给定的hash值则下次从当前查找到的节点的左子节点开始查找
                    p = pl;
                else if (ph < h)
                    //当前查找到的节点的hash值小于给定的hash值则下次从当前查找到的节点的右子节点开始查找
                    p = pr;
                else if ((pk = p.key) == k || (k != null && k.equals(pk)))
                    //当前查找到的节点的key与给定的key相同则返回该节点
                    return p;
                else if (pl == null)
                    //左子节点为null则从右子节点开始查找
                    p = pr;
                else if (pr == null)
                    //右子节点为null则从左子节点开始查找
                    p = pl;
                //校验当前节点的key的class类型是否不为空或未实现comparable接口并且父节点的key的class与当前节点的class是否不匹配
                else if ((kc != null || (kc = comparableClassFor(k)) != null) && (dir = compareComparables(kc, k, pk)) != 0)
                    p = (dir < 0) ? pl : pr;
                //方法当前方法从右子节点开始查找,不为null则返回
                else if ((q = pr.find(h, k, kc)) != null)
                    return q;
                else
                    //从左子节点开始查找
                    p = pl;
            } while (p != null);
            return null;
        }

        /**
         * Calls find for root node.
         */
        final TreeNode<K,V> getTreeNode(int h, Object k) {
            return ((parent != null) ? root() : this).find(h, k, null);
        }

        /**
         * Tie-breaking utility for ordering insertions when equal
         * hashCodes and non-comparable. We don't require a total
         * order, just a consistent insertion rule to maintain
         * equivalence across rebalancings. Tie-breaking further than
         * necessary simplifies testing a bit.
         */
        static int tieBreakOrder(Object a, Object b) {
            int d;
            if (a == null || b == null || (d = a.getClass().getName().compareTo(b.getClass().getName())) == 0)
                d = (System.identityHashCode(a) <= System.identityHashCode(b) ? -1 : 1);
            return d;
        }

        /**
         * Forms tree of the nodes linked from this node.
         */
        final void treeify(Node<K,V>[] tab) {
            //根节点
            TreeNode<K,V> root = null;
            //从头节点开始遍历将链表转换为红黑树
            for (TreeNode<K,V> x = this, next; x != null; x = next) {
                //获取当前节点的下一个节点
                next = (TreeNode<K,V>)x.next;
                //初始化左子节点和右子节点
                x.left = x.right = null;
                if (root == null) {
                    //根节点为null则说明当前节点是头节点
                    //将当前节点的父节点置为null
                    x.parent = null;
                    //当前节点的颜色为黑色
                    x.red = false;
                    //将当前节点设置为根节点
                    root = x;
                }else {
                    //当前节点的key
                    K k = x.key;
                    //当前节点的hash值
                    int h = x.hash;
                    //当前节点的key的Class类型
                    Class<?> kc = null;
                    for (TreeNode<K,V> p = root;;) {
                        //ph 父节点的hash值
                        //dir 方向 -1左子节点 0右子节点
                        int dir, ph;
                        //父节点的key
                        K pk = p.key;
                        if ((ph = p.hash) > h)
                            //父节点的hash值大于当前节点的hash值则将当前节点放置根节点的左边
                            dir = -1;
                        else if (ph < h)
                            //父节点的hash值小于当前节点的hash值则将当前节点放置根节点的右边
                            dir = 1;
                        //校验当前节点的key的class类型是否为空并且未实现comparable接口或父节点的key的class与当前节点的class是否匹配
                        else if ((kc == null && (kc = comparableClassFor(k)) == null) || (dir = compareComparables(kc, k, pk)) == 0)
                            //使用父节点的key的hash值与当前节点的key的hash值进行比较
                            dir = tieBreakOrder(k, pk);
                        //父节点
                        TreeNode<K,V> xp = p;
                        //校验当前节点的插入方向并校验该方向的节点是否为null
                        //不为null则说明该节点已经存在则需要继续向下寻找,直到找到空余的节点
                        //为null则将该节点插入
                        if ((p = (dir <= 0) ? p.left : p.right) == null) {
                            //设置当前节点的父节点
                            x.parent = xp;
                            if (dir <= 0)
                                //将父节点的左子节点指针指向当前节点
                                xp.left = x;
                            else
                                //将父节点的右子节点指针指向当前节点
                                xp.right = x;
                            //使红黑树平衡并返回根节点
                            root = balanceInsertion(root, x);
                            break;
                        }
                    }
                }
            }
            //校验root节点是否是头节点,如果不是则将root节点移动到头部
            moveRootToFront(tab, root);
        }

        /**
         * 将红黑树转换为链表
         * @param map
         * @return 链表的头节点
         */
        final Node<K,V> untreeify(HashMap<K,V> map) {
            //hd 头节点
            //tl 尾节点
            Node<K,V> hd = null, tl = null;
            //this 头节点
            //从头节点开始遍历树节点取消树节点之间的关联并将节点使用链表进行关联
            for (Node<K,V> q = this; q != null; q = q.next) {
                //将树节点替换为普通节点
                Node<K,V> p = map.replacementNode(q, null);
                //将普通节点进行关联变成链表
                if (tl == null)
                    //尾节点为null则将当前节点设置为头节点
                    hd = p;
                else
                    //将上一个节点的next指针指向当前节点
                    tl.next = p;
                //将当前节点设置为尾节点
                tl = p;
            }
            //返回头节点
            return hd;
        }

        /**
         * 将元素添加到红黑树
         * @param map  当前集合
         * @param tab 当前数组
         * @param h hash值
         * @param k key
         * @param v value
         * @return
         */
        final TreeNode<K,V> putTreeVal(HashMap<K,V> map, Node<K,V>[] tab, int h, K k, V v) {
            //待添加的key的class类型
            Class<?> kc = null;
            //是否从红黑树中查找过
            boolean searched = false;
            //校验当前节点的父节点是否为null
            //如果父节点为null则当前节点为根节点
            //如果父节点不为null则从当前节点向上寻找根节点
            TreeNode<K,V> root = (parent != null) ? root() : this;
            for (TreeNode<K,V> p = root;;) {
                //dir 方向 -1左子节点 1右子节点
                //ph 父节点的hash值
                //pk 父节点的key
                int dir, ph; K pk;
                //校验父节点的hash值是否大于待添加元素的hash值
                if ((ph = p.hash) > h)
                    //父节点的hash值大于当前节点的hash值则将当前节点放置根节点的左边
                    dir = -1;
                else if (ph < h)
                    //父节点的hash值大于当前节点的hash值则将当前节点放置根节点的右边
                    dir = 1;
                //校验待添加元素的key是否跟父节点的key相同
                //相同则返回父节点,根据onlyIfAbsent来决定是否需要将value替换
                else if ((pk = p.key) == k || (k != null && k.equals(pk)))
                    return p;
                //校验当前节点的key的class类型是否为空并且未实现comparable接口或父节点的key的class与当前节点的class是否匹配
                else if ((kc == null && (kc = comparableClassFor(k)) == null) || (dir = compareComparables(kc, k, pk)) == 0) {
                    if (!searched) {
                        //ch 左子节点或右子节点
                        TreeNode<K,V> q, ch;
                        searched = true;
                        //左子节点或右子节点不为null则在红黑树中从左子节点或右子节点根据给定的key、hash、key的class类型开始查找节点
                        if (((ch = p.left) != null && (q = ch.find(h, k, kc)) != null) || ((ch = p.right) != null && (q = ch.find(h, k, kc)) != null))
                            return q;
                    }
                    //使用父节点的key的hash值与当前节点的key的hash值进行比较
                    dir = tieBreakOrder(k, pk);
                }
                //父节点
                TreeNode<K,V> xp = p;
                //校验当前节点的插入方向并校验该方向的节点是否为null
                //不为null则说明该节点已经存在则需要继续向下寻找,直到找到空余的节点
                //为null则将该节点插入
                if ((p = (dir <= 0) ? p.left : p.right) == null) {
                    //父节点的下一个节点
                    Node<K,V> xpn = xp.next;
                    //创建一个新的树节点
                    TreeNode<K,V> x = map.newTreeNode(h, k, v, xpn);
                    if (dir <= 0)
                        //将父节点的左子节点指针指向新的树节点
                        xp.left = x;
                    else
                        //将父节点的右子节点指针指向新的树节点
                        xp.right = x;
                    //父节点的下一个节点的指针指向新的树节点
                    xp.next = x;
                    //新的树节点的父节点和上一个节点的指针都指向父节点
                    x.parent = x.prev = xp;
                    if (xpn != null)
                        //如果原来的下一个节点不为空则将原来的下一个节点的上一个节点的指针指向新的树节点
                        ((TreeNode<K,V>)xpn).prev = x;
                    //将节点插入到红黑树中并使红黑树平衡
                    //并校验红黑树中的根节点是否变化
                    moveRootToFront(tab, balanceInsertion(root, x));
                    return null;
                }
            }
        }

        /**
         * Removes the given node, that must be present before this call.
         * This is messier than typical red-black deletion code because we
         * cannot swap the contents of an interior node with a leaf
         * successor that is pinned by "next" pointers that are accessible
         * independently during traversal. So instead we swap the tree
         * linkages. If the current tree appears to have too few nodes,
         * the bin is converted back to a plain bin. (The test triggers
         * somewhere between 2 and 6 nodes, depending on tree structure).
         */
        final void removeTreeNode(HashMap<K,V> map, Node<K,V>[] tab, boolean movable) {
            int n;
            if (tab == null || (n = tab.length) == 0)
                return;
            //根据hash值获取到该节点在数组中的索引位置
            int index = (n - 1) & hash;
            //first 头节点
            //root 根节点
            TreeNode<K,V> first = (TreeNode<K,V>)tab[index], root = first, rl;
            //pred 当前待删除节点的上一个节点
            //succ 当前待删除节点的下一个节点
            TreeNode<K,V> succ = (TreeNode<K,V>)next, pred = prev;
            if (pred == null)
                //当前待删除节点的上一个节点为null则说明待删除的节点是头节点
                //则将当前待删除节点的下一个节点设置为头节点
                tab[index] = first = succ;
            else
                //不为null则将当前待删除节点的上一个节点的next指针指向succ
                pred.next = succ;

            if (succ != null)
                //不为null则将当前待删除节点的下一个节点的prev指针指向pred
                succ.prev = pred;

            if (first == null)
                return;

            if (root.parent != null)
                //根据hash值获取到的索引位置上的节点不是根节点则根据该节点寻找根节点
                root = root.root();

            if (root == null || (movable && (root.right == null || (rl = root.left) == null || rl.left == null))) {
                //红黑树节点数量太少则将红黑树转换为单向链表
                tab[index] = first.untreeify(map);
                return;
            }
            //p 当前待删除节点
            //pl 左子节点
            //pr 右子节点
            TreeNode<K,V> p = this, pl = left, pr = right, replacement;
            if (pl != null && pr != null) {
                //s 与删除节点交换位置的节点
                TreeNode<K,V> s = pr, sl;
                //获取右子节点最底层的左子节点
                while ((sl = s.left) != null)
                    //更新交换的节点
                    s = sl;
                //当前待删除节点与右子节点最底层的左子节点交换节点颜色
                //如果右子节点的左子节点为null则将当前待删除节点与右子节点交换节点颜色
                boolean c = s.red; s.red = p.red; p.red = c;
                //如果右子节点的左子节点不为null sr则表示右子节点最底层的左子节点的右子节点
                //如果右子节点的左子节点为null sr则表示右子节点的右子节点
                TreeNode<K,V> sr = s.right;
                //当前待删除节点的父节点
                TreeNode<K,V> pp = p.parent;
                if (s == pr) {
                    // s == pr 说明右子节点的左子节点为null
                    //当前待删除节点的父节点指针指向右子节点
                    p.parent = s;
                    //右子节点的右子节点指针指向待删除节点
                    s.right = p;
                }else {
                    TreeNode<K,V> sp = s.parent;
                    if ((p.parent = sp) != null) {
                        if (s == sp.left)
                            sp.left = p;
                        else
                            sp.right = p;
                    }
                    if ((s.right = pr) != null)
                        pr.parent = s;
                }
                //将待删除节点的左子节点指针置为null
                p.left = null;

                //待删除节点的右子节点指针指向交换的节点的右子节点
                if ((p.right = sr) != null)
                    //交换的节点的右子节点的父节点指针指向待删除节点
                    sr.parent = p;

                //交换的节点的左子节点指针指向待删除节点的左子节点
                if ((s.left = pl) != null)
                    //待删除节点的左子节点的父节点指针指向交换的节点
                    pl.parent = s;

                //交换的节点的父节点指针指向待删除节点的父节点
                if ((s.parent = pp) == null)
                    //如果父节点为null则交换的节点设置为根节点
                    root = s;
                else if (p == pp.left)
                    //将待删除节点的父节点的左子节点的指针指向交换的节点
                    pp.left = s;
                else
                    //将待删除节点的父节点的右子节点的指针指向交换的节点
                    pp.right = s;

                if (sr != null)
                    replacement = sr;
                else
                    replacement = p;
            }else if (pl != null)
                replacement = pl;
            else if (pr != null)
                replacement = pr;
            else
                replacement = p;

            if (replacement != p) {
                TreeNode<K,V> pp = replacement.parent = p.parent;
                if (pp == null)
                    root = replacement;
                else if (p == pp.left)
                    pp.left = replacement;
                else
                    pp.right = replacement;
                //将待删除的节点的所有指针置为null
                p.left = p.right = p.parent = null;
            }
            //平衡红黑树的节点
            TreeNode<K,V> r = p.red ? root : balanceDeletion(root, replacement);

            if (replacement == p) {
                TreeNode<K,V> pp = p.parent;
                p.parent = null;
                if (pp != null) {
                    if (p == pp.left)
                        pp.left = null;
                    else if (p == pp.right)
                        pp.right = null;
                }
            }
            if (movable)
                moveRootToFront(tab, r);
        }

        /**
         * Splits nodes in a tree bin into lower and upper tree bins,
         * or untreeifies if now too small. Called only from resize;
         * see above discussion about split bits and indices.
         *
         * @param map the map
         * @param tab the table for recording bin heads
         * @param index the index of the table being split
         * @param bit the bit of hash to split on
         */
        final void split(HashMap<K,V> map, Node<K,V>[] tab, int index, int bit) {
            TreeNode<K,V> b = this;
            //低位头节点和尾节点
            TreeNode<K,V> loHead = null, loTail = null;
            //高位头节点和尾节点
            TreeNode<K,V> hiHead = null, hiTail = null;
            //高低位节点的长度
            int lc = 0, hc = 0;
            //遍历红黑树中所有节点
            for (TreeNode<K,V> e = b, next; e != null; e = next) {
                //当前遍历到的节点的下一个节点
                next = (TreeNode<K,V>)e.next;
                //将当前节点的下一个节点指针置为null
                e.next = null;
                //使用当前节点的hash值与旧数组的长度进行与运算
                //计算结果为0则将节点放入到新数组中与在旧数组中的索引位置相同
                //计算结果不为0则将节点放入到新数组中的其它索引位置,新数组的索引位置的计算方式为旧数组中的索引位置加上旧数组的容量大小
                if ((e.hash & bit) == 0) {
                    //如果低位尾节点为null则说明第一次执行则将当前节点设置为低位头节点和低位尾节点,并使低位节点长度加1
                    //如果低位尾节点不为null则将当前节点的上一个节点指针指向低位尾节点
                    //并将低位尾节点的下一个节点指针指向当前节点,并将当前节点设置为低位尾节点,并使低位节点长度加1
                    if ((e.prev = loTail) == null)
                        //当前节点设置为低位头节点
                        loHead = e;
                    else
                        //将低位尾节点的下一个节点的指针指向当前节点
                        loTail.next = e;
                    //将当前节点设置为低尾节点
                    loTail = e;
                    //低位节点长度加1
                    ++lc;
                }
                else {
                    //如果高位尾节点为null则说明第一次执行则将当前节点设置为高位头节点和高位尾节点,并使高位节点长度加1
                    //如果高位尾节点不为null则将当前节点的上一个节点指针指向高位尾节点
                    //并将高位尾节点的下一个节点指针指向当前节点,并将当前节点设置为高位尾节点,并使高位节点长度加1
                    if ((e.prev = hiTail) == null)
                        //当前节点设置为高位头节点
                        hiHead = e;
                    else
                        //将高位尾节点的下一个节点的指针指向当前节点
                        hiTail.next = e;
                    //将当前节点设置为高尾节点
                    hiTail = e;
                    //高位节点长度加1
                    ++hc;
                }
            }
            //校验低位头节点是否为null
            if (loHead != null) {
                //校验低位节点长度是否小于等于红黑树转链表的阈值
                if (lc <= UNTREEIFY_THRESHOLD)
                    //小于阈值则将红黑树转换为链表并将低位头节点添加到数组中指定的索引位置
                    tab[index] = loHead.untreeify(map);
                else {
                    //不小于红黑树转换为链表的阈值则将低位头节点添加到数组中指定的索引位置
                    tab[index] = loHead;
                    //校验高位头节点是否为null,如果不为null则说明原先红黑树有改动
                    if (hiHead != null)
                        loHead.treeify(tab);
                }
            }
            if (hiHead != null) {
                if (hc <= UNTREEIFY_THRESHOLD)
                    tab[index + bit] = hiHead.untreeify(map);
                else {
                    tab[index + bit] = hiHead;
                    if (loHead != null)
                        hiHead.treeify(tab);
                }
            }
        }

        /* ------------------------------------------------------------ */
        // Red-black tree methods, all adapted from CLR

        static <K,V> TreeNode<K,V> rotateLeft(TreeNode<K,V> root, TreeNode<K,V> p) {
            //r 右子节点
            //rl 右子节点的左子节点
            TreeNode<K,V> r, pp, rl;
            if (p != null && (r = p.right) != null) {
                if ((rl = p.right = r.left) != null)
                    rl.parent = p;
                if ((pp = r.parent = p.parent) == null)
                    (root = r).red = false;
                else if (pp.left == p)
                    pp.left = r;
                else
                    pp.right = r;
                r.left = p;
                p.parent = r;
            }
            return root;
        }

        /**
         * 节点右旋
         * @param root  根节点
         * @param p
         * @return
         */
        static <K,V> TreeNode<K,V> rotateRight(TreeNode<K,V> root, TreeNode<K,V> p) {
            //l 左子节点
            //lr 左子节点的右子节点
            TreeNode<K,V> l, pp, lr;
            //校验p节点不为null并且p节点的左子节点不为null
            if (p != null && (l = p.left) != null) {

                if ((lr = p.left = l.right) != null)
                    lr.parent = p;

                if ((pp = l.parent = p.parent) == null)
                    (root = l).red = false;

                else if (pp.right == p)
                    pp.right = l;

                else
                    pp.left = l;

                l.right = p;
                p.parent = l;
            }
            return root;
        }

        /**
         * 平衡插入到红黑树
         * @param root 根节点
         * @param x    当前节点
         * @param <K>
         * @param <V>
         * @return
         */
        static <K,V> TreeNode<K,V> balanceInsertion(TreeNode<K,V> root, TreeNode<K,V> x) {
            //设置当前节点的颜色为红色
            x.red = true;
            //xp 当前节点的父节点
            //xpp 父节点的父节点
            //xppl 父节点的父节点的左子节点
            //xppr 父节点的父节点的右子节点
            for (TreeNode<K,V> xp, xpp, xppl, xppr;;) {
                //校验当前节点的父节点是否为null
                if ((xp = x.parent) == null) {
                    //设置当前节点为黑色
                    x.red = false;
                    return x;
                //校验父节点的颜色不为红色或父节点的父节点等于null
                } else if (!xp.red || (xpp = xp.parent) == null)
                    return root;
                //校验父节点是不是左子节点
                if (xp == (xppl = xpp.left)) {
                    //校验父节点的父节点的右子节点是否不为null并且节点颜色为红色
                    if ((xppr = xpp.right) != null && xppr.red) {
                        //将父节点的父节点的右子节点设置为黑色
                        xppr.red = false;
                        //将父节点设置为黑色
                        xp.red = false;
                        //将父节点的父节点设置为红色
                        xpp.red = true;
                        //将父节点的父节点设置为当前节点
                        //继续循环校验当前节点的父节点是否为null,当前节点的父节点为null则退出循环并返回当前节点
                        x = xpp;
                    } else {
                        //校验当前节点是否是右子节点
                        if (x == xp.right) {
                            //将当前节点的父节点进行左旋,使当前节点与父节点在一条线上
                            //左旋完成之后则会调用下面右旋的方法使红黑树平衡
                            root = rotateLeft(root, x = xp);
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        if (xp != null) {
                            //将父节点设置为黑色
                            xp.red = false;
                            if (xpp != null) {
                                //将父节点的父节点设置为红色
                                xpp.red = true;
                                //右旋
                                root = rotateRight(root, xpp);
                            }
                        }
                    }
                } else {
                    //校验父节点的父节点的左子节点是否不为null并且颜色为红色
                    if (xppl != null && xppl.red) {
                        //父节点的父节点的左子节点的颜色设置为黑色
                        xppl.red = false;
                        //父节点设置为黑色
                        xp.red = false;
                        //父节点的父节点设置为红色
                        xpp.red = true;
                        //将父节点的父节点设置为当前节点
                        //继续循环校验当前节点的父节点是否为null,当前节点的父节点为null则退出循环并返回当前节点
                        x = xpp;
                    } else {
                        //校验当前节点是否是父节点的左子节点
                        if (x == xp.left) {
                            //右旋
                            root = rotateRight(root, x = xp);
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        if (xp != null) {
                            //父节点设置为黑色
                            xp.red = false;
                            if (xpp != null) {
                                //父节点的父节点设置为红色
                                xpp.red = true;
                                //左旋
                                root = rotateLeft(root, xpp);
                            }
                        }
                    }
                }
            }
        }

        static <K,V> TreeNode<K,V> balanceDeletion(TreeNode<K,V> root,TreeNode<K,V> x) {
            for (TreeNode<K,V> xp, xpl, xpr;;) {
                if (x == null || x == root)
                    return root;
                else if ((xp = x.parent) == null) {
                    x.red = false;
                    return x;
                }else if (x.red) {
                    x.red = false;
                    return root;
                }else if ((xpl = xp.left) == x) {
                    if ((xpr = xp.right) != null && xpr.red) {
                        xpr.red = false;
                        xp.red = true;
                        root = rotateLeft(root, xp);
                        xpr = (xp = x.parent) == null ? null : xp.right;
                    }
                    if (xpr == null)
                        x = xp;
                    else {
                        TreeNode<K,V> sl = xpr.left, sr = xpr.right;
                        if ((sr == null || !sr.red) &&
                            (sl == null || !sl.red)) {
                            xpr.red = true;
                            x = xp;
                        }
                        else {
                            if (sr == null || !sr.red) {
                                if (sl != null)
                                    sl.red = false;
                                xpr.red = true;
                                root = rotateRight(root, xpr);
                                xpr = (xp = x.parent) == null ?
                                    null : xp.right;
                            }
                            if (xpr != null) {
                                xpr.red = (xp == null) ? false : xp.red;
                                if ((sr = xpr.right) != null)
                                    sr.red = false;
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateLeft(root, xp);
                            }
                            x = root;
                        }
                    }
                }
                else { // symmetric
                    if (xpl != null && xpl.red) {
                        xpl.red = false;
                        xp.red = true;
                        root = rotateRight(root, xp);
                        xpl = (xp = x.parent) == null ? null : xp.left;
                    }
                    if (xpl == null)
                        x = xp;
                    else {
                        TreeNode<K,V> sl = xpl.left, sr = xpl.right;
                        if ((sl == null || !sl.red) &&
                            (sr == null || !sr.red)) {
                            xpl.red = true;
                            x = xp;
                        }
                        else {
                            if (sl == null || !sl.red) {
                                if (sr != null)
                                    sr.red = false;
                                xpl.red = true;
                                root = rotateLeft(root, xpl);
                                xpl = (xp = x.parent) == null ?
                                    null : xp.left;
                            }
                            if (xpl != null) {
                                xpl.red = (xp == null) ? false : xp.red;
                                if ((sl = xpl.left) != null)
                                    sl.red = false;
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateRight(root, xp);
                            }
                            x = root;
                        }
                    }
                }
            }
        }

        /**
         * Recursive invariant check
         */
        static <K,V> boolean checkInvariants(TreeNode<K,V> t) {
            TreeNode<K,V> tp = t.parent, tl = t.left, tr = t.right,
                tb = t.prev, tn = (TreeNode<K,V>)t.next;
            if (tb != null && tb.next != t)
                return false;
            if (tn != null && tn.prev != t)
                return false;
            if (tp != null && t != tp.left && t != tp.right)
                return false;
            if (tl != null && (tl.parent != t || tl.hash > t.hash))
                return false;
            if (tr != null && (tr.parent != t || tr.hash < t.hash))
                return false;
            if (t.red && tl != null && tl.red && tr != null && tr.red)
                return false;
            if (tl != null && !checkInvariants(tl))
                return false;
            if (tr != null && !checkInvariants(tr))
                return false;
            return true;
        }
    }

}
