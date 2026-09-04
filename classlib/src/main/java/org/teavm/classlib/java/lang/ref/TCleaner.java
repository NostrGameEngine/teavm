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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Platforms;
import org.teavm.interop.UnsupportedOn;
import org.teavm.jso.core.JSFinalizationRegistry;
import org.teavm.jso.core.JSObjects;

@UnsupportedOn(Platforms.C)
public final class TCleaner {
    private static final JSFinalizationRegistry registry = createRegistry();

    private TCleaner() {
    }

    public static TCleaner create() {
        return new TCleaner();
    }

    public Cleanable register(Object obj, Runnable action) {
        if (PlatformDetector.isWebAssemblyGC()) {
            return WasmSupport.register(obj, action);
        }
        if (registry == null) {
            return new ManualCleanable(action);
        }
        var token = JSObjects.create();
        registry.register(obj, action, token);
        return () -> {
            if (registry.unregister(token)) {
                action.run();
            }
        };
    }

    private static JSFinalizationRegistry createRegistry() {
        if (PlatformDetector.isWebAssemblyGC() || !JSFinalizationRegistry.isSupported()) {
            return null;
        }
        return new JSFinalizationRegistry(TCleaner::runSafely);
    }

    private static void runSafely(Object held) {
        if (!(held instanceof Runnable)) {
            return;
        }
        try {
            ((Runnable) held).run();
        } catch (Throwable ignored) {
            // java.lang.ref.Cleaner specifies that cleaning exceptions are ignored.
        }
    }

    private static final class ManualCleanable implements Cleanable {
        private Runnable action;

        ManualCleanable(Runnable action) {
            this.action = action;
        }

        @Override
        public void clean() {
            var action = this.action;
            if (action == null) {
                return;
            }
            this.action = null;
            action.run();
        }
    }

    private static final class WasmSupport {
        private static final ReferenceQueue<Object> queue = new ReferenceQueue<>();
        private static WasmCleanable first;
        private static WasmCleanable last;
        private static boolean workerStarted;

        private WasmSupport() {
        }

        static Cleanable register(Object obj, Runnable action) {
            var cleanable = new WasmCleanable(obj, action);
            cleanable.previous = last;
            if (last != null) {
                last.next = cleanable;
            } else {
                first = cleanable;
            }
            last = cleanable;
            cleanable.linked = true;
            if (!workerStarted) {
                workerStarted = true;
                var worker = new Thread(WasmSupport::processQueue, "Cleaner");
                worker.setDaemon(true);
                worker.start();
            }
            return cleanable;
        }

        private static void processQueue() {
            while (true) {
                try {
                    var cleanable = (WasmCleanable) queue.remove();
                    try {
                        cleanable.clean();
                    } catch (Throwable ignored) {
                        // Automatic cleaning exceptions must not terminate the cleaner thread.
                    }
                } catch (InterruptedException ignored) {
                    // Cleaner threads are daemons and continue processing after interruption.
                }
            }
        }

        private static final class WasmCleanable extends WeakReference<Object> implements Cleanable {
            private Runnable action;
            private WasmCleanable previous;
            private WasmCleanable next;
            private boolean linked;

            WasmCleanable(Object obj, Runnable action) {
                super(obj, queue);
                this.action = action;
            }

            @Override
            public void clean() {
                var action = this.action;
                if (action == null) {
                    return;
                }
                this.action = null;
                if (linked) {
                    if (previous != null) {
                        previous.next = next;
                    } else {
                        first = next;
                    }
                    if (next != null) {
                        next.previous = previous;
                    } else {
                        last = previous;
                    }
                    previous = null;
                    next = null;
                    linked = false;
                }
                action.run();
            }
        }
    }

    public interface Cleanable {
        void clean();
    }
}
