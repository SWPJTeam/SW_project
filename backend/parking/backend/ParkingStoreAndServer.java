package parking.backend;

//서버 관련
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
//서버 관련 끝

import java.io.IOException; // 네트워크 통신 중에 오류 뜰 때 처리하는거
import java.io.OutputStream; // 데이터 전송
import java.net.InetSocketAddress; // 서버 IP포트 주소 지정을 위해 사용
import java.nio.charset.StandardCharsets; // UTF-8로 해야 문자 안깨짐
import java.time.Instant; //차량 입차 시간이랑 출차 시간 기록하고 계산하기 위해 사용
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // 주차중인 차량 보관 조회

public class ParkingStoreAndServer {

    public static class Ticket {
        //입차 시간이랑 요금 때문에 final 사용 
    	public final String plateNumber; // 번호판
        public final VehicleType vehicleType; // CAR, TRUCK, BUS 이런거 구분
        public final ZoneType zoneType; // 주차 구역 종류
        public final Instant entryTime; // 입차 시간 저장

        public Ticket(String plateNumber, VehicleType vehicleType, ZoneType zoneType, Instant entryTime) {
            this.plateNumber = plateNumber;
            this.vehicleType = vehicleType;
            this.zoneType = zoneType;
            this.entryTime = entryTime;
        }
    }

    // 영수증
    public static class Receipt {
        public final String plateNumber;
        public final VehicleType vehicleType; // 차종에 따른 할인
        public final ZoneType zoneType;
        public final DiscountKind discountKind;
        public final long seconds;
        public final long fee;
        public final Instant exitTime;

        // check out 처리되면 history 목록에 보관
        public Receipt(String plateNumber, VehicleType vehicleType, ZoneType zoneType,
                       DiscountKind discountKind, long seconds, long fee, Instant exitTime) {
            this.plateNumber = plateNumber;
            this.vehicleType = vehicleType;
            this.zoneType = zoneType;
            this.discountKind = discountKind;
            this.seconds = seconds;
            this.fee = fee;
            this.exitTime = exitTime;
        }
    }

    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>(); // (번호판을 키로 티켓)을 값으로
    private final List<Receipt> history = new ArrayList<>(); // 출차 차량 목록 나중에 조회할 때 사용하기 위해서

    // activeTickets에 같은 번호판을 가진 차량이 있으면 IllegalStateException을 던져서 막는다
    public Ticket checkIn(String plateNumber, VehicleType type) { //  
        if (activeTickets.containsKey(plateNumber)) {
            throw new IllegalStateException("이미 입차 중인 차량입니다: " + plateNumber); // 던지기
        }
        Vehicle vehicle = createVehicle(plateNumber, type);
        Ticket ticket = new Ticket(plateNumber, type, vehicle.targetZone(), Instant.now());
        activeTickets.put(plateNumber, ticket);
        return ticket;
    }

    // 출차경우임: 
    public Receipt checkOut(String plateNumber, DiscountKind discountKind) {
        Ticket ticket = activeTickets.remove(plateNumber); // 차량 꺼내기
        //예외처리
        if (ticket == null) {
            throw new IllegalArgumentException("입차 기록이 없는 차량입니다: " + plateNumber);
        }
        Instant now = Instant.now(); // 시간 계산
        // 차량 요금 계산 파트  / 초단위
        long elapsedSeconds = Math.max(1L, now.getEpochSecond() - ticket.entryTime.getEpochSecond());

        Vehicle vehicle = createVehicle(plateNumber, ticket.vehicleType);
        long baseFee = vehicle.calcFee(elapsedSeconds); //  기본 요금 계싼
        long finalFee = applyDiscount(baseFee, discountKind); // 할인

        // history에 추가 
        Receipt receipt = new Receipt(
                plateNumber, ticket.vehicleType, ticket.zoneType,
                discountKind, elapsedSeconds, finalFee, now
        );
        history.add(receipt);
        return receipt;
    }

