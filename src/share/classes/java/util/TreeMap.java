/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class TreeMap<K, V> extends AbstractMap<K, V> implements NavigableMap<K, V>, Cloneable, java.io.Serializable {
    /**
     * 比较器
     * 用于维护TreeMap中的键的顺序
     * 如果比较器为null则使用默认的排序方式
     */
    private final Comparator<? super K> comparator;

    /**
     * 根节点
     */
    private transient Entry<K, V> root;

    /**
     * 集合长度
     */
    private transient int size = 0;

    /**
     * 集合修改次数
     */
    private transient int modCount = 0;


    /**
     * 创建一个空的集合
     * 比较器为null则使用默认的排序方式
     */
    public TreeMap() {
        comparator = null;
    }

    /**
     * 创建一个指定排序方式的比较器的集合
     * @param comparator
     */
    public TreeMap(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    /**
     * 根据指定的集合中的元素创建一个新的集合
     * 使用默认的排序方式进行排序
     * @param m
     */
    public TreeMap(Map<? extends K, ? extends V> m) {
        comparator = null;
        //将集合中的元素添加到当前集合中
        putAll(m);
    }

    /**
     * 根据指定的集合中的元素和比较器创建一个新的集合
     * @param m
     */
    public TreeMap(SortedMap<K, ? extends V> m) {
        //获取集合中的比较器
        comparator = m.comparator();
        try {
            //根据集合中的元素构建红黑树
            buildFromSorted(m.size(), m.entrySet().iterator(), null, null);
        } catch (java.io.IOException cannotHappen) {
        } catch (ClassNotFoundException cannotHappen) {
        }
    }


    /**
     * 获取集合长度
     * @return
     */
    public int size() {
        return size;
    }

    /**
     * 根据指定的key校验红黑树节点中是否包含该key
     * @param key
     * @return
     */
    public boolean containsKey(Object key) {
        //根据指定的key获取该key所在的entry节点
        //节点不为空则返回true,反之则返回false
        return getEntry(key) != null;
    }

    /**
     * 根据指定的value校验红黑树节点中是否包含该value
     * @param value
     * @return
     */
    public boolean containsValue(Object value) {
        //getFirstEntry() 获取红黑树中最左侧节点
        //successor(e) 根据节点获取该节点的后继者
        //从红黑树的最左侧节点开始向右侧节点遍历并校验节点中的value是否与指定的value相等
        for (Entry<K, V> e = getFirstEntry(); e != null; e = successor(e))
            //校验指定的value是否与entry节点中的value相等
            if (valEquals(value, e.value))
                return true;
        return false;
    }

    /**
     * 根据指定的key获取value
     * @param key
     * @return value
     */
    public V get(Object key) {
        //根据key从红黑树中获取entry节点
        Entry<K, V> p = getEntry(key);
        //返回节点中的value
        return (p == null ? null : p.value);
    }

    /**
     * 获取集合中的比较器
     * @return
     */
    public Comparator<? super K> comparator() {
        return comparator;
    }

    /**
     * 获取红黑树中第一个节点的key
     * 第一个节点则是红黑树中最左侧节点
     * @return
     */
    public K firstKey() {
        return key(getFirstEntry());
    }

    /**
     * 获取红黑树中最后一个节点的key
     * 最后一个节点则是红黑树中最右侧节点
     * @return
     */
    public K lastKey() {
        return key(getLastEntry());
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     * These mappings replace any mappings that this map had for any
     * of the keys currently in the specified map.
     *
     * @param map mappings to be stored in this map
     * @throws ClassCastException   if the class of a key or value in
     *                              the specified map prevents it from being stored in this map
     * @throws NullPointerException if the specified map is null or
     *                              the specified map contains a null key and this map does not
     *                              permit null keys
     */
    public void putAll(Map<? extends K, ? extends V> map) {
        //获取集合长度
        int mapSize = map.size();
        //校验集合是否不为空并且集合对象是否是SortedMap的实例对象
        if (size == 0 && mapSize != 0 && map instanceof SortedMap) {
            //获取集合的比较器
            Comparator<?> c = ((SortedMap<?, ?>) map).comparator();
            //校验集合中的比较器是否与当前集合中的比较器相同
            if (c == comparator || (c != null && c.equals(comparator))) {
                //集合修改次数加1
                ++modCount;
                try {
                    //构建红黑树
                    buildFromSorted(mapSize, map.entrySet().iterator(),null, null);
                } catch (java.io.IOException cannotHappen) {
                } catch (ClassNotFoundException cannotHappen) {
                }
                return;
            }
        }
        //集合对象不是SortedMap的实例对象
        //或集合中的比较器与当前集合中的比较器不相同
        //调用AbstractMap中的方法循环调用TreeMap中的put将集合中的元素添加到当前集合中
        super.putAll(map);
    }

    /**
     * 根据key从根节点开始比较获取key所在的entry节点
     * @param key
     * @return entry节点
     */
    final Entry<K, V> getEntry(Object key) {
        if (comparator != null)
            //集合中的比较器不为空则使用集合中的比较器
            //从根节点开始比较key,获取key所在的entry节点
            return getEntryUsingComparator(key);
        if (key == null)
            //key为空则抛出空指针异常
            throw new NullPointerException();
        //获取key自身的比较器
        @SuppressWarnings("unchecked")
        Comparable<? super K> k = (Comparable<? super K>) key;
        //根节点为红黑树中的当前节点
        Entry<K, V> p = root;
        while (p != null) {
            //指定的key与当前节点进行比较
            int cmp = k.compareTo(p.key);
            if (cmp < 0)
                //小于则将当前节点的左子节点设置为当前节点继续进行比较
                p = p.left;
            else if (cmp > 0)
                //大于则将当前节点的右子节点设置为当前节点继续进行比较
                p = p.right;
            else
                //等于则返回key所在的entry节点
                return p;
        }
        return null;
    }

    /**
     * 使用集合中的比较器从根节点开始比较key
     * 获取key所在的entry节点
     * @param key
     * @return
     */
    final Entry<K, V> getEntryUsingComparator(Object key) {
        //强转成泛型
        @SuppressWarnings("unchecked")
        K k = (K) key;
        //获取集合中的比较器
        Comparator<? super K> cpr = comparator;
        if (cpr != null) {
            //根节点为红黑树中的当前节点
            Entry<K, V> p = root;
            while (p != null) {
                //指定的key与当前节点进行比较
                int cmp = cpr.compare(k, p.key);
                if (cmp < 0)
                    //小于则将当前节点的左子节点设置为当前节点继续进行比较
                    p = p.left;
                else if (cmp > 0)
                    //大于则将当前节点的右子节点设置为当前节点继续进行比较
                    p = p.right;
                else
                    //等于则返回key所在的entry节点
                    return p;
            }
        }
        return null;
    }

    /**
     * 获取等于指定key的节点
     * 如果没有等于指定的key的节点则返回大于指定的key的节点
     * @param key
     * @return
     */
    final Entry<K, V> getCeilingEntry(K key) {
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp < 0) {
                if (p.left != null)
                    p = p.left;
                else
                    return p;
            } else if (cmp > 0) {
                if (p.right != null) {
                    p = p.right;
                } else {
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.right) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            } else
                return p;
        }
        return null;
    }

    /**
     * 获取指定key相同的节点
     * 如果没有相同的key的节点则返回小于指定的key的节点
     * 如果也没有小于指定key的节点则返回null
     * @param key
     * @return
     */
    final Entry<K, V> getFloorEntry(K key) {
        //根节点
        Entry<K, V> p = root;
        //从根节点开始比较
        while (p != null) {
            //指定的key与节点p进行比较
            int cmp = compare(key, p.key);
            if (cmp > 0) {
                //指定的key大于节点p的key
                //校验节点p是否有右子节点
                if (p.right != null)
                    //将指针p指向p节点的右子节点继续比较
                    p = p.right;
                else
                    //节点p没有右子节点说明指定的key不存在相同的key的节点则返回小于指定key的节点
                    return p;
            } else if (cmp < 0) {
                //指定的key小于节点p的key
                //校验节点p是否有左子节点
                if (p.left != null) {
                    //将指针p指向p节点的左子节点继续比较
                    p = p.left;
                } else {
                    //节点p没有左子节点则从节点p向上获取父节点
                    //直到父节点为空或ch节点不是左子节点
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.left) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            } else
                //指定的key与节点p的key相等则返回节点p
                return p;

        }
        return null;
    }

    /**
     * Gets the entry for the least key greater than the specified
     * key; if no such entry exists, returns the entry for the least
     * key greater than the specified key; if no such entry exists
     * returns {@code null}.
     */
    final Entry<K, V> getHigherEntry(K key) {
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp < 0) {
                if (p.left != null)
                    p = p.left;
                else
                    return p;
            } else {
                if (p.right != null) {
                    p = p.right;
                } else {
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.right) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            }
        }
        return null;
    }

    /**
     * 获取小于指定key的节点
     * 如果key为8则返回小于8的最大节点
     * 如果没有小于指定key的节点则返回null
     * @param key
     * @return
     */
    final Entry<K, V> getLowerEntry(K key) {
        //根节点
        Entry<K, V> p = root;
        //从根节点开始比较
        while (p != null) {
            //指定的key与节点p的key比较
            int cmp = compare(key, p.key);
            if (cmp > 0) {
                //大于节点p则从节点p的右子节点查找
                if (p.right != null)
                    //节点p的右子节点不为空则继续向下查找
                    p = p.right;
                else
                    //节点p没有右子节点则返回节点p
                    return p;
            } else {
                //小于等于节点p
                if (p.left != null) {
                    //节点p的左子节点不为空则继续向下查找
                    p = p.left;
                } else {
                    //左子节点为空则说明节点p的key大于等于指定的key
                    //获取节点p的父节点
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    //如果当前节点ch是左子节点的话那就一直向上获取父节点
                    //直到ch节点不是左子节点
                    while (parent != null && ch == parent.left) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            }
        }
        return null;
    }

    /**
     * 将指定的key-value相关联添加到集合中
     * 如果集合中存在指定的key则使用新的value替换旧的value
     * @param key
     * @param value
     * @return
     */
    public V put(K key, V value) {
        //根节点
        Entry<K, V> t = root;
        //校验根节点是否为空
        if (t == null) {
            compare(key, key);
            //将当前添加的key-value封装成节点并设置为根节点
            root = new Entry<>(key, value, null);
            //更新集合长度和修改次数
            size = 1;
            modCount++;
            return null;
        }
        int cmp;
        //父节点
        Entry<K, V> parent;
        //获取当前集合的比较器
        Comparator<? super K> cpr = comparator;
        if (cpr != null) {
            //比较器不为空则从根节点开始比较
            do {
                //以根节点为父节点
                parent = t;
                //当前添加的key与父节点的key进行比较
                cmp = cpr.compare(key, t.key);
                if (cmp < 0)
                    //将父节点的左子节点设置为父节点
                    t = t.left;
                else if (cmp > 0)
                    //将父节点的右子节点设置为父节点
                    t = t.right;
                else
                    //key相同则替换value
                    return t.setValue(value);
                //父节点不为空则继续比较
                //直到父节点为空或key相同
            } while (t != null);
        } else {
            //比较器为空
            if (key == null)
                //key为空则抛出异常
                throw new NullPointerException();
            //集合的比较器为空则获取key自身的比较器
            @SuppressWarnings("unchecked")
            Comparable<? super K> k = (Comparable<? super K>) key;
            do {
                //以根节点为父节点
                parent = t;
                //当前添加的key与父节点的key进行比较
                cmp = k.compareTo(t.key);
                if (cmp < 0)
                    //将父节点的左子节点设置为父节点
                    t = t.left;
                else if (cmp > 0)
                    //将父节点的右子节点设置为父节点
                    t = t.right;
                else
                    //key相同则替换value
                    return t.setValue(value);
                //父节点不为空则继续比较
                //直到父节点为空或key相同
            } while (t != null);
        }
        //将key-value以及所在的父节点封装成一个节点
        Entry<K, V> e = new Entry<>(key, value, parent);
        if (cmp < 0)
            //将新创建的节点添加到左子节点
            parent.left = e;
        else
            //将新创建的节点添加到右子节点
            parent.right = e;
        //平衡红黑树的节点
        fixAfterInsertion(e);
        //更新集合长度和修改次数
        size++;
        modCount++;
        return null;
    }

    /**
     * Removes the mapping for this key from this TreeMap if present.
     *
     * @param key key for which mapping should be removed
     * @return the previous value associated with {@code key}, or
     * {@code null} if there was no mapping for {@code key}.
     * (A {@code null} return can also indicate that the map
     * previously associated {@code null} with {@code key}.)
     * @throws ClassCastException   if the specified key cannot be compared
     *                              with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     */
    public V remove(Object key) {
        //获取指定的key所在的节点
        Entry<K, V> p = getEntry(key);
        if (p == null)
            //节点为空则返回空
            return null;
        //获取节点中的value
        V oldValue = p.value;
        //删除节点
        deleteEntry(p);
        //返回被删除的节点中的value
        return oldValue;
    }

    /**
     * 清空集合
     */
    public void clear() {
        //集合修改次数加1
        modCount++;
        //集合长度置空
        size = 0;
        //将根节点置空
        //根节点置空后红黑树中的其它节点就没有了引用,等待gc回收
        root = null;
    }

    /**
     * 获取集合副本
     * @return
     */
    public Object clone() {
        TreeMap<?, ?> clone;
        try {
            //调用克隆方法获取到一个新的集合对象
            clone = (TreeMap<?, ?>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }

        //对新的集合进行初始化
        clone.root = null;
        clone.size = 0;
        clone.modCount = 0;
        clone.entrySet = null;
        clone.navigableKeySet = null;
        clone.descendingMap = null;
        try {
            //将原集合中的节点添加到克隆的集合中并将节点构建成红黑树
            clone.buildFromSorted(size, entrySet().iterator(), null, null);
        } catch (java.io.IOException cannotHappen) {
        } catch (ClassNotFoundException cannotHappen) {
        }
        //返回副本
        return clone;
    }

    // NavigableMap API methods

    /**
     * 获取红黑树中的第一个节点
     * 第一个节点则是红黑树中最左侧节点
     * @since 1.6
     */
    public Map.Entry<K, V> firstEntry() {
        return exportEntry(getFirstEntry());
    }

    /**
     * 获取红黑树中的最后一个节点
     * 最后一个节点则是红黑树中最右侧节点
     * @since 1.6
     */
    public Map.Entry<K, V> lastEntry() {
        return exportEntry(getLastEntry());
    }

    /**
     * 弹出红黑树中的第一个节点
     * 弹出节点会将该节点从红黑树中删除
     * @since 1.6
     */
    public Map.Entry<K, V> pollFirstEntry() {
        //获取红黑树中第一个节点
        Entry<K, V> p = getFirstEntry();
        Map.Entry<K, V> result = exportEntry(p);
        if (p != null)
            //节点不为空则删除节点
            deleteEntry(p);
        return result;
    }

    /**
     * 弹出红黑树中的最后一个节点
     * 弹出节点会将该节点从红黑树中删除
     * @since 1.6
     */
    public Map.Entry<K, V> pollLastEntry() {
        //获取红黑树中最后一个节点
        Entry<K, V> p = getLastEntry();
        Map.Entry<K, V> result = exportEntry(p);
        if (p != null)
            //节点不为空则删除节点
            deleteEntry(p);
        return result;
    }

    /**
     * 获取小于指定key的节点
     * @param key
     * @return
     */
    public Map.Entry<K, V> lowerEntry(K key) {
        return exportEntry(getLowerEntry(key));
    }

    /**
     * 获取小于指定key的key
     * @param key
     * @return
     */
    public K lowerKey(K key) {
        return keyOrNull(getLowerEntry(key));
    }

    /**
     * 获取等于指定key的节点
     * 如果没有等于指定的key的节点则返回小于指定的key的节点
     * @param key
     * @return
     */
    public Map.Entry<K, V> floorEntry(K key) {
        return exportEntry(getFloorEntry(key));
    }

    /**
     * 获取等于指定key的节点的key
     * 如果没有等于指定的key的节点的key则返回小于指定的key的节点的key
     * @param key
     * @return
     */
    public K floorKey(K key) {
        return keyOrNull(getFloorEntry(key));
    }

    /**
     * 获取等于指定key的节点
     * 如果没有等于指定的key的节点则返回大于指定的key的节点
     * @param key
     * @return
     */
    public Map.Entry<K, V> ceilingEntry(K key) {
        return exportEntry(getCeilingEntry(key));
    }

    /**
     * 获取等于指定key的节点的key
     * 如果没有等于指定的key的节点的key则返回大于指定的key的节点的key
     * @param key
     * @return
     */
    public K ceilingKey(K key) {
        return keyOrNull(getCeilingEntry(key));
    }

    /**
     * 获取大于指定的key的节点
     * @param key
     * @return
     */
    public Map.Entry<K, V> higherEntry(K key) {
        return exportEntry(getHigherEntry(key));
    }

    /**
     * 获取大于指定的key的节点的key
     * @param key
     * @return
     */
    public K higherKey(K key) {
        return keyOrNull(getHigherEntry(key));
    }

    // Views

    /**
     * Fields initialized to contain an instance of the entry set view
     * the first time this view is requested.  Views are stateless, so
     * there's no reason to create more than one.
     */
    private transient EntrySet entrySet;
    private transient KeySet<K> navigableKeySet;
    private transient NavigableMap<K, V> descendingMap;

    /**
     * 获取key集合
     * @return
     */
    public Set<K> keySet() {
        return navigableKeySet();
    }

    /**
     * 获取key集合
     */
    public NavigableSet<K> navigableKeySet() {
        KeySet<K> nks = navigableKeySet;
        //如果当前的key集合为空则创建一个key的集合并将当前集合中的元素添加到创建的集合中
        return (nks != null) ? nks : (navigableKeySet = new KeySet<>(this));
    }

    /**
     * @since 1.6
     */
    public NavigableSet<K> descendingKeySet() {
        return descendingMap().navigableKeySet();
    }

    /**
     * 获取集合中的所有value
     * @return
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new Values();
            values = vs;
        }
        return vs;
    }

    /**
     * 获取集合中的所有的键值对
     * @return
     */
    public Set<Map.Entry<K, V>> entrySet() {
        EntrySet es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    /**
     * @since 1.6
     */
    public NavigableMap<K, V> descendingMap() {
        NavigableMap<K, V> km = descendingMap;
        return (km != null) ? km :
                (descendingMap = new DescendingSubMap<>(this,
                        true, null, true,
                        true, null, true));
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code fromKey} or {@code toKey} is
     *                                  null and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive,
                                     K toKey, boolean toInclusive) {
        return new AscendingSubMap<>(this,
                false, fromKey, fromInclusive,
                false, toKey, toInclusive);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code toKey} is null
     *                                  and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return new AscendingSubMap<>(this,
                true, null, true,
                false, toKey, inclusive);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code fromKey} is null
     *                                  and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return new AscendingSubMap<>(this,
                false, fromKey, inclusive,
                true, null, true);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code fromKey} or {@code toKey} is
     *                                  null and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        return subMap(fromKey, true, toKey, false);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code toKey} is null
     *                                  and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<K, V> headMap(K toKey) {
        return headMap(toKey, false);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code fromKey} is null
     *                                  and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<K, V> tailMap(K fromKey) {
        return tailMap(fromKey, true);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Entry<K, V> p = getEntry(key);
        if (p != null && Objects.equals(oldValue, p.value)) {
            p.value = newValue;
            return true;
        }
        return false;
    }

    @Override
    public V replace(K key, V value) {
        Entry<K, V> p = getEntry(key);
        if (p != null) {
            V oldValue = p.value;
            p.value = value;
            return oldValue;
        }
        return null;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        int expectedModCount = modCount;
        for (Entry<K, V> e = getFirstEntry(); e != null; e = successor(e)) {
            action.accept(e.key, e.value);

            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        int expectedModCount = modCount;

        for (Entry<K, V> e = getFirstEntry(); e != null; e = successor(e)) {
            e.value = function.apply(e.key, e.value);

            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    // View class support

    class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator(getFirstEntry());
        }

        public int size() {
            return TreeMap.this.size();
        }

        public boolean contains(Object o) {
            return TreeMap.this.containsValue(o);
        }

        public boolean remove(Object o) {
            for (Entry<K, V> e = getFirstEntry(); e != null; e = successor(e)) {
                if (valEquals(e.getValue(), o)) {
                    deleteEntry(e);
                    return true;
                }
            }
            return false;
        }

        public void clear() {
            TreeMap.this.clear();
        }

        public Spliterator<V> spliterator() {
            return new ValueSpliterator<K, V>(TreeMap.this, null, null, 0, -1, 0);
        }
    }

    class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        public Iterator<Map.Entry<K, V>> iterator() {
            return new EntryIterator(getFirstEntry());
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            Object value = entry.getValue();
            Entry<K, V> p = getEntry(entry.getKey());
            return p != null && valEquals(p.getValue(), value);
        }

        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            Object value = entry.getValue();
            Entry<K, V> p = getEntry(entry.getKey());
            if (p != null && valEquals(p.getValue(), value)) {
                deleteEntry(p);
                return true;
            }
            return false;
        }

        public int size() {
            return TreeMap.this.size();
        }

        public void clear() {
            TreeMap.this.clear();
        }

        public Spliterator<Map.Entry<K, V>> spliterator() {
            return new EntrySpliterator<K, V>(TreeMap.this, null, null, 0, -1, 0);
        }
    }

    /*
     * Unlike Values and EntrySet, the KeySet class is static,
     * delegating to a NavigableMap to allow use by SubMaps, which
     * outweighs the ugliness of needing type-tests for the following
     * Iterator methods that are defined appropriately in main versus
     * submap classes.
     */

    Iterator<K> keyIterator() {
        return new KeyIterator(getFirstEntry());
    }

    Iterator<K> descendingKeyIterator() {
        return new DescendingKeyIterator(getLastEntry());
    }

    static final class KeySet<E> extends AbstractSet<E> implements NavigableSet<E> {
        private final NavigableMap<E, ?> m;

        KeySet(NavigableMap<E, ?> map) {
            m = map;
        }

        public Iterator<E> iterator() {
            if (m instanceof TreeMap)
                return ((TreeMap<E, ?>) m).keyIterator();
            else
                return ((TreeMap.NavigableSubMap<E, ?>) m).keyIterator();
        }

        public Iterator<E> descendingIterator() {
            if (m instanceof TreeMap)
                return ((TreeMap<E, ?>) m).descendingKeyIterator();
            else
                return ((TreeMap.NavigableSubMap<E, ?>) m).descendingKeyIterator();
        }

        public int size() {
            return m.size();
        }

        public boolean isEmpty() {
            return m.isEmpty();
        }

        public boolean contains(Object o) {
            return m.containsKey(o);
        }

        public void clear() {
            m.clear();
        }

        public E lower(E e) {
            return m.lowerKey(e);
        }

        public E floor(E e) {
            return m.floorKey(e);
        }

        public E ceiling(E e) {
            return m.ceilingKey(e);
        }

        public E higher(E e) {
            return m.higherKey(e);
        }

        public E first() {
            return m.firstKey();
        }

        public E last() {
            return m.lastKey();
        }

        public Comparator<? super E> comparator() {
            return m.comparator();
        }

        public E pollFirst() {
            Map.Entry<E, ?> e = m.pollFirstEntry();
            return (e == null) ? null : e.getKey();
        }

        public E pollLast() {
            Map.Entry<E, ?> e = m.pollLastEntry();
            return (e == null) ? null : e.getKey();
        }

        public boolean remove(Object o) {
            int oldSize = size();
            m.remove(o);
            return size() != oldSize;
        }

        public NavigableSet<E> subSet(E fromElement, boolean fromInclusive,
                                      E toElement, boolean toInclusive) {
            return new KeySet<>(m.subMap(fromElement, fromInclusive,
                    toElement, toInclusive));
        }

        public NavigableSet<E> headSet(E toElement, boolean inclusive) {
            return new KeySet<>(m.headMap(toElement, inclusive));
        }

        public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
            return new KeySet<>(m.tailMap(fromElement, inclusive));
        }

        public SortedSet<E> subSet(E fromElement, E toElement) {
            return subSet(fromElement, true, toElement, false);
        }

        public SortedSet<E> headSet(E toElement) {
            return headSet(toElement, false);
        }

        public SortedSet<E> tailSet(E fromElement) {
            return tailSet(fromElement, true);
        }

        public NavigableSet<E> descendingSet() {
            return new KeySet<>(m.descendingMap());
        }

        public Spliterator<E> spliterator() {
            return keySpliteratorFor(m);
        }
    }

    /**
     * Base class for TreeMap Iterators
     */
    abstract class PrivateEntryIterator<T> implements Iterator<T> {
        Entry<K, V> next;
        Entry<K, V> lastReturned;
        int expectedModCount;

        PrivateEntryIterator(Entry<K, V> first) {
            expectedModCount = modCount;
            lastReturned = null;
            next = first;
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Entry<K, V> nextEntry() {
            Entry<K, V> e = next;
            if (e == null)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            next = successor(e);
            lastReturned = e;
            return e;
        }

        final Entry<K, V> prevEntry() {
            Entry<K, V> e = next;
            if (e == null)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            next = predecessor(e);
            lastReturned = e;
            return e;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            // deleted entries are replaced by their successors
            if (lastReturned.left != null && lastReturned.right != null)
                next = lastReturned;
            deleteEntry(lastReturned);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }

    final class EntryIterator extends PrivateEntryIterator<Map.Entry<K, V>> {
        EntryIterator(Entry<K, V> first) {
            super(first);
        }

        public Map.Entry<K, V> next() {
            return nextEntry();
        }
    }

    final class ValueIterator extends PrivateEntryIterator<V> {
        ValueIterator(Entry<K, V> first) {
            super(first);
        }

        public V next() {
            return nextEntry().value;
        }
    }

    final class KeyIterator extends PrivateEntryIterator<K> {
        KeyIterator(Entry<K, V> first) {
            super(first);
        }

        public K next() {
            return nextEntry().key;
        }
    }

    final class DescendingKeyIterator extends PrivateEntryIterator<K> {
        DescendingKeyIterator(Entry<K, V> first) {
            super(first);
        }

        public K next() {
            return prevEntry().key;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            deleteEntry(lastReturned);
            lastReturned = null;
            expectedModCount = modCount;
        }
    }

    // Little utilities

    /**
     * Compares two keys using the correct comparison method for this TreeMap.
     */
    @SuppressWarnings("unchecked")
    final int compare(Object k1, Object k2) {
        return comparator == null ? ((Comparable<? super K>) k1).compareTo((K) k2)
                : comparator.compare((K) k1, (K) k2);
    }

    /**
     * 比较两个值是否相等
     * @param o1
     * @param o2
     * @return
     */
    static final boolean valEquals(Object o1, Object o2) {
        return (o1 == null ? o2 == null : o1.equals(o2));
    }

    /**
     * 返回节点,如果节点为null则返回null
     * @param e
     * @return
     */
    static <K, V> Map.Entry<K, V> exportEntry(TreeMap.Entry<K, V> e) {
        return (e == null) ? null :
                new AbstractMap.SimpleImmutableEntry<>(e);
    }

    /**
     * 返回key,如果key为null则返回null
     */
    static <K, V> K keyOrNull(TreeMap.Entry<K, V> e) {
        return (e == null) ? null : e.key;
    }

    /**
     * 返回节点中的key
     * 如果节点为空则抛出异常
     * @param e
     * @param <K>
     * @return
     */
    static <K> K key(Entry<K, ?> e) {
        if (e == null)
            throw new NoSuchElementException();
        return e.key;
    }


    // SubMaps

    /**
     * Dummy value serving as unmatchable fence key for unbounded
     * SubMapIterators
     */
    private static final Object UNBOUNDED = new Object();

    /**
     * @serial include
     */
    abstract static class NavigableSubMap<K, V> extends AbstractMap<K, V>
            implements NavigableMap<K, V>, java.io.Serializable {
        private static final long serialVersionUID = -2102997345730753016L;
        /**
         * The backing map.
         */
        final TreeMap<K, V> m;

        /**
         * Endpoints are represented as triples (fromStart, lo,
         * loInclusive) and (toEnd, hi, hiInclusive). If fromStart is
         * true, then the low (absolute) bound is the start of the
         * backing map, and the other values are ignored. Otherwise,
         * if loInclusive is true, lo is the inclusive bound, else lo
         * is the exclusive bound. Similarly for the upper bound.
         */
        final K lo, hi;
        final boolean fromStart, toEnd;
        final boolean loInclusive, hiInclusive;

        NavigableSubMap(TreeMap<K, V> m,
                        boolean fromStart, K lo, boolean loInclusive,
                        boolean toEnd, K hi, boolean hiInclusive) {
            if (!fromStart && !toEnd) {
                if (m.compare(lo, hi) > 0)
                    throw new IllegalArgumentException("fromKey > toKey");
            } else {
                if (!fromStart) // type check
                    m.compare(lo, lo);
                if (!toEnd)
                    m.compare(hi, hi);
            }

            this.m = m;
            this.fromStart = fromStart;
            this.lo = lo;
            this.loInclusive = loInclusive;
            this.toEnd = toEnd;
            this.hi = hi;
            this.hiInclusive = hiInclusive;
        }

        // internal utilities

        final boolean tooLow(Object key) {
            if (!fromStart) {
                int c = m.compare(key, lo);
                if (c < 0 || (c == 0 && !loInclusive))
                    return true;
            }
            return false;
        }

        final boolean tooHigh(Object key) {
            if (!toEnd) {
                int c = m.compare(key, hi);
                if (c > 0 || (c == 0 && !hiInclusive))
                    return true;
            }
            return false;
        }

        final boolean inRange(Object key) {
            return !tooLow(key) && !tooHigh(key);
        }

        final boolean inClosedRange(Object key) {
            return (fromStart || m.compare(key, lo) >= 0)
                    && (toEnd || m.compare(hi, key) >= 0);
        }

        final boolean inRange(Object key, boolean inclusive) {
            return inclusive ? inRange(key) : inClosedRange(key);
        }

        /*
         * Absolute versions of relation operations.
         * Subclasses map to these using like-named "sub"
         * versions that invert senses for descending maps
         */

        final TreeMap.Entry<K, V> absLowest() {
            TreeMap.Entry<K, V> e =
                    (fromStart ? m.getFirstEntry() :
                            (loInclusive ? m.getCeilingEntry(lo) :
                                    m.getHigherEntry(lo)));
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final TreeMap.Entry<K, V> absHighest() {
            TreeMap.Entry<K, V> e =
                    (toEnd ? m.getLastEntry() :
                            (hiInclusive ? m.getFloorEntry(hi) :
                                    m.getLowerEntry(hi)));
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final TreeMap.Entry<K, V> absCeiling(K key) {
            if (tooLow(key))
                return absLowest();
            TreeMap.Entry<K, V> e = m.getCeilingEntry(key);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final TreeMap.Entry<K, V> absHigher(K key) {
            if (tooLow(key))
                return absLowest();
            TreeMap.Entry<K, V> e = m.getHigherEntry(key);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final TreeMap.Entry<K, V> absFloor(K key) {
            if (tooHigh(key))
                return absHighest();
            TreeMap.Entry<K, V> e = m.getFloorEntry(key);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final TreeMap.Entry<K, V> absLower(K key) {
            if (tooHigh(key))
                return absHighest();
            TreeMap.Entry<K, V> e = m.getLowerEntry(key);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        /**
         * Returns the absolute high fence for ascending traversal
         */
        final TreeMap.Entry<K, V> absHighFence() {
            return (toEnd ? null : (hiInclusive ?
                    m.getHigherEntry(hi) :
                    m.getCeilingEntry(hi)));
        }

        /**
         * Return the absolute low fence for descending traversal
         */
        final TreeMap.Entry<K, V> absLowFence() {
            return (fromStart ? null : (loInclusive ?
                    m.getLowerEntry(lo) :
                    m.getFloorEntry(lo)));
        }

        // Abstract methods defined in ascending vs descending classes
        // These relay to the appropriate absolute versions

        abstract TreeMap.Entry<K, V> subLowest();

        abstract TreeMap.Entry<K, V> subHighest();

        abstract TreeMap.Entry<K, V> subCeiling(K key);

        abstract TreeMap.Entry<K, V> subHigher(K key);

        abstract TreeMap.Entry<K, V> subFloor(K key);

        abstract TreeMap.Entry<K, V> subLower(K key);

        /**
         * Returns ascending iterator from the perspective of this submap
         */
        abstract Iterator<K> keyIterator();

        abstract Spliterator<K> keySpliterator();

        /**
         * Returns descending iterator from the perspective of this submap
         */
        abstract Iterator<K> descendingKeyIterator();

        // public methods

        public boolean isEmpty() {
            return (fromStart && toEnd) ? m.isEmpty() : entrySet().isEmpty();
        }

        public int size() {
            return (fromStart && toEnd) ? m.size() : entrySet().size();
        }

        public final boolean containsKey(Object key) {
            return inRange(key) && m.containsKey(key);
        }

        public final V put(K key, V value) {
            if (!inRange(key))
                throw new IllegalArgumentException("key out of range");
            return m.put(key, value);
        }

        public final V get(Object key) {
            return !inRange(key) ? null : m.get(key);
        }

        public final V remove(Object key) {
            return !inRange(key) ? null : m.remove(key);
        }

        public final Map.Entry<K, V> ceilingEntry(K key) {
            return exportEntry(subCeiling(key));
        }

        public final K ceilingKey(K key) {
            return keyOrNull(subCeiling(key));
        }

        public final Map.Entry<K, V> higherEntry(K key) {
            return exportEntry(subHigher(key));
        }

        public final K higherKey(K key) {
            return keyOrNull(subHigher(key));
        }

        public final Map.Entry<K, V> floorEntry(K key) {
            return exportEntry(subFloor(key));
        }

        public final K floorKey(K key) {
            return keyOrNull(subFloor(key));
        }

        public final Map.Entry<K, V> lowerEntry(K key) {
            return exportEntry(subLower(key));
        }

        public final K lowerKey(K key) {
            return keyOrNull(subLower(key));
        }

        public final K firstKey() {
            return key(subLowest());
        }

        public final K lastKey() {
            return key(subHighest());
        }

        public final Map.Entry<K, V> firstEntry() {
            return exportEntry(subLowest());
        }

        public final Map.Entry<K, V> lastEntry() {
            return exportEntry(subHighest());
        }

        public final Map.Entry<K, V> pollFirstEntry() {
            TreeMap.Entry<K, V> e = subLowest();
            Map.Entry<K, V> result = exportEntry(e);
            if (e != null)
                m.deleteEntry(e);
            return result;
        }

        public final Map.Entry<K, V> pollLastEntry() {
            TreeMap.Entry<K, V> e = subHighest();
            Map.Entry<K, V> result = exportEntry(e);
            if (e != null)
                m.deleteEntry(e);
            return result;
        }

        // Views
        transient NavigableMap<K, V> descendingMapView;
        transient EntrySetView entrySetView;
        transient KeySet<K> navigableKeySetView;

        public final NavigableSet<K> navigableKeySet() {
            KeySet<K> nksv = navigableKeySetView;
            return (nksv != null) ? nksv :
                    (navigableKeySetView = new TreeMap.KeySet<>(this));
        }

        public final Set<K> keySet() {
            return navigableKeySet();
        }

        public NavigableSet<K> descendingKeySet() {
            return descendingMap().navigableKeySet();
        }

        public final SortedMap<K, V> subMap(K fromKey, K toKey) {
            return subMap(fromKey, true, toKey, false);
        }

        public final SortedMap<K, V> headMap(K toKey) {
            return headMap(toKey, false);
        }

        public final SortedMap<K, V> tailMap(K fromKey) {
            return tailMap(fromKey, true);
        }

        // View classes

        abstract class EntrySetView extends AbstractSet<Map.Entry<K, V>> {
            private transient int size = -1, sizeModCount;

            public int size() {
                if (fromStart && toEnd)
                    return m.size();
                if (size == -1 || sizeModCount != m.modCount) {
                    sizeModCount = m.modCount;
                    size = 0;
                    Iterator<?> i = iterator();
                    while (i.hasNext()) {
                        size++;
                        i.next();
                    }
                }
                return size;
            }

            public boolean isEmpty() {
                TreeMap.Entry<K, V> n = absLowest();
                return n == null || tooHigh(n.key);
            }

            public boolean contains(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                Object key = entry.getKey();
                if (!inRange(key))
                    return false;
                TreeMap.Entry<?, ?> node = m.getEntry(key);
                return node != null &&
                        valEquals(node.getValue(), entry.getValue());
            }

            public boolean remove(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                Object key = entry.getKey();
                if (!inRange(key))
                    return false;
                TreeMap.Entry<K, V> node = m.getEntry(key);
                if (node != null && valEquals(node.getValue(),
                        entry.getValue())) {
                    m.deleteEntry(node);
                    return true;
                }
                return false;
            }
        }

        /**
         * Iterators for SubMaps
         */
        abstract class SubMapIterator<T> implements Iterator<T> {
            TreeMap.Entry<K, V> lastReturned;
            TreeMap.Entry<K, V> next;
            final Object fenceKey;
            int expectedModCount;

            SubMapIterator(TreeMap.Entry<K, V> first,
                           TreeMap.Entry<K, V> fence) {
                expectedModCount = m.modCount;
                lastReturned = null;
                next = first;
                fenceKey = fence == null ? UNBOUNDED : fence.key;
            }

            public final boolean hasNext() {
                return next != null && next.key != fenceKey;
            }

            final TreeMap.Entry<K, V> nextEntry() {
                TreeMap.Entry<K, V> e = next;
                if (e == null || e.key == fenceKey)
                    throw new NoSuchElementException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                next = successor(e);
                lastReturned = e;
                return e;
            }

            final TreeMap.Entry<K, V> prevEntry() {
                TreeMap.Entry<K, V> e = next;
                if (e == null || e.key == fenceKey)
                    throw new NoSuchElementException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                next = predecessor(e);
                lastReturned = e;
                return e;
            }

            final void removeAscending() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                // deleted entries are replaced by their successors
                if (lastReturned.left != null && lastReturned.right != null)
                    next = lastReturned;
                m.deleteEntry(lastReturned);
                lastReturned = null;
                expectedModCount = m.modCount;
            }

            final void removeDescending() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                m.deleteEntry(lastReturned);
                lastReturned = null;
                expectedModCount = m.modCount;
            }

        }

        final class SubMapEntryIterator extends SubMapIterator<Map.Entry<K, V>> {
            SubMapEntryIterator(TreeMap.Entry<K, V> first,
                                TreeMap.Entry<K, V> fence) {
                super(first, fence);
            }

            public Map.Entry<K, V> next() {
                return nextEntry();
            }

            public void remove() {
                removeAscending();
            }
        }

        final class DescendingSubMapEntryIterator extends SubMapIterator<Map.Entry<K, V>> {
            DescendingSubMapEntryIterator(TreeMap.Entry<K, V> last,
                                          TreeMap.Entry<K, V> fence) {
                super(last, fence);
            }

            public Map.Entry<K, V> next() {
                return prevEntry();
            }

            public void remove() {
                removeDescending();
            }
        }

        // Implement minimal Spliterator as KeySpliterator backup
        final class SubMapKeyIterator extends SubMapIterator<K>
                implements Spliterator<K> {
            SubMapKeyIterator(TreeMap.Entry<K, V> first,
                              TreeMap.Entry<K, V> fence) {
                super(first, fence);
            }

            public K next() {
                return nextEntry().key;
            }

            public void remove() {
                removeAscending();
            }

            public Spliterator<K> trySplit() {
                return null;
            }

            public void forEachRemaining(Consumer<? super K> action) {
                while (hasNext())
                    action.accept(next());
            }

            public boolean tryAdvance(Consumer<? super K> action) {
                if (hasNext()) {
                    action.accept(next());
                    return true;
                }
                return false;
            }

            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.ORDERED |
                        Spliterator.SORTED;
            }

            public final Comparator<? super K> getComparator() {
                return NavigableSubMap.this.comparator();
            }
        }

        final class DescendingSubMapKeyIterator extends SubMapIterator<K>
                implements Spliterator<K> {
            DescendingSubMapKeyIterator(TreeMap.Entry<K, V> last,
                                        TreeMap.Entry<K, V> fence) {
                super(last, fence);
            }

            public K next() {
                return prevEntry().key;
            }

            public void remove() {
                removeDescending();
            }

            public Spliterator<K> trySplit() {
                return null;
            }

            public void forEachRemaining(Consumer<? super K> action) {
                while (hasNext())
                    action.accept(next());
            }

            public boolean tryAdvance(Consumer<? super K> action) {
                if (hasNext()) {
                    action.accept(next());
                    return true;
                }
                return false;
            }

            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.ORDERED;
            }
        }
    }

    /**
     * @serial include
     */
    static final class AscendingSubMap<K, V> extends NavigableSubMap<K, V> {
        private static final long serialVersionUID = 912986545866124060L;

        AscendingSubMap(TreeMap<K, V> m,
                        boolean fromStart, K lo, boolean loInclusive,
                        boolean toEnd, K hi, boolean hiInclusive) {
            super(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive);
        }

        public Comparator<? super K> comparator() {
            return m.comparator();
        }

        public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive,
                                         K toKey, boolean toInclusive) {
            if (!inRange(fromKey, fromInclusive))
                throw new IllegalArgumentException("fromKey out of range");
            if (!inRange(toKey, toInclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new AscendingSubMap<>(m,
                    false, fromKey, fromInclusive,
                    false, toKey, toInclusive);
        }

        public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new AscendingSubMap<>(m,
                    fromStart, lo, loInclusive,
                    false, toKey, inclusive);
        }

        public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive))
                throw new IllegalArgumentException("fromKey out of range");
            return new AscendingSubMap<>(m,
                    false, fromKey, inclusive,
                    toEnd, hi, hiInclusive);
        }

        public NavigableMap<K, V> descendingMap() {
            NavigableMap<K, V> mv = descendingMapView;
            return (mv != null) ? mv :
                    (descendingMapView =
                            new DescendingSubMap<>(m,
                                    fromStart, lo, loInclusive,
                                    toEnd, hi, hiInclusive));
        }

        Iterator<K> keyIterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        Spliterator<K> keySpliterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        Iterator<K> descendingKeyIterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        final class AscendingEntrySetView extends EntrySetView {
            public Iterator<Map.Entry<K, V>> iterator() {
                return new SubMapEntryIterator(absLowest(), absHighFence());
            }
        }

        public Set<Map.Entry<K, V>> entrySet() {
            EntrySetView es = entrySetView;
            return (es != null) ? es : (entrySetView = new AscendingEntrySetView());
        }

        TreeMap.Entry<K, V> subLowest() {
            return absLowest();
        }

        TreeMap.Entry<K, V> subHighest() {
            return absHighest();
        }

        TreeMap.Entry<K, V> subCeiling(K key) {
            return absCeiling(key);
        }

        TreeMap.Entry<K, V> subHigher(K key) {
            return absHigher(key);
        }

        TreeMap.Entry<K, V> subFloor(K key) {
            return absFloor(key);
        }

        TreeMap.Entry<K, V> subLower(K key) {
            return absLower(key);
        }
    }

    /**
     * @serial include
     */
    static final class DescendingSubMap<K, V> extends NavigableSubMap<K, V> {
        private static final long serialVersionUID = 912986545866120460L;

        DescendingSubMap(TreeMap<K, V> m,
                         boolean fromStart, K lo, boolean loInclusive,
                         boolean toEnd, K hi, boolean hiInclusive) {
            super(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive);
        }

        private final Comparator<? super K> reverseComparator =
                Collections.reverseOrder(m.comparator);

        public Comparator<? super K> comparator() {
            return reverseComparator;
        }

        public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive,
                                         K toKey, boolean toInclusive) {
            if (!inRange(fromKey, fromInclusive))
                throw new IllegalArgumentException("fromKey out of range");
            if (!inRange(toKey, toInclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new DescendingSubMap<>(m,
                    false, toKey, toInclusive,
                    false, fromKey, fromInclusive);
        }

        public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new DescendingSubMap<>(m,
                    false, toKey, inclusive,
                    toEnd, hi, hiInclusive);
        }

        public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive))
                throw new IllegalArgumentException("fromKey out of range");
            return new DescendingSubMap<>(m,
                    fromStart, lo, loInclusive,
                    false, fromKey, inclusive);
        }

        public NavigableMap<K, V> descendingMap() {
            NavigableMap<K, V> mv = descendingMapView;
            return (mv != null) ? mv :
                    (descendingMapView =
                            new AscendingSubMap<>(m,
                                    fromStart, lo, loInclusive,
                                    toEnd, hi, hiInclusive));
        }

        Iterator<K> keyIterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        Spliterator<K> keySpliterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        Iterator<K> descendingKeyIterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        final class DescendingEntrySetView extends EntrySetView {
            public Iterator<Map.Entry<K, V>> iterator() {
                return new DescendingSubMapEntryIterator(absHighest(), absLowFence());
            }
        }

        public Set<Map.Entry<K, V>> entrySet() {
            EntrySetView es = entrySetView;
            return (es != null) ? es : (entrySetView = new DescendingEntrySetView());
        }

        TreeMap.Entry<K, V> subLowest() {
            return absHighest();
        }

        TreeMap.Entry<K, V> subHighest() {
            return absLowest();
        }

        TreeMap.Entry<K, V> subCeiling(K key) {
            return absFloor(key);
        }

        TreeMap.Entry<K, V> subHigher(K key) {
            return absLower(key);
        }

        TreeMap.Entry<K, V> subFloor(K key) {
            return absCeiling(key);
        }

        TreeMap.Entry<K, V> subLower(K key) {
            return absHigher(key);
        }
    }

    /**
     * This class exists solely for the sake of serialization
     * compatibility with previous releases of TreeMap that did not
     * support NavigableMap.  It translates an old-version SubMap into
     * a new-version AscendingSubMap. This class is never otherwise
     * used.
     *
     * @serial include
     */
    private class SubMap extends AbstractMap<K, V>
            implements SortedMap<K, V>, java.io.Serializable {
        private static final long serialVersionUID = -6520786458950516097L;
        private boolean fromStart = false, toEnd = false;
        private K fromKey, toKey;

        private Object readResolve() {
            return new AscendingSubMap<>(TreeMap.this,
                    fromStart, fromKey, true,
                    toEnd, toKey, false);
        }

        public Set<Map.Entry<K, V>> entrySet() {
            throw new InternalError();
        }

        public K lastKey() {
            throw new InternalError();
        }

        public K firstKey() {
            throw new InternalError();
        }

        public SortedMap<K, V> subMap(K fromKey, K toKey) {
            throw new InternalError();
        }

        public SortedMap<K, V> headMap(K toKey) {
            throw new InternalError();
        }

        public SortedMap<K, V> tailMap(K fromKey) {
            throw new InternalError();
        }

        public Comparator<? super K> comparator() {
            throw new InternalError();
        }
    }


    //节点颜色,默认为黑色
    private static final boolean RED = false;
    private static final boolean BLACK = true;

    static final class Entry<K, V> implements Map.Entry<K, V> {
        K key;
        V value;
        //左子节点
        Entry<K, V> left;
        //右子节点
        Entry<K, V> right;
        //父节点
        Entry<K, V> parent;
        //该节点的颜色
        boolean color = BLACK;

        /**
         * Make a new cell with given key, value, and parent, and with
         * {@code null} child links, and BLACK color.
         */
        Entry(K key, V value, Entry<K, V> parent) {
            this.key = key;
            this.value = value;
            this.parent = parent;
        }

        /**
         * Returns the key.
         *
         * @return the key
         */
        public K getKey() {
            return key;
        }

        /**
         * Returns the value associated with the key.
         *
         * @return the value associated with the key
         */
        public V getValue() {
            return value;
        }

        /**
         * Replaces the value currently associated with the key with the given
         * value.
         *
         * @return the value associated with the key before this method was
         * called
         */
        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;

            return valEquals(key, e.getKey()) && valEquals(value, e.getValue());
        }

        public int hashCode() {
            int keyHash = (key == null ? 0 : key.hashCode());
            int valueHash = (value == null ? 0 : value.hashCode());
            return keyHash ^ valueHash;
        }

        public String toString() {
            return key + "=" + value;
        }
    }

    /**
     * 获取红黑树中最左侧节点
     * @return
     */
    final Entry<K, V> getFirstEntry() {
        //以根节点为父节点
        Entry<K, V> p = root;
        if (p != null)
            //父节点不为空
            //获取红黑树中最左侧的节点
            while (p.left != null)
                //将父节点的左子节点设置为父节点
                p = p.left;
        //返回最左侧节点
        //如果红黑树为空则返回null
        return p;
    }

    /**
     * 获取红黑树中最右侧节点
     * @return
     */
    final Entry<K, V> getLastEntry() {
        //以根节点为父节点
        Entry<K, V> p = root;
        if (p != null)
            //父节点不为空
            //获取红黑树中最右侧的节点
            while (p.right != null)
                //将父节点的右子节点设置为父节点
                p = p.right;
        //返回最右侧节点
        //如果红黑树为空则返回null
        return p;
    }

    /**
     * 根据节点获取该节点的后继者节点
     * 如果该节点没有后继者节点如果该节点是左子节点则返回父节点
     * 如果该节点是右子节点则一直向上获取父节点
     * 直到父节点不是右子节点则返回父节点的父节点
     * @param t
     * @return
     */
    static <K, V> TreeMap.Entry<K, V> successor(Entry<K, V> t) {
        if (t == null)
            return null;
        //校验右子节点是否不为空
        else if (t.right != null) {
            //右子节点不为空
            //将第一个右子节点设置为父节点
            Entry<K, V> p = t.right;
            //获取父节点的最左子节点
            while (p.left != null)
                //将父节点的左子节点设置为父节点
                p = p.left;
            //返回最左子节点
            return p;
        } else {
            //右子节点为空
            //获取父节点
            Entry<K, V> p = t.parent;
            //当前节点
            Entry<K, V> ch = t;
            //如果当前节点ch是右子节点的话那就一直向上获取父节点
            //直到ch节点不是右子节点
            while (p != null && ch == p.right) {
                //将父节点设置为当前节点
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    /**
     * Returns the predecessor of the specified Entry, or null if no such.
     */
    static <K, V> Entry<K, V> predecessor(Entry<K, V> t) {
        if (t == null)
            return null;
        else if (t.left != null) {
            Entry<K, V> p = t.left;
            while (p.right != null)
                p = p.right;
            return p;
        } else {
            Entry<K, V> p = t.parent;
            Entry<K, V> ch = t;
            while (p != null && ch == p.left) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    /**
     * 获取节点的颜色
     * @param p
     * @return
     */
    private static <K, V> boolean colorOf(Entry<K, V> p) {
        return (p == null ? BLACK : p.color);
    }

    /**
     * 获取父节点
     * @param p
     * @return 父节点
     */
    private static <K, V> Entry<K, V> parentOf(Entry<K, V> p) {
        return (p == null ? null : p.parent);
    }

    /**
     * 将节点设置为指定的颜色
     * @param p 节点
     * @param c 颜色
     */
    private static <K, V> void setColor(Entry<K, V> p, boolean c) {
        if (p != null)
            p.color = c;
    }

    /**
     * 获取左子节点
     * @param p
     * @return 左子节点
     */
    private static <K, V> Entry<K, V> leftOf(Entry<K, V> p) {
        return (p == null) ? null : p.left;
    }

    /**
     * 获取右子节点
     * @param p
     * @return 右子节点
     */
    private static <K, V> Entry<K, V> rightOf(Entry<K, V> p) {
        return (p == null) ? null : p.right;
    }

    /**
     * From CLR
     */
    private void rotateLeft(Entry<K, V> p) {
        if (p != null) {
            Entry<K, V> r = p.right;
            p.right = r.left;
            if (r.left != null)
                r.left.parent = p;
            r.parent = p.parent;
            if (p.parent == null)
                root = r;
            else if (p.parent.left == p)
                p.parent.left = r;
            else
                p.parent.right = r;
            r.left = p;
            p.parent = r;
        }
    }

    /**
     * From CLR
     */
    private void rotateRight(Entry<K, V> p) {
        if (p != null) {
            Entry<K, V> l = p.left;
            p.left = l.right;
            if (l.right != null) l.right.parent = p;
            l.parent = p.parent;
            if (p.parent == null)
                root = l;
            else if (p.parent.right == p)
                p.parent.right = l;
            else p.parent.left = l;
            l.right = p;
            p.parent = l;
        }
    }

    /**
     * 平衡节点
     */
    private void fixAfterInsertion(Entry<K, V> x) {
        //将当前添加的节点设置为红色
        x.color = RED;
        //当前节点不为空并且不是根节点并且父节点的颜色是红色
        while (x != null && x != root && x.parent.color == RED) {
            //校验当前添加的节点的父节点是否是左子节点
            if (parentOf(x) == leftOf(parentOf(parentOf(x)))) {
                //获取叔叔节点(父节点的父节点的右子节点)
                Entry<K, V> y = rightOf(parentOf(parentOf(x)));
                //校验叔叔节点(父节点的父节点的右子节点)的颜色是否是红色
                if (colorOf(y) == RED) {
                    //叔叔节点(父节点的父节点的右子节点)的颜色为红色
                    //将父节点设置为黑色
                    setColor(parentOf(x), BLACK);
                    //将叔叔节点设置为黑色
                    setColor(y, BLACK);
                    //将父节点的父节点的颜色设置为红色
                    setColor(parentOf(parentOf(x)), RED);
                    //将父节点的父节点设置为当前节点继续平衡红黑树
                    x = parentOf(parentOf(x));
                } else {
                    //叔叔节点(父节点的父节点的右子节点)的颜色为黑色
                    //校验当前节点是否是右子节点
                    if (x == rightOf(parentOf(x))) {
                        //当前节点是右子节点
                        //将父节点设置为当前节点
                        x = parentOf(x);
                        //根据当前节点进行左旋
                        rotateLeft(x);
                    }
                    //将当前节点的父节点的颜色设置为黑色
                    setColor(parentOf(x), BLACK);
                    //将父节点的父节点的颜色设置为红色
                    setColor(parentOf(parentOf(x)), RED);
                    //根据父节点的父节点进行右旋
                    rotateRight(parentOf(parentOf(x)));
                }
            } else {
                //获取叔叔节点(父节点的父节点的左子节点)
                Entry<K, V> y = leftOf(parentOf(parentOf(x)));
                //校验叔叔节点(父节点的父节点的左子节点)的颜色是否是红色
                if (colorOf(y) == RED) {
                    //叔叔节点(父节点的父节点的左子节点)的颜色为红色
                    //将父节点设置为黑色
                    setColor(parentOf(x), BLACK);
                    //将叔叔节点设置为黑色
                    setColor(y, BLACK);
                    //将父节点的父节点的颜色设置为红色
                    setColor(parentOf(parentOf(x)), RED);
                    //将父节点的父节点设置为当前节点继续平衡红黑树
                    x = parentOf(parentOf(x));
                } else {
                    //叔叔节点(父节点的父节点的左子节点)的颜色为黑色
                    //校验当前节点是否是左子节点
                    if (x == leftOf(parentOf(x))) {
                        //当前节点是左子节点
                        //将父节点设置为当前节点
                        x = parentOf(x);
                        //根据当前节点进行右旋
                        rotateRight(x);
                    }
                    //将当前节点的父节点的颜色设置为黑色
                    setColor(parentOf(x), BLACK);
                    //将父节点的父节点的颜色设置为红色
                    setColor(parentOf(parentOf(x)), RED);
                    //根据父节点的父节点进行左旋
                    rotateLeft(parentOf(parentOf(x)));
                }
            }
        }
        //根节点的颜色设置为黑色
        //此处为什么要将根节点的颜色设置为黑色
        //因为在上面的代码中可以看出来在平衡节点的时候将父节点的父节点的颜色设置为了红色
        //如果这个父节点的父节点刚好是根节点这样就会导致红黑树不平衡
        root.color = BLACK;
    }

    /**
     * 删除指定的节点并重新对红黑树进行平衡
     * 待删除节点主要分为两种情况
     * 情况1：待删除节点的左右子节点都不为空
     * 情况2：待删除节点的左右子节点为空或左右子节点只有一个节点为空
     *
     * 在情况1下则使用待删除节点的后继节点来替换待删除节点
     * 该后继节点则是待删除节点的第一个右子节点的最左子节点
     * 按红黑树左小右大来,该最左子节点则是在待删除节点中的右子节点中的最小的一个节点
     * 如果右子节点中没有最左子节点则使用右子节点替换待删除的节点
     *
     * 在情况2下如果待删除节点的左右子节点都为空则直接将该节点删除
     * 如果左右子节点只有一个节点为空则使用不为空的子节点替换待删除的节点
     *
     * @param p 待删除节点
     */
    private void deleteEntry(Entry<K, V> p) {
        //更新集合长度和修改次数
        modCount++;
        size--;
        if (p.left != null && p.right != null) {
            //如果待删除节点有左右子节点则获取右子节点中最左侧的节点
            //如果待删除节点的右子节点没有左子节点则返回待删除节点的右子节点
            Entry<K, V> s = successor(p);
            //使用节点s中的key-value替换待删除的节点的key-value
            //此时红黑树中则会有两个相同的key-value节点
            p.key = s.key;
            p.value = s.value;
            //将变量p的指针指向s指针指向的节点
            //此时s指针指向的节点就变成了待删除的节点
            //为什么要将p指针指向s指针所在的节点呢？
            //因为在上面已经将待删除的节点中的key-value替换
            //被替换之后指定删除的key的节点已经不存在
            //此时只需要将两个相同key-value节点删除一个即可
            //此时则会将s指针指向的节点删除,因为简单,只需要将s指针指向的节点的后续节点的指针指向原p节点
            p = s;
        }
        //获取替换的节点
        //如果替换的节点为空则说明待删除的节点左右子节点都为空
        //不为空则说明待删除的节点的左右子节点有一个节点不为空
        Entry<K, V> replacement = (p.left != null ? p.left : p.right);
        //校验替换的节点是否为空
        if (replacement != null) {
            //左右子节点只有一个节点为空
            //替换的节点的父节点指针指向待删除节点的父节点
            replacement.parent = p.parent;
            //待删除节点的父节点是否为空
            if (p.parent == null)
                //待删除节点的父节点为空则说明待删除节点是根节点
                //将替换的节点设置为根节点
                root = replacement;
            //待删除节点是否为左子节点
            else if (p == p.parent.left)
                //待删除节点是左子节点则将待删除节点的父节点的左子节点指针指向替换的节点
                p.parent.left = replacement;
            else
                //待删除节点是右子节点则将待删除节点的父节点的右子节点指针指向替换的节点
                p.parent.right = replacement;
            //将待删除节点的指针置空
            p.left = p.right = p.parent = null;
            //删除的节点的颜色是否为黑色
            //如果为黑色则需要对红黑树进行平衡
            if (p.color == BLACK)
                //对红黑树进行平衡
                fixAfterDeletion(replacement);
        } else if (p.parent == null) {
            //待删除的节点是唯一的节点
            //将根节点置空
            root = null;
        } else {
            //待删除节点的左右子节点都为空
            //待删除节点的颜色是否为黑色
            //然后为黑色则需要对红黑树进行平衡
            if (p.color == BLACK)
                //对红黑树进行平衡
                fixAfterDeletion(p);
            if (p.parent != null) {
                //校验待删除节点是否是左子节点
                if (p == p.parent.left)
                    //待删除节点是左子节点则将父节点的左子节点的指针置为空
                    p.parent.left = null;
                //校验待删除节点是否是右子节点
                else if (p == p.parent.right)
                    //待删除节点是右子节点则将父节点的右子节点的指针置为空
                    p.parent.right = null;
                //将待删除节点的父节点指针置为空
                p.parent = null;
            }
        }
    }

    /**
     * 平衡节点
     * x = 待删除的节点 | 替换待删除的节点
     */
    private void fixAfterDeletion(Entry<K, V> x) {
        //x节点不是根节点并且节点的颜色是黑色则对红黑树进行平衡
        while (x != root && colorOf(x) == BLACK) {
            //校验x节点是否是左子节点
            if (x == leftOf(parentOf(x))) {
                //x节点是左子节点
                //获取x节点的兄弟节点(x节点的父节点的右子节点)
                Entry<K, V> sib = rightOf(parentOf(x));
                //校验x节点的兄弟节点的节点颜色是否是红色
                if (colorOf(sib) == RED) {
                    //将x节点的兄弟节点的颜色设置为黑色
                    setColor(sib, BLACK);
                    //将父节点的颜色设置为红色
                    setColor(parentOf(x), RED);
                    //以父节点向左进行旋转使红黑树平衡
                    rotateLeft(parentOf(x));
                    //获取平衡后的x节点的兄弟节点
                    sib = rightOf(parentOf(x));
                }
                //校验x节点的兄弟节点的左右子节点的颜色是否为黑色
                if (colorOf(leftOf(sib)) == BLACK && colorOf(rightOf(sib)) == BLACK) {
                    //将兄弟节点的颜色设置为红色
                    setColor(sib, RED);
                    //将x指针指向父节点下次循环则会对父节点进行平衡
                    x = parentOf(x);
                } else {
                    //x节点的兄弟节点的左右子节点的颜色都不为黑色或有一个不为黑色
                    //校验x节点的兄弟节点的右子节点的颜色是否为黑色
                    if (colorOf(rightOf(sib)) == BLACK) {
                        //兄弟节点的右子节点的颜色为红色则将兄弟节点的左子节点的颜色设置为黑色
                        setColor(leftOf(sib), BLACK);
                        //将兄弟节点的颜色设置为红色
                        setColor(sib, RED);
                        //以兄弟节点向右旋转使红黑树平衡
                        rotateRight(sib);
                        //获取平衡后的x节点的兄弟节点
                        sib = rightOf(parentOf(x));
                    }
                    //将x节点的兄弟节点的颜色设置为父节点的颜色
                    setColor(sib, colorOf(parentOf(x)));
                    //将x节点的父节点颜色设置为黑色
                    setColor(parentOf(x), BLACK);
                    //将x节点的兄弟节点的右子节点的颜色设置为黑色
                    setColor(rightOf(sib), BLACK);
                    //以父节点向左进行旋转使红黑树平衡
                    rotateLeft(parentOf(x));
                    //将x指针指向根节点退出循环
                    x = root;
                }
            } else {
                //x节点是右子节点
                //获取x节点的兄弟节点(x节点的父节点的左子节点)
                Entry<K, V> sib = leftOf(parentOf(x));
                //校验x节点的兄弟节点的节点颜色是否是红色
                if (colorOf(sib) == RED) {
                    //将x节点的兄弟节点的颜色设置为黑色
                    setColor(sib, BLACK);
                    //将父节点的颜色设置为红色
                    setColor(parentOf(x), RED);
                    //以父节点向右进行旋转使红黑树平衡
                    rotateRight(parentOf(x));
                    //获取平衡后的x节点的兄弟节点
                    sib = leftOf(parentOf(x));
                }
                //校验x节点的兄弟节点的左右子节点的颜色是否为黑色
                if (colorOf(rightOf(sib)) == BLACK && colorOf(leftOf(sib)) == BLACK) {
                    //将兄弟节点的颜色设置为红色
                    setColor(sib, RED);
                    //将x指针指向父节点下次循环则会对父节点进行平衡
                    x = parentOf(x);
                } else {
                    //x节点的兄弟节点的左右子节点的颜色都不为黑色或有一个不为黑色
                    //校验x节点的兄弟节点的左子节点的颜色是否为黑色
                    if (colorOf(leftOf(sib)) == BLACK) {
                        //兄弟节点的左子节点的颜色为红色则将兄弟节点的有子节点的颜色设置为黑色
                        setColor(rightOf(sib), BLACK);
                        //将兄弟节点的颜色设置为红色
                        setColor(sib, RED);
                        //以兄弟节点向左旋转使红黑树平衡
                        rotateLeft(sib);
                        //获取平衡后的x节点的兄弟节点
                        sib = leftOf(parentOf(x));
                    }
                    //将x节点的兄弟节点的颜色设置为父节点的颜色
                    setColor(sib, colorOf(parentOf(x)));
                    //将x节点的父节点颜色设置为黑色
                    setColor(parentOf(x), BLACK);
                    //将x节点的兄弟节点的左子节点的颜色设置为黑色
                    setColor(leftOf(sib), BLACK);
                    //以父节点向右进行旋转使红黑树平衡
                    rotateRight(parentOf(x));
                    //将x指针指向根节点退出循环
                    x = root;
                }
            }
        }
        //将根节点设置为黑色
        //在红黑树平衡的过程中根节点可能会更改
        //更改的根节点的颜色可能为红色
        //此时就不符合红黑树根节点为黑色节点的条件
        //所以需要将红黑树的根节点设置为黑色
        setColor(x, BLACK);
    }

    private static final long serialVersionUID = 919286545866124006L;

    /**
     * Save the state of the {@code TreeMap} instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <em>size</em> of the TreeMap (the number of key-value
     * mappings) is emitted (int), followed by the key (Object)
     * and value (Object) for each key-value mapping represented
     * by the TreeMap. The key-value mappings are emitted in
     * key-order (as determined by the TreeMap's Comparator,
     * or by the keys' natural ordering if the TreeMap has no
     * Comparator).
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        // Write out the Comparator and any hidden stuff
        s.defaultWriteObject();

        // Write out size (number of Mappings)
        s.writeInt(size);

        // Write out keys and values (alternating)
        for (Iterator<Map.Entry<K, V>> i = entrySet().iterator(); i.hasNext(); ) {
            Map.Entry<K, V> e = i.next();
            s.writeObject(e.getKey());
            s.writeObject(e.getValue());
        }
    }

    /**
     * Reconstitute the {@code TreeMap} instance from a stream (i.e.,
     * deserialize it).
     */
    private void readObject(final java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        // Read in the Comparator and any hidden stuff
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();

        buildFromSorted(size, null, s, null);
    }

    /**
     * Intended to be called only from TreeSet.readObject
     */
    void readTreeSet(int size, java.io.ObjectInputStream s, V defaultVal)
            throws java.io.IOException, ClassNotFoundException {
        buildFromSorted(size, null, s, defaultVal);
    }

    /**
     * Intended to be called only from TreeSet.addAll
     */
    void addAllForTreeSet(SortedSet<? extends K> set, V defaultVal) {
        try {
            buildFromSorted(set.size(), set.iterator(), null, defaultVal);
        } catch (java.io.IOException cannotHappen) {
        } catch (ClassNotFoundException cannotHappen) {
        }
    }


    /**
     * 构建红黑树
     * @param size 集合长度
     * @param it  集合迭代器
     * @param str key-value的Stream流
     *            集合迭代器和Stream流至少有一个为空
     * @param defaultVal 默认value
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void buildFromSorted(int size, Iterator<?> it, java.io.ObjectInputStream str, V defaultVal) throws java.io.IOException, ClassNotFoundException {
        //设置集合长度
        this.size = size;
        //buildFromSorted 构建红黑树中的每一个节点的左右子节点
        //将中间索引的节点设置为根节点
        root = buildFromSorted(0, 0, size - 1, computeRedLevel(size), it, str, defaultVal);
    }

    /**
     * 递归构建红黑树中每一个节点的左右子节点
     * 当节点层级等于redLevel的时候则将节点置为红色
     * 其余节点则置为黑色
     * 这样就能满足红黑树的5个条件
     * @param level  树的级别
     * @param lo     最小索引(0)
     * @param hi     最大索引(size-1)
     * @param redLevel 红色节点所在的层级
     * @param it  集合迭代器
     * @param str key-value的Stream流
     *            集合迭代器和Stream流至少有一个为空
     * @param defaultVal 默认value
     * @return
     */
    @SuppressWarnings("unchecked")
    private final Entry<K, V> buildFromSorted(int level, int lo, int hi, int redLevel, Iterator<?> it, java.io.ObjectInputStream str, V defaultVal) throws java.io.IOException, ClassNotFoundException {
        if (hi < lo) return null;
        //获取中间索引
        int mid = (lo + hi) >>> 1;
        //left 每一个节点的左子节点
        Entry<K, V> left = null;
        //lo >= mid 说明节点已经是最低层的左子节点
        if (lo < mid)
            //递归构建左子节点
            left = buildFromSorted(level + 1, lo, mid - 1, redLevel,it, str, defaultVal);

        //从迭代器或Stream流中获取key-value
        K key;
        V value;
        //校验迭代器是否为空
        if (it != null) {
            if (defaultVal == null) {
                //defaultVal为空
                //从迭代器中获取key-value
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) it.next();
                key = (K) entry.getKey();
                value = (V) entry.getValue();
            } else {
                //defaultVal不为空
                //从迭代器中获取key
                key = (K) it.next();
                //使用defaultVal作为value
                value = defaultVal;
            }
        } else {
            //迭代器为空
            //从Stream流中获取key
            key = (K) str.readObject();
            //校验defaultVal是否为空
            //不为空则使用defaultVal
            //为空则从Stream流中获取value
            value = (defaultVal != null ? defaultVal : (V) str.readObject());
        }
        //创建节点,默认为黑色节点
        Entry<K, V> middle = new Entry<>(key, value, null);

        if (level == redLevel)
            //节点已经是最底层的节点
            //将节点颜色置为红色
            middle.color = RED;

        if (left != null) {
            //左子节点已经构造完成
            //将当前节点的左子节点的指针指向left
            middle.left = left;
            //将left的父节点指针指向当前节点
            left.parent = middle;
        }

        if (mid < hi) {
            //递归构建右子节点
            Entry<K, V> right = buildFromSorted(level + 1, mid + 1, hi, redLevel, it, str, defaultVal);
            //将当前节点的右子节点的指针指向right
            middle.right = right;
            //将right的父节点指针指向当前节点
            right.parent = middle;
        }

        return middle;
    }

    /**
     * 找到所有节点为黑色节点的级别,将剩余节点涂为红色
     * 其实就是将红黑树中的最后一层节点涂为红色,其余节点都为黑色
     * 这样就能满足红黑树的5个条件
     * @param sz 长度
     * @return 红色节点所在的层级
     */
    private static int computeRedLevel(int sz) {
        int level = 0;
        for (int m = sz - 1; m >= 0; m = m / 2 - 1)
            level++;
        return level;
    }

    /**
     * Currently, we support Spliterator-based versions only for the
     * full map, in either plain of descending form, otherwise relying
     * on defaults because size estimation for submaps would dominate
     * costs. The type tests needed to check these for key views are
     * not very nice but avoid disrupting existing class
     * structures. Callers must use plain default spliterators if this
     * returns null.
     */
    static <K> Spliterator<K> keySpliteratorFor(NavigableMap<K, ?> m) {
        if (m instanceof TreeMap) {
            @SuppressWarnings("unchecked") TreeMap<K, Object> t =
                    (TreeMap<K, Object>) m;
            return t.keySpliterator();
        }
        if (m instanceof DescendingSubMap) {
            @SuppressWarnings("unchecked") DescendingSubMap<K, ?> dm =
                    (DescendingSubMap<K, ?>) m;
            TreeMap<K, ?> tm = dm.m;
            if (dm == tm.descendingMap) {
                @SuppressWarnings("unchecked") TreeMap<K, Object> t =
                        (TreeMap<K, Object>) tm;
                return t.descendingKeySpliterator();
            }
        }
        @SuppressWarnings("unchecked") NavigableSubMap<K, ?> sm =
                (NavigableSubMap<K, ?>) m;
        return sm.keySpliterator();
    }

    final Spliterator<K> keySpliterator() {
        return new KeySpliterator<K, V>(this, null, null, 0, -1, 0);
    }

    final Spliterator<K> descendingKeySpliterator() {
        return new DescendingKeySpliterator<K, V>(this, null, null, 0, -2, 0);
    }

    /**
     * Base class for spliterators.  Iteration starts at a given
     * origin and continues up to but not including a given fence (or
     * null for end).  At top-level, for ascending cases, the first
     * split uses the root as left-fence/right-origin. From there,
     * right-hand splits replace the current fence with its left
     * child, also serving as origin for the split-off spliterator.
     * Left-hands are symmetric. Descending versions place the origin
     * at the end and invert ascending split rules.  This base class
     * is non-commital about directionality, or whether the top-level
     * spliterator covers the whole tree. This means that the actual
     * split mechanics are located in subclasses. Some of the subclass
     * trySplit methods are identical (except for return types), but
     * not nicely factorable.
     * <p>
     * Currently, subclass versions exist only for the full map
     * (including descending keys via its descendingMap).  Others are
     * possible but currently not worthwhile because submaps require
     * O(n) computations to determine size, which substantially limits
     * potential speed-ups of using custom Spliterators versus default
     * mechanics.
     * <p>
     * To boostrap initialization, external constructors use
     * negative size estimates: -1 for ascend, -2 for descend.
     */
    static class TreeMapSpliterator<K, V> {
        final TreeMap<K, V> tree;
        TreeMap.Entry<K, V> current; // traverser; initially first node in range
        TreeMap.Entry<K, V> fence;   // one past last, or null
        int side;                   // 0: top, -1: is a left split, +1: right
        int est;                    // size estimate (exact only for top-level)
        int expectedModCount;       // for CME checks

        TreeMapSpliterator(TreeMap<K, V> tree,
                           TreeMap.Entry<K, V> origin, TreeMap.Entry<K, V> fence,
                           int side, int est, int expectedModCount) {
            this.tree = tree;
            this.current = origin;
            this.fence = fence;
            this.side = side;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getEstimate() { // force initialization
            int s;
            TreeMap<K, V> t;
            if ((s = est) < 0) {
                if ((t = tree) != null) {
                    current = (s == -1) ? t.getFirstEntry() : t.getLastEntry();
                    s = est = t.size;
                    expectedModCount = t.modCount;
                } else
                    s = est = 0;
            }
            return s;
        }

        public final long estimateSize() {
            return (long) getEstimate();
        }
    }

    static final class KeySpliterator<K, V>
            extends TreeMapSpliterator<K, V>
            implements Spliterator<K> {
        KeySpliterator(TreeMap<K, V> tree,
                       TreeMap.Entry<K, V> origin, TreeMap.Entry<K, V> fence,
                       int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public KeySpliterator<K, V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K, V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0) ? tree.root : // was top
                                    (d > 0) ? e.right :   // was right
                                            (d < 0 && f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new KeySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super K> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K, V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.key);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    } else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            TreeMap.Entry<K, V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e.key);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.ORDERED;
        }

        public final Comparator<? super K> getComparator() {
            return tree.comparator;
        }

    }

    static final class DescendingKeySpliterator<K, V>
            extends TreeMapSpliterator<K, V>
            implements Spliterator<K> {
        DescendingKeySpliterator(TreeMap<K, V> tree,
                                 TreeMap.Entry<K, V> origin, TreeMap.Entry<K, V> fence,
                                 int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public DescendingKeySpliterator<K, V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K, V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0) ? tree.root : // was top
                                    (d < 0) ? e.left :    // was left
                                            (d > 0 && f != null) ? f.right :   // was right
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) > 0) {       // e not already past s
                side = 1;
                return new DescendingKeySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super K> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K, V> f = fence, e, p, pr;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.key);
                    if ((p = e.left) != null) {
                        while ((pr = p.right) != null)
                            p = pr;
                    } else {
                        while ((p = e.parent) != null && e == p.left)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            TreeMap.Entry<K, V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = predecessor(e);
            action.accept(e.key);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.ORDERED;
        }
    }

    static final class ValueSpliterator<K, V>
            extends TreeMapSpliterator<K, V>
            implements Spliterator<V> {
        ValueSpliterator(TreeMap<K, V> tree,
                         TreeMap.Entry<K, V> origin, TreeMap.Entry<K, V> fence,
                         int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public ValueSpliterator<K, V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K, V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0) ? tree.root : // was top
                                    (d > 0) ? e.right :   // was right
                                            (d < 0 && f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new ValueSpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super V> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K, V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.value);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    } else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super V> action) {
            TreeMap.Entry<K, V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e.value);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) | Spliterator.ORDERED;
        }
    }

    static final class EntrySpliterator<K, V>
            extends TreeMapSpliterator<K, V>
            implements Spliterator<Map.Entry<K, V>> {
        EntrySpliterator(TreeMap<K, V> tree,
                         TreeMap.Entry<K, V> origin, TreeMap.Entry<K, V> fence,
                         int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public EntrySpliterator<K, V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K, V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0) ? tree.root : // was top
                                    (d > 0) ? e.right :   // was right
                                            (d < 0 && f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new EntrySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K, V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    } else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<K, V>> action) {
            TreeMap.Entry<K, V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.ORDERED;
        }

        @Override
        public Comparator<Map.Entry<K, V>> getComparator() {
            // Adapt or create a key-based comparator
            if (tree.comparator != null) {
                return Map.Entry.comparingByKey(tree.comparator);
            } else {
                return (Comparator<Map.Entry<K, V>> & Serializable) (e1, e2) -> {
                    @SuppressWarnings("unchecked")
                    Comparable<? super K> k1 = (Comparable<? super K>) e1.getKey();
                    return k1.compareTo(e2.getKey());
                };
            }
        }
    }
}
