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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;
import org.teavm.junit.TestPlatform;

@RunWith(TeaVMTestRunner.class)
@SkipJVM
@OnlyPlatform({TestPlatform.JAVASCRIPT, TestPlatform.WEBASSEMBLY_GC})
@EachTestCompiledSeparately
public class StringInteropTest {
    @Test
    public void repeatedTransfersPreserveEveryUtf16CodeUnit() {
        char[] characters = new char[65536];
        for (int i = 0; i < characters.length; ++i) {
            characters[i] = (char) i;
        }
        String value = new String(characters);
        for (int i = 0; i < 3; ++i) {
            assertArrayEquals(characters, roundTrip(value).toCharArray());
        }
    }

    @Test
    public void handlesEmptyNullAndUndefined() {
        assertEquals("", roundTrip(""));
        assertNull(roundTrip(null));
        assertNull(undefined());
    }

    @Test
    public void importedStringsRemainIndependentOfTheirContainer() {
        JSObject holder = holder("first\u0000\ud800");
        String first = read(holder);
        write(holder, "second\udfff");
        String second = read(holder);
        assertEquals("first\u0000\ud800", roundTrip(first));
        assertEquals("second\udfff", roundTrip(second));
        assertEquals("first\u0000\ud800", roundTrip(first));
    }

    @Test
    public void cachedTransfersDoNotExposeConstructorStorage() {
        char[] characters = { 'a', '\ud800', 'z' };
        String value = new String(characters);
        assertEquals("a\ud800z", roundTrip(value));
        characters[0] = 'b';
        assertEquals("a\ud800z", roundTrip(value));
        String copy = new String(value);
        assertNotSame(value, copy);
        assertEquals(value, roundTrip(copy));
    }

    @Test
    public void builderAndSubstringTransfersRetainTheirOwnContents() {
        StringBuilder builder = new StringBuilder("first\u0000\ud800");
        String value = builder.toString();
        String slice = value.substring(1, 6);
        assertEquals("irst\u0000", roundTrip(slice));
        assertEquals("first\u0000\ud800", roundTrip(value));
        builder.setCharAt(0, 'x');
        builder.append("changed");
        assertEquals("first\u0000\ud800", roundTrip(value));
        assertEquals("irst\u0000", roundTrip(slice));
    }

    @Test
    public void largeImportedSnapshotsRemainIndependentAfterEviction() {
        String expected = "first\u0000\ud800".repeat(8192);
        JSObject container = holder(expected);
        String imported = read(container);
        write(container, "second\udfff".repeat(8192));
        assertEquals(expected, roundTrip(imported));
        String[] retained = new String[80];
        for (int i = 0; i < retained.length; ++i) {
            retained[i] = roundTrip("value-" + i + "\u0000\ud800" + "x".repeat(65536));
        }
        assertEquals(expected, roundTrip(imported));
        for (int i = 0; i < retained.length; ++i) {
            assertEquals("value-" + i + "\u0000\ud800" + "x".repeat(65536), roundTrip(retained[i]));
        }
    }

    @Test
    public void largeCachedStringsKeepConstructorAndBuilderOwnership() {
        char[] source = new char[8192];
        java.util.Arrays.fill(source, 'a');
        String owned = new String(source);
        String imported = roundTrip(owned);
        java.util.Arrays.fill(source, 'b');
        assertEquals(owned, roundTrip(imported));
        StringBuilder builder = new StringBuilder(imported);
        String snapshot = roundTrip(builder.toString());
        builder.setCharAt(0, 'c');
        assertEquals(owned, roundTrip(snapshot));
        String copy = new String(imported);
        assertNotSame(imported, copy);
        assertEquals(owned, roundTrip(copy));
    }

    @JSBody(params = "value", script = "return value;")
    private static native String roundTrip(String value);

    @JSBody(script = "return undefined;")
    private static native String undefined();

    @JSBody(params = "value", script = "return {value: value};")
    private static native JSObject holder(String value);

    @JSBody(params = "holder", script = "return holder.value;")
    private static native String read(JSObject holder);

    @JSBody(params = {"holder", "value"}, script = "holder.value = value;")
    private static native void write(JSObject holder, String value);
}
