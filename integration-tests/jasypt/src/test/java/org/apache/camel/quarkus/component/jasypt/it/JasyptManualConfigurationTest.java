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
package org.apache.camel.quarkus.component.jasypt.it;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
@TestProfile(JasyptManualConfigurationProfile.class)
class JasyptManualConfigurationTest {

    @Test
    void manualJasyptConfiguration() {
        RestAssured.given()
                .queryParam("endpointURI", "direct:decryptManualConfiguration")
                .get("/jasypt/decrypt/configuration/greeting.secret")
                .then()
                .statusCode(200)
                .body(is("Hello World"));

        RestAssured.given()
                .queryParam("endpointURI", "direct:decryptManualConfigurationExpression")
                .get("/jasypt/decrypt/configuration/greeting.expression.secret")
                .then()
                .statusCode(200)
                .body(is("Hello World From Expression"));

        // Verify it's the Camel properties component doing the decryption & not SmallRye Config
        RestAssured.given()
                .queryParam("endpointURI", "direct:decryptManualConfigurationExpression")
                .get("/jasypt/decrypt/configuration-from-provider/greeting.expression.secret")
                .then()
                .statusCode(200)
                .body(startsWith("ENC("));
    }
}
