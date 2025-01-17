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

package org.apache.felix.ipojo.manipulation;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.apache.felix.ipojo.InstanceManager;
import org.apache.felix.ipojo.Pojo;
import org.mockito.Mockito;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ManipulatorTest extends TestCase {

    public static byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        long length = file.length();
        byte[] bytes = new byte[(int) length];

        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
                && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }

        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("Could not completely read file " + file.getName());
        }

        // Close the input stream and return bytes
        is.close();
        return bytes;
    }

    public void testClusterDaemon() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/ClusterDaemon.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);

        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.ClusterDaemon", clazz);

        //Assert.assertNotNull(manipulator.getManipulationMetadata());

        //System.out.println(manipulator.getManipulationMetadata());


        ClassReader reader = new ClassReader(clazz);
        CheckClassAdapter.verify(reader, false, new PrintWriter(new File("/tmp/class_dump")));

        Class cl = classloader.findClass("test.ClusterDaemon");
        //Assert.assertNotNull(cl);

        // The manipulation add stuff to the class.
        //Assert.assertTrue(clazz.length > getBytesFromFile(new File("target/test-classes/test/ClusterDaemon.class")).length);

        //Assert.assertNotNull(cl.newInstance());

    }

    public void testCrypto() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/frames/CryptoServiceSingleton.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);


        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.frames.CryptoServiceSingleton", clazz);

        //Assert.assertNotNull(manipulator.getManipulationMetadata());

        //System.out.println(manipulator.getManipulationMetadata());


        ClassReader reader = new ClassReader(clazz);
        CheckClassAdapter.verify(reader, false, new PrintWriter(new File("/tmp/class_dump")));

        Class cl = classloader.findClass("test.frames.CryptoServiceSingleton");
        Assert.assertNotNull(cl);

        final Constructor constructor = cl.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();

        Method method = cl.getMethod("encryptAESWithCBC", String.class, String.class);
        final String salt = "0000000000000000";
        String result = (String) method.invoke(instance, "hello", salt);
        assertNotNull(result);

        // The manipulation add stuff to the class.
        //Assert.assertTrue(clazz.length > getBytesFromFile(new File("target/test-classes/test/ClusterDaemon.class")).length);

        //Assert.assertNotNull(cl.newInstance());

    }

    public void testManipulatingPojoWithStaticInterface() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/PojoWithStaticInterface.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);
        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.PojoWithStaticInterface", clazz);
        Class cl = classloader.findClass("test.PojoWithStaticInterface");
        Assert.assertNotNull(cl);
        Assert.assertNotNull(manipulator.getManipulationMetadata());

        final Constructor constructor = cl.getDeclaredConstructor();
        Assert.assertNotNull(constructor);
        final Object instance = constructor.newInstance();
        Assert.assertNotNull(instance);

        Method method = cl.getMethod("doSomething", new Class[0]);
        Assert.assertEquals(((String) method.invoke(instance, new Object[0])), "test");
    }

    public void testManipulatingPojoWithFinalArray() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/PojoWithFinalArray.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);

        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.PojoWithFinalArray", clazz);
        Class cl = classloader.findClass("test.PojoWithFinalArray");
        Assert.assertNotNull(cl);
        Assert.assertNotNull(manipulator.getManipulationMetadata());

        final Constructor constructor = cl.getDeclaredConstructor();
        Assert.assertNotNull(constructor);
        final Object instance = constructor.newInstance();
        Assert.assertNotNull(instance);

        Method method = cl.getMethod("doSomething", new Class[0]);
        Assert.assertEquals(((String) method.invoke(instance, new Object[0])), "test");
    }

    private byte[] getTestClassBytes(String className) throws Exception {
        String classesPath = "target/test-classes/";
        return getBytesFromFile(new File(classesPath + className + ".class"));
    }

    private ManipulatedClassLoader manipulate(String className, String ... extraClasses) throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());

        byte[] originalClass = getTestClassBytes(className);

        manipulator.prepare(originalClass);
        Collection<String> innerClasses = manipulator.getInnerClasses();
        Map<String, byte[]> originalInnerClasses = new HashMap<>();
        for (String innerClass : innerClasses) {
            byte[] originalInnerClass = getTestClassBytes(innerClass);
            manipulator.prepareInnerClass(innerClass, originalInnerClass);
            originalInnerClasses.put(innerClass, originalInnerClass);
        }

        byte[] manipulatedClass = manipulator.manipulate(originalClass);
        ManipulatedClassLoader classloader = new ManipulatedClassLoader(className.replace('/', '.'), manipulatedClass, manipulator);
        for (String innerClass : innerClasses) {
            byte[] originalInnerClass = originalInnerClasses.get(innerClass);
            byte[] manipulatedInnerClass = manipulator.manipulateInnerClass(innerClass, originalInnerClass);

            classloader.addInnerClass(innerClass.replace('/', '.'), manipulatedInnerClass);
        }

        for (String extraClass : extraClasses) {
            byte[] extraClassBytes = getTestClassBytes(extraClass);
            classloader.addInnerClass(extraClass.replace('/', '.'), extraClassBytes);
        }

        return classloader;
    }

    public void testClassLoaderInnerClassAlreadyExists() throws Exception {
        ManipulatedClassLoader classloader = new ManipulatedClassLoader("", new byte[0]);
        String className = "test";
        classloader.addInnerClass(className, new byte[0]);
        try {
            classloader.addInnerClass(className, new byte[0]);
            fail("Expected IllegalStateException here.");
        } catch (Exception e) {
            assertTrue("Expected IllegalStateException, but got " + e.getClass().getName(), (e instanceof IllegalStateException));
            assertEquals(e.getMessage(), ManipulatedClassLoader.CLASS_ALREADY_EXISTS_EXCEPTION_MESSAGE.apply(className));
        }
    }

    private <T> Constructor<T> getInstanceManagerConstructor(Class<T> clazz) {
        Constructor[] csts = clazz.getDeclaredConstructors();
        for (Constructor constructor : csts) {
            System.out.println(Arrays.asList(constructor.getParameterTypes()));
            if (constructor.getParameterTypes().length == 1 &&
                    constructor.getParameterTypes()[0].equals(InstanceManager.class)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        return null;
    }

    /**
     * This test is for the construction where an static method starts an anonymous inner class. The issue is that
     * the anonymous inner class is not static by itself, but due to the construction via a static method effectively
     * it is. See PTX-9242
     *
     * @throws Exception
     */
    public void testManipulatingStaticInnerClass() throws Exception {
        // Not that when the next line results in an IllegalStateException with the message that test/StaticInnerClass$1
        // already exists, this will be an indication that the inner class test/StaticInnerClass$1 is manipulated while
        // that must be prevented
        ManipulatedClassLoader classLoader = manipulate("test/StaticInnerClass", "test/StaticInnerClass$1");
        classLoader.dump();
        Class cl = classLoader.findClass("test.StaticInnerClass");
        Assert.assertNotNull(cl);
        Assert.assertNotNull(classLoader.getManipulator().getManipulationMetadata());

        Constructor constructor = getInstanceManagerConstructor(cl);
        Assert.assertNotNull(constructor);

        Object instance = constructor.newInstance(new Object[]{new InstanceManager()});

        String testValue = "This Is a test string";
        int expectedTestValue = testValue.hashCode();

        Method staticMethod = cl.getMethod("testStatic", new Class[]{String.class});
        // check that we don't get a 'java.lang.NoSuchFieldError: this$0'.
        Assert.assertEquals(expectedTestValue, staticMethod.invoke(instance, new Object[]{testValue}));

        Method nonStaticMethod = cl.getMethod("testNonStatic", new Class[]{String.class});
        // check that the non static method calls the instance manager
        Assert.assertEquals(expectedTestValue * 2, nonStaticMethod.invoke(instance, new Object[]{testValue}));
    }

    public void testManipulatingTheSimplePojo() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/SimplePojo.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);
        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.SimplePojo", clazz);
        Class cl = classloader.findClass("test.SimplePojo");
        Assert.assertNotNull(cl);
        Assert.assertNotNull(manipulator.getManipulationMetadata());

        System.out.println(manipulator.getManipulationMetadata());

        // The manipulation add stuff to the class.
        Assert.assertTrue(clazz.length > getBytesFromFile(new File("target/test-classes/test/SimplePojo.class")).length);


        boolean found = false;
        Constructor cst = null;
        Constructor[] csts = cl.getDeclaredConstructors();
        for (int i = 0; i < csts.length; i++) {
            System.out.println(Arrays.asList(csts[i].getParameterTypes()));
            if (csts[i].getParameterTypes().length == 1 &&
                    csts[i].getParameterTypes()[0].equals(InstanceManager.class)) {
                found = true;
                cst = csts[i];
            }
        }
        Assert.assertTrue(found);

        // We still have the empty constructor
        found = false;
        csts = cl.getDeclaredConstructors();
        for (int i = 0; i < csts.length; i++) {
            System.out.println(Arrays.asList(csts[i].getParameterTypes()));
            if (csts[i].getParameterTypes().length == 0) {
                found = true;
            }
        }
        Assert.assertTrue(found);

        // Check the POJO interface
        Assert.assertTrue(Arrays.asList(cl.getInterfaces()).contains(Pojo.class));

        cst.setAccessible(true);
        Object pojo = cst.newInstance(new Object[]{new InstanceManager()});
        Assert.assertNotNull(pojo);
        Assert.assertTrue(pojo instanceof Pojo);

        Method method = cl.getMethod("doSomething", new Class[0]);
        Assert.assertTrue(((Boolean) method.invoke(pojo, new Object[0])).booleanValue());

    }

    public void testManipulatingTheNonSunPOJO() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/NonSunClass.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);

        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.NonSunClass", clazz);
        Class cl = classloader.findClass("test.NonSunClass");
        Assert.assertNotNull(cl);
        Assert.assertNotNull(manipulator.getManipulationMetadata());

        System.out.println(manipulator.getManipulationMetadata());

        // The manipulation add stuff to the class.
        Assert.assertTrue(clazz.length > getBytesFromFile(new File("target/test-classes/test/NonSunClass.class")).length);


        boolean found = false;
        Constructor cst = null;
        Constructor[] csts = cl.getDeclaredConstructors();
        for (int i = 0; i < csts.length; i++) {
            System.out.println(Arrays.asList(csts[i].getParameterTypes()));
            if (csts[i].getParameterTypes().length == 1 &&
                    csts[i].getParameterTypes()[0].equals(InstanceManager.class)) {
                found = true;
                cst = csts[i];
            }
        }
        Assert.assertTrue(found);

        // Check the POJO interface
        Assert.assertTrue(Arrays.asList(cl.getInterfaces()).contains(Pojo.class));

        cst.setAccessible(true);
        Object pojo = cst.newInstance(new Object[]{new InstanceManager()});
        Assert.assertNotNull(pojo);
        Assert.assertTrue(pojo instanceof Pojo);

        Method method = cl.getMethod("getS1", new Class[0]);
        Assert.assertTrue(((Boolean) method.invoke(pojo, new Object[0])).booleanValue());

    }

    public void testManipulatingChild() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/Child.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);

        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.Child", clazz);
        Class cl = classloader.findClass("test.Child");
        Assert.assertNotNull(cl);
        Assert.assertNotNull(manipulator.getManipulationMetadata());

        boolean found = false;
        Constructor cst = null;
        Constructor[] csts = cl.getDeclaredConstructors();
        for (int i = 0; i < csts.length; i++) {
            System.out.println(Arrays.asList(csts[i].getParameterTypes()));
            if (csts[i].getParameterTypes().length == 1 &&
                    csts[i].getParameterTypes()[0].equals(InstanceManager.class)) {
                found = true;
                cst = csts[i];
            }
        }
        Assert.assertTrue(found);

        // We still have the regular constructor
        found = false;
        csts = cl.getDeclaredConstructors();
        for (int i = 0; i < csts.length; i++) {
            System.out.println(Arrays.asList(csts[i].getParameterTypes()));
            if (csts[i].getParameterTypes().length == 2) {
                found = true;
            }
        }
        Assert.assertTrue(found);

        // Check the POJO interface
        Assert.assertTrue(Arrays.asList(cl.getInterfaces()).contains(Pojo.class));

        InstanceManager im = (InstanceManager) Mockito.mock(InstanceManager.class);
        cst.setAccessible(true);
        Object pojo = cst.newInstance(new Object[]{im});
        Assert.assertNotNull(pojo);
        Assert.assertTrue(pojo instanceof Pojo);

        Method method = cl.getMethod("doSomething", new Class[0]);
        Assert.assertEquals(9, ((Integer) method.invoke(pojo, new Object[0])).intValue());

    }

    public void testManipulatingWithConstructorModification() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/Child.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);
        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.Child", clazz);
        Class cl = classloader.findClass("test.Child");
        Assert.assertNotNull(cl);
        Assert.assertNotNull(manipulator.getManipulationMetadata());

        boolean found = false;
        Constructor cst = null;
        Constructor[] csts = cl.getDeclaredConstructors();
        for (int i = 0; i < csts.length; i++) {
            System.out.println(Arrays.asList(csts[i].getParameterTypes()));
            if (csts[i].getParameterTypes().length == 1 &&
                    csts[i].getParameterTypes()[0].equals(InstanceManager.class)) {
                found = true;
                cst = csts[i];
            }
        }
        Assert.assertTrue(found);

        // We still have the regular constructor
        found = false;
        csts = cl.getDeclaredConstructors();
        for (int i = 0; i < csts.length; i++) {
            System.out.println(Arrays.asList(csts[i].getParameterTypes()));
            if (csts[i].getParameterTypes().length == 2) {
                found = true;
            }
        }
        Assert.assertTrue(found);

        // Check that we have the IM, Integer, String constructor too
        Constructor cst2 = cl.getDeclaredConstructor(new Class[]{InstanceManager.class, Integer.TYPE, String.class});
        Assert.assertNotNull(cst2);

        // Check the POJO interface
        Assert.assertTrue(Arrays.asList(cl.getInterfaces()).contains(Pojo.class));


        // Creation using cst
        InstanceManager im = (InstanceManager) Mockito.mock(InstanceManager.class);
        cst.setAccessible(true);
        Object pojo = cst.newInstance(new Object[]{im});
        Assert.assertNotNull(pojo);
        Assert.assertTrue(pojo instanceof Pojo);

        Method method = cl.getMethod("doSomething", new Class[0]);
        Assert.assertEquals(9, ((Integer) method.invoke(pojo, new Object[0])).intValue());

        // Try to create using cst2
        im = (InstanceManager) Mockito.mock(InstanceManager.class);
        cst2.setAccessible(true);
        pojo = cst2.newInstance(new Object[]{im, new Integer(2), "bariton"});
        Assert.assertNotNull(pojo);
        Assert.assertTrue(pojo instanceof Pojo);

        method = cl.getMethod("doSomething", new Class[0]);
        Assert.assertEquals(10, ((Integer) method.invoke(pojo, new Object[0])).intValue());


    }

    public void testManipulatingWithNoValidConstructor() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/NoValidConstructor.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);
        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.NoValidConstructor", clazz);
        Class cl = classloader.findClass("test.NoValidConstructor");
        Assert.assertNotNull(cl);
        Assert.assertNotNull(manipulator.getManipulationMetadata());

        System.out.println(manipulator.getManipulationMetadata());

        // The manipulation add stuff to the class.
        Assert.assertTrue(clazz.length > origin.length);


        boolean found = false;
        Constructor cst = null;
        Constructor[] csts = cl.getDeclaredConstructors();
        for (int i = 0; i < csts.length; i++) {
            System.out.println(Arrays.asList(csts[i].getParameterTypes()));
            if (csts[i].getParameterTypes().length == 1 &&
                    csts[i].getParameterTypes()[0].equals(InstanceManager.class)) {
                found = true;
                cst = csts[i];
            }
        }
        Assert.assertTrue(found);

        // Check the POJO interface
        Assert.assertTrue(Arrays.asList(cl.getInterfaces()).contains(Pojo.class));

        cst.setAccessible(true);
        Object pojo = cst.newInstance(new Object[]{new InstanceManager()});
        Assert.assertNotNull(pojo);
        Assert.assertTrue(pojo instanceof Pojo);

    }

    public void testConstructor() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/ConstructorCheck.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);

