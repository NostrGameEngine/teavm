/*
 *  Copyright 2026 Nostr Game Engine.
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class EnumArrayTest {
    enum Feature { VIEW, INPUT, HIT_TEST }

    @Test
    public void enumConstantsWithoutReflectiveArrayCreation() {
        var first = Feature.class.getEnumConstants();
        var second = Feature.class.getEnumConstants();
        assertEquals(3, first.length);
        assertSame(Feature[].class, first.getClass());
        assertSame(Feature.VIEW, first[0]);
        assertNotSame(first, second);
        first[0] = null;
        assertSame(Feature.VIEW, second[0]);
    }

    @Test
    public void enumSetWithoutReflectiveArrayCreation() {
        var features = EnumSet.allOf(Feature.class);
        assertEquals(3, features.size());
        features.remove(Feature.INPUT);
        assertEquals(EnumSet.of(Feature.VIEW, Feature.HIT_TEST), features);
    }
}
