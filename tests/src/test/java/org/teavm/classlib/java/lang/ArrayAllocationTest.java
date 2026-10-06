/*
 *  Copyright 2026 TeaVM contributors.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class ArrayAllocationTest {
    private int evaluations;

    @Test
    public void rejectsNegativeLengthsForEveryArrayType() {
        List<IntFunction<Object>> allocations = List.of(
                length -> new Object[length], length -> new String[length],
                length -> new boolean[length], length -> new byte[length],
                length -> new char[length], length -> new short[length],
                length -> new int[length], length -> new long[length],
                length -> new float[length], length -> new double[length]);
        for (var allocation : allocations) {
            assertNegativeLength(allocation);
        }
    }

    @Test
    public void validatesDimensionsBehindEmptyOuterArrays() {
        List<IntFunction<Object>> allocations = List.of(
                length -> new Object[0][length], length -> new Object[length][0],
                length -> new Object[0][1][length], length -> new Object[1][0][length],
                length -> new Object[0][length][0], length -> new Object[0][0][1][length],
                length -> new boolean[0][length], length -> new byte[0][length],
                length -> new char[0][length], length -> new short[0][length],
                length -> new int[0][length], length -> new long[0][length],
                length -> new float[0][length], length -> new double[0][length],
                length -> new int[1][0][length], length -> new int[0][length][0],
                length -> new int[0][0][1][length]);
        for (var allocation : allocations) {
            assertNegativeLength(allocation);
        }
    }

    @Test
    public void evaluatesAllDimensionsBeforeThrowing() {
        for (int negativeDimension = 0; negativeDimension < 3; ++negativeDimension) {
            evaluations = 0;
            try {
                Object array = new Object[dimension(negativeDimension == 0 ? -1 : 0)]
                        [dimension(negativeDimension == 1 ? -1 : 0)]
                        [dimension(negativeDimension == 2 ? -1 : 0)];
                fail("Allocated " + array.getClass());
            } catch (NegativeArraySizeException e) {
                assertEquals(3, evaluations);
            }
        }
    }

    @Test
    public void preservesEmptyMultidimensionalArrayTypes() {
        assertEquals(Object[][][].class, new Object[0][2][3].getClass());
        assertEquals(int[][][].class, new int[2][0][3].getClass());
        assertEquals(0, new Object[0][2][3].length);
        assertEquals(0, (new int[2][0][3])[0].length);
    }

    private void assertNegativeLength(IntFunction<Object> allocation) {
        for (int length : new int[] { -1, Integer.MIN_VALUE }) {
            try {
                allocation.apply(length);
                fail("Accepted negative array dimension: " + length);
            } catch (NegativeArraySizeException e) {
                // A host allocation error or trap must not replace the Java exception.
            }
        }
    }

    private int dimension(int value) {
        ++evaluations;
        return value;
    }
}
