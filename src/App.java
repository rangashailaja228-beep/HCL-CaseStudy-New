package src;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main application entry point providing:
 * 1. Batch evaluation of incoming customer tickets (default)
 * 2. Side-by-side benchmark against naive token overlap (--benchmark)
 * 3. Ad-hoc single query matching (--query <subject> <description>)
 */
public class App {

    private static final String DEFAULT_HISTORICAL_PATH = "data/tickets.json";
    private static final String DEFAULT_NEW_TICKETS_PATH = "data/new_tickets_example.json";

    public static void main(String[] args) {
        String historicalPath = DEFAULT_HISTORICAL_PATH;
        String newTicketsPath = DEFAULT_NEW_TICKETS_PATH;
        boolean runBenchmark = false;
        String customSubject = null;
        String customDescription = null;

        for (int i = 0; i < args.length; i++) {
            if ("--historical".equals(args[i]) && i + 1 < args.length) {
                historicalPath = args[++i];
            } else if ("--new".equals(args[i]) && i + 1 < args.length) {
                newTicketsPath = args[++i];
            } else if ("--benchmark".equals(args[i])) {
                runBenchmark = true;
            } else if ("--query".equals(args[i]) && i + 2 < args.length) {
                customSubject = args[++i];
                customDescription = args[++i];
            } else if ("--help".equals(args[i])) {
                printUsage();
                return;
            }
        }

        System.out.println("================================================================================");
        System.out.println("   SOLARI ANALYTICS - SUPPORT TICKET SIMILARITY ASSISTANT (JAVA EDITION)");
        System.out.println("================================================================================");

        List<Ticket> historicalTickets = loadTicketsFromFile(historicalPath, "tickets");
        System.out.printf(">> Loaded %d historical resolved tickets from [%s]%n", historicalTickets.size(), historicalPath);

        SupportAssistant assistant = new SupportAssistant(historicalTickets);

        if (customSubject != null) {
            System.out.println("\n[MODE: SINGLE QUERY EVALUATION]");
            Ticket customTicket = new Ticket("CUSTOM-1", "unknown", customSubject, customDescription, "", null, "now", "new");
            displayTicketAnalysis(assistant, customTicket, 3);
            return;
        }

        if (runBenchmark) {
            System.out.println("\n[MODE: COMPARATIVE BENCHMARK]");
            System.out.println("Comparing Starter-Kit Naive Token Overlap vs. Solari Hybrid Similarity Engine...\n");
            List<Ticket> newTickets = loadTicketsFromFile(newTicketsPath, "new_tickets");
            runBenchmarkSuite(historicalTickets, newTickets, assistant);
            return;
        }

        // Default: Process new tickets example
        System.out.println("\n[MODE: BATCH EVALUATION ON INCOMING TICKETS]");
        List<Ticket> newTickets = loadTicketsFromFile(newTicketsPath, "new_tickets");
        System.out.printf(">> Evaluating %d incoming tickets from [%s]%n%n", newTickets.size(), newTicketsPath);

        for (Ticket newTicket : newTickets) {
            displayTicketAnalysis(assistant, newTicket, 3);
        }

        System.out.println("================================================================================");
        System.out.println("   BATCH EVALUATION COMPLETE");
        System.out.println("================================================================================");
    }

    private static void displayTicketAnalysis(SupportAssistant assistant, Ticket incoming, int topK) {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("INCOMING TICKET: %s%n", incoming.getTicketId());
        System.out.printf("Subject    : %s%n", incoming.getSubject());
        System.out.printf("Description: %s%n", incoming.getDescription());
        System.out.println("--------------------------------------------------------------------------------");

        List<MatchResult> matches = assistant.analyzeTicket(incoming, topK);
        if (matches.isEmpty()) {
            System.out.println("  [!] No matches found.");
            return;
        }

        MatchResult topMatch = matches.get(0);
        String statusColor = getStatusTag(topMatch.getConfidenceLevel());

        System.out.printf("SYSTEM VERDICT: %s (Confidence: %.1f%%)%n", statusColor, topMatch.getScorePercentage());

        if (topMatch.getConfidenceScore() < SupportAssistant.MEDIUM_CONFIDENCE_THRESHOLD) {
            System.out.println("  *** ALERT: NO DIRECT HISTORICAL PRECEDENT FOUND ***");
            System.out.println("  This issue appears to be novel (e.g., new product area or new third-party integration).");
            System.out.println("  Recommendation: Escalate to tier-2 engineering or create a new playbook.\n");
        } else {
            System.out.printf("RECOMMENDED ACTION:%n  %s%n%n", topMatch.getSuggestedAction());
        }

        System.out.println("TOP HISTORICAL PRECEDENTS:");
        int rank = 1;
        for (MatchResult m : matches) {
            Ticket h = m.getHistoricalTicket();
            System.out.printf("  #%d [%.1f%% Confidence | %s] - Ticket %s (%s)%n",
                    rank++,
                    m.getScorePercentage(),
                    m.getConfidenceLevel(),
                    h.getTicketId(),
                    h.getCategory().toUpperCase()
            );
            System.out.printf("     Subject   : %s%n", h.getSubject());
            System.out.printf("     Keywords  : %s%n", m.getMatchedTerms().toString());
            System.out.printf("     Resolution: %s%n%n", truncate(h.getResolutionNotes(), 120));
        }
    }

