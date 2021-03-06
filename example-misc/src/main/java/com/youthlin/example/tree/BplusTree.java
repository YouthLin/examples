package com.youthlin.example.tree;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author youthlin.chen
 * @date 2019-03-15 20:33
 * @link https://www.cnblogs.com/nullzx/p/8729425.html
 */
public class BplusTree<K, V> extends AbstractMap<K, V> implements Map<K, V>, Cloneable, Serializable {
    private static final long serialVersionUID = -244544544703860023L;

    /**
     * 结点
     */
    private static class Node<K, V> {
        /**
         * 为 null 说明是 root 结点
         */
        private Node<K, V> parent;
        /**
         * 结点的关键字
         */
        private List<Entry<K, V>> data = Lists.newLinkedList();
        /**
         * n个关键字则有n+1个子孩子
         * 为 null 说明是叶结点
         */
        private List<Node<K, V>> children;
        private Node<K, V> prev;
        /**
         * 同层次的下一个结点
         */
        private Node<K, V> next;

        private void addChild(Node<K, V> child) {
            children.add(child);
            child.parent = this;
        }

        private void addChild(int index, Node<K, V> child) {
            children.add(index, child);
            child.parent = this;
        }

        private void addChildren(Collection<Node<K, V>> children) {
            this.children.addAll(children);
            for (Node<K, V> child : children) {
                child.parent = this;
            }
        }
    }

    private static final int DEFAULT_MAX_ELEMENT_PER_NODE = 5;
    /**
     * 根结点
     */
    private transient Node<K, V> root;
    /**
     * 最小的结点
     */
    private transient Node<K, V> min;
    /**
     * 阶数
     * m 阶 B+ 树内个节点最多存放 m-1 项数据, 最多 m 个子孩子
     */
    private final int maxChildren;
    /**
     * 每个节点最少需要的记录数
     */
    private final int minElementPerNode;
    /**
     * 比较器 如果为 null 则使用 {@link K} 的自然顺序, 此时要求 {@link K}  实现了 {@link Comparable}  接口
     */
    private final Comparator<? super K> comparator;
    private transient int size;
    private transient int modCount;
    private transient Set<Map.Entry<K, V>> entrySet;

    //region 构造方法

    public BplusTree() {
        this(DEFAULT_MAX_ELEMENT_PER_NODE, null, null);
    }

    public BplusTree(int maxChildren) {
        this(maxChildren, null, null);
    }

    public BplusTree(Comparator<? super K> comparator) {
        this(DEFAULT_MAX_ELEMENT_PER_NODE, null, comparator);
    }

    public BplusTree(int maxChildren, Comparator<? super K> comparator) {
        this(maxChildren, null, comparator);
    }

    public BplusTree(int maxChildren, int minElementPerNode) {
        this(maxChildren, minElementPerNode, null);
    }

    public BplusTree(int maxChildren, Integer minElementPerNode, Comparator<? super K> comparator) {
        if (minElementPerNode == null) {
            minElementPerNode = maxChildren >> 1;
        }
        Preconditions.checkArgument(maxChildren > 2, "maxChildren should greater than 2");
        Preconditions.checkArgument(minElementPerNode > 0, "minElementPerNode should greater than 0");
        this.maxChildren = maxChildren;
        this.minElementPerNode = minElementPerNode;
        this.comparator = comparator;
    }

    //endregion 构造方法

    //region 重写父类方法

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean containsKey(@NotNull Object key) {
        @SuppressWarnings("unchecked") K k = (K) Objects.requireNonNull(key);
        Node<K, V> leaf = findLeafNode(k);
        return getExactIndex(k, leaf) != -1;
    }

    @Override
    public V get(@NotNull Object key) {
        @SuppressWarnings("unchecked") K k = (K) Objects.requireNonNull(key);
        Node<K, V> leaf = findLeafNode(k);
        return getOnLeafOrNull(k, leaf);
    }

