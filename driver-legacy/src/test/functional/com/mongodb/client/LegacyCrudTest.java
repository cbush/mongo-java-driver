/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.client;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import util.JsonPoweredTestHelper;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.mongodb.ClusterFixture.isSharded;
import static com.mongodb.JsonTestServerVersionChecker.skipTest;
import static com.mongodb.client.Fixture.getDefaultDatabaseName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

// See https://github.com/mongodb/specifications/tree/master/source/crud/tests
@RunWith(Parameterized.class)
public class LegacyCrudTest extends LegacyDatabaseTestCase {
    private final String filename;
    private final String description;
    private final String databaseName;
    private final BsonArray data;
    private final BsonDocument definition;
    private final boolean skipTest;
    private MongoCollection<BsonDocument> collection;
    private JsonPoweredCrudTestHelper helper;

    public LegacyCrudTest(final String filename, final String description, final String databaseName, final BsonArray data,
                          final BsonDocument definition, final boolean skipTest) {
        this.filename = filename;
        this.description = description;
        this.databaseName = databaseName;
        this.data = data;
        this.definition = definition;
        this.skipTest = skipTest;
    }

    @Before
    public void setUp() {
        super.setUp();
        assumeFalse(skipTest);
        // No runOn syntax for legacy CRUD, so skipping these manually for now
        assumeFalse(isSharded() && description.startsWith("Aggregate with $currentOp"));
        if (!data.isEmpty()) {
            List<BsonDocument> documents = new ArrayList<BsonDocument>();
            for (BsonValue document : data) {
                documents.add(document.asDocument());
            }
            getCollectionHelper().insertDocuments(documents);
        }
        database = client.getDatabase(databaseName);
        collection = database.getCollection(getClass().getName(), BsonDocument.class);
        helper = new JsonPoweredCrudTestHelper(description, database, collection);
    }

    @Test
    public void shouldPassAllOutcomes() {
        BsonDocument outcome = helper.getOperationResults(definition.getDocument("operation"));
        BsonDocument expectedOutcome = definition.getDocument("outcome");

        if (expectedOutcome.containsKey("error")) {
            assertEquals("Expected error", expectedOutcome.getBoolean("error"), outcome.get("error"));
        }

        // Hack to workaround the lack of upsertedCount
        BsonValue expectedResult = expectedOutcome.get("result");
        BsonValue actualResult = outcome.get("result");
        if (actualResult.isDocument()
                    && actualResult.asDocument().containsKey("upsertedCount")
                    && actualResult.asDocument().getNumber("upsertedCount").intValue() == 0
                    && !expectedResult.asDocument().containsKey("upsertedCount")) {
            expectedResult.asDocument().append("upsertedCount", actualResult.asDocument().get("upsertedCount"));
        }

        // Hack to workaround the lack of insertedIds
        if (expectedResult.isDocument()
                && !expectedResult.asDocument().containsKey("insertedIds")) {
            actualResult.asDocument().remove("insertedIds");
        }

        assertEquals(description, expectedResult, actualResult);

        if (expectedOutcome.containsKey("collection")) {
            assertCollectionEquals(expectedOutcome.getDocument("collection"));
        }
    }

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() throws URISyntaxException, IOException {
        List<Object[]> data = new ArrayList<Object[]>();
        for (File file : JsonPoweredTestHelper.getTestFiles("/crud")) {
            BsonDocument testDocument = util.JsonPoweredTestHelper.getTestDocument(file);
            for (BsonValue test: testDocument.getArray("tests")) {
                data.add(new Object[]{file.getName(), test.asDocument().getString("description").getValue(),
                        testDocument.getString("database_name", new BsonString(getDefaultDatabaseName())).getValue(),
                        testDocument.getArray("data"), test.asDocument(), skipTest(testDocument, test.asDocument())});
            }
        }
        return data;
    }

    private void assertCollectionEquals(final BsonDocument expectedCollection) {
        MongoCollection<BsonDocument> collectionToCompare = collection;
        if (expectedCollection.containsKey("name")) {
            collectionToCompare = database.getCollection(expectedCollection.getString("name").getValue(), BsonDocument.class);
        }
        assertEquals(description, expectedCollection.getArray("data"), collectionToCompare.find().into(new BsonArray()));
    }
}
