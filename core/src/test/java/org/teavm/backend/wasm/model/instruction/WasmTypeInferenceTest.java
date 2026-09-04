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
import java.util.List;
import org.junit.Test;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;

public class WasmTypeInferenceTest {
    @Test
    public void teeLocalProducesTheDeclaredReferenceType() {
        var baseType = new WasmStructure("Base");
        var derivedType = new WasmStructure("Derived");
        derivedType.setSupertype(baseType);
        var local = new WasmLocal(baseType.getReference());
        var inference = new WasmTypeInference();
        inference.typeStack.add(derivedType.getReference());

        new WasmTeeLocal(local).acceptVisitor(inference);

        assertSame(baseType.getReference(), inference.typeStack.get(0));
    }

    @Test
    public void allocationInstructionsProduceNonNullReferences() {
        var structure = new WasmStructure("Structure");
        var array = new WasmArray("Array", WasmType.INT32.asStorage());
        var functionType = new WasmFunctionType("Function", (WasmType) null, List.of());
        var function = new WasmFunction(functionType);
        var inference = new WasmTypeInference();

        new WasmStructNewDefault(structure).acceptVisitor(inference);
        assertSame(structure.getNonNullReference(), inference.typeStack.remove(0));

        inference.typeStack.add(WasmType.INT32);
        new WasmArrayNewDefault(array).acceptVisitor(inference);
        assertSame(array.getNonNullReference(), inference.typeStack.remove(0));

        new WasmArrayNewFixed(array, 0).acceptVisitor(inference);
        assertSame(array.getNonNullReference(), inference.typeStack.remove(0));

        new WasmFunctionReference(function).acceptVisitor(inference);
        assertSame(functionType.getNonNullReference(), inference.typeStack.remove(0));

        inference.typeStack.add(WasmType.INT32);
        new WasmInt31Reference().acceptVisitor(inference);
        assertSame(WasmType.I31.asNonNull(), inference.typeStack.remove(0));
    }
}