    // 구역별 주차 현황 집계 부분
    // activeTickets를 돌면서 zoneType에 해당하는 카운트를 하나씩 올림
    public Map<String, Integer> zoneSummary() {
        Map<String, Integer> counts = new HashMap<>();
        for (ZoneType zone : ZoneType.values()) {
            counts.put(zone.name(), 0);
        }
        for (Ticket ticket : activeTickets.values()) {
            counts.put(ticket.zoneType.name(), counts.get(ticket.zoneType.name()) + 1);
        }
        return counts;
    }

    // 복사본 만들어서 외부 접근 막기(코드 수정 막기) ;; 굳이 할 필요는 없는 기능?
    public List<Receipt> getHistory() {
        return new ArrayList<>(history);
    }

    // 할인 적용
    private long applyDiscount(long baseFee, DiscountKind discountKind) {
        return switch (discountKind) {
            case NONE -> baseFee; // 할인 없이 baseFee 그대로 반환
            case PASS -> 0L; // 정기권으로 무료 처리
            case EV -> Math.round(baseFee * 0.5); // 전기차 할인으로 절반 가격 할인 (추가 논의 필요)
        };
    }

    // 차종마다 달리지는 배정구역(targetZone())이나 요금 계산할 때 Vehicle 하위 클래스에 연결고리 역할
    private Vehicle createVehicle(String plateNumber, VehicleType type) {
        return switch (type) {
            case CAR -> new PassengerCar(plateNumber);
            case TRUCK -> new HeavyTruck(plateNumber);
            case BUS -> new Bus(plateNumber);
            case EV -> new ElectricVehicle(plateNumber);
        };
    }

    public void startServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/health", exchange -> sendJson(exchange, 200, "{\"status\":\"ok\"}"));
        server.createContext("/api/zones", exchange -> {
            Map<String, Integer> summary = zoneSummary();
            String json = String.format(
                    "{\"GENERAL\":%d,\"LARGE\":%d,\"EV_CHARGING\":%d,\"activeCount\":%d}",
                    summary.get("GENERAL"), summary.get("LARGE"), summary.get("EV_CHARGING"), activeTickets.size()
            );
            sendJson(exchange, 200, json);
        });
        server.createContext("/api/checkin", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String plate = params.getOrDefault("plate", "00가0000");
            VehicleType type = parseType(params.get("type"));
            try {
                Ticket t = checkIn(plate, type);
                String json = String.format(
                        "{\"plate\":\"%s\",\"type\":\"%s\",\"zone\":\"%s\",\"entryTime\":\"%s\"}",
                        t.plateNumber, t.vehicleType, t.zoneType, t.entryTime
                );
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });
        server.createContext("/api/checkout", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String plate = params.getOrDefault("plate", "00가0000");
            DiscountKind discount = parseDiscount(params.get("discount"));
            try {
                Receipt r = checkOut(plate, discount);
                String json = String.format(
                        "{\"plate\":\"%s\",\"type\":\"%s\",\"zone\":\"%s\",\"discount\":\"%s\",\"seconds\":%d,\"fee\":%d}",
                        r.plateNumber, r.vehicleType, r.zoneType, r.discountKind, r.seconds, r.fee
                );
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Backend server started at http://localhost:" + port);
    }

    private static VehicleType parseType(String raw) {
        if (raw == null) return VehicleType.CAR;
        return VehicleType.valueOf(raw.toUpperCase());
    }

    private static DiscountKind parseDiscount(String raw) {
        if (raw == null) return DiscountKind.NONE;
        return DiscountKind.valueOf(raw.toUpperCase());
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return map;
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        return map;
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // " 이거 섞이면 문자열 구조가 깨지는 경우 발생 -> 구글에 검색했는데 이런 경우가 생길 수 있다고 함
    // text == null 일때를 대비해서 빈 문자열 "" 돌려주기 때문에 NullPoint예외 회피 가능
    private static String escape(String text) {
        return text == null ? "" : text.replace("\"", "\\\"");
    }

    public static void main(String[] args) throws IOException {
        ParkingStoreAndServer backend = new ParkingStoreAndServer();
        backend.startServer(8080);
    }
}