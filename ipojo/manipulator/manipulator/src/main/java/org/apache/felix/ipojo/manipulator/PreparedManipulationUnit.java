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

import org.apache.felix.ipojo.manipulation.Manipulator;

public class PreparedManipulationUnit {
    private ManipulationUnit manipulationUnit;
    private ManipulationResultVisitor resultVisitor;
    private Manipulator manipulator;
    private byte[] bytecode;

    public PreparedManipulationUnit(ManipulationUnit manipulationUnit, ManipulationResultVisitor resultVisitor, Manipulator manipulator, byte[] bytecode) {
        this.manipulationUnit = manipulationUnit;
        this.resultVisitor = resultVisitor;
        this.manipulator = manipulator;
        this.bytecode = bytecode;
    }

    public ManipulationUnit getManipulationUnit() {
        return manipulationUnit;
    }

    public ManipulationResultVisitor getResultVisitor() {
        return resultVisitor;
    }

    public Manipulator getManipulator() {
        return manipulator;
    }

    public byte[] getBytecode() {
        return bytecode;
    }
}
