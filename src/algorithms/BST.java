package algorithms;

import java.util.concurrent.locks.ReentrantLock;
import main.BSTInterface;


public class BST implements BSTInterface {

    class Node{
        
        volatile Node left;
        volatile Node right;
        volatile int key;
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

    @Override
    public final boolean contains(final int key) {
        Node[] nodes = find(key);
        Node curr = nodes[0];
        return curr != null && curr.key == key && !curr.marked;
    }

    @Override
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

    @Override
    public final boolean remove(final int key) {
        while (true) {
            Node[] nodes = find(key);
            Node curr = nodes[0];
            Node pred = nodes[1];
            
            if (curr == null) return false; // Key not found
            
            // Lock parent → current
            pred.lock.lock();
            curr.lock.lock();
            
            try {
                // Validate the path is still valid
                if (!validate(pred, curr, key)) {
                    continue; // Retry if path changed
                }
                
                // Mark node as logically deleted first
                curr.marked = true; // LINEARIZATION POINT
                
                // -------- Case 0 or 1 child --------
                if (curr.left == null || curr.right == null) {
                    Node child = (curr.left != null) ? curr.left : curr.right;
                    
                    // Physical removal
                    if (key < pred.key) {
                        pred.left = child;
                    } else {
                        pred.right = child;
                    }
                    
                    return true; 
                }
                
                // -------- Case 2 children --------
                // Find successor (leftmost node in right subtree)
                Node succPred = curr;
                Node succ = curr.right;
                
                while (succ.left != null) {
                    succPred = succ;
                    succ = succ.left;
                }
                
                // Lock successor path (prevent deadlock with consistent ordering)
                if (succPred != curr) {
                    succPred.lock.lock();
                }
                succ.lock.lock();
                
                try {
                    // Validate successor is still in correct position
                    if ((succPred == curr && curr.right != succ) || 
                        (succPred != curr && succPred.left != succ)) {
                        continue; // Retry if successor moved
                    }
                    
                    // Store current's children before modification
                    Node currLeft = curr.left;
                    Node currRight = curr.right;
                    
                    // 1. Remove successor from its current position
                    Node succRightChild = succ.right;
                    if (succPred == curr) {
                        // Successor is direct right child
                        curr.right = succRightChild;
                    } else {
                        // Successor is deeper in left subtree of right child
                        succPred.left = succRightChild;
                    }
                    
                    // 2. Replace curr with successor
                    succ.left = currLeft;
                    succ.right = (succPred == curr) ? succRightChild : currRight;
                    
                    // 3. Update parent pointer to successor
                    if (key < pred.key) {
                        pred.left = succ;
                    } else {
                        pred.right = succ;
                    }
                    
                    // Clear successor's marked flag (it's now the replacement)
                    succ.marked = false;
                    
                    return true; // LINEARIZATION POINT
                    
                } finally {
                    succ.lock.unlock();
                    if (succPred != curr) {
                        succPred.lock.unlock();
                    }
                }
                
            } finally {
                curr.lock.unlock();
                pred.lock.unlock();
            }
        }
    }





    // Return your ID #
    @Override
    public String getName() {
        return "328456645";
    }

    // Returns size of the tree.
    @Override
    public final int size() {
    // NOTE: Guaranteed to be called without concurrent operations,
	// so need to be thread-safe.  The method will only be called
	// once the benchmark completes.
        return countNodes(sentinel.left);
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
    @Override
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