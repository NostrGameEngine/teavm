/*
 *  Copyright 2024 Alexey Andreev.
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
package org.teavm.jso.impl.wasmgc;

import org.teavm.interop.Import;
import org.teavm.interop.Intrinsified;
import org.teavm.jso.JSObject;

final class WasmGCJSRuntime {
    private WasmGCJSRuntime() {
    }

    private static boolean stringBuiltinsSupported = stringBuiltinsSupported();

    private static final int MAX_CACHED_UNITS = 262144;
    private static final int MAX_CACHED_STRINGS = 64;
    private static final int MIN_CACHED_UNITS = 4096;
    private static final CachedString[] cachedStrings = new CachedString[MAX_CACHED_STRINGS];

    private static final class CachedString {
        final String string;
        final JSObject value;

        CachedString(String string, JSObject value) {
            this.string = string;
            this.value = value;
        }
    }

    private static int firstCachedString;
    private static int cachedStringCount;
    private static int cachedUnits;

    static JSObject stringToJs(String str) {
        if (str == null) {
            return null;
        }
        var characters = CharArrayData.of(str);
        var length = str.length();
        if (length >= MIN_CACHED_UNITS && length <= MAX_CACHED_UNITS) {
            for (var i = 0; i < cachedStringCount; ++i) {
                var index = (firstCachedString + i) % MAX_CACHED_STRINGS;
                if (cachedStrings[index].string == str) {
                    return cachedStrings[index].value;
                }
            }
        }
        if (stringBuiltinsSupported) {
            return cacheExport(str, stringFromCharArray(characters, 0, length).toNullable());
        } else {
            if (str.isEmpty()) {
                return substring(stringFromCharCode(32).toNullable(), 0, 0).toNullable();
            }
            var result = stringFromCharCode(str.charAt(0)).toNullable();
            for (var i = 1; i < str.length(); ++i) {
                result = concatStrings(result, stringFromCharCode(str.charAt(i)).toNullable()).toNullable();
            }
            return cacheExport(str, result);
        }
    }

    static String jsToString(JSObject obj) {
        if (obj == null || isUndefined(obj)) {
            return null;
        }
        var length = stringLength(obj);
        var chars = CharArrayData.create(length);
        if (stringBuiltinsSupported) {
            stringIntoCharArray(obj, chars, 0);
        } else {
            for (var i = 0; i < length; i++) {
                var code = (char) charCodeAt(obj, i);
                chars.put(i, code);
            }
        }
        // Do not retain native inputs: even a small substring can keep a large host buffer alive.
        return chars.asString();
    }

    private static JSObject cacheExport(String string, JSObject value) {
        var length = string.length();
        if (length >= MIN_CACHED_UNITS && length <= MAX_CACHED_UNITS) {
            while (cachedStringCount == MAX_CACHED_STRINGS || cachedUnits + length > MAX_CACHED_UNITS) {
                cachedUnits -= cachedStrings[firstCachedString].string.length();
                cachedStrings[firstCachedString] = null;
                firstCachedString = (firstCachedString + 1) % MAX_CACHED_STRINGS;
                --cachedStringCount;
            }
            var index = (firstCachedString + cachedStringCount) % MAX_CACHED_STRINGS;
            cachedStrings[index] = new CachedString(string, value);
            cachedUnits += length;
            ++cachedStringCount;
        }
        return value;
    }

    @Import(name = "length", module = "wasm:js-string")
    static native int stringLength(JSObject str);

    @Import(name = "fromCharCodeArray", module = "wasm:js-string")
    static native NonNullExternal stringFromCharArray(CharArrayData data, int start, int end);

    @Import(name = "fromCharCode", module = "wasm:js-string")
    static native NonNullExternal stringFromCharCode(int charCode);

    @Import(name = "intoCharCodeArray", module = "wasm:js-string")
    static native int stringIntoCharArray(JSObject str, CharArrayData data, int start);

    @Import(name = "concat", module = "wasm:js-string")
    static native NonNullExternal concatStrings(JSObject first, JSObject second);

    @Import(name = "substring", module = "wasm:js-string")
    static native NonNullExternal substring(JSObject string, int start, int end);

    @Import(name = "charCodeAt", module = "wasm:js-string")
    static native int charCodeAt(JSObject string, int index);

    @Import(name = "stringBuiltinsSupported", module = "teavmJso")
    static native boolean stringBuiltinsSupported();

    @Import(name = "isUndefined", module = "teavmJso")
    static native boolean isUndefined(JSObject o);

    @Import(name = "emptyArray", module = "teavmJso")
    static native JSObject emptyArray();

    @Import(name = "appendToArray", module = "teavmJso")
    static native JSObject appendToArray(JSObject array, JSObject element);

    @Intrinsified
    static native JSObject wrapObject(Object obj);

    static Throwable wrapException(JSObject obj) {
        return new WasmGCExceptionWrapper(obj);
    }

    static JSObject extractException(Throwable e) {
        return e instanceof WasmGCExceptionWrapper ? ((WasmGCExceptionWrapper) e).jsException : null;
    }

    static final class CharArrayData {
        @Intrinsified
        static native CharArrayData of(String s);

        @Intrinsified
        native String asString();

        @Intrinsified
        static native CharArrayData create(int size);

        @Intrinsified
        native void put(int index, char code);
    }

    static final class NonNullExternal {
        @Intrinsified
        native JSObject toNullable();
    }
}
