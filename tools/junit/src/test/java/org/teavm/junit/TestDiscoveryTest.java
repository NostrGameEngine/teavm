/*
 *  Copyright 2026 NostrGameEngine contributors.
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
package org.teavm.junit;

import static org.junit.Assert.assertEquals;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.Description;

public class TestDiscoveryTest {
    @Test
    public void helperOverloadsDoNotHideAnnotatedTests() throws Exception {
        assertEquals(Set.of("alpha", "beta", "gamma"), names(OverloadedHelpers.class));
    }

    @Test
    public void realOverridesStillHideSuperclassTests() throws Exception {
        assertEquals(Set.of("inherited"), names(Overrides.class));
    }

    private static Set<String> names(Class<?> type) throws Exception {
        Set<String> names = new HashSet<>();
        for (Description child : new TeaVMTestRunner(type).getDescription().getChildren()) {
            names.add(child.getMethodName());
        }
        return names;
    }

    public static class OverloadedHelpers {
        @Test
        public void alpha() { }
        private void alpha(int value) { }
        @Test
        public void beta() { }
        private void beta(String value) { }
        @Test
        public void gamma() { }
        private void gamma(Object value) { }
    }

    public static class Base {
        @Test
        public void overridden() { }
        @Test
        public void inherited() { }
    }

    public static class Overrides extends Base {
        @Override
        public void overridden() { }
        private void inherited(int value) { }
    }
}
