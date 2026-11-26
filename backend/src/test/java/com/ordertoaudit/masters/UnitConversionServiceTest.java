package com.ordertoaudit.masters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ordertoaudit.AbstractIntegrationTest;
import com.ordertoaudit.common.error.BadRequestException;
import com.ordertoaudit.common.error.UnitMismatchException;
import com.ordertoaudit.masters.service.UnitConversionService;

/** R3.1 to R3.3. Uses the seeded units and items. */
class UnitConversionServiceTest extends AbstractIntegrationTest {

    @Autowired
    UnitConversionService conversion;

    @Autowired
    JdbcTemplate jdbc;

    private long itemId(String code) {
        return jdbc.queryForObject("SELECT id FROM item WHERE code = ?", Long.class, code);
    }

    private String toBase(String itemCode, String qty, String uom) {
        return conversion.toBase(itemId(itemCode), new BigDecimal(qty), uom).toPlainString();
    }

    @Test
    void baseUnitIsUnchanged() {
        assertThat(toBase("FLOUR", "60", "kg")).isEqualTo("60.0000");
        assertThat(toBase("OIL", "10.5", "L")).isEqualTo("10.5000");
        assertThat(toBase("POUCH", "194", "pc")).isEqualTo("194.0000");
        assertThat(toBase("MURUKKU200", "188", "pkt")).isEqualTo("188.0000");
    }

    @Test
    void massAndVolumeConvertByDimension() {
        assertThat(toBase("FLOUR", "60000", "g")).isEqualTo("60.0000");
        assertThat(toBase("SPICE", "1200", "g")).isEqualTo("1.2000");
        assertThat(toBase("OIL", "500", "ml")).isEqualTo("0.5000");
    }

    @Test
    void cartonConvertsThroughTheItemSpecificConversion() {
        // 1 carton of Murukku 200 g = 20 packets
        assertThat(toBase("MURUKKU200", "5", "ctn")).isEqualTo("100.0000");
    }

    @Test
    void unitOfAnotherDimensionIsRejected() {
        assertThatThrownBy(() -> toBase("FLOUR", "10", "L")).isInstanceOf(UnitMismatchException.class);
        assertThatThrownBy(() -> toBase("OIL", "10", "kg")).isInstanceOf(UnitMismatchException.class);
        assertThatThrownBy(() -> toBase("FLOUR", "10", "ml")).isInstanceOf(UnitMismatchException.class);
    }

    @Test
    void countItemAcceptsOnlyItsBaseUnitOrAConfiguredUnit() {
        // A pouch is counted in pieces. "pkt" is also a COUNT unit, but it is not configured for the pouch.
        assertThatThrownBy(() -> toBase("POUCH", "10", "pkt"))
                .isInstanceOf(UnitMismatchException.class)
                .hasMessageContaining("POUCH");
        // The finished good is counted in packets: pieces are not allowed for it either
        assertThatThrownBy(() -> toBase("MURUKKU200", "10", "pc")).isInstanceOf(UnitMismatchException.class);
        // Carton has a conversion only for the finished good, not for pouches or cartons-as-items
        assertThatThrownBy(() -> toBase("POUCH", "1", "ctn")).isInstanceOf(UnitMismatchException.class);
        assertThatThrownBy(() -> toBase("CARTON", "1", "pkt")).isInstanceOf(UnitMismatchException.class);
    }

    @Test
    void cartonIsRejectedForAMassItem() {
        assertThatThrownBy(() -> toBase("FLOUR", "1", "ctn")).isInstanceOf(UnitMismatchException.class);
    }

    @Test
    void unknownUnitOrItemIsRejected() {
        assertThatThrownBy(() -> toBase("FLOUR", "1", "bag"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bag");
        assertThatThrownBy(() -> conversion.toBase(999_999L, BigDecimal.ONE, "kg"))
                .isInstanceOf(BadRequestException.class);
    }
}
