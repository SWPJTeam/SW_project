package parking.frontend;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static parking.frontend.ParkingData.*;

final class PrototypeParkingApi {
    private static final Pattern ESCAPED_PLATE_TEXT = Pattern.compile("(?:%[0-9a-fA-F]{2})+");
    private final ParkingApi http;

    PrototypeParkingApi(ParkingApi http) {
        this.http = http;
    }

    Ticket enter(String plate, VehicleType type) throws IOException, InterruptedException {
        try {
            return http.prototypeRequest("/api/checkin", Map.of("plate", plate, "type", type.name()),
                    true, Ticket.class);
        } catch (ParkingApi.ApiException e) {
            throw new ParkingApi.ApiException(displayText(e.getMessage()));
        }
    }

    Checkout exit(String plate) throws IOException, InterruptedException {
        // The prototype accepts a caller-supplied discount but has no eligibility lookup.
        try {
            return http.prototypeRequest("/api/checkout", Map.of("plate", plate, "discount", "NONE"),
                    true, Checkout.class);
        } catch (ParkingApi.ApiException e) {
            throw new ParkingApi.ApiException(displayText(e.getMessage()));
        }
    }

    ZoneCounts zones() throws IOException, InterruptedException {
        return http.prototypeRequest("/api/zones", Map.of(), false, ZoneCounts.class);
    }

    static String displayText(String text) {
        // Older servers echo the raw percent-encoded plate. Decode only its escaped bytes for display.
        return ESCAPED_PLATE_TEXT.matcher(text).replaceAll(match -> Matcher.quoteReplacement(
                URLDecoder.decode(match.group(), StandardCharsets.UTF_8)));
    }

    record Ticket(@JsonProperty(required = true) String plate, VehicleType type, ZoneType zone, String entryTime) {}

    record Checkout(@JsonProperty(required = true) String plate, VehicleType type, ZoneType zone,
                    String discount, @JsonProperty(required = true) long seconds,
                    @JsonProperty(required = true) long fee) {}

    record ZoneCounts(@JsonProperty(value = "GENERAL", required = true) int general,
                      @JsonProperty(value = "LARGE", required = true) int large,
                      @JsonProperty(value = "EV_CHARGING", required = true) int evCharging,
                      @JsonProperty(required = true) int activeCount) {}
}
