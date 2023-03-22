/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.felix.ipojo.manipulator;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.apache.felix.ipojo.InstanceManager;
import org.apache.felix.ipojo.manipulation.ManipulatedClassLoader;
import org.apache.felix.ipojo.manipulator.util.ConstructorUtils;
import org.apache.felix.ipojo.manipulator.util.Streams;
import org.apache.felix.ipojo.manipulator.util.Strings;
import org.apache.felix.ipojo.metadata.Element;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import test.ClusterDaemon;
import test.InnerAnonymousUnmanipulated;
import test.PojoWithInner;
import test.StaticInnerClassWithAnonymousInnerClass;
import test.StaticInnerClassWithOutsideFieldAccess;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ManipulationEngineTestCase extends TestCase {

    @Mock
    private Reporter reporter;

    @Mock
    private ResourceStore store;

    @Mock
    private ManipulationVisitor visitor;

    @Mock
    private ManipulationResultVisitor result;

    @InjectMocks
    private ManipulationEngine engine = new ManipulationEngine(this.getClass().getClassLoader());


    @Override
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    public void testManipulationOfSimpleClass() throws Exception {

        when(store.read(anyString())).thenReturn(from(ClusterDaemon.class));
        when(visitor.visitManipulationResult(any(Element.class))).thenReturn(result);

        String path = Strings.asResourcePath(ClusterDaemon.class.getName());
        Element metadata = new Element("", "");
        ManipulationUnit info = new ManipulationUnit(path, metadata);
        engine.addManipulationUnit(info);

        engine.generate();

        verify(visitor).visitManipulationResult(eq(metadata));
        verify(result).visitClassStructure(any(Element.class));
        verify(result).visitManipulatedResource(eq(path), any(byte[].class));
        verify(result).visitEnd();

    }

    public void testManipulationOfInnerClass() throws Exception {

        when(visitor.visitManipulationResult(any(Element.class))).thenReturn(result);

        String innerPath = Strings.asResourcePath(PojoWithInner.MyInner.class.getName());
        when(store.read(innerPath)).thenReturn(from(PojoWithInner.MyInner.class));

        String path = Strings.asResourcePath(PojoWithInner.class.getName());
        when(store.read(path)).thenReturn(from(PojoWithInner.class));

        Element metadata = new Element("", "");
        ManipulationUnit info = new ManipulationUnit(path, metadata);
        engine.addManipulationUnit(info);

        engine.generate();

        verify(visitor).visitManipulationResult(eq(metadata));
        verify(result).visitClassStructure(any(Element.class));
        verify(result).visitManipulatedResource(eq(path), any(byte[].class));
        verify(result).visitManipulatedResource(eq(innerPath), any(byte[].class));
        verify(result).visitEnd();
    }

    public void testManipulationOfInnerClassWithDirectFieldAccess() throws Exception {
        ManipulatedClassLoader classLoader = createManipulationClassLoader();
        addManipulationClasses(StaticInnerClassWithOutsideFieldAccess.Factory.class, StaticInnerClassWithOutsideFieldAccess.class);

        engine.generate();

        Class factoryClass = classLoader.findClass("test.StaticInnerClassWithOutsideFieldAccess$Factory");
        Class instanceClass = classLoader.findClass("test.StaticInnerClassWithOutsideFieldAccess");
        Assert.assertNotNull(factoryClass);

        Constructor constructor = ConstructorUtils.getInstanceManagerConstructor(factoryClass);
        Assert.assertNotNull(constructor);

        Object factory = constructor.newInstance(new Object[]{new InstanceManager()});

        Method createCall = factoryClass.getMethod("create", new Class[]{});
        Object instance = createCall.invoke(factory, new Object[0]);
        Method testCall = instanceClass.getMethod("testCall", new Class[]{String.class});
        try {
            testCall.invoke(instance, new Object[]{"value"});
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
            NullPointerException npe = (NullPointerException) e.getCause();
            // When not manipulated, we get an error with a pointer to the null values as:
            // test.StaticInnerClassWithOutsideFieldAccess$Factory.dependency
            // Note that the pointer must not ends with '.dependency', but with '__getdependency()'
            // I'm not happy with this type of test, but I do not see another option
            assertFalse(npe.getMessage().contains(".dependency"));
        }
    }

    public void testInnerAnonymousUnmanipulated() throws Exception {
        ManipulatedClassLoader classLoader = createManipulationClassLoader();
        addManipulationClasses(InnerAnonymousUnmanipulated.class);
        addAnonymousManipulationClass(InnerAnonymousUnmanipulated.class, 1);

        engine.generate();

        Class instanceClass = classLoader.findClass("test.InnerAnonymousUnmanipulated");
        Assert.assertNotNull(instanceClass);

        Constructor constructor = ConstructorUtils.getInstanceManagerConstructor(instanceClass);
        Assert.assertNotNull(constructor);

        classLoader.dump();
        Object factory = constructor.newInstance(new Object[]{new InstanceManager()});

        Method createCall = instanceClass.getMethod("newSupplier", new Class[]{});
        Supplier<Boolean> instance = (Supplier<Boolean>) createCall.invoke(factory, new Object[0]);

        Method property = instance.getClass().getMethod("property", new Class[]{boolean.class});
        property.setAccessible(true);
        property.invoke(instance, new Object[]{true});
        assertTrue(instance.get());
    }

    public void testManipulationOfStaticInnerClassWithAnonymousInner() throws Exception {
        ManipulatedClassLoader classLoader = createManipulationClassLoader();
        addManipulationClasses(StaticInnerClassWithAnonymousInnerClass.class, StaticInnerClassWithAnonymousInnerClass.Inner.class);
        addAnonymousManipulationClass(StaticInnerClassWithAnonymousInnerClass.Inner.class, 1);

        engine.generate();

        Class cl = classLoader.findClass(StaticInnerClassWithAnonymousInnerClass.Inner.class.getName());
        Assert.assertNotNull(cl);

        String testValue = "This Is a test string";

        Constructor constructor = ConstructorUtils.getInstanceManagerConstructor(cl);
        Assert.assertNotNull(constructor);

        Object instance = constructor.newInstance(new Object[]{new InstanceManager()});
        Method setField = cl.getMethod("setField", new Class[]{String.class});
        setField.invoke(instance, testValue);

        Method create = cl.getMethod("create", new Class[]{String.class});
        Object result = create.invoke(instance, "aabb");

        assertEquals("aabb" + testValue, result.toString());
    }

    private void addAnonymousManipulationClass(Class<?> clazz, int number) throws Exception {
        String classPath = Strings.asResourcePath(clazz.getName() + "$" + number);
        when(store.read(classPath)).thenReturn(from(classPath, clazz.getClassLoader()));
    }

    private void addManipulationClasses(Class<?>... classes) throws Exception {
        for (Class<?> clazz : classes) {
            String classPath = Strings.asResourcePath(clazz.getName());
            when(store.read(classPath)).thenReturn(from(clazz));

            Element metadata = new Element(clazz.getName(), "");
            engine.addManipulationUnit(new ManipulationUnit(classPath, metadata));
        }
    }

    private ManipulatedClassLoader createManipulationClassLoader() {
        ManipulatedClassLoader classLoader = new ManipulatedClassLoader();

        // copy manipulated classes to the classloader
        doAnswer(i -> {
            String type = (String) i.getArguments()[0];
            byte[] resource = (byte[]) i.getArguments()[1];
            classLoader.addInnerClass(type.replace('/', '.').replace(".class", ""), resource);
            return null;
        }).when(result).visitManipulatedResource(anyString(), any(byte[].class));
        when(visitor.visitManipulationResult(any(Element.class))).thenReturn(result);
        return classLoader;
    }

    private byte[] from(Class<?> type) throws IOException {
        ClassLoader loader = type.getClassLoader();
        return from(Strings.asResourcePath(type.getName()), loader);
    }

    private byte[] from(String path, ClassLoader loader) throws IOException {
        InputStream is = loader.getResourceAsStream(path);
        return Streams.readBytes(is);
    }
}
