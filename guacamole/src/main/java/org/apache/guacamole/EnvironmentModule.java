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

package org.apache.guacamole;

import com.google.inject.AbstractModule;
import org.apache.guacamole.environment.Environment;

/**
 * Guice module which binds the base Guacamole server environment.
 *
 * @author Michael Jumper
 */
public class EnvironmentModule extends AbstractModule {

    /**
     * The Guacamole server environment.
     */
    private final Environment environment;

    /**
     * Creates a new EnvironmentModule which will bind the given environment
     * for future injection.
     *
     * @param environment
     *     The environment to bind.
     */
    public EnvironmentModule(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void configure() {

        // Bind environment
        bind(Environment.class).toInstance(environment);

    }

}

