/*
 *  Copyright 2013 Alexey Andreev.
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
package org.teavm.classlib.java.lang;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

@TeaVMTest
@EachTestCompiledSeparately
public class StringTest {
    @Test(expected = NullPointerException.class)
    public void nullStringCopyThrowsJavaException() {
        new String((String) null);
    }

    @Test
    public void hashCodeMatchesHotSpotUtf16Fixtures() {
        char[] characters = new char[65536];
        for (int i = 0; i < characters.length; ++i) {
            characters[i] = (char) i;
        }
        String[] values = { "abc", "Aa", "BB", "polygenelubricants", "\ud800", "\ud800\udfff",
                new String(characters), "nostr4j\u0000\ud800\udfff".repeat(1024) };
        int[] hashes = { 96354, 2112, 2112, -2147483648, 55296, 1771519, 2130739200, -1338940416 };
        for (int i = 0; i < values.length; ++i) {
            assertEquals(hashes[i], values[i].hashCode());
            assertEquals(hashes[i], new String(values[i]).hashCode());
            assertEquals(hashes[i], values[i].hashCode());
        }
    }

    @Test
    public void zeroHashesAndIndependentCopiesRemainCorrect() {
        String value = "\u0000".repeat(8192);
        assertEquals(0, value.hashCode());
        assertEquals(0, value.hashCode());
        assertEquals(0, new String(value).hashCode());
        String original = "polygenelubricants";
        original.hashCode();
        String copy = new String(original);
        org.junit.Assert.assertNotSame(original, copy);
        assertEquals(-2147483648, copy.hashCode());
    }

    @Test
    public void substringSearchPreservesBoundsAndUtf16() {
        String value = "a\u0000\ud800b\ud83d\ude00a\u0000\ud800b";
        assertEquals(0, value.indexOf("", -1));
        assertEquals(value.length(), value.indexOf("", Integer.MAX_VALUE));
        assertEquals(-1, value.lastIndexOf("", -1));
        assertEquals(value.length(), value.lastIndexOf("", Integer.MAX_VALUE));
        assertEquals(1, value.indexOf("\u0000\ud800b", Integer.MIN_VALUE));
        assertEquals(7, value.indexOf("\u0000\ud800b", 2));
        assertEquals(-1, value.indexOf("\u0000\ud800b", Integer.MAX_VALUE));
        assertEquals(7, value.lastIndexOf("\u0000\ud800b", Integer.MAX_VALUE));
        assertEquals(1, value.lastIndexOf("\u0000\ud800b", 6));
        assertEquals(4, value.indexOf("\ud83d\ude00"));
        assertEquals(5, value.lastIndexOf("\ude00", 5));
        assertEquals(-1, value.indexOf(value + "x"));
        assertEquals(-1, value.lastIndexOf(value + "x"));
        assertEquals(0, "".indexOf("", Integer.MAX_VALUE));
        assertEquals(0, "".lastIndexOf("", Integer.MAX_VALUE));
        try {
            value.indexOf(null, -1);
            throw new AssertionError("Null search succeeded");
        } catch (NullPointerException expected) {
            assertEquals(-1, value.indexOf("missing"));
        }
        try {
            value.lastIndexOf(null, -1);
            throw new AssertionError("Null search succeeded");
        } catch (NullPointerException expected) {
            assertEquals(-1, value.lastIndexOf("missing"));
        }
    }

    @Test
    public void stringPoolPreservesWtf8BoundariesAndSurrogates() {
        String value = "\0\u007f\u0080\u07ff\u0800\ud800\udc00\udfff\uffff";
        char[] expected = { 0, 0x7f, 0x80, 0x7ff, 0x800, 0xd800, 0xdc00, 0xdfff, 0xffff };
        assertArrayEquals(expected, value.toCharArray());
        assertEquals(new String(expected), value);
        assertSame(value, new String(expected).intern());
    }

    @Test
    public void stringPoolRetainsAdjacentEmptyAndMixedConstants() {
        String[] values = { "", "ASCII", "\u0800middle\u0080", "\ud83d\ude00", "\ud800x\udfff" };
        char[][] expected = { {}, { 'A', 'S', 'C', 'I', 'I' },
                { 0x800, 'm', 'i', 'd', 'd', 'l', 'e', 0x80 }, { 0xd83d, 0xde00 }, { 0xd800, 'x', 0xdfff } };
        for (int i = 0; i < values.length; i++) {
            assertArrayEquals(expected[i], values[i].toCharArray());
            assertSame(values[i], new String(expected[i]).intern());
        }
    }

    @Test
    public void stringPoolPreservesControlCharactersNextToDigits() {
        String value = "\u007f7\u00808\u00859\u009f0\u00a0f";
        char[] expected = { 0x7f, '7', 0x80, '8', 0x85, '9', 0x9f, '0', 0xa0, 'f' };
        assertArrayEquals(expected, value.toCharArray());
        assertSame(value, new String(expected).intern());
    }

    @Test
    public void comparisonReturnsExactUtf16Difference() {
        for (int length : new int[] { 1, 16, 64, 257, 1024 }) {
            char[] leftChars = new char[length];
            for (int i = 0; i < length; i++) {
                leftChars[i] = (char) (i * 8191);
            }
            String left = new String(leftChars);
            assertEquals(0, left.compareTo(new String(leftChars)));
            for (int i = 0; i < length; i++) {
                char original = leftChars[i];
                leftChars[i] ^= 0xffff;
                String right = new String(leftChars);
                int difference = original - leftChars[i];
                assertEquals(difference, left.compareTo(right));
                assertEquals(-difference, right.compareTo(left));
                leftChars[i] = original;
            }
            assertEquals(-3, left.compareTo(left + "xyz"));
            assertEquals(3, (left + "xyz").compareTo(left));
        }
    }

    @Test
    public void comparisonHandlesEmptySlicesAndNull() {
        assertEquals(0, "".compareTo(new String(new char[0])));
        assertEquals(-2, "".compareTo("ab"));
        assertEquals(2, "ab".compareTo(""));
        String slice = new String(new char[] { 'x', 0, 0xd800, 0xdc00, 0xffff, 'y' }, 1, 4);
        assertEquals(0, slice.compareTo(new String(slice.toCharArray())));
        assertEquals(0, slice.compareTo(("x" + slice + "y").substring(1, 5)));
        try {
            slice.compareTo(null);
            throw new AssertionError("Null comparison succeeded");
        } catch (NullPointerException expected) {
            // The public method must retain Java's null check.
        }
    }

    @Test
    public void equalityHandlesEmptyNullAndOtherTypes() {
        String empty = new String(new char[0]);
        assertTrue(empty.equals(""));
        assertTrue(empty.equals(empty));
        assertFalse(empty.equals(null));
        assertFalse(empty.equals(new StringBuilder()));
        assertFalse(empty.equals("a"));
        assertFalse("a".equals(empty));
        assertFalse("a".equals("ab"));
        assertFalse("ab".equals("a"));
    }

    @Test
    public void equalityComparesAllUtf16CodeUnits() {
        int[] lengths = { 1, 2, 15, 16, 63, 64, 65, 1024 };
        for (int length : lengths) {
            char[] characters = new char[length];
            for (int i = 0; i < length; i++) {
                characters[i] = (char) (i * 8191);
            }
            String left = new String(characters);
            String right = new String(characters);
            assertTrue(left.equals(right));
            for (int i = 0; i < length; i++) {
                characters[i] ^= 1;
                String different = new String(characters);
                assertFalse(left.equals(different));
                assertFalse(different.equals(right));
                characters[i] ^= 1;
            }
            assertTrue(left.equals(right));
        }
        String surrogate = new String(new char[] { 0, 0xd800, 0xdc00, 0xdfff, 0xffff });
        assertTrue(surrogate.equals(new String(surrogate.toCharArray())));
        assertFalse(surrogate.equals(new String(new char[] { 0, 0xd800, 0xdc00, 0xdffe, 0xffff })));
    }

    @Test
    public void equalityRespectsSlicesAndCopiedStorage() {
        char[] characters = { 'x', 'a', 0xd800, 0, 'b', 'y' };
        String sliced = new String(characters, 1, 4);
        String copied = new String(sliced.toCharArray());
        String substring = new String(characters).substring(1, 5);
        assertTrue(sliced.equals(copied));
        assertTrue(sliced.equals(substring));
        characters[1] = 'z';
        assertTrue(sliced.equals(copied));
        assertFalse(sliced.equals(new String(characters, 1, 4)));
    }

    @Test
    public void charsExtracted() {
        String str = "123";
        assertEquals('1', str.charAt(0));
        assertEquals('2', str.charAt(1));
        assertEquals('3', str.charAt(2));
    }

    @Test
    public void lengthComputed() {
        String str = "123";
        assertEquals(3, str.length());
    }

    @Test
    public void stringCreatedFromChars() {
        String str = new String(new char[] { '1', '2', '3' });
        assertEquals("123", str);
    }

    @Test
    public void stringsAreEqual() {
        String a = new String(new char[] { '1', '2', '3' });
        String b = new String(new char[] { '1', '2', '3' });
        assertEquals(a, b);
    }

    @Test
    public void stringsAreNotEqual() {
        String a = new String(new char[] { '1', '2', '3' });
        String b = new String(new char[] { '1', '4', '3' });
        assertNotEquals(a, b);
    }

    @Test
    public void stringCharactersRead() {
        char[] buffer = new char[4];
        "123".getChars(0, 3, buffer, 0);
        assertArrayEquals(new char[] { '1', '2', '3', '\0' }, buffer);
    }

    @Test
    public void stringIsEqualToBuilder() {
        assertTrue("123".contentEquals(new StringBuilder().append('1').append('2').append('3')));
    }

    @Test
    public void comparesSameStrings() {
        String a = "123";
        String b = new String(a);
        assertTrue(a.compareTo(b) == 0);
    }

    @Test
    public void comparesToPrecedingStrings() {
        assertTrue("abc".compareTo("abbc") > 0);
    }

    @Test
    public void comparesToSuccessorStrings() {
        assertTrue("abc".compareTo("abdc") < 0);
    }

    @Test
    public void startsWithWorks() {
        assertTrue("123".startsWith("12"));
    }

    @Test
    public void regionsMatched() {
        assertTrue("12345".regionMatches(2, "23456", 1, 2));
    }

    @Test
    public void endsWithWorks() {
        assertTrue("12345".endsWith("45"));
    }

    @Test
    public void exposesSupplementaryCodePoint() {
        String str = new String(new char[] { (char) 56178, (char) 56972 });
        assertEquals(969356, str.codePointAt(0));
        assertEquals(56972, str.codePointAt(1));
    }

    @Test
    public void exposesWrongSurrogates() {
        String str = new String(new char[] { (char) 56972, (char) 56178 });
        assertEquals(56972, str.codePointAt(0));
        assertEquals(56178, str.codePointAt(1));
    }

    @Test
    public void exposesSupplementaryCodePointBefore() {
        String str = new String(new char[] { (char) 56178, (char) 56972 });
        assertEquals(969356, str.codePointBefore(2));
        assertEquals(56178, str.codePointBefore(1));
    }

    @Test
    public void countsCodePoints() {
        String str = new String(new char[] { (char) 56178, (char) 56972, 'a', 'b' });
        assertEquals(3, str.codePointCount(0, 4));
        assertEquals(1, str.codePointCount(0, 2));
        assertEquals(2, str.codePointCount(2, 4));
        assertEquals(3, str.codePointCount(1, 4));
    }

    @Test
    public void givesOffsetByCodePoint() {
        String str = new String(new char[] { (char) 56178, (char) 56972, 'a', 'b' });
        assertEquals(2, str.offsetByCodePoints(0, 1));
        assertEquals(2, str.offsetByCodePoints(1, 1));
        assertEquals(4, str.offsetByCodePoints(0, 3));
        assertEquals(4, str.offsetByCodePoints(1, 3));
    }

    @Test
    public void findsCodePoint() {
        String str = new String(new char[] { 'a', 'b', (char) 56178, (char) 56972, 'c',
                (char) 56178, (char) 56972, 'c', 'd' });
        assertEquals(2, str.indexOf(969356));
        assertEquals(4, str.indexOf('c'));
    }

    @Test
    public void findsCodePointBackward() {
        String str = new String(new char[] { 'a', 'b', (char) 56178, (char) 56972, 'c',
                (char) 56178, (char) 56972, 'c', 'd' });
        assertEquals(5, str.lastIndexOf(969356));
        assertEquals(7, str.lastIndexOf('c'));
    }

    @Test
    public void findsString() {
        assertEquals(1, "abcdbcd".indexOf("bc"));
        assertEquals(3, "abcdbcd".indexOf("dbcd"));
        assertEquals(-1, "abcdbcd".indexOf("bb"));
    }

    @Test
    public void findsStringBackward() {
        assertEquals(4, "abcdbcd".lastIndexOf("bc"));
        assertEquals(-1, "abcdbcd".lastIndexOf("bb"));
    }

    @Test
    public void concatenatesStrings() {
        assertEquals("abcd", "ab".concat("cd"));
    }

    @Test
    public void replacesCharacter() {
        assertEquals("abbdbbd", "abcdbcd".replace('c', 'b'));
    }

    @Test
    public void containsWorks() {
        assertTrue("abcd".contains("bc"));
    }

    @Test
    public void sequenceReplaced() {
        assertEquals("ba", "aaa".replace("aa", "b"));
        assertEquals("xaxaxax", "aaa".replace("", "x"));
        assertEquals("axc", "abc".replace("b", "x"));
        assertEquals("abc", "abc".replace("bc", "bc"));
        assertEquals("xyzbxyz", "abcbabc".replace("abc", "xyz"));
        assertEquals("[]b[]b[]", "aabaabaa".replace("aa", "[]"));
        assertEquals("--", "abcabc".replace("abc", "-"));
        assertEquals("", "abcabc".replace("abc", ""));
        assertEquals("aXYZXYZb", "a12b".replace(new StringBuilder("1"), "XYZ").replace("2", "XYZ"));
        assertEquals("ab", "ab".replace("abc", "x"));
        assertEquals("[]b[]", "aabaa".replace(new StringBuilder("aa"), new StringBuilder("[]")));
        assertEquals("-b-", "aabaa".replace(new StringBuilder("aa"), "-"));
        assertSame("ab", "ab".replace(new StringBuilder("ac"), "x"));
    }

    @Test
    public void sequenceReplaceReturnsSameStringWhenNotFound() {
        var s = new String(new char[] { 'a', 'b', 'c' });
        assertSame(s, s.replace("bd", "x"));
        assertSame(s, s.replace("d", "x"));
    }

    @Test
    public void trimWorks() {
        assertEquals("ab", "  ab   ".trim());
        assertEquals("ab", "ab".trim());
        assertEquals("", "  ".trim());
    }

    @Test
    public void stripWorks() {
        assertEquals("ab", "  ab   ".strip());
        assertEquals("ab", "ab".strip());
        assertEquals("", "  \t".strip());
        assertEquals("ab", "\t\n \u2008ab\r\f".strip());
    }

    @Test
    public void stripLeadingWorks() {
        assertEquals("ab   ", "  ab   ".stripLeading());
        assertEquals("ab   ", "ab   ".stripLeading());
        assertEquals("ab", "ab".stripLeading());
        assertEquals("a b", "a b".stripLeading());
        assertEquals("", "  \t".stripLeading());
        assertEquals("ab", "\t\n \u2008\r\fab".stripLeading());
    }

    @Test
    public void stripTrailingWorks() {
        assertEquals("  ab", "  ab   ".stripTrailing());
        assertEquals("  ab", "  ab".stripTrailing());
        assertEquals("ab", "ab".stripTrailing());
        assertEquals("a b", "a b".stripTrailing());
        assertEquals("", "  \t".stripTrailing());
        assertEquals("ab", "ab\t\n \u2008\r\f".stripTrailing());
    }

    @Test
    public void convertedToCharArray() {
        char[] array = "123".toCharArray();
        assertEquals(3, array.length);
        assertArrayEquals(new char[] { '1', '2', '3' }, array);
    }

    @Test
    public void createdFromByteArray() throws UnsupportedEncodingException {
        byte[] bytes = { 49, 50, 51 };
        assertEquals("123", new String(bytes, "UTF-8"));
    }

    @Test
    public void createdFromUTF8ByteArray() throws UnsupportedEncodingException {
        byte[] bytes = { 65, -62, -69, -32, -82, -69, -16, -66, -78, -69 };
        assertEquals("A\u00BB\u0BBB\uD8BB\uDCBB", new String(bytes, "UTF-8"));
    }

    @Test
    public void createFromLongUTF8ByteArray() throws UnsupportedEncodingException {
        byte[] bytes = new byte[16384];
        for (int i = 0; i < bytes.length;) {
            bytes[i++] = -16;
            bytes[i++] = -66;
            bytes[i++] = -78;
            bytes[i++] = -69;
        }
        String str = new String(bytes, "UTF-8");
        assertEquals('\uD8BB', str.charAt(8190));
        assertEquals('\uDCBB', str.charAt(8191));
    }

    @Test
    public void getByteArray() throws UnsupportedEncodingException {
        byte[] bytes = "123".getBytes("UTF-8");
        assertArrayEquals(new byte[] { 49, 50, 51 }, bytes);
        assertEquals(3, bytes.length);
    }

    @Test
    public void getUTF8ByteArray() throws UnsupportedEncodingException {
        byte[] bytes = "A\u00BB\u0BBB\uD8BB\uDCBB".getBytes("UTF-8");
        assertArrayEquals(new byte[] { 65, -62, -69, -32, -82, -69, -16, -66, -78, -69 }, bytes);
    }

    @Test
    public void getUTF8ByteArrayOfLongString() throws UnsupportedEncodingException {
        char[] chars = new char[8192];
        for (int i = 0; i < chars.length;) {
            chars[i++] = '\uD8BB';
            chars[i++] = '\uDCBB';
        }
        byte[] bytes = new String(chars).getBytes("UTF-8");
        assertEquals(-16, bytes[16380]);
        assertEquals(-66, bytes[16381]);
        assertEquals(-78, bytes[16382]);
        assertEquals(-69, bytes[16383]);
    }

    @Test
    public void createsStringFromCodePoints() {
        int[] codePoints = { 97, 98, 969356, 99, 969356, 99, 100 };
        String str = new String(codePoints, 0, codePoints.length);
        assertEquals('a', str.charAt(0));
        assertEquals('b', str.charAt(1));
        assertEquals(56178, str.charAt(2));
        assertEquals(56972, str.charAt(3));
        assertEquals('c', str.charAt(4));
        assertEquals(56178, str.charAt(5));
        assertEquals(56972, str.charAt(6));
        assertEquals('c', str.charAt(7));
        assertEquals('d', str.charAt(8));
    }

    @Test
    public void convertsCase() {
        assertEquals("foo bar", "FoO bAr".toLowerCase());
        assertEquals("FOO BAR", "FoO bAr".toUpperCase());
    }

    private String common = "i̇stanbul";
    private String turkish = "istanbul";

    @Test
    @SkipPlatform({ TestPlatform.C, TestPlatform.WEBASSEMBLY_GC })
    public void convertsCaseLocaled() {
        assertEquals(turkish, "İstanbul".toLowerCase(new Locale("tr", "TR")));
        assertEquals(common, "İstanbul".toLowerCase(Locale.US));
        assertNotEquals(turkish, common);
        assertEquals("İSTANBUL", common.toUpperCase(Locale.US));
        assertEquals("İSTANBUL", turkish.toUpperCase(new Locale("tr", "TR")));
        assertNotEquals(common.toUpperCase(Locale.US), turkish.toUpperCase(new Locale("tr", "TR")));
        assertEquals(common.toUpperCase(Locale.US), common.toUpperCase(Locale.CANADA));
        assertEquals(common.toUpperCase(Locale.US), common.toUpperCase(Locale.ROOT));
    }

    @Test
    public void interns() {
        assertSame("xabc".substring(1).intern(), "abcx".substring(0, 3).intern());
        assertSame("xabc".substring(1).intern(), "abc");
    }

    @Test
    public void internsConstants() {
        assertSame("abc", ("a" + "bc").intern());
    }

    @Test
    public void joinStrings() {
        String str = String.join("/", "a", "b");
        assertEquals(3, str.length());
        assertEquals("a/b", str);
    }

    @Test
    public void joinSingleString() {
        String str = String.join("/", "a");
        assertEquals(1, str.length());
        assertEquals("a", str);
    }

    @Test
    public void joinNoStrings() {
        String str = String.join("/");
        assertEquals(0, str.length());
        assertEquals("", str);
    }
    
    @Test
    public void testIsBlank() {
        assertTrue(new String(new char[0]).isBlank());
        assertTrue(new String(new char[] { ' ', ' ' }).isBlank());
        assertFalse(new String(new char[] { ' ', 'x', ' ' }).isBlank());
        assertFalse(new String(new char[] { 'a', ' ' }).isBlank());
    }

    @Test
    public void testChars() {
        assertEquals(0, "".chars().toArray().length);
        assertArrayEquals(new int[] {'A', 'B', 'C', '1', '2', '3'}, "ABC123".chars().toArray());
    }

    @Test
    public void codePointsStream() {
        assertEquals(0, "".codePoints().toArray().length);
        assertArrayEquals(new int[] {'A', 'B', 'C', '1', '2', '3'}, "ABC123".chars().toArray());

        assertArrayEquals(new int[] { 969356 }, new String(new char[] { (char) 56178, (char) 56972 })
                .codePoints().toArray());
        assertArrayEquals(new int[] { 56972, 56178 }, new String(new char[] { (char) 56972, (char) 56178 })
                .codePoints().toArray());
        assertArrayEquals(new int[] { 56178 }, String.valueOf((char) 56178).codePoints().toArray());
    }
    
    @Test
    public void translateEscapes() {
        assertEquals("abc", "abc".translateEscapes());
        assertEquals("\n\r\t\n", "\\n\\r\\t\\12".translateEscapes());
    }
    
    @Test
    public void stripIndent() {
        assertEquals("abc", "abc".stripIndent());
        assertEquals("abc", "  abc".stripIndent());
        assertEquals("abc\ndef", " abc\n def".stripIndent());
        assertEquals("abc\n\ndef\n", " abc\n \n def\n ".stripIndent());
        assertEquals("abc\n\ndef\n", " abc\r\n \n def\r ".stripIndent());
        assertEquals("", " ".stripIndent());
        assertEquals("\n", " \n ".stripIndent());
    }
}
