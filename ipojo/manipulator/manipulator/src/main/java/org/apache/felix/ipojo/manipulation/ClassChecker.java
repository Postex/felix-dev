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

import org.objectweb.asm.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Checks that a POJO is already manipulated or not.
 * Moreover it allows to get manipulation data about this class.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class ClassChecker extends ClassVisitor implements Opcodes {

    /**
     * True if the class is already manipulated.
     */
    private boolean m_isAlreadyManipulated = false;

    /**
     * Interfaces implemented by the component.
     */
    private List<String> m_itfs = new ArrayList<String>();

    /**
     * Method List of method descriptor discovered in the component class.
     */
    private List<MethodDescriptor> m_methods = new ArrayList<MethodDescriptor>();

    /**
     * Super class if not java.lang.Object.
     */
    private String m_superClass;

    /**
     * Class name.
     */
    private String m_className;

    /**
     * List of visited inner class owned by the implementation class.
     */
    private Map<String, List<MethodDescriptor>> m_inners = new LinkedHashMap<String, List<MethodDescriptor>>();

    private Set<String> m_excludeInnerClasses = new HashSet<>();

    /**
     * Class Version.
     * Used to determine the frame format.
     */
    private int m_classVersion;
    private GlobalManipulationFieldsRegistry m_fieldsRegistry;

    public ClassChecker(GlobalManipulationFieldsRegistry fieldsRegistry) {
        super(Opcodes.ASM9);
        this.m_fieldsRegistry = fieldsRegistry;
    }

    /**
     * Check if the _cm field already exists.
     * Update the field list.
     *
     * @param access    : access of the field
     * @param name      : name of the field
     * @param desc      : description of the field
     * @param signature : signature of the field
     * @param value     : value of the field (for static field only)
     * @return the field visitor
     * @see org.objectweb.asm.ClassVisitor#visitField(int, java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
     */
    public FieldVisitor visitField(int access, String name, String desc,
                                   String signature, Object value) {
        if (name.equals(ClassManipulator.IM_FIELD)
                && desc.equals("Lorg/apache/felix/ipojo/InstanceManager;")) {
            m_isAlreadyManipulated = true;
        }
        Type type = Type.getType(desc);
        m_fieldsRegistry.registerField(getClassName(), name, type, access);

        return null;
    }

    /**
     * Add the inner class to the list of inner class to manipulate.
     * The method checks that the inner class is really owned by the implementation class.
     *
     * @param name      inner class qualified name
     * @param outerName outer class name (may be null for anonymous class)
     * @param innerName inner class simple (i.e. short) name
     * @param access    inner class visibility
     * @see org.objectweb.asm.ClassVisitor#visitInnerClass(java.lang.String, java.lang.String, java.lang.String, int)
     */
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (m_className.equals(outerName) || outerName == null) { // Anonymous classes does not have an outer class.
            // Do not include inner static class
            if (!((access & ACC_STATIC) == ACC_STATIC)) {
                m_inners.put(name, new ArrayList<MethodDescriptor>());
            } else {
                m_excludeInnerClasses.add(name);
            }
        }
    }

    /**
     * Check if the class was already manipulated.
     *
     * @return true if the class is already manipulated.
     */
    public boolean isAlreadyManipulated() {
        return m_isAlreadyManipulated;
    }

    /**
     * Gets the extracted class version
     *
     * @return the class version.
     */
    public int getClassVersion() {
        return m_classVersion;
    }

    /**
     * Visit the class.
     * Update the implemented interface list.
     *
     * @param version    : version of the class
     * @param access     : access of the class
     * @param name       : name of the class
     * @param signature  : signature of the class
     * @param superName  : super class of the class
     * @param interfaces : implemented interfaces.
     * @see org.objectweb.asm.ClassVisitor#visit(int, int, java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
     */
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {

        m_classVersion = version;

        if (!superName.equals("java/lang/Object")) {
            m_superClass = superName.replace('/', '.');
        }

        for (String anInterface : interfaces) {
            if (!anInterface.equals("org/apache/felix/ipojo/Pojo")) {
                m_itfs.add(anInterface.replace('/', '.'));
            }
        }

        m_className = name;
    }

    /**
     * Visit a method.
     * Update the method list (except if it init or clinit.
     *
     * @param access     - the method's access flags (see Opcodes). This parameter also indicates if the method is synthetic and/or deprecated.
     * @param name       - the method's name.
     * @param desc       - the method's descriptor (see Type).
     * @param signature  - the method's signature. May be null if the method parameters, return type and exceptions do not use generic types.
     * @param exceptions - the internal names of the method's exception classes (see getInternalName). May be null.
     * @return nothing.
     * @see org.objectweb.asm.ClassVisitor#visitMethod(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
     */
    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        if (!name.equals("<clinit>")) {

            if (name.equals("<init>")) {
                if (!isGeneratedConstructor(name, desc)) {
                    final MethodDescriptor md = new MethodDescriptor("$init", desc, (access & ACC_STATIC) == ACC_STATIC);
                    m_methods.add(md);
                    return new MethodInfoCollector(md);
                }
            } else {
                // no constructors.
                if (!isGeneratedMethod(name, desc)) {
                    final MethodDescriptor md = new MethodDescriptor(name, desc, (access & ACC_STATIC) == ACC_STATIC);
                    m_methods.add(md);
                    return new MethodInfoCollector(md);
                }
            }

        }

        if (name.equals("<clinit>")) {
            return new InnerClassAssignedToStaticFieldDetector();
        }

        return null;
    }

    public static boolean isGeneratedConstructor(String name, String desc) {
        return ("<init>".equals(name) && isFirstArgumentInstanceManager(desc));
    }

    public static boolean isFirstArgumentInstanceManager(String desc) {
        Type[] types = Type.getArgumentTypes(desc);
        return types != null && (types.length >= 1)
                && Type.getType("Lorg/apache/felix/ipojo/InstanceManager;").equals(types[0]);
    }

    public static boolean isGeneratedMethod(String name, String desc) {
        return isGetterMethod(name, desc)
                || isSetterMethod(name, desc)
                || isSetInstanceManagerMethod(name)
                || isGetComponentInstanceMethod(name, desc)
                || isManipulatedMethod(name);
    }

    private static boolean isGetterMethod(String name, String desc) {
        // TYPE __getXXX()
        Type[] arguments = Type.getArgumentTypes(desc);
        return (name.startsWith("__get")
                && (arguments.length == 0)
                && !Type.VOID_TYPE.equals(Type.getReturnType(desc)));
    }

    private static boolean isSetterMethod(String name, String desc) {
        // void __setXXX(TYPE)
        Type[] arguments = Type.getArgumentTypes(desc);
        return (name.startsWith("__set")
                && (arguments.length == 1)
                && Type.VOID_TYPE.equals(Type.getReturnType(desc)));
    }

    private static boolean isSetInstanceManagerMethod(String name) {
        return name.startsWith("_setInstanceManager");
    }

    private static boolean isGetComponentInstanceMethod(String name, String desc) {
        return (name.startsWith("getComponentInstance")
                && Type.getType("Lorg/apache/felix/ipojo/ComponentInstance;").equals(Type.getReturnType(desc)));
    }

    private static boolean isManipulatedMethod(String name) {
        return (name.startsWith(ClassManipulator.PREFIX));
    }

    /**
     * Get collected interfaces.
     *
     * @return the interfaces implemented by the component class.
     */
    public List<String> getInterfaces() {
        return m_itfs;
    }

    /**
     * Get collected methods.
     *
     * @return the method list of [method, signature].
     */
    public List<MethodDescriptor> getMethods() {
        return m_methods;
    }

    public String getSuperClass() {
        return m_superClass;
    }

    public Map<String, List<MethodDescriptor>> getInnerClassesAndMethods() {
        return m_inners.entrySet().stream()
                .filter(this::isNotExcludeOrInnerOfExclude)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean isNotExcludeOrInnerOfExclude(Map.Entry<String, List<MethodDescriptor>> entry) {
        String className = entry.getKey();
        return !m_excludeInnerClasses.stream()
                .filter(excludeClassName -> className.startsWith(excludeClassName))
                .findAny()
                .isPresent();
    }

    public String getClassName() {
        return m_className;
    }

    /**
     * This class collects annotations in a method.
     * This class creates an {@link AnnotationDescriptor}
     * if an annotation is found during the visit.
     * It also collects local variables definition.
     *
     * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
     */
    private final class MethodInfoCollector extends MethodVisitor {
        /**
         * The method descriptor of the visited method.
         */
        private MethodDescriptor m_method;

        /**
         * Creates an annotation collector.
         *
         * @param md the method descriptor of the visited method.
         */
        private MethodInfoCollector(MethodDescriptor md) {
            super(Opcodes.ASM9);
            m_method = md;
        }

        /**
         * Visits an annotation.
         * This class checks the visibility. If the annotation is visible,
         * creates the {@link AnnotationDescriptor} corresponding to this annotation
         * to visit this annotation. This {@link AnnotationDescriptor} is added to
         * the {@link MethodDescriptor} of the visited method.
         *
         * @param name    the name of the annotation
         * @param visible is the annotation visible at runtime
         * @return the {@link AnnotationDescriptor} to visit this annotation or
         * <code>null</code> if the annotation is not visible.
         * @see org.objectweb.asm.MethodVisitor#visitAnnotation(java.lang.String, boolean)
         */
        public AnnotationVisitor visitAnnotation(String name, boolean visible) {
            if (visible) {
                AnnotationDescriptor ann = new AnnotationDescriptor(name, true);
                m_method.addAnnotation(ann);
                return ann;
            }
            return null;
        }

        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            m_method.addLocalVariable(name, desc, signature, index);
        }

        public void visitEnd() {
            m_method.end();
        }

        public AnnotationVisitor visitParameterAnnotation(int id,
                                                          String name, boolean visible) {
            if (visible) {
                AnnotationDescriptor ann = new AnnotationDescriptor(name, true);
                m_method.addParameterAnnotation(id, ann);
                return ann;
            }

            /*
             * It is harmless to keep injected parameter annotations on original constructor
             * for correct property resolution in case of re-manipulation
             */
            if (m_method.getName().equals("$init")) {
                AnnotationDescriptor ann = new AnnotationDescriptor(name, false);
                m_method.addParameterAnnotation(id, ann);
                return ann;
            }

            return null;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (m_method.isStatic() && (opcode & NEW) == NEW && m_inners.containsKey(type)) {
                // this is the case for a static method which starts an (anonymous) inner class
                // but due to that the method is static, the (anonymous) inner class has to be interpreted as static and
                // thus not manipulated See PTX-9242
                m_excludeInnerClasses.add(type);
            }
            super.visitTypeInsn(opcode, type);
        }
    }

    /**
     * Describes a method or constructor annotation.
     * This allows creating a copy of the annotations found in the original class
     * to move them on inserted method. This class implements an
     * {@link AnnotationVisitor} in order to create the copy.
     * This class contains a <code>visit</code> method re-injecting the
     * annotation in the generated method.
     *
     * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
     */
    public class AnnotationDescriptor extends AnnotationVisitor {
        /**
         * The name of the annotation.
         */
        private String m_name;
        /**
         * Is the annotation visible at runtime?
         */
        private boolean m_visible;
        /**
         * The description of the annotation.
         * This attribute is set only for nested annotations.
         */
        private String m_desc;
        /**
         * The list of 'simple' attributes.
         */
        private List<SimpleAttribute> m_simples = new ArrayList<SimpleAttribute>(0);
        /**
         * The list of attribute containing an
         * enumeration value.
         */
        private List<EnumAttribute> m_enums = new ArrayList<EnumAttribute>(0);
        /**
         * The list of attribute which are
         * annotations.
         */
        private List<AnnotationDescriptor> m_nested = new ArrayList<AnnotationDescriptor>(0);
        /**
         * The list of attribute which are
         * arrays.
         */
        private List<ArrayAttribute> m_arrays = new ArrayList<ArrayAttribute>(0);


        /**
         * Creates an annotation descriptor.
         * This constructor is used for 'root' annotations.
         *
         * @param name    the name of the  annotation
         * @param visible the visibility of the annotation at runtime
         */
        public AnnotationDescriptor(String name, boolean visible) {
            super(Opcodes.ASM9);
            m_name = name;
            m_visible = visible;
        }

        /**
         * Creates an annotation descriptor.
         * This constructor is used for nested annotations.
         *
         * @param name the name of the  annotation
         * @param desc the descriptor of the annotation
         */
        public AnnotationDescriptor(String name, String desc) {
            super(Opcodes.ASM9);
            m_name = name;
            m_visible = true;
            m_desc = desc;
        }


        /**
         * Visits a simple attribute.
         *
         * @param arg0 the attribute name
         * @param arg1 the attribute value
         * @see org.objectweb.asm.AnnotationVisitor#visit(java.lang.String, java.lang.Object)
         */
        public void visit(String arg0, Object arg1) {
            m_simples.add(new SimpleAttribute(arg0, arg1));
        }


        /**
         * Visits a nested annotation.
         *
         * @param arg0 the attribute name
         * @param arg1 the annotation descriptor
         * @return the annotation visitor parsing the nested annotation
         * @see org.objectweb.asm.AnnotationVisitor#visitAnnotation(java.lang.String, java.lang.String)
         */
        public AnnotationVisitor visitAnnotation(String arg0, String arg1) {
            AnnotationDescriptor ad = new AnnotationDescriptor(arg0, arg1);
            m_nested.add(ad);
            return ad;
        }


        /**
         * Visits an array attribute.
         *
         * @param arg0 the name of the attribute
         * @return the annotation visitor parsing the content of the array,
         * uses a specific {@link ArrayAttribute} to parse this array
         * @see org.objectweb.asm.AnnotationVisitor#visitArray(java.lang.String)
         */
        public AnnotationVisitor visitArray(String arg0) {
            ArrayAttribute aa = new ArrayAttribute(arg0);
            m_arrays.add(aa);
            return aa;
        }


        /**
         * End of the visit.
         *
         * @see org.objectweb.asm.AnnotationVisitor#visitEnd()
         */
        public void visitEnd() {
        }


        /**
         * Visits an enumeration attribute.
         *
         * @param arg0 the attribute name
         * @param arg1 the enumeration descriptor
         * @param arg2 the attribute value
         * @see org.objectweb.asm.AnnotationVisitor#visitEnum(java.lang.String, java.lang.String, java.lang.String)
         */
        public void visitEnum(String arg0, String arg1, String arg2) {
            m_enums.add(new EnumAttribute(arg0, arg1, arg2));
        }

        /**
         * Methods allowing to recreate the visited (stored) annotation
         * into the destination method.
         * This method recreate the annotations itself and any other
         * attributes.
         *
         * @param mv the method visitor visiting the destination method.
         */
        public void visitAnnotation(MethodVisitor mv) {
            AnnotationVisitor av = mv.visitAnnotation(m_name, m_visible);
            for (SimpleAttribute simple : m_simples) {
                simple.visit(av);
            }
            for (EnumAttribute en : m_enums) {
                en.visit(av);
            }
            for (AnnotationDescriptor nested : m_nested) {
                nested.visit(av);
            }
            for (ArrayAttribute array : m_arrays) {
                array.visit(av);
            }
            av.visitEnd();
        }

        /**
         * Methods allowing to recreate the visited (stored) parameter annotations
         * into the destination method.
         * This method recreate the annotations itself and any other
         * attributes.
         *
         * @param id the paramter id
         * @param mv the method visitor visiting the destination method.
         */
        public void visitParameterAnnotation(int id, MethodVisitor mv) {
            AnnotationVisitor av = mv.visitParameterAnnotation(id, m_name, m_visible);
            for (SimpleAttribute simple : m_simples) {
                simple.visit(av);
            }
            for (EnumAttribute en : m_enums) {
                en.visit(av);
            }
            for (AnnotationDescriptor nested : m_nested) {
                nested.visit(av);
            }
            for (ArrayAttribute array : m_arrays) {
                array.visit(av);
            }
            av.visitEnd();
        }

        /**
         * Method allowing to recreate the visited (stored) annotation
         * into the destination annotation. This method is used only
         * for nested annotation.
         *
         * @param mv the annotation visitor to populate with the stored
         *           annotation
         */
        public void visit(AnnotationVisitor mv) {
            AnnotationVisitor av = mv.visitAnnotation(m_name, m_desc);
            for (SimpleAttribute simple : m_simples) {
                simple.visit(av);
            }
            for (EnumAttribute enu : m_enums) {
                enu.visit(av);
            }
            for (AnnotationDescriptor nested : m_nested) {
                nested.visit(av);
            }
            for (ArrayAttribute array : m_arrays) {
                array.visit(av);
            }
            av.visitEnd();
        }


    }

    /**
     * Describes an array attribute.
     * This class is able to visit an annotation array attribute, and to
     * recreate this array on another annotation.
     *
     * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
     */
    public class ArrayAttribute extends AnnotationVisitor {
        /**
         * The name of the attribute.
         */
        private String m_name;
        /**
         * The content of the parsed array.
         */
        private List<Object> m_content = new ArrayList<Object>();

        /**
         * Creates an array attribute.
         *
         * @param name the name of the attribute.
         */
        public ArrayAttribute(String name) {
            super(Opcodes.ASM9);
            m_name = name;
        }

        /**
         * Visits the content of the array. This method is called for
         * simple values.
         *
         * @param arg0 <code>null</code>
         * @param arg1 the value
         * @see org.objectweb.asm.AnnotationVisitor#visit(java.lang.String, java.lang.Object)
         */
        public void visit(String arg0, Object arg1) {
            m_content.add(arg1);
        }

        /**
         * Visits the content of the array. This method is called for
         * nested annotations (annotations contained in the array).
         *
         * @param arg0 <code>null</code>
         * @param arg1 the annotation descriptor
         * @return an {@link AnnotationDescriptor} which creates a copy of
         * the contained annotation.
         * @see org.objectweb.asm.AnnotationVisitor#visitAnnotation(String, String)
         */
        public AnnotationVisitor visitAnnotation(String arg0, String arg1) {
            AnnotationDescriptor ad = new AnnotationDescriptor(null, arg1);
            m_content.add(ad);
            return ad;
        }

        /**
         * Visits the content of the array. This method is called for
         * nested arrays (arrays contained in the array).
         *
         * @param arg0 <code>null</code>
         * @return an {@link AnnotationVisitor} which creates a copy of
         * the contained array.
         * @see org.objectweb.asm.AnnotationVisitor#visitArray(String)
         */
        public AnnotationVisitor visitArray(String arg0) {
            ArrayAttribute aa = new ArrayAttribute(null);
            m_content.add(aa);
            return aa;
        }

        /**
         * End of the array attribute visit.
         *
         * @see org.objectweb.asm.AnnotationVisitor#visitEnd()
         */
        public void visitEnd() {
        }

        /**
         * Visits the content of the array. This method is called for
         * enumeration values.
         *
         * @param arg0 <code>null</code>
         * @param arg1 the enumeration descriptor
         * @param arg2 the value
         * @see org.objectweb.asm.AnnotationVisitor#visitEnum(String, String, String)
         */
        public void visitEnum(String arg0, String arg1, String arg2) {
            EnumAttribute ea = new EnumAttribute(null, arg1, arg2);
            m_content.add(ea);
        }

        /**
         * Recreates the visited array attribute. This method
         * handle the generation of the object embedded in the
         * array.
         *
         * @param av the annotation visitor on which the array attribute
         *           needs to be injected.
         */
        public void visit(AnnotationVisitor av) {
            AnnotationVisitor content = av.visitArray(m_name);
            for (Object component : m_content) {
                if (component instanceof AnnotationDescriptor) {
                    ((AnnotationDescriptor) component).visit(content);
                } else if (component instanceof EnumAttribute) {
                    ((EnumAttribute) component).visit(content);
                } else if (component instanceof ArrayAttribute) {
                    ((ArrayAttribute) component).visit(content);
                } else { // Simple
                    content.visit(null, component);
                }
            }
            content.visitEnd();
        }

    }

    /**
     * Describes a simple attribute.
     * This class is able to visit an annotation simple attribute, and to
     * recreate this attribute on another annotation.
     *
     * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
     */
    public static final class SimpleAttribute {
        /**
         * The name of the attribute.
         */
        private String m_name;
        /**
         * The value of the attribute.
         */
        private Object m_value;

        /**
         * Creates a simple attribute.
         *
         * @param name   the name of the attribute
         * @param object the value of the attribute
         */
        private SimpleAttribute(String name, Object object) {
            m_name = name;
            m_value = object;
        }

        /**
         * Recreates the attribute on the given annotation.
         *
         * @param visitor the visitor on which the attribute needs
         *                to be injected.
         */
        public void visit(AnnotationVisitor visitor) {
            visitor.visit(m_name, m_value);
        }
    }

    /**
     * Describes an attribute. The value of this attribute is an enumerated
     * value.
     * This class is able to visit an annotation enumeration attribute, and to
     * recreate this attribute on another annotation.
     *
     * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
     */
    public static final class EnumAttribute {
        /**
         * The name of the attribute.
         */
        private String m_name;
        /**
         * The descriptor of the enumeration.
         */
        private String m_desc;
        /**
         * The value of the attribute.
         */
        private String m_value;

        /**
         * Creates a enumeration attribute.
         *
         * @param name  the name of the attribute.
         * @param desc  the descriptor of the {@link Enum}
         * @param value the enumerated value
         */
        private EnumAttribute(String name, String desc, String value) {
            m_name = name;
            m_value = value;
            m_desc = desc;
        }

        /**
         * Recreates the attribute on the given annotation.
         *
         * @param visitor the visitor on which the attribute needs
         *                to be injected.
         */
        public void visit(AnnotationVisitor visitor) {
            visitor.visitEnum(m_name, m_desc, m_value);
        }

    }


    /**
     * Class required to detect inner classes assigned to static field and thus must not be manipulated (FELIX-4347).
     * If an inner class is assigned to a static field, it must not be manipulated.
     * <p/>
     * However notice that this is only useful when AspectJ is used, because aspectJ is changing the 'staticity' of
     * the inner class.
     */
    private class InnerClassAssignedToStaticFieldDetector extends MethodVisitor {

        public InnerClassAssignedToStaticFieldDetector() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == NEW && m_inners.containsKey(type)) {
                m_inners.remove(type);
            }
        }
    }
}
