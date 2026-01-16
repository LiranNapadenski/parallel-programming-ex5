/**
 * Java test harness for throughput experiments on concurrent data structures.
 * Copyright (C) 2012 Trevor Brown
 * Contact (tabrown [at] cs [dot] toronto [dot edu]) with any questions or comments.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package main;

import main.*;
import algorithms.*;
import java.io.*;
import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    // some variables for the test harness
    protected final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    public static final int RAW_NUMBER_OF_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final int NUMBER_OF_PROCESSORS = RAW_NUMBER_OF_PROCESSORS == 8 ? 4 : RAW_NUMBER_OF_PROCESSORS; // override for hyperthreading on i7
    public static final boolean PRINT_FREEMEM = false; // note: just a (rather inaccurate) estimate
    private long startFreemem = 0;

    // variables for the experiment
    protected int nthreads;
    protected int ntrials;
    protected double nseconds;
    protected String filename;
    protected Ratio ratio;
    protected String alg;
    protected SwitchMap switches;
    protected boolean prefill;
    
    // some timing variables
    protected AtomicLong startUserTime = new AtomicLong(0);
    protected AtomicLong startWallTime = new AtomicLong(0);
    
    public Main(int nthreads, int ntrials, double nseconds, String filename,
            Ratio ratio, SwitchMap switches, boolean prefill) {
        this.nthreads = nthreads;
        this.ntrials = ntrials;
        this.nseconds = nseconds;
        this.filename = filename;
        this.ratio = ratio;
        this.alg = "BST";
        this.switches = switches;
        this.prefill = prefill;
    }
    
    public final class RandomGenerator {
        final Random rng;
        final int maxKey;
        final int id, numberOfIds;

        public RandomGenerator(final int id, final int numberOfIds, final Random rng, final int maxKey) {
            if (maxKey < 0) throw new RuntimeException("maxKey must be > 0");
            this.rng = rng;
            this.maxKey = maxKey;
            this.id = id;
            this.numberOfIds = numberOfIds;
        }

        public Integer next() {
            return rng.nextNatural(maxKey)+1;
        }
    }

    public abstract class Worker extends Thread {
        public abstract int getOpCount();
        public abstract int getTrueIns();
        public abstract int getFalseIns();
        public abstract int getTrueDel();
        public abstract int getFalseDel();
        public abstract int getTrueFind();
        public abstract int getFalseFind();
        public abstract long getEndTime();
        public abstract long getStartTime();
        public abstract long getMyStartCPUTime();
        public abstract long getMyStartUserTime();
        public abstract long getMyStartWallTime();
        public abstract long getUserTime();
        public abstract long getWallTime();
        public abstract long getCPUTime();
        public abstract long getKeysum();
    }
    
    public class TimedWorker extends Worker {
        public final long WORK_TIME;
        CyclicBarrier start;
        RandomGenerator gen;
        BSTInterface tree;
        int trueDel, falseDel, trueIns, falseIns, trueFind, falseFind;
        long keysum; // sum of new keys inserted by this thread minus keys deleted by this thread
        final Experiment ex;
        Random rng;

        private long id;
        private ThreadMXBean bean;
        
        public final AtomicLong sharedStartUserTime;
        public final AtomicLong sharedStartWallTime;
        public long myStartCPUTime;
        public long myStartUserTime;
        public long myStartWallTime;
        public long cpuTime;
        public long userTime;
        public long wallTime;
        public ArrayList<Worker> workers3; // ref to containing array [dirty technique :P...]

        public TimedWorker(final long WORK_TIME,
                           final RandomGenerator gen,
                           final Experiment ex,
                           final java.util.Random rng,
                           final BSTInterface tree,
                           final CyclicBarrier start,
                           final AtomicLong sharedStart,
                           final AtomicLong sharedStartWallTime,
                           final ArrayList<Worker> workers) {
            this.WORK_TIME = WORK_TIME;
            this.gen = gen;
            this.ex = ex;
            this.rng = new Random(rng.nextInt());
            this.tree = tree;
            this.start = start;
            this.sharedStartUserTime = sharedStart;
            this.workers3 = workers;
            this.sharedStartWallTime = sharedStartWallTime;
        }

        @Override
        @SuppressWarnings("empty-statement")
        public final void run() {
            bean = ManagementFactory.getThreadMXBean();
            if (!bean.isCurrentThreadCpuTimeSupported()) {
                System.out.println("THREAD CPU TIME UNSUPPORTED");
                System.exit(-1);
            }
            if (!bean.isThreadCpuTimeEnabled()) {
                System.out.println("THREAD CPU TIME DISABLED");
                System.exit(-1);
            }
            id = java.lang.Thread.currentThread().getId();
            
            // everyone waits on barrier
            if (start != null) try { start.await(); } catch (Exception e) { e.printStackTrace(); System.exit(-1); }
            
            // everyone waits until main thread sets experiment state to RUNNING
            while (ex.state == ExperimentState.PENDING);

            // start timing
            myStartUserTime = bean.getThreadUserTime(id);
            myStartCPUTime = bean.getThreadCpuTime(id);
            myStartWallTime = System.nanoTime();
            sharedStartUserTime.compareAndSet(0, myStartUserTime);
            sharedStartWallTime.compareAndSet(0, myStartWallTime);
            
            // perform operations while experiment's state is running
            while (ex.state == ExperimentState.RUNNING) {
                final int key = gen.next();
                final double op = rng.nextNatural() / (double) Integer.MAX_VALUE;
                if (op < ratio.ins) {
                    if (tree.insert(key)) {
                        keysum += key;
                        trueIns++;
                    } else falseIns++;                    
                } else if (op < ratio.ins + ratio.del) {
                    if (tree.remove(key)) {
                        keysum -= key;
                        trueDel++;
                    } else falseDel++;
                } else {
                    if (tree.contains(key)) trueFind++;
                    else falseFind++;
                }
            }
            
            // finish timing
            wallTime = System.nanoTime();
            userTime = bean.getThreadUserTime(id);
            cpuTime = bean.getThreadCpuTime(id);
        }

        public int getOpCount() { return 0; }
        public int getTrueIns() { return trueIns; }
        public int getFalseIns() { return falseIns; }
        public int getTrueDel() { return trueDel; }
        public int getFalseDel() { return falseDel; }
        public int getTrueFind() { return trueFind; }
        public int getFalseFind() { return falseFind; }
        public long getStartTime() { return myStartWallTime; }
        public long getEndTime() { return wallTime; }
        public long getMyStartCPUTime() { return myStartCPUTime; }
        public long getMyStartUserTime() { return myStartUserTime; }
        public long getMyStartWallTime() { return myStartWallTime; }
        public long getUserTime() { return userTime; }
        public long getWallTime() { return wallTime; }
        public long getCPUTime() { return wallTime; }
        public long getKeysum() { return keysum; }
    }
    
    final class BoolHolder { volatile boolean b; }
    
    final class FixedNumberOfOpsWorker extends Thread {
        final BSTInterface tree;
        final CyclicBarrier start, end;
        final int opsToPerform;
        final Random rng;
        final Ratio ratio;
        final int maxkey;
        final BoolHolder done;
        long keysum;
        
        public FixedNumberOfOpsWorker(
                final BSTInterface tree,
                final int opsToPerform,
                final Ratio ratio,
                final int maxkey,
                final Random rng,
                final CyclicBarrier start,
                final CyclicBarrier end,
                final BoolHolder done) {
            this.tree = tree;
            this.opsToPerform = opsToPerform;
            this.ratio = ratio;
            this.maxkey = maxkey;
            this.rng = rng;
            this.start = start;
            this.end = end;
            this.done = done;
        }
        
        @Override
        public void run() {
            try { start.await(); } catch (Exception ex) { ex.printStackTrace(); System.exit(-1); }
            
            for (int i=0; i < opsToPerform && !done.b; i++) {
                int key = rng.nextNatural(maxkey)+1;
                if (rng.nextNatural() < ratio.ins * Integer.MAX_VALUE) {
                    if (tree.insert(key)) keysum += key;
                } else {
                    if (tree.remove(key)) keysum -= key;
                }
            }
            done.b = true;
            try { end.await(); } catch (Exception ex) { ex.printStackTrace(); System.exit(-1); }
        }
        
        public long getKeysum() {
            return keysum;
        }
    }
    
    protected boolean runTrial(
            final PrintStream out,
            final boolean discardResults,
            final boolean shouldMeasureTrees,
            final String prefix,
            final SizeKeysumPair pair,
            final java.util.Random rng,
            final BSTInterface tree,
            final Experiment ex) {
        
        // prepare worker threads to run the trial
        startWallTime = new AtomicLong(0);
        startUserTime = new AtomicLong(0);
        CyclicBarrier start = new CyclicBarrier(ex.nprocs);
        ArrayList<RandomGenerator> arrays =  new ArrayList<RandomGenerator>(ex.nprocs); // generators supply keys for each thread
        ArrayList<Worker> workers = new ArrayList<Worker>(ex.nprocs);    // these are the threads that perform random operations
        for (int i=0;i<ex.nprocs;i++) {
            arrays.add(new RandomGenerator(i, ex.nprocs, new Random(rng.nextInt()), ex.maxkey));
            workers.add(new TimedWorker((long) (nseconds*1e9), arrays.get(i), ex, rng, tree, start, startUserTime, startWallTime, workers));
        }
        
        // perform garbage collection to clean up after the last trial, and record how much GC has happened so far
        System.gc();
        final long gcTimeStart = totalGarbageCollectionTimeMillis();
        
        // run the trial
        for (int i=0;i<ex.nprocs;i++) workers.get(i).start();
        ex.state = ExperimentState.RUNNING;
        long localStartTime = System.nanoTime();
        try {
            Thread.sleep((long)(nseconds * 1e3));
        } catch (InterruptedException ex1) {
            ex1.printStackTrace();
            System.exit(-1);
        }
        long localEndTime = System.nanoTime();
        ex.state = ExperimentState.STOPPED;

        // stop all threads and record how much GC has happened so far
        try { for (int i=0;i<ex.nprocs;i++) workers.get(i).join(); }
        catch (InterruptedException e) { e.printStackTrace(); System.exit(-1); }
        final long gcTimeEnd = totalGarbageCollectionTimeMillis();
        
        // compute key checksum for all threads (including from prefilling) and compare it with the key checksum for the data structure
        long threadsKeysum = pair.keysum;
        for (int i=0;i<ex.nprocs;++i) {
            threadsKeysum += workers.get(i).getKeysum();
        }
        long dsKeysum = tree.getKeysum();
        if (dsKeysum != threadsKeysum) {
            throw new RuntimeException("threadsKeysum=" + threadsKeysum + " does not match dsKeysum=" + dsKeysum);
        }
        // produce output
        if (!discardResults) {
            long endWallTime = Long.MAX_VALUE;
            for (Thread t : workers) {
                Worker w = (Worker) t;
                if (w.getEndTime() < endWallTime) endWallTime = w.getEndTime();
            }
            
            double elapsed = (localEndTime - localStartTime)/1e9;
            out.print(prefix + ",");
            long ntrueins = 0, nfalseins = 0, ntruedel = 0, nfalsedel = 0, ntruefind = 0, nfalsefind = 0;
            for (Worker w : workers) {
                ntrueins += w.getTrueIns();
                nfalseins += w.getFalseIns();
                ntruedel += w.getTrueDel();
                nfalsedel += w.getFalseDel();
                ntruefind += w.getTrueFind();
                nfalsefind += w.getFalseFind();
            }
            int nnodes = 0;
            double averageDepth = 0;
            long ntrue = ntrueins+ntruedel+ntruefind, nfalse = nfalseins+nfalsedel+nfalsefind;
            long nops = ntrue+nfalse;
            ex.throughput = (int)(nops/(double)elapsed);
            out.print(ex.nprocs + "," + nops + "," + ex.maxkey + ",");
            out.print(ex.ratio + ",");
            out.print(rng.nextInt() + "," + elapsed + ",");
            
            // merged-experiment
            //String mergedEx = ex.ratio.ins + "i-" + ex.ratio.del + "d-" + ex.maxkey;
            //out.print("," + mergedEx);
            
            // garbage collection time and desired total elapsed time
            final double gcElapsedTime = (gcTimeEnd-gcTimeStart)/1e3;
            out.print(gcElapsedTime + ",");
        
            out.print(ex.throughput);
    
            if (PRINT_FREEMEM) {
                System.gc();
                final long freemem = Runtime.getRuntime().freeMemory();
                out.print("," + freemem + "," + (startFreemem - freemem) + "," + (nnodes > 0 ? ((startFreemem - freemem)/nnodes) : 0));
            }
            
            out.println(); // finished line of output
        }
        return true;
    }
    
    private long totalGarbageCollectionTimeMillis() {
        final List<GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();
        long result = 0;
        for (GarbageCollectorMXBean gcbean : gcbeans) {
            result += gcbean.getCollectionTime();
        }
        return result;
    }   
    
    protected static final class Ratio {
        final double del, ins;
        public Ratio(final double ins, final double del) {
            if (ins < 0 || del < 0 || ins+del > 1) throw new RuntimeException("invalid ratio " + ins + "i-" + del + "d");
            this.del = del;
            this.ins = ins;
        }
        @Override
        public String toString() { return "" + (int)(100*ins) + "i-" + (int)(100*del) + "d"; }
    }
    
    protected enum ExperimentState { PENDING, RUNNING, STOPPED }

    public final class Experiment {
        volatile ExperimentState state = ExperimentState.PENDING;
        double totalThreadTime;
        final String alg;
        final int nprocs, maxkey;
        final Ratio ratio;
        int throughput; // exists to make access to this convenient so that we can decide whether we have finished warming up

        public Experiment(final String alg, final int nprocs, final int maxkey, final Ratio ratio) {
            this.alg = alg;
            this.nprocs = nprocs;
            this.maxkey = maxkey;
            this.ratio = ratio;
        }
        @Override
        public String toString() {
            return alg + "-" + nprocs + "thr-" + maxkey + "keys-" + ratio;
        }
    }

    public static class SwitchMap {
        private TreeMap<String, Double> backingMap;
        public SwitchMap() { backingMap = new TreeMap<String, Double>(); }
        public int size() { return backingMap.size(); }
        public void put(String key, Double val) { backingMap.put(key, val); }
        public double get(String key) {
            if (!backingMap.containsKey(key)) return 0;
            else return backingMap.get(key);
        }
        public String toString() {
            String s = "";
            boolean first = true;
            for (Entry<String, Double> e : backingMap.entrySet()) {
                s += (first ? "" : " ") + e.getKey() + "=" + e.getValue();
                first = false;
            }
            return s;
        }
    }

    protected class DualPrintStream {
        private PrintStream stdout, fileout;
        public DualPrintStream(String filename) throws IOException {
            if (filename != null) {
                fileout = new PrintStream(new FileOutputStream(filename));
            }
            stdout = System.out;
        }
        public void print(double x) {
            print(String.valueOf(x));
        }
        public void println(double x) {
            println(String.valueOf(x));
        }
        public void print(String x) {
            stdout.print(x);
            if (fileout != null) fileout.print(x);
        }
        public void println(String x) {
            print(x + "\n");
        }
    }

    public class SizeKeysumPair {
        public final long treeSize;
        public final long keysum;
        public SizeKeysumPair(long treeSize, long keysum) {
            this.treeSize = treeSize;
            this.keysum = keysum;
        }
    }
    
    public class Pair<A, B> {
        public final A first;
        public final B second;

        public Pair(A first, B second) {
            super();
            this.first = first;
            this.second = second;
        }
    }
    
    SizeKeysumPair fillToSteadyState(
            final java.util.Random rand,
            final BSTInterface tree,
            Ratio ratio,
            int maxkey,
            final boolean showProgress) {

        long keysum = 0;
        
        // deal with an all-search workload by prefilling 50% insert, 50% delete
        // and normalize other ratios to have 100% updates.
        if (Math.abs(ratio.ins + ratio.del) < 1e-8) ratio = new Ratio(0.5, 0.5);
        else ratio = new Ratio(ratio.ins / (ratio.ins+ratio.del), ratio.del / (ratio.ins+ratio.del));
        
        final int MAX_REPS = 200;
        final double THRESHOLD_PERCENT = 5; // must be within THRESHOLD_PERCENT percent of expected size to stop
        final int expectedSize = (int)(maxkey * (ratio.ins / (ratio.ins+ratio.del)) + 0.5);
        int treeSize = 0;
        int nreps = 0;
        long startFilling = System.nanoTime();

        int numThreads = 0;    // number of threads to use for prefilling phase
        int numOperations = 0; // number of operations to perform per thread in each iteration (up to MAX_REPS iterations)
        
        numThreads = Math.min(48, Runtime.getRuntime().availableProcessors() / 2);
        numOperations = 10 + maxkey/(2*numThreads);

        // we prefill over several iterations (up to MAX_REPS iterations).
        // in each iteration, we spawn nthreads threads, which each try to
        // perform nOperations random operations according to the ratio
        // passed to this method.
        // as soon as one of these threads finishes performings its
        // nOperations operations, all threads will stop and wait on
        // a barrier, and the main thread will measure the size of the tree.
        // if it is within THRESHOLD_PERCENT percent of the expected size
        // in the steady state for this ratio, then we are done.
        // otherwise, we continue prefilling the tree.
        while (Math.abs(toPercent((double)treeSize / expectedSize) - 100) > THRESHOLD_PERCENT) {
            if (nreps++ > MAX_REPS) {
                System.out.println("WARNING: COULD NOT REACH STEADY STATE AFTER " + nreps + " REPETITIONS.");
                System.out.println("         treesize=" + treeSize + " expected=" + expectedSize + " percentToExpected=" + toPercent((double)treeSize / expectedSize) + " %diff=" + Math.abs(toPercent((double)treeSize / expectedSize) - 100) + " THRESHOLD_PERCENT=" + THRESHOLD_PERCENT);
                System.exit(-1);
            }

            final CyclicBarrier start = new CyclicBarrier(numThreads);
            final CyclicBarrier end = new CyclicBarrier(numThreads+1);
            final FixedNumberOfOpsWorker[] workers = new FixedNumberOfOpsWorker[numThreads];
            final BoolHolder done = new BoolHolder();
            for (int i=0;i<numThreads;i++) {
                workers[i] = new FixedNumberOfOpsWorker(tree, numOperations, ratio, maxkey, new Random(rand.nextInt()), start, end, done);
            }
            for (int i=0;i<numThreads;i++) workers[i].start();
            try { end.await(); } catch (Exception ex) { ex.printStackTrace(); System.exit(-1); }
            treeSize = tree.size();
            for (int i=0;i<numThreads;i++) {
                keysum += workers[i].getKeysum();
            }
        }
        
        long endFilling = System.nanoTime();
        System.out.print("initnodes-" + treeSize + "-");
        System.out.print("in" + toPercent((endFilling-startFilling) / 1e9 / 100) + "s["+nreps+"reps]-");
        return new SizeKeysumPair(treeSize, keysum);
    }
    
    protected ArrayList<Experiment> getExperiments() {
        final ArrayList<Experiment> exp = new ArrayList<Experiment>();
        exp.add(new Experiment(alg, nthreads, (int) switches.get("keyRange"), ratio));
        return exp;
    }
    
    public void run(final PrintStream output) {
        // create output streams
        PrintStream out = output;
        if (out == null) {
            if (filename == null) {
                out = System.out;
            } else {
                try { out = new PrintStream(new File(filename)); }
                catch (Exception e) { e.printStackTrace(); System.exit(-1); }
            }
        }
        DualPrintStream stdout = null;
        try { stdout = new DualPrintStream(filename + "_stdout"); } catch (Exception e) { e.printStackTrace(); System.exit(-1); }
        
        // print header
        out.print("name"
                + ",trial"
                + ",nthreads"
                + ",threadops"
                + ",maxkey"
                + ",ratio"
                + ",seed"
                + ",time"
                + ",gcTime"
                + ",throughput"
                );
        out.println();
        
        // retrieve list of experiments to perform (this is a method because subclasses can implement it differently)
        ArrayList<Experiment> exp = getExperiments();

        // preview experiments, and determine now many runs there will be in total
        for (Experiment ex : exp) {
            System.out.println(ex);
        }
        System.out.println(exp.size() + " experiments in total");
        int numberOfRuns = exp.size() * ntrials;

        // start measuring time for the purpose of progress updates
        final long startTime = System.nanoTime();
        int nCompleted = 0;
        
        if (PRINT_FREEMEM) {
            System.gc();
            startFreemem = Runtime.getRuntime().freeMemory();
            System.out.println(" free memory: " + startFreemem);
        }        

        // perform the experiment
        java.util.Random rng = new java.util.Random((int) switches.get("seed"));
        for (Experiment ex : exp) {
            int experimentSeed = rng.nextInt();
            java.util.Random experimentRng = new java.util.Random(experimentSeed);

            for (int trial=0;trial<ntrials;++trial) {
                BSTInterface tree = new BST();
                SizeKeysumPair p = new SizeKeysumPair(0, 0);
                if (prefill) p = fillToSteadyState(experimentRng, tree, ex.ratio, ex.maxkey, false);
                if (!runTrial(out, false, trial+1 == ntrials, tree.getName() + "," + trial, p, experimentRng, tree, ex)) System.exit(-1);
                progress(stdout, tree, ++nCompleted, trial, tree.getName(), startTime, numberOfRuns, ex);
            }
        }
    }

    void progress(
            DualPrintStream stdout,
            final BSTInterface tree,
            int z,
            int i,
            String name,
            long startTime,
            int nRuns,
            Experiment ex) {
        
        double prog = ((int)(1000*(double) z / nRuns)) / 10.0;
        int elapsed = (int)((System.nanoTime()-startTime)/1e9+0.5);
        stdout.println(ex + " " + name + " trial " + i + " : " + prog + "% done, " + "elapsed " + elapsed + "s");
    }
    
    double toPercent(double x) { // keep only 1 decimal point
        return Math.abs((int)(x*1000)/10.0);
    }

    public static void invokeRun(String[] args, final PrintStream output) {
        if (args.length < 4) {
            System.out.println("Insufficient command-line arguments.");
            System.out.println("Must include: #THREADS #TRIALS SECONDS_PER_TRIAL");
            System.out.println("Can also include switches after mandatory arguments:");
            System.out.println("\t-s###     to set the random seed (32-bit signed int; default is " + Globals.DEFAULT_SEED + ")");
            System.out.println("\t-prefill  to prefill structures to steady state with random operations");
            System.out.println("\t-file-### to specify an output file to store results in");
            System.out.println("The following switches determine which operations are run (leftover % becomes search):");
            System.out.println("\t-ins%     to specify what % (0 to 100) of ops should be inserts");
            System.out.println("\t-del%     to specify what % (0 to 100) of ops should be deletes");
            System.out.println("\t-keysM    random keys will be uniformly from range [0,M) (default 1000000)");
            System.exit(-1);
        }
        int nthreads = 0;
        int ntrials = 0;
        double nseconds = 0;
        String filename = null;
        boolean prefill = false;

        SwitchMap switches = new SwitchMap();
        switches.put("seed", (double) Globals.DEFAULT_SEED);
        switches.put("keyRange", (double) Globals.DEFAULT_KEYRANGE);
        
        try {
            nthreads = Integer.parseInt(args[0]);
            ntrials = Integer.parseInt(args[1]);
            nseconds = Double.parseDouble(args[2]);
        } catch (Exception ex) {
            System.out.println("NUMBER_OF_THREADS, NUMBER_OF_TRIALS, SECONDS_PER_TRIAL must all be numeric");
            System.exit(-1);
        }
        if (nthreads < 0) {
            System.out.println("Number of threads n must satisfy 0 <= n");
            System.exit(-1);
        }
        if (ntrials <= 0) {
            System.out.println("Must run at least 1 trial (recommended to run several and discard the first few)");
            System.exit(-1);
        }
        if (nseconds <= 0) {
            System.out.println("Number of seconds per trial s must satisfy 0 < s (should be at least a second, really)");
            System.exit(-1);
        }

        int totalOpPercent = 0;

        for (int i=0;i<args.length;i++) {
            if (args[i].startsWith("-")) {
                if (args[i].matches("-seed[0-9]+")) {
                    try {
                        switches.put("seed", (double) Integer.parseInt(args[i].substring("-seed".length())));
                    } catch (Exception ex) {
                        System.out.println("Seed must be a 32-bit signed integer.");
                        System.exit(-1);
                    }
                } else if (args[i].matches("-ins[0-9]+(\\.[0-9]+){0,1}")) {
                    try {
                        switches.put("ratio-ins", Double.parseDouble(args[i].substring(4, args[i].length())));
                        totalOpPercent += switches.get("ratio-ins");
                        if (switches.get("ratio-ins") < 0) {
                            System.out.println("The insert percentage must be >= 0");
                            System.exit(-1);
                        }
                    } catch (Exception ex) {
                        System.out.println("The insert percentage must be a 32-bit integer.");
                        System.exit(-1);
                    }
                } else if (args[i].matches("-del[0-9]+(\\.[0-9]+){0,1}")) {
                    try {
                        switches.put("ratio-del", Double.parseDouble(args[i].substring(4, args[i].length())));
                        totalOpPercent += switches.get("ratio-del");
                        if (switches.get("ratio-del") < 0) {
                            System.out.println("The delete percentage must be >= 0");
                            System.exit(-1);
                        }
                    } catch (Exception ex) {
                        System.out.println("The delete percentage must be a 32-bit integer.");
                        System.exit(-1);
                    }
                } else if (args[i].matches("-keys[0-9]+")) {
                    try {
                        switches.put("keyRange", (double) Integer.parseInt(args[i].substring(5, args[i].length())));
                        if (switches.get("keyRange") < 1) {
                            System.out.println("The key range must be > 0");
                            System.exit(-1);
                        }
                    } catch (Exception ex) {
                        System.out.println("The key range must be a 32-bit integer.");
                        System.exit(-1);
                    }
                } else if (args[i].startsWith("-file-")) {
                    filename = args[i].substring("-file-".length());
                } else if (args[i].matches("-prefill")) {
                    prefill = true;
                } else {
                    System.out.println("Unrecognized command-line switch: \"" + args[i] + "\"");
                    System.exit(-1);
                }
            }
        }

        if (totalOpPercent > 100) {
            System.out.println("Total percentage over all operations cannot exceed 100");
            System.exit(-1);
        }

        (new Main(nthreads, ntrials, nseconds, filename,
                new Ratio(switches.get("ratio-ins") / 100., switches.get("ratio-del") / 100.),
                switches, prefill)).run(output);
    }

    public static void main(String[] args) throws Exception {
        invokeRun(args, null);
    }
}
