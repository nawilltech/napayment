package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** GET /api/v1/banks?term= backs the searchable bank picker: name fragment or code prefix, A-Z ignoring case. */
class BankSearchIT extends AbstractIntegrationTest {

    @Test
    void termMatchesNameFragmentOrCodePrefixSortedCaseInsensitively() {
        String marker = "Qx" + UUID.randomUUID().toString().substring(0, 6);
        String code = "9" + (System.nanoTime() % 100_000_000L);
        insertBank(marker + " ZULU BANK", "8" + code);
        insertBank(marker + " alpha bank", code);
        insertBank(marker + " Mid Bank", "7" + code);
        String token = signupAndGetToken("Bank", "Searcher", "SecurePass123!");

        List<Map<String, Object>> byName = getPagedContent("/api/v1/banks?term=" + marker.toLowerCase(), authHeaders(token));
        List<Map<String, Object>> byCode = getPagedContent("/api/v1/banks?term=8" + code, authHeaders(token));

        assertThat(byName).extracting(bank -> bank.get("name"))
                .containsExactly(marker + " alpha bank", marker + " Mid Bank", marker + " ZULU BANK");
        // A code prefix, not a substring: "8<code>" must not also match "7<code>" or "<code>".
        assertThat(byCode).extracting(bank -> bank.get("name")).containsExactly(marker + " ZULU BANK");
    }

    private void insertBank(String name, String code) {
        jdbcTemplate.update("INSERT INTO banks (id, name, code) VALUES (?, ?, ?)", UUID.randomUUID(), name, code);
    }
}
