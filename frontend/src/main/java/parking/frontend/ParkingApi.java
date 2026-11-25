package parking.frontend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static parking.frontend.ParkingData.*;

final class ParkingApi {
    private final URI baseUrl;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    ParkingApi(URI baseUrl) {
        if (!List.of("http", "https").contains(String.valueOf(baseUrl.getScheme()))
                || baseUrl.getHost() == null || baseUrl.getRawQuery() != null
                || baseUrl.getRawFragment() != null || baseUrl.getRawUserInfo() != null
                || !(baseUrl.getPath().isEmpty() || baseUrl.getPath().equals("/"))) {
            throw new IllegalArgumentException("서버 주소는 http://localhost:8080 형식으로 입력하세요.");
        }
        this.baseUrl = baseUrl;
    }

    Session enter(String plate, VehicleType type) throws IOException, InterruptedException {
        return exchange("/api/parking/entries", new EntryRequest(plate, type), 201, type(Session.class));
    }

    Settlement exit(String plate) throws IOException, InterruptedException {
        return exchange("/api/parking/exits", new ExitRequest(plate), 200, type(Settlement.class));
    }

    Settlement receipt(String id) throws IOException, InterruptedException {
        return exchange("/api/parking/sessions/" + encode(id) + "/receipt", null, 200, type(Settlement.class));
    }

    Pass registerPass(String plate, LocalDate from, LocalDate through) throws IOException, InterruptedException {
        return exchange("/api/passes", new PassRequest(plate, from.toString(), through.toString()), 201, type(Pass.class));
    }

    List<Zone> zones() throws IOException, InterruptedException {
        return exchange("/api/admin/zones", null, 200, listOf(Zone.class));
    }

    List<Session> active(String plateContains, Integer minMinutes) throws IOException, InterruptedException {
        return exchange("/api/admin/active-sessions" + query(Map.of(
                "plateContains", plateContains, "minMinutes", minMinutes == null ? "" : minMinutes.toString())),
                null, 200, listOf(Session.class));
    }

    History history(LocalDate from, LocalDate to, String plate) throws IOException, InterruptedException {
        return exchange("/api/admin/history" + query(Map.of(
                "from", from.toString(), "to", to.toString(), "plateNumber", plate)),
                null, 200, type(History.class));
    }

    private JavaType type(Class<?> valueType) {
        return json.constructType(valueType);
    }

    private JavaType listOf(Class<?> valueType) {
        return json.getTypeFactory().constructCollectionType(List.class, valueType);
    }

    private <T> T exchange(String path, Object body, int expectedStatus, JavaType type)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUrl.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
        if (body != null) {
            request.header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        }
        HttpResponse<String> response = client.send(request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != expectedStatus) {
            throw responseError(response);
        }
        try {
            T result = json.readValue(response.body(), type);
            if (result == null) {
                throw new ApiException("서버가 빈 응답을 보냈습니다.");
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new ApiException("서버 응답이 명세서의 JSON 형식과 다릅니다.");
        }
    }

    private ApiException responseError(HttpResponse<String> response) {
        try {
            ErrorBody error = json.readValue(response.body(), ErrorBody.class);
            if (error != null && error.code() != null && error.message() != null
                    && !error.message().isBlank()) {
                return new ApiException(error.message() + " (" + error.code() + ")");
            }
        } catch (JsonProcessingException ignored) {
            // Use the HTTP status when the server returns a non-JSON error page.
        }
        return new ApiException("서버 응답 오류 (HTTP " + response.statusCode() + ")");
    }

    private static String query(Map<String, String> parameters) {
        List<String> parts = new ArrayList<>();
        parameters.forEach((key, value) -> {
            if (!value.isEmpty()) {
                parts.add(key + "=" + encode(value));
            }
        });
        return parts.isEmpty() ? "" : "?" + String.join("&", parts);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static final class ApiException extends IOException {
        ApiException(String message) {
            super(message);
        }
    }
}
