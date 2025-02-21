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
package org.apache.camel.quarkus.component.file.it;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.apache.camel.quarkus.core.util.FileUtils;
import org.junit.jupiter.api.Test;

import static org.apache.camel.quarkus.component.file.it.FileResource.SEPARATOR;
import static org.apache.camel.quarkus.component.file.it.FileResource.SORT_BY;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;

@QuarkusTest
class FileTest {

    private static final String FILE_CONTENT_01 = "Hello1";
    private static final String FILE_CONTENT_02 = "Hello2";
    private static final String FILE_CONTENT_03 = "Hello3";

    @Test
    public void sortBy() throws IOException, InterruptedException {
        createFile(FILE_CONTENT_03, "/file/create/" + SORT_BY, null, "c_" + UUID.randomUUID().toString());
        createFile(FILE_CONTENT_01, "/file/create/" + SORT_BY, null, "a_" + UUID.randomUUID().toString());
        createFile(FILE_CONTENT_02, "/file/create/" + SORT_BY, null, "b_" + UUID.randomUUID().toString());

        startRouteAndWait(SORT_BY);

        await().atMost(10, TimeUnit.SECONDS).until(
                () -> RestAssured
                        .get("/file/getFromMock/" + SORT_BY)
                        .then()
                        .extract().asString(),
                equalTo(FILE_CONTENT_03 + SEPARATOR + FILE_CONTENT_02 + SEPARATOR + FILE_CONTENT_01));
    }

    @Test
    public void fileWatchCreateUpdate() throws IOException, InterruptedException {
        final Path dir = Files.createTempDirectory(FileTest.class.getSimpleName()).toRealPath();
        RestAssured.given()
                .queryParam("path", dir.toString())
                .get("/file-watch/get-events")
                .then()
                .statusCode(204);

        final Path file = dir.resolve("file.txt");
        Files.write(file, "a file content".getBytes(StandardCharsets.UTF_8));

        awaitEvent(dir, file, "CREATE");

        Files.write(file, "changed content".getBytes(StandardCharsets.UTF_8));

        awaitEvent(dir, file, "MODIFY");

        Files.delete(file);

        awaitEvent(dir, file, "DELETE");
    }

    static String createFile(String content, String path, String charset, String prefix)
            throws UnsupportedEncodingException {
        if (charset == null) {
            charset = "UTF-8";
        }
        return createFile(content.getBytes(charset), path, charset, prefix);
    }

    static String createFile(byte[] content, String path, String charset, String fileName) {
        String createdFilePath = RestAssured.given()
                .urlEncodingEnabled(true)
                .queryParam("charset", charset)
                .contentType(ContentType.BINARY)
                .body(content)
                .queryParam("fileName", fileName)
                .post(path)
                .then()
                .statusCode(201)
                .extract()
                .body()
                .asString();

        return FileUtils.nixifyPath(createdFilePath);
    }

    static void startRouteAndWait(String routeId) throws InterruptedException {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body(routeId)
                .post("/file/startRoute")
                .then()
                .statusCode(204);

        // wait for start
        Thread.sleep(500);

    }

    private static void awaitEvent(final Path dir, final Path file, final String type) {
        await()
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .atMost(20, TimeUnit.SECONDS)
                .until(() -> {
                    final ValidatableResponse response = RestAssured.given()
                            .queryParam("path", dir.toString())
                            .get("/file-watch/get-events")
                            .then();
                    switch (response.extract().statusCode()) {
                    case 204:
                        /*
                         * the event may come with some delay through all the OS and Java layers so it is
                         * rather normal to get 204 before getting the expected event
                         */
                        return false;
                    case 200:
                        final JsonPath json = response
                                .extract()
                                .jsonPath();
                        String expectedPath = FileUtils.nixifyPath(file);
                        String actualPath = json.getString("path");
                        return expectedPath.equals(actualPath) && type.equals(json.getString("type"));
                    default:
                        throw new RuntimeException("Unexpected status code " + response.extract().statusCode());
                    }
                });
    }
}
