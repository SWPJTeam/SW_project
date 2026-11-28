package parking.frontend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static parking.frontend.ParkingData.*;

class ParkingApiTest {
    private static final String ID = "29213cb1-b25a-4906-b7c9-c81836a3dd12";
    private static final String ACTIVE = """
            {"sessionId":"%s","plateNumber":"12가3456","vehicleType":"EV",
             "spotId":"E01","enteredAt":"2026-11-25T09:00:00+09:00","exitedAt":null}
            """.formatted(ID);
    private static final String COMPLETE = ACTIVE.replace("null", "\"2026-11-25T10:00:00+09:00\"");
    private static final String SETTLEMENT = """
            {"session":%s,"receipt":{"sessionId":"%s","durationMinutes":60,"grossFeeWon":4000,
             "discountWon":2000,"totalFeeWon":2000,"discountKind":"EV"}}
            """.formatted(COMPLETE, ID);
    private static final LocalDate START = LocalDate.of(2026, 11, 25);
    private static final LocalDate END = START.plusDays(7);

    private final ObjectMapper json = new ObjectMapper();
    private final LinkedBlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private volatile Response response;
    private HttpServer server;
    private ParkingApi api;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI(),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                        exchange.getRequestHeaders().getFirst("Content-Type")));
                Response configured = response;
                byte[] bytes = configured.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(configured.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        api = new ParkingApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void entrySendsCanonicalVehicleCodeAndReadsAssignedSpot() throws Exception {
        response = new Response(201, ACTIVE);
        Session session = api.enter("12가3456", VehicleType.EV);
        assertEquals("E01", session.spotId());
        assertNull(session.exitedAt());
        Request request = request("POST", "/api/parking/entries");
        assertTrue(request.contentType().startsWith("application/json"));
        assertEquals(json.readTree("""
                {"plateNumber":"12가3456","vehicleType":"EV"}
                """), json.readTree(request.body()));
    }

    @Test
    void exitAndReceiptReadSameSettlementShape() throws Exception {
        response = new Response(200, SETTLEMENT);
        assertEquals(2000, api.exit("12가3456").receipt().totalFeeWon());
        assertEquals("12가3456", json.readTree(request("POST", "/api/parking/exits").body())
                .get("plateNumber").asText());
        assertEquals(ID, api.receipt(ID).session().sessionId());
        request("GET", "/api/parking/sessions/" + ID + "/receipt");
    }

    @Test
    void passUsesInclusiveDateStrings() throws Exception {
        response = new Response(201, """
                {"passId":"%s","plateNumber":"12가3456","validFrom":"%s","validThrough":"%s"}
                """.formatted(ID, START, END));
        assertEquals(ID, api.registerPass("12가3456", START, END).passId());
        assertEquals(END.toString(), json.readTree(request("POST", "/api/passes").body())
                .get("validThrough").asText());
    }

    @Test
    void zoneSummaryDecodesSpotOccupancy() throws Exception {
        response = new Response(200, """
                [{"zoneType":"EV_CHARGING","capacity":1,"occupied":1,"available":0,
                  "spots":[{"spotId":"E01","occupied":true}]}]
                """);
        assertTrue(api.zones().getFirst().spots().getFirst().occupied());
        request("GET", "/api/admin/zones");
    }

    @Test
    void filtersEncodeHangulAndOmitUnspecifiedParameters() throws Exception {
        response = new Response(200, "[" + ACTIVE + "]");
        assertEquals(1, api.active("12가", 30).size());
        assertEquals(Map.of("plateContains", "12가", "minMinutes", "30"),
                query(request("GET", "/api/admin/active-sessions").uri()));
        api.active("", null);
        assertNull(request("GET", "/api/admin/active-sessions").uri().getRawQuery());
    }

    @Test
    void historyReadsSessionsAndTotals() throws Exception {
        response = new Response(200, """
                {"sessions":[%s],"dailyTotals":[{"date":"%s","exitCount":1,"totalFeeWon":2000}]}
                """.formatted(COMPLETE, START));
        History history = api.history(START, END, "");
        assertNotNull(history.sessions().getFirst().exitedAt());
        assertEquals(2000, history.dailyTotals().getFirst().totalFeeWon());
        assertEquals(Map.of("from", START.toString(), "to", END.toString()),
                query(request("GET", "/api/admin/history").uri()));
    }

    @Test
    void rejectionKeepsBackendCodeAndMalformedSuccessIsReported() {
        response = new Response(409, """
                {"code":"ZONE_FULL","message":"해당 구역에 빈 주차 면이 없습니다."}
                """);
        assertTrue(assertThrows(ParkingApi.ApiException.class, () -> api.enter("12가3456", VehicleType.EV))
                .getMessage().contains("ZONE_FULL"));
        response = new Response(200, "{bad JSON}");
        assertTrue(assertThrows(ParkingApi.ApiException.class, api::zones).getMessage().contains("JSON"));
    }

    @Test
    void currentBackendUsesQueryParametersAndReturnsZoneCountsAndPlainErrors() throws Exception {
        PrototypeParkingApi prototype = new PrototypeParkingApi(api);
        response = new Response(200, """
                {"plate":"12%EA%B0%803456","type":"EV","zone":"EV_CHARGING","entryTime":"2026-11-28T00:00:00Z"}
                """);
        var ticket = prototype.enter("12가3456", VehicleType.EV);
        assertEquals("12가3456", PrototypeParkingApi.displayText(ticket.plate()));
        assertEquals(ZoneType.EV_CHARGING, ticket.zone());
        assertEquals(Map.of("plate", "12가3456", "type", "EV"),
                query(request("POST", "/api/checkin").uri()));

        response = new Response(400, """
                {"error":"이미 입차 중인 차량입니다: 12%EA%B0%803456"}
                """);
        assertEquals("이미 입차 중인 차량입니다: 12가3456",
                assertThrows(ParkingApi.ApiException.class,
                        () -> prototype.enter("12가3456", VehicleType.EV)).getMessage());
        request("POST", "/api/checkin");

        response = new Response(200, """
                {"GENERAL":0,"LARGE":0,"EV_CHARGING":1,"activeCount":1}
                """);
        assertEquals(1, prototype.zones().evCharging());
        request("GET", "/api/zones");

        response = new Response(200, """
                {"plate":"12%EA%B0%803456","type":"EV","zone":"EV_CHARGING","discount":"NONE","seconds":61,"fee":1000}
                """);
        assertEquals(1000, prototype.exit("12가3456").fee());
        assertEquals(Map.of("plate", "12가3456", "discount", "NONE"),
                query(request("POST", "/api/checkout").uri()));
    }

    private Request request(String method, String path) {
        Request request = requests.remove();
        assertEquals(method, request.method());
        assertEquals(path, request.uri().getPath());
        return request;
    }

    private static Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(value -> value.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    record Request(String method, URI uri, String body, String contentType) {}
    record Response(int status, String body) {}
}
