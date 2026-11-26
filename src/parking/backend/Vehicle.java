package parking.backend;

enum VehicleType {
    CAR, TRUCK, BUS, EV
}

enum ZoneType {
    GENERAL, LARGE, EV_CHARGING
}

enum DiscountKind {
    NONE, PASS, EV
}

// 수업 때 다뤘던 Vehicle 뼈대
public abstract class Vehicle {
    private int weight;
    protected String plateNumber;
    protected VehicleType vehicleType;

    public Vehicle() {
        this.weight = 0;
    }

    public Vehicle(int w) {
        this.weight = w;
    }

    public Vehicle(String plateNumber, VehicleType vehicleType, int weight) {
        this.plateNumber = plateNumber;
        this.vehicleType = vehicleType;
        this.weight = weight;
    }

    public void goForward(int speed) {
        System.out.println("Going forward at the speed of " + speed + "km/h");
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int updateWeight) {
        this.weight = updateWeight;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    // 하위 클래스에서 구현할 메서드 (나중에 리팩토링할 때 중복 정리)
    public abstract ZoneType targetZone();
    public abstract long calcFee(long seconds);

    public static void main(String[] args) {
        Vehicle testVehicle = new PassengerCar("12가3456", 1000);
        testVehicle.goForward(50);
        System.out.println("Weight: " + testVehicle.getWeight());
    }
}

class PassengerCar extends Vehicle {
    PassengerCar(String plateNumber) {
        super(plateNumber, VehicleType.CAR, 1500);
    }

    PassengerCar(String plateNumber, int weight) {
        super(plateNumber, VehicleType.CAR, weight);
    }

    @Override
    public ZoneType targetZone() {
        return ZoneType.GENERAL;
    }

    @Override
    public long calcFee(long seconds) {
        long mins = (seconds + 59) / 60;
        if (mins <= 30) return 1000;
        long extraMins = mins - 30;
        long extraHours = (extraMins + 59) / 60;
        return 1000 + extraHours * 1000;
    }
}

class HeavyTruck extends Vehicle {
    HeavyTruck(String plateNumber) {
        super(plateNumber, VehicleType.TRUCK, 5000);
    }

    @Override
    public ZoneType targetZone() {
        return ZoneType.LARGE;
    }

    @Override
    public long calcFee(long seconds) {
        long mins = (seconds + 59) / 60;
        if (mins <= 30) return 1500;
        long extraMins = mins - 30;
        long extraHours = (extraMins + 59) / 60;
        return 1500 + extraHours * 1500;
    }
}

class Bus extends Vehicle {
    Bus(String plateNumber) {
        super(plateNumber, VehicleType.BUS, 7000);
    }

    @Override
    public ZoneType targetZone() {
        return ZoneType.LARGE;
    }

    @Override
    public long calcFee(long seconds) {
        // 임시로 HeavyTruck 계산식 복붙해둔 상태 (나중에 정리)
        long mins = (seconds + 59) / 60;
        if (mins <= 30) return 1500;
        long extraMins = mins - 30;
        long extraHours = (extraMins + 59) / 60;
        return 1500 + extraHours * 1500;
    }
}

class ElectricVehicle extends Vehicle {
    ElectricVehicle(String plateNumber) {
        super(plateNumber, VehicleType.EV, 1800);
    }

    @Override
    public ZoneType targetZone() {
        return ZoneType.EV_CHARGING;
    }

    @Override
    public long calcFee(long seconds) {
        long mins = (seconds + 59) / 60;
        if (mins <= 30) return 1000;
        long extraMins = mins - 30;
        long extraHours = (extraMins + 59) / 60;
        return 1000 + extraHours * 1000;
    }
}