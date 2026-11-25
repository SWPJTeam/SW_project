package parking.frontend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputTest {
    @Test
    void validatesAndNormalizesPlateNumbers() {
        assertEquals("A12가3456", Input.plate("a12 가3456", false));
        assertEquals("", Input.plate(" ", true));
        assertThrows(IllegalArgumentException.class, () -> Input.plate(" ", false));
        assertThrows(IllegalArgumentException.class, () -> Input.plate("가-12", false));
    }

    @Test
    void validatesDatesAndMinutes() {
        assertThrows(IllegalArgumentException.class, () -> Input.date("2026-02-29"));
        assertThrows(IllegalArgumentException.class,
                () -> Input.checkRange(Input.date("2026-11-26"), Input.date("2026-11-25")));
        assertNull(Input.minutes(""));
        assertEquals(0, Input.minutes("0"));
        assertThrows(IllegalArgumentException.class, () -> Input.minutes("-1"));
    }
}