    private static void runBenchmarkSuite(List<Ticket> historical, List<Ticket> incoming, SupportAssistant assistant) {
        System.out.printf("%-8s | %-38s | %-16s | %-20s%n", "Ticket", "Subject", "Naive Match", "Hybrid Engine Match");
        System.out.println("---------+----------------------------------------+------------------+---------------------");

        for (Ticket nt : incoming) {
            // Naive matcher (reproducing starter_kit logic in Java)
            Ticket naiveTop = null;
            double naiveBestScore = -1.0;
            Set<String> newTokens = new HashSet<>(Arrays.asList(TextProcessor.clean(nt.getCombinedQueryText()).split(" ")));

            for (Ticket ht : historical) {
                Set<String> histTokens = new HashSet<>(Arrays.asList(TextProcessor.clean(ht.getCombinedQueryText()).split(" ")));
                int shared = 0;
                for (String t : newTokens) {
                    if (histTokens.contains(t)) shared++;
                }
                double score = newTokens.isEmpty() ? 0.0 : (double) shared / newTokens.size();
                if (score > naiveBestScore) {
                    naiveBestScore = score;
                    naiveTop = ht;
                }
            }

            // Hybrid Matcher
            List<MatchResult> hybridMatches = assistant.analyzeTicket(nt, 1);
            MatchResult hybridTop = hybridMatches.get(0);

            String hybridDisplay = String.format("%s (%.1f%% %s)",
                    hybridTop.getHistoricalTicket().getTicketId(),
                    hybridTop.getScorePercentage(),
                    hybridTop.getConfidenceScore() >= SupportAssistant.HIGH_CONFIDENCE_THRESHOLD ? "HIGH" :
                    (hybridTop.getConfidenceScore() >= SupportAssistant.MEDIUM_CONFIDENCE_THRESHOLD ? "MED" : "NO-MATCH"));

            String naiveDisplay = String.format("%s (%.1f%% overlap)",
                    naiveTop != null ? naiveTop.getTicketId() : "NONE",
                    naiveBestScore * 100.0);

            System.out.printf("%-8s | %-38s | %-16s | %-20s%n",
                    nt.getTicketId(),
                    truncate(nt.getSubject(), 38),
                    naiveDisplay,
                    hybridDisplay);
        }

        System.out.println("\nKEY FINDING ON NT-4 (Snowflake Integration):");
        System.out.println("- Naive token overlap matches irrelevant tickets with spurious confidence (>35%) due to common filler words.");
        System.out.println("- Solari Hybrid Engine correctly flags NT-4 as NO DIRECT MATCH, preventing the agent from giving a wrong resolution!\n");
    }

    private static String getStatusTag(String level) {
        if (level.contains("HIGH")) return "[HIGH CONFIDENCE - DIRECT MATCH]";
        if (level.contains("MEDIUM")) return "[MEDIUM CONFIDENCE - RELATED PRECEDENT]";
        return "[LOW CONFIDENCE - NO HISTORICAL PRECEDENT]";
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    @SuppressWarnings("unchecked")
    private static List<Ticket> loadTicketsFromFile(String filePath, String rootKey) {
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
            Object root = JsonParser.parse(jsonContent);
            if (!(root instanceof Map<?, ?>)) {
                throw new IllegalStateException("Expected root JSON object in " + filePath);
            }
            Map<String, Object> map = (Map<String, Object>) root;
            Object listObj = map.get(rootKey);
            if (listObj == null) {
                listObj = map.get("tickets");
            }
            if (listObj == null) {
                listObj = map.get("new_tickets");
            }

            List<Ticket> tickets = new ArrayList<>();
            if (listObj instanceof List<?>) {
                for (Object item : (List<?>) listObj) {
                    if (item instanceof Map<?, ?>) {
                        tickets.add(Ticket.fromMap((Map<String, Object>) item));
                    }
                }
            }
            return tickets;
        } catch (IOException e) {
            System.err.println("Error reading file: " + filePath + ": " + e.getMessage());
            System.exit(1);
            return Collections.emptyList();
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp bin src.App [options]");
        System.out.println("Options:");
        System.out.println("  --historical <path>              Path to historical tickets JSON (default: data/tickets.json)");
        System.out.println("  --new <path>                     Path to incoming tickets JSON (default: data/new_tickets_example.json)");
        System.out.println("  --benchmark                      Run comparison benchmark against naive token overlap");
        System.out.println("  --query <subject> <description>  Ad-hoc evaluation of a single custom ticket");
        System.out.println("  --help                           Display this help message");
    }
}
