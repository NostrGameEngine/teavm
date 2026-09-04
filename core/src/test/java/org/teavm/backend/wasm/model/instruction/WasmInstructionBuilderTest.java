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
package org.teavm.backend.wasm.model.instruction;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;

public class WasmInstructionBuilderTest {
    @Test
    public void referenceSetAndGetPreservesSpecificValueType() {
        var baseType = new WasmStructure("Base");
        var derivedType = new WasmStructure("Derived");
        derivedType.setSupertype(baseType);
        var local = new WasmLocal(baseType.getReference());
        var instructions = new WasmInstructionList();
        var builder = new WasmInstructionBuilder(instructions);
        builder.typeInference.typeStack.add(derivedType.getReference());

        builder.setLocal(local).getLocal(local);

        assertTrue(instructions.getFirst() instanceof WasmTeeLocal);
        assertTrue(instructions.getLast() instanceof WasmCast);
        assertSame(derivedType.getReference(), builder.typeInference.typeStack.get(0));
    }

    @Test
    public void numericSetAndGetStillUsesTeeLocal() {
        var local = new WasmLocal(WasmType.INT32);
        var instructions = new WasmInstructionList();
        var builder = new WasmInstructionBuilder(instructions);
        builder.typeInference.typeStack.add(WasmType.INT32);

        builder.setLocal(local).getLocal(local);

        assertSame(instructions.getFirst(), instructions.getLast());
        assertTrue(instructions.getFirst() instanceof WasmTeeLocal);
        assertSame(WasmType.INT32, builder.typeInference.typeStack.get(0));
    }

    @Test
    public void doesNotReuseValueTypeAfterAnExternallyAddedSetLocal() {
        var baseType = new WasmStructure("Base");
        var derivedType = new WasmStructure("Derived");
        derivedType.setSupertype(baseType);
        var local = new WasmLocal(baseType.getReference());
        var instructions = new WasmInstructionList();
        var builder = new WasmInstructionBuilder(instructions);
        builder.typeInference.typeStack.add(derivedType.getReference());
        builder.setLocal(local);

        instructions.clear();
        instructions.add(new WasmSetLocal(local));
        builder.typeInference.typeStack.add(baseType.getReference());
        builder.getLocal(local);

        assertTrue(instructions.getFirst() instanceof WasmSetLocal);
        assertTrue(instructions.getLast() instanceof WasmGetLocal);
    }
}
