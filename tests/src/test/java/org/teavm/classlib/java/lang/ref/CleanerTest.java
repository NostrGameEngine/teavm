/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.java.lang.ref;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.classlib.support.GCSupport;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTestRunner;
import org.teavm.junit.TestPlatform;

@RunWith(TeaVMTestRunner.class)
@EachTestCompiledSeparately
@SkipPlatform(TestPlatform.C)
public class CleanerTest {
    @Test
    public void cleanerCreated() {
        var cleaner = Cleaner.create();
        assertTrue(cleaner != null);
    }

    @Test
    public void manualClean() {
        var cleaner = Cleaner.create();
        var counter = new int[1];
        var obj = new Object();
        var cleanable = cleaner.register(obj, () -> counter[0]++);

        cleanable.clean();
        assertEquals(1, counter[0]);

        // Second call to clean() must be a no-op
        cleanable.clean();
        assertEquals(1, counter[0]);
    }

    @Test
    public void manualCleaningExceptionIsPropagated() {
        var cleaner = Cleaner.create();
        var cleanable = cleaner.register(new Object(), () -> {
            throw new IllegalStateException("expected test failure");
        });

        try {
            cleanable.clean();
            throw new AssertionError("Cleaning exception was not propagated");
        } catch (IllegalStateException expected) {
            // Expected: explicit cleanup executes on the caller's thread.
        }
        cleanable.clean();
    }

    @Test
    public void manualCleanDoesNotRetainActionAfterExecution() {
        var cleaner = Cleaner.create();
        var counter = new int[1];
        var cleanable = cleaner.register(new Object(), () -> counter[0]++);

        cleanable.clean();
        cleanable.clean();

        assertEquals(1, counter[0]);
    }

    @Test
    @SkipPlatform(TestPlatform.JAVASCRIPT)
    public void gcTriggersClean() throws InterruptedException {
        var cleaner = Cleaner.create();
        var counter = new int[1];
        var reference = registerForCleanup(cleaner, counter);
        GCSupport.tryToTriggerGC(reference);
        assertNull(reference.get());
        for (var i = 0; i < 20 && counter[0] == 0; ++i) {
            Thread.sleep(50);
        }
        assertEquals(1, counter[0]);
    }

    private WeakReference<Object> registerForCleanup(Cleaner cleaner, int[] counter) {
        var obj = new Object();
        var reference = new WeakReference<>(obj);
        cleaner.register(obj, () -> counter[0]++);
        return reference;
    }
}
