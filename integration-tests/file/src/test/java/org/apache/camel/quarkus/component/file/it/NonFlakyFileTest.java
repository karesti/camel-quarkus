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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.apache.camel.quarkus.component.file.it.NonFlakyFileTestResource.*;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is an attempt to rewrite file tests from scratch in a cleaner way. It aims at having the test logic more
 * separated and avoiding synchronization issues. On the long run we may evaluate whether this approach is really more
 * readable and less flaky.
 *
 * Linked to https://github.com/apache/camel-quarkus/issues/3584
 */
@QuarkusTest
@QuarkusTestResource(NonFlakyFileTestResource.class)
class NonFlakyFileTest {

    @Test
    void idempotentFileShouldBeReadOnlyOnce() throws IOException, InterruptedException {

        // Assert that the idempotent file has been read once
        await().atMost(1, TimeUnit.SECONDS).until(
                () -> RestAssured
                        .get("/file/getFromMock/idempotent_" + IDEMPOTENT_FILE_NAME + "_was-read-once")
                        .then()
                        .extract().asString(),
                equalTo(IDEMPOTENT_FILE_CONTENT));

        // Atomically move the previously read idempotent file back to input folder
        Path donePath = TEST_FILES_FOLDER.resolve(Paths.get("idempotent", "done", IDEMPOTENT_FILE_NAME));
        Path targetPath = donePath.getParent().getParent().resolve(IDEMPOTENT_FILE_NAME);
        Files.move(donePath, targetPath, StandardCopyOption.ATOMIC_MOVE);

        // Let one second to ensure that the idempotent file is NOT read once again
        Thread.sleep(1000L);
        String result = RestAssured
                .get("/file/getFromMock/idempotent_" + IDEMPOTENT_FILE_NAME + "_was-read-more-than-once")
                .then()
                .extract().asString();
        assertNotEquals(IDEMPOTENT_FILE_CONTENT, result);
    }

    @Test
    void filterShouldReadOnlyMatchingFile() {
        await().atMost(1, TimeUnit.SECONDS).until(
                () -> RestAssured
                        .get("/file/getFromMock/filter")
                        .then()
                        .extract().asString(),
                equalTo(FILTER_NON_SKIPPED_FILE_CONTENT));
    }

    @Test
    public void pollEnrichShouldSetExchangeBodyWithFileContent() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .post("/file/route/pollEnrich")
                .then()
                .statusCode(200)
                .body(Matchers.is(POLL_ENRICH_FILE_CONTENT));
    }

    @Test
    public void quartzScheduledFilePollingShouldSucceed() {
        await().atMost(10, TimeUnit.SECONDS).until(
                () -> RestAssured
                        .get("/file/getFromMock/quartzScheduledFilePolling")
                        .then()
                        .extract().asString(),
                equalTo(QUARTZ_SCHEDULED_FILE_CONTENT));
    }

    @Test
    public void createFileShouldSucceed() throws IOException {

        String charset = "UTF-8";
        Path expectedFilePath = TEST_FILES_FOLDER.resolve(Paths.get(FILE_CREATION_FOLDER, FILE_CREATION_FILE_NAME));

        assertFalse(Files.exists(expectedFilePath));

        // Create a new file in the "test-files" folder so that it's cleared at each run by the NonFlakyFileTestResource
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body(FILE_CREATION_FILE_CONTENT)
                .queryParam("folder", FILE_CREATION_FOLDER)
                .queryParam("charset", charset)
                .queryParam("fileName", FILE_CREATION_FILE_NAME)
                .post("/file/create-file")
                .then()
                .statusCode(201);

        await().atMost(1, TimeUnit.SECONDS).until(() -> Files.exists(expectedFilePath));

        assertEquals(FILE_CREATION_FILE_CONTENT, readFileToString(expectedFilePath.toFile(), charset));
    }

    @Test
    void readFileWithIso8859_1CharsetShouldSucceed() {
        await().atMost(10, TimeUnit.SECONDS).until(
                () -> RestAssured
                        .get("/file/getFromMock/charsetIsoRead")
                        .then()
                        .extract().asString(),
                equalTo(CHARSET_READ_FILE_CONTENT));
    }

    @Test
    void writeFileWithIso8859_1CharsetShouldSucceed() throws IOException {
        String charset = "ISO-8859-1";
        Path expectedFilePath = TEST_FILES_FOLDER
                .resolve(Paths.get(CHARSET_WRITE_FILE_CREATION_FOLDER, CHARSET_WRITE_FILE_NAME));

        assertFalse(Files.exists(expectedFilePath));

        // Create a new file in the "test-files" folder so that it's cleared at each run by the NonFlakyFileTestResource
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body(CHARSET_WRITE_FILE_CONTENT)
                .queryParam("folder", CHARSET_WRITE_FILE_CREATION_FOLDER)
                .queryParam("charset", charset)
                .queryParam("fileName", CHARSET_WRITE_FILE_NAME)
                .post("/file/create-file")
                .then()
                .statusCode(201);

        await().atMost(1, TimeUnit.SECONDS).until(() -> Files.exists(expectedFilePath));

        assertEquals(CHARSET_WRITE_FILE_CONTENT, readFileToString(expectedFilePath.toFile(), charset));
    }

    @Test
    void readLockCantBeAcquiredOnFileSmallerThanReadLockMinLength() throws InterruptedException {

        Path inputFilePath = TEST_FILES_FOLDER.resolve(Paths.get(READ_LOCK_FOLDER_IN, READ_LOCK_FILE_NAME));
        Path doneFilePath = TEST_FILES_FOLDER.resolve(Paths.get(READ_LOCK_FOLDER_IN, ".done", READ_LOCK_FILE_NAME));
        Path outputFilePath = TEST_FILES_FOLDER.resolve(Paths.get(READ_LOCK_FOLDER_OUT, READ_LOCK_FILE_NAME));

        Thread.sleep(10_000L);

        // Check that the input file still reside in input folder
        assertTrue(Files.exists(inputFilePath));

        // Check that .done and output folder do not contain the input file
        assertFalse(Files.exists(doneFilePath));
        assertFalse(Files.exists(outputFilePath));
    }

    @Test
    void twoFilesShouldBeReadInSameBatch() {
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            Map<?, ?> records = RestAssured
                    .get("/file/getBatch/")
                    .then()
                    .statusCode(200)
                    .extract().as(Map.class);

            return records.size() == 2 && records.keySet().contains(BATCH_FILE_NAME_1_CONTENT)
                    && records.keySet().contains(BATCH_FILE_NAME_2_CONTENT)
                    && records.values().contains(0) && records.values().contains(1);
        });
    }
}
