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

package org.apache.guacamole.form;

/**
 * Represents a field which can contain multiple lines of text.
 *
 * @author Michael Jumper
 */
public class MultilineField extends Field {

    /**
     * Creates a new MultilineField with the given name.
     *
     * @param name
     *     The unique name to associate with this field.
     */
    public MultilineField(String name) {
        super(name, Field.Type.MULTILINE);
    }

}
