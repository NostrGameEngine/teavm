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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class ArrayListTest {
    @Test
    public void searchesLogicalElementsAndNulls() {
        ArrayList<String> list = new ArrayList<>(32);
        assertFalse(list.contains(null));
        assertEquals(-1, list.indexOf(null));
        assertEquals(-1, list.lastIndexOf(null));
        list.addAll(Arrays.asList("a", null, "b", "a", null));
        assertTrue(list.contains("a"));
        assertTrue(list.contains(null));
        assertFalse(list.contains("missing"));
        assertEquals(0, list.indexOf("a"));
        assertEquals(3, list.lastIndexOf("a"));
        assertEquals(1, list.indexOf(null));
        assertEquals(4, list.lastIndexOf(null));
        list.remove(4);
        assertEquals(1, list.lastIndexOf(null));
        list.clear();
        assertFalse(list.contains(null));
        assertEquals(-1, list.lastIndexOf("a"));
    }

    @Test
    public void searchesInvokeQueryEqualityInEncounterOrder() {
        ArrayList<Object> list = new ArrayList<>(Arrays.asList("a", null, "b", "a"));
        List<Object> visited = new ArrayList<>();
        Object query = new Object() {
            @Override
            public boolean equals(Object other) {
                visited.add(other);
                return "a".equals(other);
            }
        };
        assertTrue(list.contains(query));
        assertEquals(Arrays.asList("a"), visited);
        visited.clear();
        assertEquals(0, list.indexOf(query));
        assertEquals(Arrays.asList("a"), visited);
        visited.clear();
        assertEquals(3, list.lastIndexOf(query));
        assertEquals(Arrays.asList("a"), visited);
        list.set(3, "c");
        visited.clear();
        assertEquals(3, Collections.unmodifiableList(list).lastIndexOf(new Object() {
            @Override
            public boolean equals(Object other) {
                visited.add(other);
                return "c".equals(other);
            }
        }));
        assertEquals(Arrays.asList("c"), visited);
    }

    @Test
    public void missingSearchVisitsNullsInBothDirections() {
        List<Object> visited = new ArrayList<>();
        Object query = new Object() {
            @Override
            public boolean equals(Object other) {
                visited.add(other);
                return false;
            }
        };
        List<Object> list = new ArrayList<>(Arrays.asList("a", null, "b"));
        assertEquals(-1, list.indexOf(query));
        assertEquals(Arrays.asList("a", null, "b"), visited);
        visited.clear();
        assertEquals(-1, list.lastIndexOf(query));
        assertEquals(Arrays.asList("b", null, "a"), visited);
    }

    @Test
    public void searchDoesNotSkipEqualityForIdenticalReferences() {
        int[] calls = { 0 };
        Object value = new Object() {
            @Override
            public boolean equals(Object other) {
                calls[0]++;
                return this == other;
            }
        };
        ArrayList<Object> list = new ArrayList<>(Arrays.asList(value));
        assertTrue(list.contains(value));
        assertEquals(0, list.indexOf(value));
        assertEquals(0, list.lastIndexOf(value));
        assertEquals(3, calls[0]);
    }

    @Test
    public void containsHonorsOverriddenIndexOf() {
        ArrayList<Object> list = new ArrayList<>() {
            @Override
            public int indexOf(Object value) {
                return "virtual".equals(value) ? 0 : -1;
            }
        };
        assertTrue(list.contains("virtual"));
        assertFalse(list.contains("missing"));
        assertTrue(Collections.unmodifiableList(list).contains("virtual"));
    }

    @Test
    public void searchPropagatesQueryExceptionWithoutChangingList() {
        RuntimeException expected = new RuntimeException("query");
        Object query = new Object() {
            @Override
            public boolean equals(Object other) {
                throw expected;
            }
        };
        ArrayList<Object> list = new ArrayList<>(Arrays.asList("a", "b"));
        try {
            list.contains(query);
            fail("Expected query exception");
        } catch (RuntimeException e) {
            assertSame(expected, e);
        }
        assertEquals(Arrays.asList("a", "b"), list);
    }

    @Test
    public void removalUsesQueryEqualityAndRemovesFirstMatch() {
        ArrayList<Object> list = new ArrayList<>(Arrays.asList("a", null, "a"));
        Object query = new Object() {
            @Override
            public boolean equals(Object other) {
                return "a".equals(other);
            }
        };
        assertTrue(list.remove(query));
        assertEquals(Arrays.asList(null, "a"), list);
        assertTrue(list.remove(null));
        assertEquals(Arrays.asList("a"), list);
    }

    @Test
    public void copyConstructorPreservesNullsAndOwnsStorage() {
        ArrayList<Object> source = new ArrayList<>(32);
        source.add("first");
        source.add(null);
        ArrayList<Object> copy = new ArrayList<>(source);
        assertArrayEquals(new Object[] { "first", null }, copy.toArray());
        source.set(0, "source");
        copy.set(1, 42);
        copy.add("added");
        assertArrayEquals(new Object[] { "source", null }, source.toArray());
        assertArrayEquals(new Object[] { "first", 42, "added" }, copy.toArray());
    }

    @Test
    public void copyConstructorHonorsSubclassCollectionView() {
        ArrayList<String> source = new ArrayList<String>() {
            @Override
            public Object[] toArray() {
                return new Object[] { "iterated" };
            }

            @Override
            public Iterator<String> iterator() {
                return Arrays.asList("iterated").iterator();
            }
        };
        source.add("stored");
        assertEquals("iterated", new ArrayList<>(source).get(0));
    }

    @Test
    public void emptyCopyAndCloneOwnTheirStorage() {
        ArrayList<String> source = new ArrayList<>(32);
        ArrayList<String> copy = new ArrayList<>(source);
        ArrayList<?> clone = (ArrayList<?>) source.clone();
        source.add("source");
        copy.add("copy");
        assertTrue(clone.isEmpty());
        assertEquals("source", source.get(0));
        assertEquals("copy", copy.get(0));
    }

    @Test
    public void toArrayCopiesLogicalElements() {
        ArrayList<String> list = new ArrayList<>(32);
        list.add("first");
        list.add(null);
        Object[] copy = list.toArray();
        assertEquals(Object[].class, copy.getClass());
        assertArrayEquals(new Object[] { "first", null }, copy);
        copy[0] = "copy";
        assertEquals("first", list.get(0));
        list.set(0, "list");
        assertEquals("copy", copy[0]);
    }

    @Test
    public void toArrayPreservesUnusedTail() {
        ArrayList<String> list = new ArrayList<>(Arrays.asList("first", "second"));
        String[] target = { "old", "old", "terminate", "retain" };
        assertSame(target, list.toArray(target));
        assertArrayEquals(new String[] { "first", "second", null, "retain" }, target);
        list.clear();
        target[1] = "retain";
        assertSame(target, list.toArray(target));
        assertArrayEquals(new String[] { null, "retain", null, "retain" }, target);
    }

    @Test
    public void toArrayAllocatesRequestedComponentType() {
        ArrayList<String> list = new ArrayList<>(Arrays.asList("first", null));
        String[] target = new String[0];
        String[] copy = list.toArray(target);
        assertNotSame(target, copy);
        assertEquals(String[].class, copy.getClass());
        assertArrayEquals(new String[] { "first", null }, copy);
    }

    @Test
    public void toArrayChecksStoresBeforeOverwritingLaterElements() {
        ArrayList<Object> list = new ArrayList<>(Arrays.asList("first", 42, "last"));
        String[] target = { "old0", "old1", "old2" };
        try {
            list.toArray(target);
            fail("Expected ArrayStoreException");
        } catch (ArrayStoreException e) {
            assertArrayEquals(new String[] { "first", "old1", "old2" }, target);
        }
    }

    @Test(expected = NullPointerException.class)
    public void toArrayRejectsNullTarget() {
        new ArrayList<String>().toArray((String[]) null);
    }

    @Test
    public void elementsAdded() {
        List<Integer> list = new ArrayList<>();
        list.add(2);
        list.add(3);
        list.add(4);
        assertEquals(3, list.size());
        assertEquals(Integer.valueOf(4), list.get(2));
    }

    @Test
    public void capacityIncreased() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 150; ++i) {
            list.add(i);
        }
        assertEquals(150, list.size());
        assertEquals(Integer.valueOf(101), list.get(101));
    }

    @Test
    public void elementsInserted() {
        List<Integer> list = fillFromZeroToNine();
        list.add(5, -1);
        assertEquals(11, list.size());
        assertEquals(Integer.valueOf(-1), list.get(5));
        assertEquals(Integer.valueOf(5), list.get(6));
        assertEquals(Integer.valueOf(9), list.get(10));
    }

    @Test
    public void elementsRemoved() {
        List<Integer> list = fillFromZeroToNine();
        list.remove(5);
        assertEquals(9, list.size());
        assertEquals(Integer.valueOf(6), list.get(5));
        assertEquals(Integer.valueOf(9), list.get(8));
    }

    @Test
    public void concurrentModificationsRestricted() {
        assertThrows(ConcurrentModificationException.class, () -> {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < 10; ++i) {
                list.add(i);
            }
            for (Integer item : list) {
                if (item.equals(5)) {
                    list.remove(5);
                }
            }
        });
    }

    @Test
    public void manyElementsAdded() {
        List<Integer> list = fillFromZeroToNine();
        list.addAll(3, fillFromZeroToNine());
        assertEquals(20, list.size());
        assertEquals(Integer.valueOf(2), list.get(2));
        assertEquals(Integer.valueOf(0), list.get(3));
        assertEquals(Integer.valueOf(9), list.get(12));
        assertEquals(Integer.valueOf(3), list.get(13));
        assertEquals(Integer.valueOf(9), list.get(19));
    }

    @Test
    public void manyElementsRemoved() {
        List<Integer> list = fillFromZeroToNine();
        list.subList(2, 4).clear();
        assertEquals(8, list.size());
        assertEquals(Integer.valueOf(1), list.get(1));
        assertEquals(Integer.valueOf(4), list.get(2));
        assertEquals(Integer.valueOf(9), list.get(7));
    }

    @Test
    public void elementIndexFound() {
        List<Integer> list = fillFromZeroToNine();
        assertEquals(3, list.indexOf(3));
        assertEquals(-1, list.indexOf(100));
    }

    private List<Integer> fillFromZeroToNine() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            list.add(i);
        }
        return list;
    }

    @Test
    public void subListRange() {
        List<Integer> list = fillFromZeroToNine();

        List<Integer> sublist = list.subList(0, 10);
        assertEquals(10, sublist.size());

        sublist = list.subList(0, 0);
        assertEquals(0, sublist.size());

        sublist = list.subList(10, 10);
        assertEquals(0, sublist.size());

        sublist = list.subList(5, 5);
        assertEquals(0, sublist.size());

        try {
            list.subList(-1, -1);
            fail("Expected IOOBE for negative indexes");
        } catch (IndexOutOfBoundsException e) {
            // OK
        }

        try {
            list.subList(11, 11);
            fail("Expected IOOBE for indexes beyond size");
        } catch (IndexOutOfBoundsException e) {
            // OK
        }

        try {
            list.subList(-1, 11);
            fail("Expected IOOBE for indexes beyond limits");
        } catch (IndexOutOfBoundsException e) {
            // OK
        }

        try {
            list.subList(5, 4);
            fail("Expected IAE for lowerIndex > upperIndex");
        } catch (IllegalArgumentException e) {
            // OK
        }
    }

    @Test
    public void removeIf() {
        List<String> list = new ArrayList<>();
        list.add("A1");
        list.add("A2");
        list.add("B1");
        list.add("B2");

        list.removeIf(e -> e.endsWith("2"));

        assertEquals(2, list.size());
        assertEquals("A1", list.get(0));
        assertEquals("B1", list.get(1));
    }

    @Test
    public void testToString() {
        List list = new ArrayList<>();
        list.add(list);
        list.add("A");

        assertEquals("[(this Collection), A]", list.toString());
        assertEquals("[]", new ArrayList().toString());
    }

    @Test
    public void testSort() {
        int size = 10;
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(size - 1 - i);
        }
        list.sort(Comparator.naturalOrder());
        for (int i = 0; i < size; i++) {
            assertEquals(i, list.get(i).intValue());
        }
    }

    @Test
    public void hashCodeEquals() {
        assertEquals(956197, new ArrayList<>(Arrays.asList(1, 3, null, 2)).hashCode());
        assertEquals(new LinkedList<>(Arrays.asList(1, 3, null, 2)), new ArrayList<>(Arrays.asList(1, 3, null, 2)));
        assertNotEquals(new ArrayList<>(Arrays.asList(1, 3, 2)), new ArrayList<>(Arrays.asList(1, 3, null, 2)));
    }

    @Test
    public void testSequencedCollectionReadOnly() {
        List<String> list = new ArrayList<>(List.of("0", "1", "2", "3", "4", "5", "6"));
        List<String> reversed = list.reversed();
        assertEquals("1", reversed.get(5));
        Iterator<String> it = reversed.iterator();
        assertEquals("6", it.next());
        assertEquals("5", it.next());
        assertEquals("6", reversed.getFirst());
        assertEquals("0", reversed.getLast());
        ListIterator<String> lit = reversed.listIterator();
        assertFalse(lit.hasPrevious());
        assertTrue(lit.hasNext());
        assertEquals("6", lit.next());
        assertEquals("5", lit.next());
        assertEquals("5", lit.previous());
        lit = reversed.listIterator(2);
        assertEquals("4", lit.next());
        lit.previous();
        assertEquals("5", lit.previous());
        assertSame(list, reversed.reversed());
        List<String> subList = reversed.subList(3, 5);
        assertEquals("2", subList.getLast());
        assertEquals("3", subList.listIterator().next());
        StringBuilder sb = new StringBuilder();
        subList.forEach(sb::append);
        assertEquals("32", sb.toString());
        List<Integer> duplicates = new ArrayList<>(List.of(0, 1, 2, 3, 2, 1, 0, 0)).reversed();
        assertEquals(2, duplicates.indexOf(1));
        assertEquals(6, duplicates.lastIndexOf(1));
    }

    @Test
    public void testSequencedCollectionMutations() {
        List<String> list = new ArrayList<>(List.of("a", "b", "c", "d"));
        assertEquals("a", list.removeFirst());
        assertEquals("d", list.removeLast());
        list.addFirst("u");
        list.addLast("e");
        assertEquals(List.of("u", "b", "c", "e"), list);
        list = new ArrayList<>(List.of("a", "b", "c", "d")).reversed();
        assertEquals("d", list.removeFirst());
        assertEquals("a", list.removeLast());
        list.addFirst("u");
        list.addLast("e");
        assertEquals("c", list.remove(1));
        list.add(2, "f");
        list.set(1, "k");
        assertEquals(List.of("u", "k", "f", "e"), list);
    }

    @Test
    public void testSequencedCollectionIterator() {
        List<String> list = new ArrayList<>(List.of("a", "b", "c", "d")).reversed();
        Iterator<String> it = list.iterator();
        assertEquals("d", it.next());
        assertEquals("c", it.next());
        it.remove();
        assertEquals(List.of("d", "b", "a"), list);
        list = new ArrayList<>(List.of("a", "b", "c", "d")).reversed();
        ListIterator<String> lit = list.listIterator();
        assertEquals("d", lit.next());
        assertEquals("c", lit.next());
        assertEquals("b", lit.next());
        lit.remove();
        assertEquals("c", lit.previous());
        assertEquals(0, lit.previousIndex());
        assertEquals(1, lit.nextIndex());
        lit.remove();
        assertEquals(0, lit.previousIndex());
        assertEquals(1, lit.nextIndex());
        lit.add("x");
        assertEquals(List.of("d", "x", "a"), list);
    }
    
    @Test
    public void concurrentModificationOverflow() {
        var list = new ArrayList<>(List.of("a", "b"));
        var iter = list.iterator();
        var listIter = list.listIterator();
        iter.next();
        listIter.next();
        
        var count = 1 + (1 << 30);
        for (var i = 0; i < count; ++i) {
            list.add("c");
            list.removeLast();
        }
        
        try {
            iter.next();
            fail("Expected concurrent modification");
        } catch (ConcurrentModificationException e) {
            // expected
        }
        try {
            listIter.next();
            fail("Expected concurrent modification");
        } catch (ConcurrentModificationException e) {
            // expected
        }
    }

    @Test
    public void iterInvalidRemove() {
        var list = new ArrayList<>(List.of("a", "b"));
        try {
            list.listIterator().remove();
            fail("No exception thrown for initial state");
        } catch (IllegalStateException e) {
            // expected
        }

        var iter = list.listIterator();
        iter.next();
        iter.remove();
        try {
            iter.remove();
            fail("No exception thrown for subsequent remove without advancing");
        } catch (IllegalStateException e) {
            // expected
        }
        
        list = new ArrayList<>(List.of("a", "b"));
        iter = list.listIterator(1);
        iter.previous();
        try {
            iter.previous();
            fail("No exception thrown");
        } catch (NoSuchElementException e) {
            // expected
        }
        
        iter.remove();
        assertEquals(List.of("b"), list);
    }

    @Test
    public void sequenceCollectionMethodsOnEmpty() {
        var empty = new ArrayList<>();

        try {
            empty.getFirst();
            fail();
        } catch (NoSuchElementException e) {
            // ok
        }
        try {
            empty.getLast();
            fail();
        } catch (NoSuchElementException e) {
            // ok
        }

        try {
            empty.removeFirst();
            fail();
        } catch (NoSuchElementException e) {
            // ok
        }
        try {
            empty.removeLast();
            fail();
        } catch (NoSuchElementException e) {
            // ok
        }
    }
}
