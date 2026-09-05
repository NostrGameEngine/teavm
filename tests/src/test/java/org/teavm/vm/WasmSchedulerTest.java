/*
 * Copyright 2026 TeaVM contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.teavm.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;
import org.teavm.junit.TestPlatform;

@RunWith(TeaVMTestRunner.class)
@EachTestCompiledSeparately
@OnlyPlatform(TestPlatform.WEBASSEMBLY_GC)
@SkipJVM
public class WasmSchedulerTest {
    @Test
    public void oneYieldAfterBudgetLetsAnotherThreadRun() throws InterruptedException {
        boolean[] ran = {false};
        Thread other = new Thread(() -> ran[0] = true);
        other.start();
        long start = System.nanoTime();
        while (System.nanoTime() - start < 8_000_000L) {
            // Simulate a bounded CPU chunk without introducing a suspension point.
        }
        Thread.yield();
        assertTrue("A single yield after the budget must be sufficient", ran[0]);
        other.join();
    }

    @Test
    public void readyThreadsRespectPriority() throws InterruptedException {
        List<Integer> order = new ArrayList<>();
        Thread low = new Thread(() -> order.add(1));
        Thread high = new Thread(() -> order.add(10));
        low.setPriority(Thread.MIN_PRIORITY);
        high.setPriority(Thread.MAX_PRIORITY);
        low.start();
        high.start();
        low.join();
        high.join();
        assertEquals(List.of(10, 1), order);
    }

    @Test
    public void interruptedSleepDoesNotResumeTwice() throws InterruptedException {
        int[] wakes = {0};
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException expected) {
                wakes[0]++;
            }
            wakes[0]++;
        });
        sleeper.start();
        Thread.sleep(5);
        sleeper.interrupt();
        sleeper.join();
        Thread.sleep(50);
        assertEquals(2, wakes[0]);
    }
}
