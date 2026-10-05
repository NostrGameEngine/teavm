/*
 *  Copyright 2013 Alexey Andreev.
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
package org.teavm.classlib.java.lang;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class InterfaceAssignabilityTest {
    @Test
    public void checksInterfaceInheritanceAndDiamonds() {
        Class<?>[] interfaces = { Root.class, Left.class, Right.class, Diamond.class };
        Object value = new Leaf();
        for (Class<?> type : interfaces) {
            assertTrue(type.isInstance(value));
            assertTrue(type.isAssignableFrom(value.getClass()));
        }
        assertFalse(Diamond.class.isInstance(new Derived()));
        assertFalse(Right.class.isInstance(new Base()));
        assertFalse(Root.class.isInstance(new Object()));
        assertFalse(Root.class.isInstance(null));
    }

    @Test
    public void checksInterfacesAcrossMultipleMaskWords() {
        Object value = new ManyInterfaces();
        Class<?>[] interfaces = {
            I0.class, I1.class, I2.class, I3.class, I4.class,
            I5.class, I6.class, I7.class, I8.class, I9.class,
            I10.class, I11.class, I12.class, I13.class, I14.class,
            I15.class, I16.class, I17.class, I18.class, I19.class,
            I20.class, I21.class, I22.class, I23.class, I24.class,
            I25.class, I26.class, I27.class, I28.class, I29.class,
            I30.class, I31.class, I32.class, I33.class, I34.class,
            I35.class, I36.class, I37.class, I38.class, I39.class
        };
        for (Class<?> type : interfaces) {
            assertTrue(type.isInstance(value));
            assertTrue(type.isAssignableFrom(ManyInterfaces.class));
            assertFalse(type.isInstance(new Object()));
            assertFalse(type.isAssignableFrom(Leaf.class));
        }
        assertFalse(Root.class.isInstance(value));
        assertFalse(Root.class.isAssignableFrom(ManyInterfaces.class));
    }

    @Test
    public void preservesArrayCovariance() {
        assertTrue(Root[].class.isInstance(new Leaf[2]));
        assertTrue(Root[][].class.isInstance(new Derived[2][3]));
        assertTrue(Object[].class.isInstance(new int[2][3]));
        assertFalse(Diamond[].class.isInstance(new Derived[2]));
        assertFalse(Root[].class.isInstance(new int[2]));
        assertFalse(int[].class.isInstance(new long[2]));
        assertTrue(int[].class.isInstance(new int[2]));
        assertTrue(int[][].class.isAssignableFrom(int[][].class));
    }

    @Test
    public void preservesClassAndPrimitiveChecks() {
        assertTrue(Object.class.isAssignableFrom(Root.class));
        assertTrue(Object.class.isAssignableFrom(ManyInterfaces.class));
        assertFalse(Object.class.isAssignableFrom(int.class));
        assertFalse(Root.class.isAssignableFrom(int.class));
        assertFalse(Root.class.isInstance(Integer.valueOf(2)));
        assertTrue(int.class.isAssignableFrom(int.class));
        assertFalse(long.class.isAssignableFrom(int.class));
        assertTrue(Base.class.isInstance(new Leaf()));
        assertFalse(Leaf.class.isInstance(new Base()));
    }

    @Test
    public void primitiveClassesOnlyAcceptTheirOwnClass() {
        Class<?>[] primitives = { boolean.class, byte.class, short.class, char.class, int.class,
                long.class, float.class, double.class, void.class };
        Class<?>[] wrappers = { Boolean.class, Byte.class, Short.class, Character.class, Integer.class,
                Long.class, Float.class, Double.class, Void.class };
        Object[] values = { true, (byte) 1, (short) 1, 'a', 1, 1L, 1F, 1D, new Object() };
        for (int i = 0; i < primitives.length; ++i) {
            for (int j = 0; j < primitives.length; ++j) {
                assertTrue((i == j) == primitives[i].isAssignableFrom(primitives[j]));
                assertFalse(primitives[i].isAssignableFrom(wrappers[j]));
                assertFalse(wrappers[i].isAssignableFrom(primitives[j]));
                assertFalse(primitives[i].isInstance(values[j]));
                try {
                    primitives[i].cast(values[j]);
                    fail("Primitive class accepted an object");
                } catch (ClassCastException expected) {
                    assertNull(primitives[i].cast(null));
                }
            }
            assertFalse(Object.class.isAssignableFrom(primitives[i]));
            try {
                primitives[i].isAssignableFrom(null);
                fail("Null class was accepted");
            } catch (NullPointerException expected) {
                assertFalse(primitives[i].isInstance(null));
            }
        }
    }

    @Test
    public void checksInterfacesOfLibraryCollections() {
        List<String> list = new ArrayList<>();
        assertTrue(List.class.isInstance(list));
        assertTrue(Iterable.class.isInstance(list));
        assertFalse(Root.class.isInstance(list));
    }

    @Test
    public void inheritedClosuresDoNotChangeWhenChildrenAddInterfaces() {
        Object base = new Base();
        Object child = new PlainChild();
        Object extended = new ExtendedChild();
        assertTrue(Left.class.isInstance(child));
        assertTrue(Root.class.isAssignableFrom(PlainGrandchild.class));
        assertFalse(Right.class.isInstance(base));
        assertFalse(Right.class.isInstance(child));
        assertTrue(Right.class.isInstance(extended));
        assertFalse(Diamond.class.isInstance(extended));
        assertTrue(I39.class.isAssignableFrom(ManyInterfacesChild.class));
        assertFalse(Right.class.isAssignableFrom(ManyInterfacesChild.class));
        assertFalse(I39.class.isAssignableFrom(PlainGrandchild.class));
        // Recheck ancestors after querying the extended descendant.
        assertFalse(Right.class.isAssignableFrom(Base.class));
        assertFalse(Right.class.isAssignableFrom(PlainChild.class));
    }

    private static class PlainChild extends Base {
    }

    private static class PlainGrandchild extends PlainChild {
    }

    private static class ExtendedChild extends PlainChild implements Right {
    }

    private static class ManyInterfacesChild extends ManyInterfaces {
    }

    private interface Root {
    }

    private interface Left extends Root {
    }

    private interface Right extends Root {
    }

    private interface Diamond extends Left, Right {
    }

    private static class Base implements Left {
    }

    private static class Derived extends Base implements Right {
    }

    private static class Leaf extends Derived implements Diamond {
    }

    private interface I0 {
    }

    private interface I1 {
    }

    private interface I2 {
    }

    private interface I3 {
    }

    private interface I4 {
    }

    private interface I5 {
    }

    private interface I6 {
    }

    private interface I7 {
    }

    private interface I8 {
    }

    private interface I9 {
    }

    private interface I10 {
    }

    private interface I11 {
    }

    private interface I12 {
    }

    private interface I13 {
    }

    private interface I14 {
    }

    private interface I15 {
    }

    private interface I16 {
    }

    private interface I17 {
    }

    private interface I18 {
    }

    private interface I19 {
    }

    private interface I20 {
    }

    private interface I21 {
    }

    private interface I22 {
    }

    private interface I23 {
    }

    private interface I24 {
    }

    private interface I25 {
    }

    private interface I26 {
    }

    private interface I27 {
    }

    private interface I28 {
    }

    private interface I29 {
    }

    private interface I30 {
    }

    private interface I31 {
    }

    private interface I32 {
    }

    private interface I33 {
    }

    private interface I34 {
    }

    private interface I35 {
    }

    private interface I36 {
    }

    private interface I37 {
    }

    private interface I38 {
    }

    private interface I39 {
    }

    private static class ManyInterfaces implements
            I0, I1, I2, I3, I4, I5, I6, I7, I8, I9,
            I10, I11, I12, I13, I14, I15, I16, I17, I18, I19,
            I20, I21, I22, I23, I24, I25, I26, I27, I28, I29,
            I30, I31, I32, I33, I34, I35, I36, I37, I38, I39 {
    }
}