    /**
     * 插入
     * 1. 若为空树 创建一个叶子结点 插入 此时root,min也是该叶子结点 结束
     * 2. 定位到要插入的叶子结点
     * 3. 针对叶子结点
     * 3.1 插入记录，若当前叶子结点记录数小于等于 maxChildren-1 结束
     * 3.2 否则将该叶子结点分裂为左右两个叶子结点，左边包含前 maxChildren/2 个，右边包含剩下的
     * 3.3 将分裂后右边的第一个记录进位到父结点中 该关键字的左右子孩子分别是刚分裂的左右叶子结点
     * 3.4 将当前结点指向父结点
     * 4. 针对内结点
     * 4.1 若当前结点记录数小于等于 maxChildren-1 结束
     * 4.2 否则 将该内结点分裂为两个内结点 左结点包含前 (maxChildren-1)/2 个记录 右结点包含 maxChildren/2 个记录
     * 中间的记录进位到父结点中 进位的该关键字左右子孩子分别是刚分裂的左右内结点
     * 4.3 将当前结点指向父结点 重复第 4 步
     *
     * @return the put value always
     */
    @Override
    public V put(@NotNull K key, V value) {
        Node<K, V> leaf = findLeafNode(Objects.requireNonNull(key));
        insertToLeafNode(leaf, key, value);
        if (shouldSplit(leaf)) {
            Node<K, V> current = splitLeaf(leaf);
            while (shouldSplit(current)) {
                current = splitInnerNode(current);
            }
        }
        size++;
        modCount++;
        return value;
    }

    @Override
    public V remove(Object key) {
        @SuppressWarnings("unchecked") K k = (K) Objects.requireNonNull(key);
        Node<K, V> leaf = findLeafNode(k);
        int index = getExactIndex(k, leaf);
        if (index == -1) {
            //叶子结点没有相应的 key 删除失败
            return null;
        }
        return removeOnLeafNode(leaf, index);
    }

