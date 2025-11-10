package com.miimetiq.keycloak.spi;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for KafkaAdminClientFactory.
 * Requires Kafka to be running on localhost:9092 for integration testing.
 */
public class KafkaAdminClientFactoryTest {

    @AfterAll
    public static void cleanup() {
        // Close the AdminClient after all tests
        KafkaAdminClientFactory.close();
    }

    @Test
    public void testGetAdminClient_PLAINTEXT() throws Exception {
        // Set environment variables for PLAINTEXT connection
        // Note: In real tests, these would be set externally or via test configuration
        // For this test, we rely on defaults: localhost:9092, PLAINTEXT

        AdminClient adminClient = KafkaAdminClientFactory.getAdminClient();
        assertNotNull(adminClient, "AdminClient should not be null");

        // Verify connection by listing topics
        ListTopicsResult topics = adminClient.listTopics();
        Set<String> topicNames = topics.names().get(10, TimeUnit.SECONDS);

        // Just verify we can connect - don't assert on specific topics
        assertNotNull(topicNames, "Topic names should not be null");
        System.out.println("Successfully connected to Kafka. Topics: " + topicNames);
    }

    @Test
    public void testGetAdminClient_Singleton() {
        // Verify that multiple calls return the same instance
        AdminClient client1 = KafkaAdminClientFactory.getAdminClient();
        AdminClient client2 = KafkaAdminClientFactory.getAdminClient();

        assertSame(client1, client2, "AdminClient should be a singleton");
    }

    @Test
    public void testClose() {
        // Get a client instance
        AdminClient client = KafkaAdminClientFactory.getAdminClient();
        assertNotNull(client, "AdminClient should not be null before close");

        // Close it
        KafkaAdminClientFactory.close();

        // Get a new instance - should create a new client
        AdminClient newClient = KafkaAdminClientFactory.getAdminClient();
        assertNotNull(newClient, "AdminClient should not be null after close and reinitialization");
        assertNotSame(client, newClient, "New AdminClient should be a different instance after close");
    }
}
