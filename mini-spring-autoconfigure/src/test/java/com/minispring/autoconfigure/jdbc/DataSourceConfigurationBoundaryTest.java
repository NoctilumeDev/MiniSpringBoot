package com.minispring.autoconfigure.jdbc;

import com.minispring.core.env.MapPropertySource;
import com.minispring.core.env.StandardEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceConfigurationBoundaryTest {

    @Test
    void rejectsPoolSizesOutsideTheHardBudgetBeforeOpeningAPool() {
        assertRejected("0");
        assertRejected(Integer.toString(DataSourceAutoConfiguration.MAX_POOL_SIZE + 1));
        assertRejected("not-a-number");
    }

    private void assertRejected(String configuredValue) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                DataSourceAutoConfiguration.PREFIX + ".max-pool-size", configuredValue)));
        DataSourceAutoConfiguration configuration = new DataSourceAutoConfiguration();
        configuration.setEnvironment(environment);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                configuration::dataSource);
        assertTrue(failure.getMessage().contains("max-pool-size"));
    }
}
