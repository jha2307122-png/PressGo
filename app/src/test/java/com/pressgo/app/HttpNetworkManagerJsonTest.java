package com.pressgo.app;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.pressgo.app.network.HttpNetworkManager;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Unit tests verifying JSON serialization and deserialization via Gson
 * as integrated with HttpNetworkManager architecture.
 * (Week 11: HTTP Networking & JSON Parsing).
 */
public class HttpNetworkManagerJsonTest {

    private Gson gson;

    public static class TestUserPayload {
        private String id;
        private String name;
        private int age;

        public TestUserPayload() {}

        public TestUserPayload(String id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public int getAge() { return age; }
    }

    @Before
    public void setUp() {
        gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .create();
    }

    @Test
    public void testValidJsonParsing() {
        String json = "{\"id\":\"u123\",\"name\":\"John Doe\",\"age\":21}";
        TestUserPayload payload = gson.fromJson(json, TestUserPayload.class);

        assertNotNull(payload);
        assertEquals("u123", payload.getId());
        assertEquals("John Doe", payload.getName());
        assertEquals(21, payload.getAge());
    }

    @Test
    public void testJsonListParsing() {
        String jsonList = "[{\"id\":\"u1\",\"name\":\"Alice\",\"age\":20},{\"id\":\"u2\",\"name\":\"Bob\",\"age\":22}]";
        Type listType = new TypeToken<List<TestUserPayload>>() {}.getType();
        List<TestUserPayload> list = gson.fromJson(jsonList, listType);

        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("Alice", list.get(0).getName());
        assertEquals("Bob", list.get(1).getName());
    }

    @Test(expected = JsonSyntaxException.class)
    public void testMalformedJsonHandling() {
        String malformedJson = "{id: u123, name: broken... invalid json}";
        gson.fromJson(malformedJson, TestUserPayload.class);
    }

    @Test
    public void testHttpExceptionProperties() {
        HttpNetworkManager.HttpException ex = new HttpNetworkManager.HttpException(404, "Not Found", "{\"error\":\"Resource not found\"}");
        assertEquals(404, ex.getStatusCode());
        assertEquals("{\"error\":\"Resource not found\"}", ex.getResponseBody());
        assertTrue(ex.getMessage().contains("404"));
    }

    @Test
    public void testJsonSerialization() {
        TestUserPayload payload = new TestUserPayload("usr_009", "Sarah Connor", 28);
        String json = gson.toJson(payload);

        assertNotNull(json);
        assertTrue(json.contains("usr_009"));
        assertTrue(json.contains("Sarah Connor"));
        assertTrue(json.contains("28"));
    }
}
