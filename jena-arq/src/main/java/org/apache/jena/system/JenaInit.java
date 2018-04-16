/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.system;

import org.apache.jena.sys.JenaSystem;

/**
 * This is a temporary adapter for implementations to transition to [JENA-1524]
 */
public class JenaInit {

    /** Development support - flag to enable output during
     * initialization. Output to {@code System.err}, not a logger
     * to avoid the risk of recursive initialization.
     */
    public static boolean DEBUG_INIT = false ;

    /**
     * Initialize Jena.
     */
    public static void init() {
        JenaInit.init();
    }

    /** Shutdown subsystems */
    public static void shutdown() {
        JenaSystem.shutdown();
    }

    /** Output a debugging message if DEBUG_INIT is set */
    public static void logLifecycle(String fmt, Object ...args) {
        if ( ! DEBUG_INIT )
            return ;
        System.err.printf(fmt, args) ;
        System.err.println() ;
    }
}
