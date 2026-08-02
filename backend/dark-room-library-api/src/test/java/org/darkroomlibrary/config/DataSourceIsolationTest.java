package org.darkroomlibrary.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ"
})
@ActiveProfiles("test")
class DataSourceIsolationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void usesRepeatableReadAsTheExplicitConnectionIsolation() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(
                    Connection.TRANSACTION_REPEATABLE_READ,
                    connection.getTransactionIsolation());
        }
    }
}
