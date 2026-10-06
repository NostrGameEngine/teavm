/*
 *  Copyright 2023 Alexey Andreev.
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
package org.teavm.backend.javascript.rendering;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.teavm.backend.javascript.codegen.OutputSourceWriterBuilder;

public class LongConstantRenderingTest {
    @Test
    public void writesExactLiteralsAcrossTheSignedLongRange() {
        long[] values = { 0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE,
                9007199254740993L, -9007199254740993L, Long.MAX_VALUE, Long.MIN_VALUE,
                0x6D3A91C54E27B8F0L, 0x19C7E42AB508D36FL };
        for (long value : values) {
            var output = new StringBuilder();
            var writer = new OutputSourceWriterBuilder(null).build(output);
            RenderingUtil.appendLongConstant(writer, value);
            assertEquals(value < 0 ? "(" + value + "n)" : value + "n", output.toString());
        }
    }
}
