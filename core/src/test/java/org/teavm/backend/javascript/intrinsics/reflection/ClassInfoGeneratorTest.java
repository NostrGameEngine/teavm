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
package org.teavm.backend.javascript.intrinsics.reflection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.teavm.backend.javascript.codegen.DefaultAliasProvider;
import org.teavm.backend.javascript.codegen.DefaultNamingStrategy;
import org.teavm.backend.javascript.codegen.OutputSourceWriterBuilder;
import org.teavm.backend.javascript.spi.GeneratorContext;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.ClassInfo;

public class ClassInfoGeneratorTest {
    @Test
    public void enumArrayDoesNotRequireReflectiveArrayDependency() {
        DependencyInfo dependencies = (DependencyInfo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { DependencyInfo.class }, (proxy, method, args) -> {
                    assertEquals("getMethod", method.getName());
                    assertEquals("java.lang.reflect.Array", ((MethodReference) args[0]).getClassName());
                    return null;
                });
        GeneratorContext context = (GeneratorContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { GeneratorContext.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getDependency":
                            return dependencies;
                        case "getParameterName":
                            return "p" + args[0];
                        default:
                            throw new AssertionError("Unexpected generator call " + method.getName());
                    }
                });
        var output = new StringBuilder();
        var naming = new DefaultNamingStrategy(new DefaultAliasProvider(1000), null);
        var builder = new OutputSourceWriterBuilder(naming);
        builder.setMinified(true);
        var writer = builder.build(output);
        new ClassInfoGenerator().generate(context, writer,
                new MethodReference(ClassInfo.class, "newArrayInstance", int.class, Object.class));
        assertTrue(output.toString().contains("$rt_createArray(p0,p1)"));
        assertFalse(output.toString().contains("$rt_createIntArray"));
    }
}
