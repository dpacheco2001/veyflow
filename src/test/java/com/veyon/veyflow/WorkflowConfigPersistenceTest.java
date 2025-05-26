package com.veyon.veyflow;

import com.veyon.veyflow.config.RedisWorkflowConfigRepository;
import com.veyon.veyflow.config.WorkflowConfig;
import com.veyon.veyflow.state.PersistenceMode;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class WorkflowConfigPersistenceTest {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConfigPersistenceTest.class);
    private static final String REDIS_URL = "redis://localhost:6379";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";

    private RedisClient testRedisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private RedisWorkflowConfigRepository redisWorkflowConfigRepository;

    private String uniqueTestTenantId;

    @BeforeEach
    void setUp() {
        log.info(ANSI_BLUE + "--- Setting up WorkflowConfigPersistenceTest --- " + ANSI_RESET);
        try {
            testRedisClient = RedisClient.create(REDIS_URL);
            redisConnection = testRedisClient.connect();
            redisWorkflowConfigRepository = new RedisWorkflowConfigRepository(redisConnection);
            log.info(ANSI_CYAN + "RedisWorkflowConfigRepository initialized." + ANSI_RESET);

            uniqueTestTenantId = "wc-test-tenant-" + UUID.randomUUID().toString();
            log.info(ANSI_CYAN + "Generated Tenant ID: {}" + ANSI_RESET, uniqueTestTenantId);
        } catch (Exception e) {
            log.error(ANSI_YELLOW + "Failed to connect to Redis or setup test: {}" + ANSI_RESET, e.getMessage(), e);
            fail("Test setup failed due to Redis connection issues.", e);
        }
    }

    @AfterEach
    void tearDown() {
        log.info(ANSI_BLUE + "--- Tearing down WorkflowConfigPersistenceTest --- " + ANSI_RESET);
        if (redisConnection != null) {
            try {
                // Clean up the test entry from Redis
                String redisKey = "veyflow:workflow_config:" + uniqueTestTenantId;
                log.info(ANSI_CYAN + "Attempting to clean up Redis key: {}" + ANSI_RESET, redisKey);
                Long deletedCount = redisConnection.sync().del(redisKey);
                if (deletedCount != null && deletedCount > 0) {
                    log.info(ANSI_GREEN + "Successfully cleaned up Redis key: {}" + ANSI_RESET, redisKey);
                } else {
                    log.info(ANSI_YELLOW + "Redis key {} not found or not deleted (already cleaned up or never created?)." + ANSI_RESET, redisKey);
                }
                redisConnection.close();
                log.info(ANSI_CYAN + "Redis connection closed." + ANSI_RESET);
            } catch (Exception e) {
                log.warn(ANSI_YELLOW + "Could not clean up Redis key or close connection for tenant {}: {}" + ANSI_RESET, 
                        uniqueTestTenantId, e.getMessage(), e);
            }
        }
        if (testRedisClient != null) {
            testRedisClient.shutdown();
            log.info(ANSI_CYAN + "Redis client shut down." + ANSI_RESET);
        }
        log.info(ANSI_BLUE + "--- Teardown complete --- " + ANSI_RESET);
    }

    @Test
    void testWorkflowConfigRedisPersistence() {
        log.info(ANSI_BLUE + "--- Starting WorkflowConfig Redis Persistence Test --- " + ANSI_RESET);

        // 1. Create and configure WorkflowConfig
        WorkflowConfig originalConfig = new WorkflowConfig(uniqueTestTenantId, PersistenceMode.REDIS);
        originalConfig.setRepository(redisWorkflowConfigRepository);

        String weatherServiceName = WeatherToolService.class.getName();
        List<String> weatherMethods = Arrays.asList("getWeather", "getForecast");
        originalConfig.activateToolMethods(weatherServiceName, weatherMethods);
        log.info(ANSI_CYAN + "Original WorkflowConfig created and tools activated. Tenant: {}, isDirty: {}" + ANSI_RESET, 
                 originalConfig.getTenantId(), originalConfig.isDirty());

        // 2. Save WorkflowConfig
        assertTrue(originalConfig.isDirty(), "Config should be dirty after activating tools.");
        originalConfig.save();
        log.info(ANSI_CYAN + "WorkflowConfig.save() called. isDirty after save: {}" + ANSI_RESET, originalConfig.isDirty());
        assertFalse(originalConfig.isDirty(), "Config should not be dirty after successful save.");

        // 3. Retrieve WorkflowConfig from Redis
        Optional<WorkflowConfig> retrievedConfigOptional = redisWorkflowConfigRepository.findById(uniqueTestTenantId);
        assertTrue(retrievedConfigOptional.isPresent(), "WorkflowConfig should be found in Redis.");
        WorkflowConfig retrievedConfig = retrievedConfigOptional.get();
        assertNotNull(retrievedConfig, "Retrieved WorkflowConfig from Redis should not be null.");
        log.info(ANSI_CYAN + "WorkflowConfig successfully retrieved from Redis." + ANSI_RESET);

        // 4. Verify properties of the retrieved WorkflowConfig
        assertEquals(uniqueTestTenantId, retrievedConfig.getTenantId(), "Tenant ID should match.");
        assertEquals(PersistenceMode.REDIS, retrievedConfig.getPersistenceMode(), "PersistenceMode should be REDIS.");

        Map<String, List<String>> retrievedTools = retrievedConfig.getConfiguredToolServices();
        assertNotNull(retrievedTools, "Configured tools in retrieved config should not be null.");
        assertTrue(retrievedTools.containsKey(weatherServiceName), "Retrieved config should have WeatherToolService activated.");
        assertEquals(weatherMethods, retrievedTools.get(weatherServiceName), "Activated methods for WeatherToolService should match.");
        log.info(ANSI_GREEN + "Properties of retrieved WorkflowConfig match original." + ANSI_RESET);

        // 5. Test saving when not dirty
        retrievedConfig.setRepository(redisWorkflowConfigRepository); // Set repo for the retrieved instance
        assertFalse(retrievedConfig.isDirty(), "Retrieved config should initially not be dirty.");
        retrievedConfig.save(); // Should not actually call repository.save() if not dirty
        // (Verification of this would require mocking the repository or checking logs, for now, we assume correct behavior based on isDirty flag)
        log.info(ANSI_CYAN + "Called save() on non-dirty retrieved config. isDirty: {}" + ANSI_RESET, retrievedConfig.isDirty());

        // 6. Test deletion (implicitly tested by tearDown, but can be explicit too)
        boolean deleted = redisWorkflowConfigRepository.delete(uniqueTestTenantId);
        assertTrue(deleted, "WorkflowConfig should be successfully deleted from Redis.");
        Optional<WorkflowConfig> deletedConfigOptional = redisWorkflowConfigRepository.findById(uniqueTestTenantId);
        assertFalse(deletedConfigOptional.isPresent(), "WorkflowConfig should not be found after deletion.");
        log.info(ANSI_GREEN + "WorkflowConfig successfully deleted from Redis as part of the test." + ANSI_RESET);

        log.info(ANSI_GREEN + "WorkflowConfig Redis persistence test successful!" + ANSI_RESET);
    }
}
