/*
 * Copyright 2026 Nostr Game Engine.
 * SPDX-License-Identifier: Apache-2.0
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
