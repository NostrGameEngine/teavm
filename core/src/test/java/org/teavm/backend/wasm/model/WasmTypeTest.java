/*
 *  Copyright 2026 Alexey Andreev.
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
package org.teavm.backend.wasm.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WasmTypeTest {
    @Test
    public void specialReferencesKeepCanonicalNullabilityVariants() {
        for (var kind : WasmType.SpecialReferenceKind.values()) {
            var nullable = kind.asType();
            var nonNull = nullable.asNonNull();
            assertNotNull(nonNull);
            assertFalse(nonNull.isNullable());
            assertSame(kind.asNonNullType(), nonNull);
            assertSame(nonNull, nonNull.asNonNull());
            assertSame(nullable, nonNull.asNullable());
            assertSame(nullable, nullable.asNullable());
            assertTrue(nullable.isNullable());
        }
    }
}