//        File out = new File("target/ManipulatedConstructorCheck.class");
//        FileOutputStream fos = new FileOutputStream(out);
//        fos.write(clazz);
//        fos.close();

        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.ConstructorCheck", clazz);
        Class cl = classloader.findClass("test.ConstructorCheck");
        Assert.assertNotNull(cl);
        Assert.assertNotNull(manipulator.getManipulationMetadata());

        System.out.println(manipulator.getManipulationMetadata());

        Constructor c = cl.getConstructor(new Class[]{String.class});
        Assert.assertNotNull(c);

        Object o = c.newInstance("toto");
        Field f = o.getClass().getField("m_foo");
        Assert.assertEquals("toto", f.get(o));
    }

    /**
     * https://issues.apache.org/jira/browse/FELIX-3621
     */
    public void testManipulatingDoubleArray() throws Exception {
        Manipulator manipulator = new Manipulator(this.getClass().getClassLoader());
        byte[] origin = getBytesFromFile(new File("target/test-classes/test/DoubleArray.class"));
        manipulator.prepare(origin);
        byte[] clazz = manipulator.manipulate(origin);
        ManipulatedClassLoader classloader = new ManipulatedClassLoader("test.DoubleArray", clazz);
        Class cl = classloader.findClass("test.DoubleArray");
        Assert.assertNotNull(cl);
        Assert.assertNotNull(manipulator.getManipulationMetadata());

        System.out.println(manipulator.getManipulationMetadata());
        Assert.assertTrue(manipulator.getManipulationMetadata().toString().contains("arguments=\"{int[][]}\""));

        // The manipulation add stuff to the class.
        Assert.assertTrue(clazz.length > origin.length);


        boolean found = false;
        Constructor cst = null;
        Constructor[] csts = cl.getDeclaredConstructors();
        for (int i = 0; i < csts.length; i++) {
            System.out.println(Arrays.asList(csts[i].getParameterTypes()));
            if (csts[i].getParameterTypes().length == 1 &&
                    csts[i].getParameterTypes()[0].equals(InstanceManager.class)) {
                found = true;
                cst = csts[i];
            }
        }
        Assert.assertTrue(found);

        // We still have the empty constructor
        found = false;
        csts = cl.getDeclaredConstructors();
        for (int i = 0; i < csts.length; i++) {
            System.out.println(Arrays.asList(csts[i].getParameterTypes()));
            if (csts[i].getParameterTypes().length == 0) {
                found = true;
            }
        }
        Assert.assertTrue(found);

        // Check the POJO interface
        Assert.assertTrue(Arrays.asList(cl.getInterfaces()).contains(Pojo.class));

        cst.setAccessible(true);
        Object pojo = cst.newInstance(new Object[]{new InstanceManager()});
        Assert.assertNotNull(pojo);
        Assert.assertTrue(pojo instanceof Pojo);

        Method method = cl.getMethod("start", new Class[0]);
        Assert.assertTrue(((Boolean) method.invoke(pojo, new Object[0])).booleanValue());

    }


}
