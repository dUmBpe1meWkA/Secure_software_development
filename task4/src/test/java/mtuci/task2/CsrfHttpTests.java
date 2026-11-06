package mtuci.task2;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Goes through a real server, because MockMvc substitutes parts of the CSRF machinery. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsrfHttpTests extends PostgresTestSupport {
    private static final String BODY = "{\"name\":\"Новый\",\"email\":\"csrf@example.com\",\"password\":\"Str0ng!Value-1\"}";
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM app_users");
    }

    @Test
    void tokenFromEndpointProtectsWrites() throws Exception {
        var issued = http.send(request("/api/auth/csrf").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(issued.statusCode()).isEqualTo(200);
        JsonNode json = mapper.readTree(issued.body());
        assertThat(json.get("headerName").asString()).isEqualTo("X-XSRF-TOKEN");
        String setCookie = issued.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(setCookie).startsWith("XSRF-TOKEN=").contains("HttpOnly").contains("SameSite=Strict");
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));

        assertThat(register(cookie, null).statusCode()).isEqualTo(403);
        assertThat(register(cookie, "forged").statusCode()).isEqualTo(403);
        assertThat(register("XSRF-TOKEN=attacker", json.get("token").asString()).statusCode()).isEqualTo(403);
        String token = json.get("token").asString();
        var registered = register(cookie, token);
        assertThat(registered.statusCode()).isEqualTo(201);
        long userId = mapper.readTree(registered.body()).get("id").asLong();

        // The token stays valid across authenticated requests.
        String credentials = "Basic " + Base64.getEncoder().encodeToString("csrf@example.com:Str0ng!Value-1".getBytes(StandardCharsets.UTF_8));
        var me = http.send(request("/api/auth/me").header("Authorization", credentials).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.headers().firstValue("Set-Cookie")).isEmpty();
        var vehicle = request("/api/vehicles").header("Authorization", credentials).header("Cookie", cookie)
                .header("X-XSRF-TOKEN", token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"plateNumber\":\"CSRF01\",\"ownerId\":%d}".formatted(userId))).build();
        assertThat(http.send(vehicle, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);
    }

    private HttpResponse<String> register(String cookie, String token) throws Exception {
        var request = request("/api/auth/register").header("Content-Type", "application/json").header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.ofString(BODY));
        if (token != null) request.header("X-XSRF-TOKEN", token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }
}
