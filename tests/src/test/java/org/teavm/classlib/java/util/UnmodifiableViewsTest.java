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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.Set;
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

    @Test
    public void readOnlyIteratorNeverAllowsRemove() {
        List<String> backing = new ArrayList<>(Arrays.asList("a"));
        Iterator<String> iterator = Collections.unmodifiableList(backing).iterator();
        assertUnsupported(iterator::remove);
        assertEquals("a", iterator.next());
        assertUnsupported(iterator::remove);
        assertFalse(iterator.hasNext());
        assertUnsupported(iterator::remove);
        assertEquals(Arrays.asList("a"), backing);
    }

    @Test
    public void arrayListSubclassKeepsItsCustomIterator() {
        List<String> backing = new ArrayList<String>() {
            @Override public Iterator<String> iterator() {
                return Arrays.asList("custom").iterator();
            }
        };
        backing.add("backing");
        Iterator<String> iterator = Collections.unmodifiableList(backing).iterator();
        assertEquals("custom", iterator.next());
        assertFalse(iterator.hasNext());
        assertUnsupported(iterator::remove);
        assertEquals("backing", backing.get(0));
    }

    @Test
    public void mutableArrayListIteratorStillRemovesAndKeepsItsCursor() {
        List<String> backing = new ArrayList<>(Arrays.asList("a", "b", "c"));
        Iterator<String> iterator = backing.iterator();
        try {
            iterator.remove();
            fail("Removal before next must fail");
        } catch (IllegalStateException expected) {
            // Mutable iterators keep the normal removal state machine.
        }
        assertEquals("a", iterator.next());
        iterator.remove();
        assertEquals("b", iterator.next());
        iterator.remove();
        assertEquals("c", iterator.next());
        assertFalse(iterator.hasNext());
        assertEquals(Arrays.asList("c"), backing);
    }

    @Test
    public void emptyAndIneffectiveCollectionMutationsAlwaysThrow() {
        for (int size : new int[] { 0, 2 }) {
            List<String> list = new ArrayList<>();
            if (size != 0) {
                list.addAll(Arrays.asList("a", "b"));
            }
            Map<String, String> map = new HashMap<>();
            if (size != 0) {
                map.put("a", "one");
                map.put("b", "two");
            }
            Map<String, String> view = Collections.unmodifiableMap(map);
            List<Collection<?>> views = Arrays.asList(Collections.unmodifiableList(list),
                    Collections.unmodifiableCollection(list), Collections.unmodifiableSet(new HashSet<>(list)),
                    view.keySet(), view.values(), view.entrySet());
            for (Collection<?> collection : views) {
                assertUnsupported(() -> collection.addAll(Collections.emptyList()));
                assertUnsupported(() -> collection.remove("missing"));
                assertUnsupported(() -> collection.removeAll(Collections.emptyList()));
                assertUnsupported(() -> collection.retainAll(collection));
                assertUnsupported(collection::clear);
                assertUnsupported(() -> collection.removeIf(value -> {
                    fail("An unmodifiable collection must not invoke a mutation callback");
                    return false;
                }));
                assertUnsupported(() -> collection.removeIf(null));
            }
            assertEquals(size, list.size());
            assertEquals(size, map.size());
        }
    }

    @Test
    public void emptyListMutatorsRejectBeforeValidationAndCallbacks() {
        List<String> view = Collections.unmodifiableList(new ArrayList<>());
        assertUnsupported(() -> view.addAll(-1, Collections.emptyList()));
        assertUnsupported(() -> view.add(-1, "a"));
        assertUnsupported(() -> view.set(-1, "a"));
        assertUnsupported(() -> view.remove(-1));
        assertUnsupported(() -> view.replaceAll(value -> {
            fail("replaceAll invoked a callback");
            return value;
        }));
        assertUnsupported(() -> view.replaceAll(null));
        assertUnsupported(() -> view.sort((a, b) -> {
            fail("sort invoked a comparator");
            return 0;
        }));
        assertUnsupported(() -> view.sort(null));
    }

    @Test
    public void mapDefaultMutatorsNeverInvokeCallbacks() {
        for (int size : new int[] { 0, 1 }) {
            Map<String, String> map = new HashMap<>();
            if (size != 0) {
                map.put("a", "one");
            }
            Map<String, String> view = Collections.unmodifiableMap(map);
            assertUnsupported(() -> view.putAll(Collections.emptyMap()));
            assertUnsupported(view::clear);
            assertUnsupported(() -> view.putIfAbsent("a", "two"));
            assertUnsupported(() -> view.remove("missing", "two"));
            assertUnsupported(() -> view.replace("missing", "two"));
            assertUnsupported(() -> view.replace("missing", "two", "three"));
            assertUnsupported(() -> view.replaceAll((key, value) -> {
                fail("replaceAll invoked a callback");
                return value;
            }));
            assertUnsupported(() -> view.computeIfAbsent("a", key -> {
                fail("computeIfAbsent invoked a callback");
                return "two";
            }));
            assertUnsupported(() -> view.computeIfPresent("missing", (key, value) -> {
                fail("computeIfPresent invoked a callback");
                return value;
            }));
            assertUnsupported(() -> view.compute("a", (key, value) -> {
                fail("compute invoked a callback");
                return value;
            }));
            assertUnsupported(() -> view.merge("a", "two", (a, b) -> {
                fail("merge invoked a callback");
                return a;
            }));
            assertUnsupported(() -> view.computeIfAbsent("a", null));
            assertUnsupported(() -> view.computeIfPresent("a", null));
            assertUnsupported(() -> view.compute("a", null));
            assertUnsupported(() -> view.replaceAll(null));
            assertUnsupported(() -> view.merge("a", null, null));
            assertEquals(size, map.size());
        }
    }

    @Test
    public void entriesRemainLiveAndProtectedThroughAllTraversalPaths() {
        Map<String, String> map = new HashMap<>();
        map.put("a", "old");
        Set<Map.Entry<String, String>> view = Collections.unmodifiableMap(map).entrySet();
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        entries.add(view.iterator().next());
        @SuppressWarnings("unchecked")
        Map.Entry<String, String> arrayEntry = (Map.Entry<String, String>) view.toArray()[0];
        entries.add(arrayEntry);
        @SuppressWarnings("unchecked")
        Map.Entry<String, String>[] typedEntries = view.toArray(new Map.Entry[0]);
        entries.add(typedEntries[0]);
        view.forEach(entries::add);
        view.iterator().forEachRemaining(entries::add);
        view.spliterator().forEachRemaining(entries::add);
        entries.add(view.stream().findFirst().get());
        map.put("a", "new");
        for (Map.Entry<String, String> entry : entries) {
            assertEquals("new", entry.getValue());
            assertUnsupported(() -> entry.setValue("bad"));
            assertEquals(new AbstractMap.SimpleImmutableEntry<>("a", "new"), entry);
            assertEquals(entry.hashCode(), new AbstractMap.SimpleImmutableEntry<>("a", "new").hashCode());
        }
        assertEquals("new", map.get("a"));
    }

    @Test
    public void entryComparisonDoesNotExposeMutableEntries() {
        Map<String, String> map = new HashMap<>();
        map.put("a", "one");
        Set<Map.Entry<String, String>> view = Collections.unmodifiableMap(map).entrySet();
        Map.Entry<String, String> hostile = new AbstractMap.SimpleEntry<>("a", "one") {
            @Override public boolean equals(Object other) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, String> entry = (Map.Entry<String, String>) other;
                entry.setValue("bad");
                return true;
            }
        };
        assertTrue(view.contains(hostile));
        assertTrue(view.containsAll(Collections.singleton(hostile)));
        assertTrue(view.equals(Collections.singleton(hostile)));
        assertEquals("one", map.get("a"));
    }

    @Test
    public void mapViewTypedArraysOnlyNullTheNextSlot() {
        Map<String, String> map = new HashMap<>();
        map.put("a", "one");
        Map<String, String> view = Collections.unmodifiableMap(map);
        String[] keys = { "0", "1", "2", "3" };
        String[] values = { "0", "1", "2", "3" };
        assertSame(keys, view.keySet().toArray(keys));
        assertSame(values, view.values().toArray(values));
        assertArrayEquals(new String[] { "a", null, "2", "3" }, keys);
        assertArrayEquals(new String[] { "one", null, "2", "3" }, values);
        Object[] entries = { "0", "1", "2", "3" };
        assertSame(entries, view.entrySet().toArray(entries));
        assertNull(entries[1]);
        assertEquals("2", entries[2]);
        assertEquals("3", entries[3]);
        @SuppressWarnings("unchecked")
        Map.Entry<String, String> entry = (Map.Entry<String, String>) entries[0];
        assertUnsupported(() -> entry.setValue("bad"));
    }

    @Test(expected = ArrayStoreException.class)
    public void mapValueArrayCopiesCheckComponentTypes() {
        Map<String, String> map = new HashMap<>();
        map.put("a", "one");
        Collections.unmodifiableMap(map).values().toArray(new Integer[0]);
    }

    @Test(expected = ArrayStoreException.class)
    public void mapEntryArrayCopiesCheckComponentTypes() {
        Map<String, String> map = new HashMap<>();
        map.put("a", "one");
        Collections.unmodifiableMap(map).entrySet().toArray(new String[0]);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void readOnlyListIteratorRejectsNegativeIndex() {
        Collections.unmodifiableList(new ArrayList<>()).listIterator(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void readOnlyListIteratorRejectsIndexPastEnd() {
        Collections.unmodifiableList(new ArrayList<>()).listIterator(1);
    }

    @Test
    public void listIteratorInsertionKeepsCursorAndSize() {
        List<String> list = new ArrayList<>(Arrays.asList("a", "b"));
        ListIterator<String> iterator = list.listIterator(1);
        iterator.add("x");
        assertEquals(2, iterator.nextIndex());
        assertEquals("b", iterator.next());
        assertFalse(iterator.hasNext());
        assertEquals("b", iterator.previous());
        assertEquals("x", iterator.previous());
        assertEquals(Arrays.asList("a", "x", "b"), list);
    }

    @Test
    public void failedListIteratorInsertionKeepsCursor() {
        ListIterator<String> iterator = Arrays.asList("a").listIterator();
        assertUnsupported(() -> iterator.add("x"));
        assertEquals(0, iterator.nextIndex());
        assertEquals("a", iterator.next());
    }

    @Test(expected = ConcurrentModificationException.class)
    public void listIteratorInsertionChecksConcurrentModification() {
        List<String> list = new ArrayList<>(Arrays.asList("a"));
        ListIterator<String> iterator = list.listIterator();
        list.add("b");
        iterator.add("x");
    }

    @Test
    public void mapLookupPreservesBackingOverride() {
        Map<String, String> map = new HashMap<>() {
            @Override public String getOrDefault(Object key, String defaultValue) {
                return "custom";
            }
        };
        assertEquals("custom", Collections.unmodifiableMap(map).getOrDefault("missing", "fallback"));
    }

    @Test
    public void emptyReadTraversalRejectsNullCallbacks() {
        List<String> list = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        Map<String, String> view = Collections.unmodifiableMap(map);
        List<Collection<?>> collections = Arrays.asList(list, Collections.unmodifiableList(list),
                Collections.unmodifiableCollection(list), Collections.unmodifiableSet(new HashSet<>()),
                map.keySet(), map.values(), map.entrySet(), view.keySet(), view.values(), view.entrySet());
        for (Collection<?> collection : collections) {
            assertNullCallback(() -> collection.forEach(null));
            assertNullCallback(() -> collection.iterator().forEachRemaining(null));
            assertNullCallback(() -> collection.spliterator().tryAdvance(null));
            assertNullCallback(() -> collection.spliterator().forEachRemaining(null));
        }
        assertNullCallback(() -> map.forEach(null));
        assertNullCallback(() -> view.forEach(null));
    }

    @Test
    public void emptySpliteratorsRejectNullCallbacks() {
        assertNullCallback(() -> java.util.Spliterators.spliterator(new Object[0], 0).tryAdvance(null));
        assertNullCallback(() -> java.util.Spliterators.spliterator(new int[0], 0)
                .tryAdvance((java.util.function.IntConsumer) null));
        assertNullCallback(() -> java.util.Spliterators
                .spliteratorUnknownSize(Arrays.stream(new long[0]).iterator(), 0)
                .tryAdvance((java.util.function.LongConsumer) null));
        assertNullCallback(() -> java.util.Spliterators
                .spliteratorUnknownSize(Arrays.stream(new double[0]).iterator(), 0)
                .tryAdvance((java.util.function.DoubleConsumer) null));
    }

    private void assertNullCallback(Runnable action) {
        try {
            action.run();
            fail("A null traversal callback must fail, even on an empty collection");
        } catch (NullPointerException expected) {
            // Java validates the callback before attempting traversal.
        }
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