    @Override
    @NotNull
    public Set<Map.Entry<K, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new EntrySet();
        }
        return entrySet;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object clone() {
        BplusTree<K, V> clone;
        try {
            clone = (BplusTree<K, V>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
        clone.root = clone.min = null;
        clone.size = clone.modCount = 0;
        clone.entrySet = null;
        clone.putAll(this);
        return clone;
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();
        s.writeInt(size);
        for (Entry<K, V> entry : entrySet()) {
            s.writeObject(entry.getKey());
            s.writeObject(entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        int size = s.readInt();
        for (int i = 0; i < size; i++) {
            put((K) s.readObject(), (V) s.readObject());
        }
    }

    @Override
    public String toString() {
        return innerToString();
    }

    //endregion 重写父类方法

    private Node<K, V> findLeafNode(K key) {
        if (root == null) {
            min = root = new Node<>();
            return root;
        } else {
            Node<K, V> current = root;
            while (current.children != null) {
                int index = getInsertIndex(current, key);
                current = current.children.get(index);
            }
            return current;
        }
    }

    /**
     * 在一个结点中定位要插入的元素应该插在哪个下标
     */
    private int getInsertIndex(Node<K, V> node, Entry<K, V> entry) {
        return getInsertIndex(node, entry.getKey());
    }

    private int getInsertIndex(Node<K, V> node, K key) {
        int index = 0;
        for (Entry<K, V> inNode : node.data) {
            if (compare(inNode.getKey(), key) <= 0) {
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    @SuppressWarnings("unchecked")
    private int compare(K left, K right) {
        if (comparator != null) {
            return comparator.compare(left, right);
        }
        if (left instanceof Comparable && right instanceof Comparable) {
            return ((Comparable) left).compareTo(right);
        }
        throw new ClassCastException("key should be Comparable when comparator not specified.");
    }

    /**
     * 获取结点中指定key的下标
     */
    private int getExactIndex(K key, Node<K, V> node) {
        int index = -1;
        for (Entry<K, V> entry : node.data) {
            index++;
            int compare = compare(entry.getKey(), key);
            if (compare == 0) {
                return index;
            }
            if (compare > 0) {
                return -1;
            }
        }
        return -1;
    }

    private V getOnLeafOrNull(K key, Node<K, V> leaf) {
        for (Entry<K, V> entry : leaf.data) {
            int compare = compare(entry.getKey(), key);
            if (compare == 0) {
                return entry.getValue();
            }
            if (compare > 0) {
                break;
            }
        }
        return null;
    }

    private void insertToLeafNode(Node<K, V> leaf, K key, V value) {
        int index = getInsertIndex(leaf, key);
        leaf.data.add(index, new SimpleEntry<>(key, value));
    }

    private boolean shouldSplit(Node<K, V> node) {
        return node.data.size() >= maxChildren;
    }

    private Node<K, V> splitLeaf(Node<K, V> leaf) {
        //分裂
        Node<K, V> right = new Node<>();
        for (int i = maxChildren >> 1; i < leaf.data.size(); ) {
            right.data.add(leaf.data.remove(i));
        }
        link(leaf, right);
        //进位
        Entry<K, V> up = right.data.get(0);
        return insertToUp(leaf, right, up);
    }

    private void link(Node<K, V> left, Node<K, V> right) {
        right.next = left.next;
        left.next = right;
        right.prev = left;
        if (right.next != null) {
            right.next.prev = right;
        }
    }

    private Node<K, V> insertToUp(Node<K, V> left, Node<K, V> right, Entry<K, V> up) {
        Node<K, V> parent = left.parent;
        if (parent == null) {
            parent = new Node<>();
            parent.children = Lists.newArrayList();
            root = parent;
        }
        int index = getInsertIndex(parent, up);
        parent.data.add(index, up);
        //noinspection StatementWithEmptyBody
        if (index < parent.children.size() && parent.children.get(index) == left) {
            //已经在不用再添加
        } else {
            parent.addChild(index, left);
        }
        parent.addChild(index + 1, right);
        return parent;
    }

    private Node<K, V> splitInnerNode(Node<K, V> node) {
        //分裂
        Node<K, V> right = new Node<>();
        right.children = Lists.newLinkedList();
        int center = maxChildren >> 1;
        for (int i = center + 1; i < node.data.size(); ) {
            right.data.add(node.data.remove(i));
            right.addChild(node.children.remove(i));
        }
        right.addChild(node.children.remove(center + 1));
        link(node, right);
        //进位
        Entry<K, V> up = node.data.remove(center);
        return insertToUp(node, right, up);
    }

    private V removeOnLeafNode(Node<K, V> leaf, int index) {
        //1 删除叶子结点
        Entry<K, V> remove = leaf.data.remove(index);
        // 删除后叶子结点key个数符合填充因子则结束 否则:
        if (tooLess(leaf)) {
            Node<K, V> richNeighborNode = findRichNeighborNode(leaf);
            if (richNeighborNode != null) {
                //2 如果兄弟有富余
                borrowFormLeafNeighbor(leaf, index, richNeighborNode);
            } else {
                //3 兄弟结点没有富余 那么与兄弟合并 并删除父节点中的key 将当前结点指向父结点
                joinWithLeafNeighbor(leaf);
            }
        }
        size--;
        modCount++;
        return remove.getValue();
    }

    private boolean tooLess(Node<K, V> node) {
        return node.data.size() < minElementPerNode;
    }

    private Node<K, V> findRichNeighborNode(Node<K, V> current) {
        Node<K, V> prev = current.prev;
        if (prev != null && prev.parent == current.parent) {
            if (rich(prev)) {
                return prev;
            }
        }
        Node<K, V> next = current.next;
        if (next != null && next.parent == current.parent) {
            if (rich(next)) {
                return next;
            }
        }
        return null;
    }

    private boolean rich(Node<K, V> node) {
        return node.data.size() > minElementPerNode;
    }

    private void borrowFormLeafNeighbor(Node<K, V> leaf, int removeIndexOnLeaf, Node<K, V> richNeighborNode) {
        if (richNeighborNode == leaf.prev) {
            //左边最后一个借过来 同时更新当前叶子的父结点为借过来的值
            //    7      11
            // 5,6  7,8,9  <11>
            //
            //    7    9
            // 5,6  7,8  9,11
            Entry<K, V> borrow = richNeighborNode.data.remove(richNeighborNode.data.size() - 1);
            leaf.data.add(0, borrow);
            int parentIndex = getExactIndex(richNeighborNode.data.get(0).getKey(), richNeighborNode.parent);
            Preconditions.checkArgument(parentIndex > -1);
            richNeighborNode.parent.data.remove(parentIndex + 1);
            richNeighborNode.parent.data.add(parentIndex + 1, borrow);
        } else if (richNeighborNode == leaf.next) {
            //右边第一个借过来 同时更新右边结点的父结点为借过来后剩下的那个最小值
            //     7   8
            // 5,6  <7>  8,9,10
            //
            //     7    9
            // 5,6  7,8  9,10
            Entry<K, V> borrow = richNeighborNode.data.remove(0);
            leaf.data.add(borrow);
            int parentIndex = getExactIndex(borrow.getKey(), richNeighborNode.parent);
            Preconditions.checkArgument(parentIndex > -1);
            richNeighborNode.parent.data.remove(parentIndex);
            richNeighborNode.parent.data.add(parentIndex, richNeighborNode.data.get(0));
            if (parentIndex > 0 && removeIndexOnLeaf == 0) {
                //删除的是中间叶子结点的最小值
                //    8     10
                // 67  <8>,9  10,11,12
                //
                //    9    11
                // 67  9,10   11,12
                richNeighborNode.parent.data.remove(parentIndex - 1);
                richNeighborNode.parent.data.add(parentIndex - 1, leaf.data.get(0));
            }
        } else {
            throw new IllegalStateException();
        }
    }

    private void joinWithLeafNeighbor(Node<K, V> leaf) {
        if (leaf.prev != null) {
            leaf = leaf.prev;
        }
        if (leaf.next != null) {
            Entry<K, V> removeInUp = leaf.next.data.get(0);
            leaf.data.addAll(leaf.next.data);
            unLinkNext(leaf);
            int indexInParent = getExactIndex(removeInUp.getKey(), leaf.parent);
            Preconditions.checkArgument(indexInParent > -1);
            leaf.parent.data.remove(indexInParent);
            leaf.parent.children.remove(indexInParent + 1);
            Node<K, V> currentInnerNode = leaf.parent;
            //4 若内结点的key个数符合填充因子则结束 否则:
            removeOnInnerNode(currentInnerNode);
        }
    }

    private void unLinkNext(Node<K, V> left) {
        left.next = left.next.next;
        if (left.next != null) {
            left.next.prev = left;
        }
    }

    private void removeOnInnerNode(Node<K, V> currentInnerNode) {
        if (currentInnerNode == root) {
            if (root.data.size() == 0) {
                Node<K, V> leaf = root.children.get(0);
                root.children.clear();
                root = leaf;
                root.parent = null;
            }
            return;
        }
        //4 若内结点的key个数符合填充因子则结束 否则:
        if (tooLess(currentInnerNode)) {
            Node<K, V> richNeighborNode = findRichNeighborNode(currentInnerNode);
            if (richNeighborNode != null) {
                //5 若兄弟结点有富余 父结点key下移 兄弟结点key上移 结束
                borrowFromInnerNeighbor(currentInnerNode, richNeighborNode);
            } else {
                //6 否则 当前结点和兄弟结点 及 父结点下移key 合并为新结点 将当前结点指向父结点 重复4
                joinWithInnerNeighbor(currentInnerNode);
            }
        }
    }

    private void borrowFromInnerNeighbor(Node<K, V> currentInnerNode, Node<K, V> richNeighborNode) {
        if (richNeighborNode == currentInnerNode.prev) {
            //        16
            // 7,9,10    <20>
            //      10
            // 7,9     16,20
            Entry<K, V> up = currentInnerNode.prev.data.remove(currentInnerNode.parent.data.size() - 1);
            int indexInParent = getInsertIndex(currentInnerNode.parent, up);
            Entry<K, V> down = currentInnerNode.parent.data.remove(indexInParent);
            currentInnerNode.parent.data.add(indexInParent, up);
            currentInnerNode.data.add(0, down);
            Node<K, V> lastChildToRight =
                    currentInnerNode.prev.children.remove(currentInnerNode.prev.children.size() - 1);
            currentInnerNode.addChild(0, lastChildToRight);
        } else if (richNeighborNode == currentInnerNode.next) {
            //      16
            // <7>      18,20,22
            //
            //        18
            // 7,16         20,22
            Entry<K, V> up = currentInnerNode.next.data.remove(0);
            int indexInParent = getInsertIndex(currentInnerNode.parent, up) - 1;
            Entry<K, V> down = currentInnerNode.parent.data.remove(indexInParent);
            currentInnerNode.parent.data.add(indexInParent, up);
            currentInnerNode.data.add(down);
            Node<K, V> firstChildToLeft = currentInnerNode.next.children.remove(0);
            currentInnerNode.addChild(firstChildToLeft);
        } else {
            throw new IllegalStateException();
        }
    }

    private void joinWithInnerNeighbor(Node<K, V> currentInnerNode) {
        if (currentInnerNode.prev != null) {
            //    18
            // 9,16  <20>
            //
            // 9,16,18,20
            currentInnerNode = currentInnerNode.prev;
        }
        if (currentInnerNode.next != null) {
            //    16
            // <9> 18,20
            //
            // 9,16,18,20
            int indexInParent = getInsertIndex(currentInnerNode.parent, currentInnerNode.next.data.get(0)) - 1;
            Entry<K, V> down = currentInnerNode.parent.data.remove(indexInParent);
            currentInnerNode.parent.children.remove(indexInParent + 1);
            currentInnerNode.data.add(down);
            currentInnerNode.data.addAll(currentInnerNode.next.data);
            currentInnerNode.addChildren(currentInnerNode.next.children);
            currentInnerNode.next.data.clear();
            currentInnerNode.next.children.clear();
            unLinkNext(currentInnerNode);
            currentInnerNode = currentInnerNode.parent;
            removeOnInnerNode(currentInnerNode);
        }
    }

    final class EntrySet extends AbstractSet<Entry<K, V>> {
        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new Iterator<Map.Entry<K, V>>() {
                Node<K, V> currentNode = min;
                int index = 0;
                int expectMod = modCount;

                @Override
                public void remove() {
                    checkModCount(expectMod);
                    BplusTree.this.removeOnLeafNode(currentNode, index);
                    expectMod = modCount;
                }

                @Override
                public boolean hasNext() {
                    checkModCount(expectMod);
                    return currentNode != null && (index < currentNode.data.size() || currentNode.next != null);
                }

                @Override
                public Map.Entry<K, V> next() {
                    checkModCount(expectMod);
                    Entry<K, V> entry = currentNode.data.get(index++);
                    if (index >= currentNode.data.size()) {
                        index = 0;
                        currentNode = currentNode.next;
                    }
                    return entry;
                }

            };
        }

        @Override
        public int size() {
            return size;
        }
    }

    private void checkModCount(int expectModCount) {
        if (expectModCount != modCount) {
            throw new ConcurrentModificationException();
        }
    }

    private String innerToString() {
        if (root == null) {
            return "{}";
        }
        int expectModCount = this.modCount;
        StringBuilder sb = new StringBuilder("{");
        Node<K, V> currentLevel = root;
        Node<K, V> current;
        int level = 0;
        while (currentLevel != null) {
            current = currentLevel;
            sb.append(level++).append("=[");
            while (current != null) {
                checkModCount(expectModCount);
                sb.append('(').append(current.data).append(')');
                current = current.next;
                if (current != null) {
                    sb.append(',');
                }
            }
            sb.append("]");
            if (currentLevel.children != null) {
                sb.append(',');
                currentLevel = currentLevel.children.get(0);
                checkModCount(expectModCount);
            } else {
                currentLevel = null;
            }
        }
        sb.append('}');
        checkModCount(expectModCount);
        return sb.toString();
    }

    public int getMaxChildren() {
        return maxChildren;
    }

    public int getMinElementPerNode() {
        return minElementPerNode;
    }

}
