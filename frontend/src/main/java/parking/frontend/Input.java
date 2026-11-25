package parking.frontend;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

final class Input {
    private static final Pattern PLATE = Pattern.compile("[가-힣A-Z0-9]{1,20}");
    private static final Pattern SPACES = Pattern.compile("\\s", Pattern.UNICODE_CHARACTER_CLASS);

    private Input() {}

    static String plate(String raw, boolean optional) {
        String value = SPACES.matcher(raw).replaceAll("").toUpperCase(Locale.ROOT);
        if (optional && value.isEmpty()) {
            return "";
        }
        if (!PLATE.matcher(value).matches()) {
            throw new IllegalArgumentException("차량 번호는 한글·영문·숫자 1~20자로 입력하세요.");
        }
        return value;
    }

    static LocalDate date(String raw) {
        try {
            if (raw.trim().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                return LocalDate.parse(raw.trim());
            }
        } catch (DateTimeParseException ignored) {
            // Show the same guidance for an invalid calendar date.
        }
        throw new IllegalArgumentException("날짜를 YYYY-MM-DD 형식으로 입력하세요.");
    }

    static void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    static Integer minutes(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Display the numeric input requirements below.
        }
        throw new IllegalArgumentException("최소 주차 시간은 0 이상의 정수로 입력하세요.");
    }
}
