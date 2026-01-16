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
                Node succPred = curr;
                Node succ = curr.right;

                // Acquire initial lock on the right child
                succ.lock.lock(); 
                try {
                    // 1. Hand-over-hand descent to the leftmost node
                    while (succ.left != null) {
                        Node nextSucc = succ.left;
                        nextSucc.lock.lock(); // Lock the child before releasing the parent
                        
                        // Release previous ancestor only if it's not the target node (curr)
                        if (succPred != curr) {
                            succPred.lock.unlock();
                        }
                        
                        succPred = succ;
                        succ = nextSucc;
                    }

                    // 2. THE CRITICAL VALIDATION STEP
                    // We must ensure 'succ' is still linked correctly to 'succPred' 
                    // and hasn't been logically deleted by another thread.
                    if (succ.marked || succPred.marked || 
                    (succPred == curr ? curr.right != succ : succPred.left != succ)) {
                        // If validation fails, something changed. Unlock and retry the whole removal.
                        if (succPred != curr) succPred.lock.unlock();
                        continue; 
                    }

                    // 3. LOGICAL DELETION (The Linearization Point)
                    // We mark the successor as the node being "removed" from the physical structure
                    succ.marked = true;

                    // 4. THE KEY SWAP
                    // Instead of moving the Node object, we move the data.
                    // This prevents concurrent 'contains' or 'find' threads from getting lost.
                    curr.key = succ.key;

                    // 5. PHYSICAL UNLINKING
                    // Connect the successor's right subtree to its parent.
                    if (succPred == curr) {
                        curr.right = succ.right;
                    } else {
                        succPred.left = succ.right;
                    }

                    return true; // Success!

                } finally {
                    // Cleanup locks
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