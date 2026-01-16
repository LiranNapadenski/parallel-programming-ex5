================
BST Test Harness
================

.. sectnum::


Introduction
============

This package contains a skeleton Java implementation of a concurrent binary
search tree (BST) and a Java test harness for exercising the implementation.

The package is customized for the TAU CS lab.  There are shell scripts to
compile and run experiments on the lab's machines.


Implementing the BST
====================

Please fill in the skeleton in ``src/algorithms/BST.java``.


Test harness
============

The test harness runs a random workload.  Each thread repeatedly performs
a random operation (one of ``insert``, ``remove`` and ``contains``) on
a uniformly random key from some key range.  The user specifies the
distribution from which operations are sampled (e.g., 30% ``insert``,
30% ``remove`` and 40% ``contains``) and the size of the key range.

By default, the workload runs on an empty tree.  The user can specify to
*prefill* the tree.  In this case, the harness fills the tree (using
concurrent threads) until its size reaches ``c*M``, where ``M`` is the
size of the key range and ``c`` is the ratio of inserts out of all updates.
For example, with 30% inserts and 30% removes, ``c`` will be 0.5 (=30/60).
With 10% inserts and 90% removes, ``c`` will be 0.1 (=10/100).

Note that if the frequency of ``insert`` and ``remove`` operations isn't
equal, the tree will converge to full (all keys in the range are in the
tree) or empty.

*Tip:* If you want to test your algorithm for correctness as a sequential
algorithm (before adding synchronization), you can disable the multi-threaded
prefilling by changing line 501 in ``Main.java`` from::

    numThreads = Math.min(48, Runtime.getRuntime().availableProcessors() / 2);

to::

    numThreads = 1;

Just don't forget to change it back afterwards, once you add synchronization.


Validation
==========

The test harness performs light-weight *validation* of the BST algorithm.
It maintains a sum of all inserted and removed elements, and once the
workload completes, it checks that the sum matches the contents of the 
tree.  When the sums don't match, this indicates a linearizability violation
(bug) in the BST and the test throws an exception::

    Exception in thread "main" java.lang.RuntimeException: threadsKeysum=X does not match dsKeysum=Y

This check is sound but not complete: if it passes, it doesn't mean that
the algorithm is bug-free, but if it fails, there's certainly a problem.

A good way to exercise your algorithm is to use workloads with many threads,
small key ranges (so small trees) and a high probability of updates
(e.g., 50%/50%).  But don't restrict yourself to such cases, try other
ones as well.


Compiling and running
=====================

To make the scripts executable, execute::

    chmod 755 compile run

To compile, execute::

    ./compile

.. note::  The package will not compile as-is, because the BST part is
           not implemented.

The ``run`` script invokes the JVM with some recommended parameters.
You need to supply the test harness parameters yourself.  To run it,
execute::

    ./run args...

Running with no arguments will print a help message.

By default, the harness prints both progress information and performance
data to stdout.  To get readable output, it is highly recommended to
dump the performance data to a file with the ``-file`` argument.

Example
-------

The following runs 5 trials, each for 5 seconds, with 8 threads.  In
each trial, we use a 50/50 ``insert``/``remove`` workload on a key range
of size 1 MiB, with the tree initially prefilled to contain 0.5 MiB
keys::

    ./run 8 5 5 -ins50 -del50 -keys1048576 -prefill -file-data-temp.csv

The output looks like this::

    BST-8thr-1048576keys-50i-50d
    1 experiments in total
    initnodes-499295-in2.6s[30reps]-BST-8thr-1048576keys-50i-50d XXXXXXXXXX trial 0 : 20.0% done, elapsed 8s
    initnodes-499350-in3.4s[35reps]-BST-8thr-1048576keys-50i-50d XXXXXXXXXX trial 1 : 40.0% done, elapsed 19s
    initnodes-501110-in4.1s[30reps]-BST-8thr-1048576keys-50i-50d XXXXXXXXXX trial 2 : 60.0% done, elapsed 30s
    initnodes-500176-in4.9s[35reps]-BST-8thr-1048576keys-50i-50d XXXXXXXXXX trial 3 : 80.0% done, elapsed 42s
    initnodes-501292-in3.5s[28reps]-BST-8thr-1048576keys-50i-50d XXXXXXXXXX trial 4 : 100.0% done, elapsed 52s

And the file ``data-temp.csv`` contains data about the runs::

    name,trial,nthreads,threadops,maxkey,ratio,seed,time,gcTime,throughput
    XXXXXXXXXX,0,8,4751801,1048576,50i-50d,1597992128,5.000148087,0.0,950332
    XXXXXXXXXX,1,8,6133494,1048576,50i-50d,-1631871343,5.000108978,0.0,1226672
    XXXXXXXXXX,2,8,6817725,1048576,50i-50d,-187834541,5.00008258,0.0,1363522
    XXXXXXXXXX,3,8,6698564,1048576,50i-50d,-1007151554,5.000519142,0.0,1339573
    XXXXXXXXXX,4,8,7211827,1048576,50i-50d,-632536764,5.00090584,0.0,1442104


Credit
======

This package is a *very* trimmed down version of `Trevor Brown's Java harness
<https://bitbucket.org/trbot86/implementations/>`.

