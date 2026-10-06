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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.lang.reflect.Array;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class SmallReferenceArrayTest {
    @Test
    public void initializesEverySlotToNull() {
        Object[][] arrays = {
            new Object[0], new Object[1], new Object[3], new Object[8], new Object[10], new Object[11]
        };
        int[] lengths = { 0, 1, 3, 8, 10, 11 };
        for (int i = 0; i < arrays.length; ++i) {
            assertEquals(lengths[i], arrays[i].length);
            assertSame(Object[].class, arrays[i].getClass());
            for (Object element : arrays[i]) {
                assertNull(element);
            }
        }
    }

    @Test
    public void initializesDynamicallySizedArraysAndOwnsTheirStorage() {
        for (int length : new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 32 }) {
            String[] array = new String[length];
            assertEquals(length, array.length);
            assertSame(String[].class, array.getClass());
            for (String element : array) {
                assertNull(element);
            }
            if (length > 0) {
                array[0] = "owned";
                assertNull((new String[length])[0]);
                String[] clone = array.clone();
                assertNotSame(array, clone);
                assertEquals("owned", clone[0]);
                clone[0] = "clone";
                assertEquals("owned", array[0]);
            }
        }
    }

    @Test
    public void preservesComponentTypesAndClassIdentity() {
        String[] strings = new String[3];
        Object[] objects = new Object[3];
        Object reflected = Array.newInstance(String.class, 3);
        assertSame(String[].class, strings.getClass());
        assertSame(String.class, strings.getClass().getComponentType());
        assertSame(strings.getClass(), reflected.getClass());
        assertSame(Object[].class, objects.getClass());
        assertSame(new String[0].getClass(), strings.getClass());
        assertNotSame(strings, reflected);
        assertArrayEquals(strings, (String[]) reflected);
    }

    @Test
    public void allocatesIndependentStorageAndClone() {
        String[] first = new String[3];
        String[] second = new String[3];
        first[0] = "first";
        String[] clone = first.clone();
        clone[1] = "clone";
        assertNull(second[0]);
        assertNull(first[1]);
        assertEquals("first", clone[0]);
        assertEquals("clone", clone[1]);
        assertSame(first.getClass(), clone.getClass());
        assertNotSame(first, clone);
    }

    @Test
    public void preservesNestedArrayTypes() {
        String[][] nested = new String[3][];
        assertSame(String[][].class, nested.getClass());
        assertSame(String[].class, nested.getClass().getComponentType());
        assertNull(nested[0]);
        nested[1] = new String[1];
        assertNull(nested[1][0]);
        assertNull(nested[2]);
    }

    @Test
    public void rejectsNegativeDynamicLength() {
        assertThrows(NegativeArraySizeException.class, () -> {
            Array.newInstance(String.class, -1);
        });
    }

    @Test
    public void checksReflectiveBounds() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            Array.set(new String[3], 3, "outside");
        });
    }
}
