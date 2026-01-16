package algorithms;

import java.util.concurrent.locks.ReentrantLock;
import main.BSTInterface;


public class BST implements BSTInterface {

    class Node{
        
        Node left;
        Node right;
        int key;
        volatile boolean marked;
        final ReentrantLock lock;

        public Node(int key){
            this.key = key;
            this.lock = new ReentrantLock();
        }

    }

    private final Node sentinel = new Node(Integer.MAX_VALUE);

    public BST() {
    }

    private Node[] find(int key) {
        Node pred = sentinel; // Start pred as the sentinel
        Node curr = sentinel.left; // The actual tree root is the left child of the sentinel
        
        while (curr != null) {
            if (key == curr.key) return new Node[]{curr, pred};
            pred = curr;
            if (key < curr.key) curr = curr.left;
            else curr = curr.right;
        }
        return new Node[]{null, pred}; // Key not found, but pred is the valid leaf parent
    }

    private boolean validate(Node pred, Node curr, int key) {
    if (pred.marked) return false;
    if (curr != null && curr.marked) return false;

    if (key < pred.key) {
        return pred.left == curr; 
    } else {
        return pred.right == curr;
    }
}

    public final boolean contains(final int key) {
        Node[] nodes = find(key);
        Node curr = nodes[0];
        return curr != null && curr.key == key && !curr.marked;
    }

    public final boolean insert(final int key) {
        while (true) {
            Node[] nodes = find(key);
            Node curr = nodes[0];
            Node pred = nodes[1];

            pred.lock.lock();
            try {
                if (!validate(pred, curr, key)) continue;

                if (curr != null) {
                    return false; 
                }
                Node newNode = new Node(key);
                if (key < pred.key) {
                    pred.left = newNode;
                } else {
                    pred.right = newNode;
                }
                return true; // Successfully inserted
                    
            } finally {
                pred.lock.unlock();
            }
        }
    }

    public final boolean remove(final int key) {
        retry:
        while (true) {
            Node[] nodes = find(key);
            Node curr = nodes[0];
            Node pred = nodes[1];

            if (curr == null) return false;

            pred.lock.lock();
            curr.lock.lock();
            try {
                if (!validate(pred, curr, key)) continue;
                if (curr.marked) return false;

                // LINEARIZATION POINT
                curr.marked = true;

                // Case 0 or 1 child
                if (curr.left == null || curr.right == null) {
                    Node child = (curr.left != null) ? curr.left : curr.right;
                    if (key < pred.key) pred.left = child;
                    else pred.right = child;
                    return true;
                }

                // Case 2 children
                Node succPred = curr;
                Node succ = curr.right;

                while (succ.left != null) {
                    succPred = succ;
                    succ = succ.left;
                }

                if (succPred != curr) succPred.lock.lock();
                succ.lock.lock();

                try {
                    // Validate successor chain
                    if (succPred.marked || succ.marked) return true;
                    if (succPred.left != succ && succPred.right != succ) return true;

                    // Unlink successor from old location
                    Node succChild = succ.right;
                    if (succPred.left == succ) succPred.left = succChild;
                    else succPred.right = succChild;

                    // Move successor into curr's position
                    succ.left = curr.left;
                    succ.right = curr.right;
                    succ.marked = false;

                    if (key < pred.key) pred.left = succ;
                    else pred.right = succ;

                    return true;

                } finally {
                    succ.lock.unlock();
                    if (succPred != curr) succPred.lock.unlock();
                }

            } finally {
                curr.lock.unlock();
                pred.lock.unlock();
            }
        }
    }


    // Return your ID #
    public String getName() {
        return "328456645";
    }

    // Returns size of the tree.
    public final int size() {
    // NOTE: Guaranteed to be called without concurrent operations,
	// so need to be thread-safe.  The method will only be called
	// once the benchmark completes.
        return countNodes(sentinel) - 1;
    }

    private int countNodes(Node node) {
        if (node == null) return 0;
        
        // Although successful remove() physically unlinks nodes,
        // we check !node.marked to ensure we only count active set members.
        return (node.marked ? 0 : 1)
         + countNodes(node.left)
         + countNodes(node.right);
    }

    // Returns the sum of keys in the tree
    public final long getKeysum() {
    // NOTE: Guaranteed to be called without concurrent operations,
	// so no need to be thread-safe.
	//
	// Make sure to sum over a "long" variable or you will get incorrect
	// results due to integer overflow!
        return sumKeys(sentinel.left);
    }

    private long sumKeys(Node node) {
        if (node == null) return 0L;
        
        long sum = node.marked ? 0L : (long) node.key;
        
        return sum + sumKeys(node.left) + sumKeys(node.right);
    }
}