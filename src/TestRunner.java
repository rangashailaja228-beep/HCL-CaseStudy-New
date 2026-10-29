package src;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Automated test suite verifying the core functionality:
 * 1. JSON Parsing integrity
 * 2. Tokenization & synonym enrichment
 * 3. High confidence matching (NT-3 -> T-1303)
 * 4. Novel ticket gating (NT-4 -> score < 0.30 / NO MATCH)
 * 5. Latency & throughput under simulated load
 */
public class TestRunner {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("Running Solari Assistant Automated Test Suite...\n");

        runTest("testJsonParser", TestRunner::testJsonParser);
        runTest("testTextProcessor", TestRunner::testTextProcessor);
        runTest("testHighConfidenceMatch", TestRunner::testHighConfidenceMatch);
        runTest("testNovelTicketDetection", TestRunner::testNovelTicketDetection);
        runTest("testThroughputAndLatency", TestRunner::testThroughputAndLatency);

        System.out.printf("%n========================================%n");
        System.out.printf("Test Results: %d Passed, %d Failed%n", testsPassed, testsFailed);
        System.out.printf("========================================%n");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String name, Runnable test) {
        try {
            test.run();
            System.out.println("[PASS] " + name);
            testsPassed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] " + name + ": " + t.getMessage());
            t.printStackTrace(System.err);
            testsFailed++;
        }
    }

    @SuppressWarnings("unchecked")
    private static void testJsonParser() {
        String sampleJson = "{\"status\": \"ok\", \"count\": 2, \"items\": [\"a\", \"b\"], \"nested\": {\"valid\": true}}";
        Object parsed = JsonParser.parse(sampleJson);
        assertTrue(parsed instanceof Map<?, ?>, "Root must be Map");
        Map<String, Object> map = (Map<String, Object>) parsed;
        assertEquals("ok", map.get("status"), "Status field mismatch");
        assertEquals(2L, ((Number) map.get("count")).longValue(), "Count mismatch");
        assertTrue(map.get("items") instanceof List<?>, "Items must be list");
    }

    private static void testTextProcessor() {
        List<String> tokens = TextProcessor.tokenize("Customer was charged twice for payment");
        assertTrue(tokens.contains("charged"), "Should contain 'charged'");
        assertTrue(tokens.contains("twice"), "Should contain 'twice'");
        assertTrue(tokens.contains("duplicate"), "Synonym 'duplicate' should be expanded");
        assertTrue(tokens.contains("billing"), "Synonym 'billing' should be expanded");
        assertTrue(tokens.contains("charged_twice"), "Bi-gram 'charged_twice' should exist");
        assertFalse(tokens.contains("for"), "Stopword 'for' should be removed");
    }

    private static void testHighConfidenceMatch() {
        List<Ticket> historical = loadTickets("data/tickets.json", "tickets");
        SupportAssistant assistant = new SupportAssistant(historical);

        Ticket nt3 = new Ticket("NT-3", "billing", "Getting charged twice this month",
                "Customer's card statement shows two charges for what should be a single monthly subscription payment.",
                "", null, "now", "new");

        List<MatchResult> results = assistant.analyzeTicket(nt3, 3);
        assertTrue(!results.isEmpty(), "Results should not be empty");
        MatchResult top = results.get(0);
        assertEquals("T-1303", top.getHistoricalTicket().getTicketId(), "Top match for NT-3 must be T-1303");
        assertTrue(top.getConfidenceScore() >= 0.50, "Confidence score should be >= 0.50 (HIGH)");
        assertEquals("HIGH CONFIDENCE", top.getConfidenceLevel(), "Confidence level should be HIGH");
    }

    private static void testNovelTicketDetection() {
        List<Ticket> historical = loadTickets("data/tickets.json", "tickets");
        SupportAssistant assistant = new SupportAssistant(historical);

        Ticket nt4 = new Ticket("NT-4", "unknown", "Our data warehouse integration with Snowflake is failing",
                "Customer set up a new integration to sync data into their Snowflake warehouse and it's failing with an unclear connection error.",
                "", null, "now", "new");

        List<MatchResult> results = assistant.analyzeTicket(nt4, 3);
        assertTrue(!results.isEmpty(), "Results should not be empty");
        MatchResult top = results.get(0);
        assertTrue(top.getConfidenceScore() < SupportAssistant.MEDIUM_CONFIDENCE_THRESHOLD,
                "Snowflake novel ticket score (" + top.getConfidenceScore() + ") must be below medium threshold (" + SupportAssistant.MEDIUM_CONFIDENCE_THRESHOLD + ")");
        assertEquals("LOW CONFIDENCE (NO DIRECT MATCH)", top.getConfidenceLevel(), "Must flag as LOW CONFIDENCE / NO DIRECT MATCH");
    }

    private static void testThroughputAndLatency() {
        List<Ticket> historical = loadTickets("data/tickets.json", "tickets");
        SupportAssistant assistant = new SupportAssistant(historical);

        Ticket query = new Ticket("PERF-1", "general", "Dashboard widget not updating",
                "The bar chart refuses to refresh after date change", "", null, "now", "new");

        long start = System.currentTimeMillis();
        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            assistant.analyzeTicket(query, 3);
        }
        long duration = System.currentTimeMillis() - start;
        double avgLatencyMs = (double) duration / iterations;

        System.out.printf("   [Latency Metric] 1,000 queries took %d ms (avg: %.3f ms/query)%n", duration, avgLatencyMs);
        assertTrue(avgLatencyMs < 2.0, "Average query latency must be sub-2ms on in-memory index");
    }

    @SuppressWarnings("unchecked")
    private static List<Ticket> loadTickets(String path, String key) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            Map<String, Object> map = (Map<String, Object>) JsonParser.parse(content);
            List<?> list = (List<?>) map.get(key);
            List<Ticket> result = new ArrayList<>();
            for (Object o : list) {
                result.add(Ticket.fromMap((Map<String, Object>) o));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (Expected: " + expected + ", Actual: " + actual + ")");
        }
    }
}
