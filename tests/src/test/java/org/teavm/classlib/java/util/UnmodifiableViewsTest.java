/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.classlib.java.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class UnmodifiableViewsTest {
    @Test
    public void preservesRandomAccessAndSubListViews() {
        List<String> backing = new ArrayList<>(Arrays.asList("a", "b", "c"));
        List<String> view = Collections.unmodifiableList(backing);
        assertTrue(view instanceof RandomAccess);
        assertTrue(view.subList(1, 3) instanceof RandomAccess);
        assertFalse(Collections.unmodifiableList(new LinkedList<>()) instanceof RandomAccess);
        backing.set(1, "updated");
        assertEquals("updated", view.get(1));
        assertEquals("updated", view.subList(1, 3).get(0));
    }

    @Test
    public void iteratesSequentialListsWithoutIndexedReads() {
        LinkedList<String> backing = new LinkedList<String>() {
            @Override public String get(int index) {
                throw new AssertionError("Sequential iteration must not use indexed reads");
            }
        };
        backing.addAll(Arrays.asList("a", "b", "c"));
        List<String> view = Collections.unmodifiableList(backing);
        List<String> copy = new ArrayList<>();
        for (String value : view) {
            copy.add(value);
        }
        assertEquals(Arrays.asList("a", "b", "c"), copy);
        ListIterator<String> iterator = view.listIterator(2);
        assertEquals("b", iterator.previous());
        assertEquals(1, iterator.nextIndex());
        assertEquals("b", iterator.next());
        assertEquals("c", iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test(expected = ConcurrentModificationException.class)
    public void iteratorRetainsBackingFailFastChecks() {
        List<String> backing = new ArrayList<>(Arrays.asList("a", "b"));
        Iterator<String> iterator = Collections.unmodifiableList(backing).iterator();
        backing.add("c");
        iterator.next();
    }

    @Test(expected = ConcurrentModificationException.class)
    public void listIteratorRetainsBackingFailFastChecks() {
        List<String> backing = new ArrayList<>(Arrays.asList("a", "b"));
        ListIterator<String> iterator = Collections.unmodifiableList(backing).listIterator();
        backing.add("c");
        iterator.next();
    }

    @Test(expected = NoSuchElementException.class)
    public void exhaustedIteratorHasTheCorrectException() {
        Iterator<String> iterator = Collections.unmodifiableList(new ArrayList<String>()).iterator();
        iterator.next();
    }

    @Test
    public void arrayCopiesKeepOwnershipAndNullTermination() {
        List<String> backing = new ArrayList<>(Arrays.asList("a", "b"));
        List<String> view = Collections.unmodifiableList(backing);
        Object[] first = view.toArray();
        Object[] second = view.toArray();
        assertNotSame(first, second);
        first[0] = "changed";
        assertEquals("a", backing.get(0));
        String[] target = { "old", "old", "old", "sentinel" };
        assertSame(target, view.toArray(target));
        assertArrayEquals(new String[] { "a", "b", null, "sentinel" }, target);
        assertArrayEquals(new String[] { "a", "b" }, view.toArray(new String[0]));
    }

    @Test(expected = ArrayStoreException.class)
    public void typedArrayCopiesCheckComponentTypes() {
        Collections.unmodifiableList(Arrays.asList("a")).toArray(new Integer[0]);
    }

    @Test(expected = NullPointerException.class)
    public void typedArrayCopiesRejectNullDestinations() {
        Collections.unmodifiableList(Arrays.asList("a")).toArray((Object[]) null);
    }

    @Test
    public void listMutationsAndIteratorMutationsStayForbidden() {
        List<String> backing = new ArrayList<>(Arrays.asList("a", "b"));
        List<String> view = Collections.unmodifiableList(backing);
        assertUnsupported(() -> view.add("c"));
        assertUnsupported(() -> view.set(0, "c"));
        assertUnsupported(() -> view.remove(0));
        assertUnsupported(() -> view.remove("a"));
        assertUnsupported(() -> view.subList(0, 1).clear());
        Iterator<String> iterator = view.iterator();
        iterator.next();
        assertUnsupported(iterator::remove);
        ListIterator<String> listIterator = view.listIterator();
        listIterator.next();
        assertUnsupported(listIterator::remove);
        assertUnsupported(() -> listIterator.set("c"));
        assertUnsupported(() -> listIterator.add("c"));
        assertEquals(Arrays.asList("a", "b"), backing);
    }

    @Test
    public void mapReadViewsReflectBackingChangesAndNulls() {
        Map<String, String> backing = new HashMap<>();
        backing.put("a", "one");
        backing.put("null", null);
        Map<String, String> view = Collections.unmodifiableMap(backing);
        assertEquals(2, view.size());
        assertFalse(view.isEmpty());
        assertTrue(view.containsKey("null"));
        assertTrue(view.containsValue(null));
        assertNull(view.getOrDefault("null", "fallback"));
        assertEquals("fallback", view.getOrDefault("missing", "fallback"));
        var keys = view.keySet();
        var values = view.values();
        var entries = view.entrySet();
        backing.put("b", "two");
        assertEquals("two", view.get("b"));
        assertTrue(keys.contains("b"));
        assertTrue(values.contains("two"));
        assertEquals(3, entries.size());
        backing.remove("a");
        assertFalse(keys.contains("a"));
        assertFalse(values.contains("one"));
        assertEquals(2, view.size());
    }

    @Test
    public void mapViewsDoNotExposeMutableEntries() {
        Map<String, String> backing = new HashMap<>();
        backing.put("a", "one");
        Map<String, String> view = Collections.unmodifiableMap(backing);
        assertUnsupported(() -> view.put("b", "two"));
        assertUnsupported(() -> view.remove("a"));
        Map.Entry<String, String> entry = view.entrySet().iterator().next();
        assertUnsupported(() -> entry.setValue("changed"));
        @SuppressWarnings("unchecked")
        Map.Entry<String, String> copiedEntry = (Map.Entry<String, String>) view.entrySet().toArray()[0];
        assertUnsupported(() -> copiedEntry.setValue("changed"));
        Iterator<String> keys = view.keySet().iterator();
        keys.next();
        assertUnsupported(keys::remove);
        Iterator<String> values = view.values().iterator();
        values.next();
        assertUnsupported(values::remove);
        assertEquals("one", backing.get("a"));
    }

    private void assertUnsupported(Runnable action) {
        try {
            action.run();
            fail("Read-only view accepted a mutation");
        } catch (UnsupportedOperationException expected) {
            // The backing collection must remain untouched.
        }
    }
}
