package com.example.officebooking.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvCellTest {
    @Test
    void quotesAndEscapes() {
        assertThat(AllocationService.cell("Иванов \"Иван\"; тест")).isEqualTo("\"Иванов \"\"Иван\"\"; тест\"");
    }

    @Test
    void neutralizesSpreadsheetFormulas() {
        assertThat(AllocationService.cell("=HYPERLINK(\"http://evil\")")).startsWith("\"'=");
        assertThat(AllocationService.cell("+1")).startsWith("\"'+");
        assertThat(AllocationService.cell("@cmd")).startsWith("\"'@");
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(AllocationService.cell(null)).isEmpty();
    }
}
