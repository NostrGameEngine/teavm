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
package org.teavm.backend.wasm.transformation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmBlock;
import org.teavm.backend.wasm.model.instruction.WasmCast;
import org.teavm.backend.wasm.model.instruction.WasmGetLocal;
import org.teavm.backend.wasm.model.instruction.WasmTeeLocal;

public class CoroutineLocalTest {
    @Test
    public void temporaryStorageIsNullableButOriginalReadsAndTeeResultsKeepTheirType() {
        var nonNull = WasmType.EXTERN.asNonNull();
        var function = new WasmFunction(new WasmFunctionType(null, List.of(), List.of(nonNull)));
        var parameter = new WasmLocal(nonNull);
        var temporary = new WasmLocal(nonNull);
        var nullable = new WasmLocal(WasmType.EXTERN);
        function.add(parameter);
        function.add(temporary);
        function.add(nullable);
        var block = new WasmBlock(false);
        var read = new WasmGetLocal(temporary);
        var tee = new WasmTeeLocal(temporary);
        var parameterRead = new WasmGetLocal(parameter);
        block.getBody().add(read);
        block.getBody().add(tee);
        block.getBody().add(parameterRead);
        function.getBody().add(block);
        CoroutineTransformation.makeLocalsDefaultable(function);
        assertTrue(((WasmType.Reference) temporary.getType()).isNullable());
        assertEquals(nonNull, parameter.getType());
        assertTrue(read.getNext() instanceof WasmCast);
        assertTrue(tee.getNext() instanceof WasmCast);
        assertEquals(nonNull, ((WasmCast) read.getNext()).getTargetType());
        assertEquals(nonNull, ((WasmCast) tee.getNext()).getTargetType());
        assertEquals(parameterRead, block.getBody().getLast());
    }
}
