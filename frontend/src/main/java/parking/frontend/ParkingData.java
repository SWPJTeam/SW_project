package parking.frontend;

import java.util.List;

final class ParkingData {
    private ParkingData() {}

    enum VehicleType {
        CAR("일반 승용차"), TRUCK("대형 화물차"), BUS("버스"), EV("전기차");

        private final String label;

        VehicleType(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum ZoneType {
        GENERAL("일반 차량"), LARGE("대형 화물차·버스"), EV_CHARGING("전기차 충전");

        private final String label;

        ZoneType(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    record EntryRequest(String plateNumber, VehicleType vehicleType) {}
    record ExitRequest(String plateNumber) {}
    record PassRequest(String plateNumber, String validFrom, String validThrough) {}
    record Session(String sessionId, String plateNumber, VehicleType vehicleType,
                   String spotId, String enteredAt, String exitedAt) {}
    record Pass(String passId, String plateNumber, String validFrom, String validThrough) {}
    record Receipt(String sessionId, long durationMinutes, long grossFeeWon,
                   long discountWon, long totalFeeWon, String discountKind) {}
    record Settlement(Session session, Receipt receipt) {}
    record Zone(ZoneType zoneType, int capacity, int occupied, int available, List<Spot> spots) {}
    record Spot(String spotId, boolean occupied) {}
    record DailyTotal(String date, int exitCount, long totalFeeWon) {}
    record History(List<Session> sessions, List<DailyTotal> dailyTotals) {}
    record ErrorBody(String code, String message) {}
}
