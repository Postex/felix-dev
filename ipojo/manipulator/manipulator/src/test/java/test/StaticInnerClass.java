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
package test;

import java.util.function.Function;

public class StaticInnerClass {

    private static Function<String, Integer> stringToIntStatic() {
        // ensure that the manipulated class does not refer to the outer class
        return new Function<String, Integer>() {
            public Integer apply(String value) {
                return value.hashCode();
            }
        };
    }

    private Function<String, Integer> stringToIntNonStatic() {
        // ensure that the manipulated inner class refers to the outer class in a manipulated way
        return new Function<String, Integer>() {
            public Integer apply(String value) {
                return value.hashCode() + stringToIntStatic().apply(value);
            }
        };
    }

    public int testStatic(String descriptor) {
        return stringToIntStatic().apply(descriptor);
    }

    public int testNonStatic(String descriptor) {
        return stringToIntNonStatic().apply(descriptor);
    }
}
