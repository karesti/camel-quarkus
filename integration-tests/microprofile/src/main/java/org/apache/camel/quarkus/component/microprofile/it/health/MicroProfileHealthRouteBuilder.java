/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.component.microprofile.it.health;

import org.apache.camel.builder.RouteBuilder;

public class MicroProfileHealthRouteBuilder extends RouteBuilder {
    @Override
    public void configure() {
        from("direct:start").routeId("healthyRoute")
                .setBody(constant("Hello Camel Quarkus"));

        from("direct:disabled").routeId("disabledHealthRoute")
                .log("This route will not show up in health checks as it is disabled in application.properties");

        from("direct:checkIntervalThreshold").routeId("checkIntervalThreshold")
                .log("This route is used to check to test health check interval / threshold");
    }
}
