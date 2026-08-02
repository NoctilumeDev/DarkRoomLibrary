package org.darkroomlibrary.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionConfigurationValidatorTest {

    @Test
    void acceptsIndependentProductionSecrets() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "Db-9cE3wB4xA7qL",
                "jwt-6f7f5ad15e8b4bb3b917c68e6b2f329d",
                true,
                "Rabbit-7nP4rX8k",
                "https://alerts.example.test/dark-room-library"
        );

        assertDoesNotThrow(validator::afterPropertiesSet);
    }

    @Test
    void rejectsKnownComposeDatabasePassword() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "DarkRoomMySQL@20606",
                "jwt-6f7f5ad15e8b4bb3b917c68e6b2f329d",
                false,
                "guest",
                ""
        );

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void rejectsKnownOrShortJwtSecret() {
        ProductionConfigurationValidator knownDefault = new ProductionConfigurationValidator(
                "Db-9cE3wB4xA7qL",
                "dark-room-library-compose-jwt-secret-change-before-public-deployment",
                false,
                "guest",
                ""
        );
        ProductionConfigurationValidator shortSecret = new ProductionConfigurationValidator(
                "Db-9cE3wB4xA7qL",
                "too-short",
                false,
                "guest",
                ""
        );

        assertThrows(IllegalStateException.class, knownDefault::afterPropertiesSet);
        assertThrows(IllegalStateException.class, shortSecret::afterPropertiesSet);
    }

    @Test
    void rejectsKnownRabbitPasswordWhenRabbitIsEnabled() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "Db-9cE3wB4xA7qL",
                "jwt-6f7f5ad15e8b4bb3b917c68e6b2f329d",
                true,
                "guest",
                "https://alerts.example.test/dark-room-library"
        );

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void requiresAlertWebhookWhenRabbitIsEnabled() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "Db-9cE3wB4xA7qL",
                "jwt-6f7f5ad15e8b4bb3b917c68e6b2f329d",
                true,
                "Rabbit-7nP4rX8k",
                ""
        );

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }
}
