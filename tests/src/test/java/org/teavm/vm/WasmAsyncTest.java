/*
 *  Copyright 2025 Alexey Andreev.
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
package org.teavm.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.interop.Intrinsified;
import org.teavm.interop.NativeAsync;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSClass;
import org.teavm.jso.JSModule;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.JSTopLevel;
import org.teavm.jso.browser.Window;
import org.teavm.jso.core.JSString;
import org.teavm.jso.function.JSConsumer;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.JsModuleTest;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.ServeJS;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;
import org.teavm.junit.TestPlatform;

@RunWith(TeaVMTestRunner.class)
@EachTestCompiledSeparately
@OnlyPlatform(TestPlatform.WEBASSEMBLY_GC)
@SkipJVM
public class WasmAsyncTest {
    @Test
    public void breakAsyncBlockFromNonAsync() {
        assertEquals(1001, generatedMethod(1));
        assertEquals(2011, generatedMethod(2));
        assertEquals(3111, generatedMethod(3));
        assertEquals(4111, generatedMethod(4));
    }

    @Test
    public void suspendingLoopAfterBranch() {
        assertEquals(100, loopAfterBranch(1));
        assertEquals(7, loopAfterBranch(0));
    }

    @Test
    public void suspendingLoopAfterThrow() {
        assertEquals(100, loopAfterThrow(1));
        try {
            loopAfterThrow(0);
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertEquals("generated", e.getMessage());
        }
    }
    
    @Test
    public void suspendWithValueTeedIntoWiderLocal() {
        // sum(20, 25) plus the length of "generated"
        assertEquals(54, teeThenSuspend());
    }

    @Test
    public void resumesAfterPassingTypedArrayToPromiseCallback() {
        assertEquals("4", typedArrayLength(new byte[] { 1, 2, 3, 4 }));
    }

    @Test
    public void resumesWithJavaScriptObjectPromiseResult() {
        assertEquals(4, decodedLength(new byte[] { 1, 2, 3, 4 }));
    }

    @Test
    @JsModuleTest
    @ServeJS(from = "org/teavm/vm/wasmAsyncModule.js", as = "wasmAsyncModule.js")
    public void resumesWithJavaScriptModulePromiseResult() {
        assertEquals(4, moduleDecodedLength(new byte[] { 1, 2, 3, 4 }));
    }

    @Test
    @JsModuleTest
    @ServeJS(from = "org/teavm/vm/wasmAsyncModule.js", as = "wasmAsyncModule.js")
    public void returnsJavaScriptObjectFromAsyncMethod() {
        assertEquals(4, moduleDecodedObject(new byte[] { 1, 2, 3, 4 }).getLength());
    }

    @Async
    private static native String typedArrayLength(byte[] data);

    private static void typedArrayLength(byte[] data, AsyncCallback<String> callback) {
        typedArrayLengthAsync(Int8Array.copyFromJavaArray(data), result -> callback.complete(result.stringValue()),
                error -> callback.error(new RuntimeException(error.stringValue())));
    }

    @JSBody(params = { "data", "resolve", "reject" },
            script = "Promise.resolve(String(data.length)).then(resolve, reject);")
    private static native void typedArrayLengthAsync(Int8Array data, JSConsumer<JSString> resolve,
            JSConsumer<JSString> reject);

    @Async
    private static native int decodedLength(byte[] data);

    private static void decodedLength(byte[] data, AsyncCallback<Integer> callback) {
        decodedLengthAsync(Int8Array.copyFromJavaArray(data), result -> callback.complete(result.getLength()),
                error -> callback.error(new RuntimeException(error.stringValue())));
    }

    @JSBody(params = { "data", "resolve", "reject" },
            script = "Promise.resolve({ length: data.length }).then(resolve, reject);")
    private static native void decodedLengthAsync(Int8Array data, JSConsumer<DecodedResult> resolve,
            JSConsumer<JSString> reject);

    interface DecodedResult extends JSObject {
        @JSProperty
        int getLength();
    }

    @Async
    private static native int moduleDecodedLength(byte[] data);

    private static void moduleDecodedLength(byte[] data, AsyncCallback<Integer> callback) {
        AsyncModule.decodedLengthAsync(data,
                result -> callback.complete(result.getLength()),
                error -> callback.error(new RuntimeException(error.stringValue())));
    }

    @Async
    private static native DecodedResult moduleDecodedObject(byte[] data);

    private static void moduleDecodedObject(byte[] data, AsyncCallback<DecodedResult> callback) {
        AsyncModule.decodedLengthAsync(data, callback::complete,
                error -> callback.error(new RuntimeException(error.stringValue())));
    }

    @JSClass
    static class AsyncModule implements JSObject {
        @JSTopLevel
        @JSModule("./wasmAsyncModule.js")
        static native void decodedLengthAsync(@JSByRef(optional = true) byte[] data,
                JSConsumer<DecodedResult> resolve,
                JSConsumer<JSString> reject);
    }

    @Async
    @NativeAsync
    @Intrinsified
    private static native int generatedMethod(int n);

    @Async
    @NativeAsync
    @Intrinsified
    private static native int loopAfterBranch(int n);

    @Async
    @NativeAsync
    @Intrinsified
    private static native int loopAfterThrow(int n);

    @Async
    @NativeAsync
    @Intrinsified
    private static native int teeThenSuspend();

    static RuntimeException newException() {
        return new RuntimeException("generated");
    }

    static int lengthOf(Throwable t) {
        return t.getMessage().length();
    }

    @Async
    private static native int sum(int a, int b);

    private static void sum(int a, int b, AsyncCallback<Integer> callback) {
        Window.setTimeout(() -> callback.complete(a + b), 0);
    }
}
