/*
 *  Copyright 2026 NostrGameEngine contributors.
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
package org.teavm.jso.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

@TeaVMTest
@SkipJVM
@OnlyPlatform({TestPlatform.JAVASCRIPT, TestPlatform.WEBASSEMBLY_GC})
@EachTestCompiledSeparately
public class StringArrayConversionTest {
    @Test
    public void preservesUnicodeAndNullElements() {
        String[] strings = { "", null, "ascii", "\u00e9\u4e2d", "\ud83d\ude80", "\ud800", "\udfff" };
        assertArrayEquals(strings, roundTrip(strings));
        assertArrayEquals(new String[0], roundTrip(new String[0]));
        assertNull(roundTrip(null));
    }

    @Test
    public void inboundCopyOwnsItsStorage() {
        JSObject nativeArray = createArray();
        String[] strings = readArray(nativeArray);
        strings[0] = "java";
        assertEquals("first", readElement(nativeArray, 0));
        writeElement(nativeArray, 1, "javascript");
        assertEquals("second", strings[1]);
    }

    @Test
    public void outboundCopyOwnsItsStorage() {
        Object storedArray = new String[] { "first", "second" };
        String[] strings = (String[]) storedArray;
        JSObject nativeArray = captureArray(strings);
        strings[0] = "java";
        assertEquals("first", readElement(nativeArray, 0));
        writeElement(nativeArray, 1, "javascript");
        assertEquals("second", strings[1]);
    }

    @Test
    public void rejectsNumberElements() {
        assertThrows(ClassCastException.class, () -> {
            assertEquals("invalid", readNonStringArray()[0]);
        });
    }

    @Test
    public void rejectsBoxedStringElements() {
        assertThrows(ClassCastException.class, () -> {
            assertEquals("invalid", readBoxedStringArray()[0]);
        });
    }

    @Test
    public void copiesLargeArray() {
        String[] strings = new String[4096];
        for (int i = 0; i < strings.length; ++i) {
            strings[i] = "value\u4e2d" + i;
        }
        assertArrayEquals(strings, roundTrip(strings));
    }

    @Test
    public void preservesNestedArrays() {
        String[][] strings = { { "first", "\ud83d\ude80" }, null, {}, { "last" } };
        String[][] copy = roundTripNested(strings);
        assertEquals(strings.length, copy.length);
        for (int i = 0; i < strings.length; ++i) {
            assertArrayEquals(strings[i], copy[i]);
        }
    }

    @JSBody(params = "array", script = "return array === null ? null : array.slice();")
    private static native String[] roundTrip(String[] array);

    @JSBody(params = "array", script = "return array.map(row => row === null ? null : row.slice());")
    private static native String[][] roundTripNested(String[][] array);

    @JSBody(script = "return ['first', 'second'];")
    private static native JSObject createArray();

    @JSBody(params = "array", script = "return array;")
    private static native String[] readArray(JSObject array);

    @JSBody(params = "array", script = "return array;")
    private static native JSObject captureArray(String[] array);

    @JSBody(params = { "array", "index" }, script = "return array[index];")
    private static native String readElement(JSObject array, int index);

    @JSBody(params = { "array", "index", "value" }, script = "array[index] = value;")
    private static native void writeElement(JSObject array, int index, String value);

    @JSBody(script = "return [42];")
    private static native String[] readNonStringArray();

    @JSBody(script = "return [new String('boxed')];")
    private static native String[] readBoxedStringArray();
}
