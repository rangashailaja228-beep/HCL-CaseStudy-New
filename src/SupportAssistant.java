package src;

import java.util.*;

/**
 * Assistant service that evaluates incoming tickets, applies confidence gating,
 * and formats actionable resolution guidance for customer support agents.
 */
public class SupportAssistant {

    public static final double HIGH_CONFIDENCE_THRESHOLD = 0.50;
    public static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.30;

    private final SimilarityEngine engine;

    public SupportAssistant(List<Ticket> historicalTickets) {
        this.engine = new SimilarityEngine(historicalTickets);
    }

    /**
     * Analyze an incoming ticket and return the top match results with confidence ratings.
     */
    public List<MatchResult> analyzeTicket(Ticket incomingTicket, int topK) {
        List<SimilarityEngine.ScoredCandidate> candidates = engine.search(incomingTicket, topK);
        List<MatchResult> results = new ArrayList<>();

        for (SimilarityEngine.ScoredCandidate candidate : candidates) {
            String level;
            String suggestedAction;

            if (candidate.score >= HIGH_CONFIDENCE_THRESHOLD) {
                level = "HIGH CONFIDENCE";
                suggestedAction = synthesizeAction(candidate.ticket, true);
            } else if (candidate.score >= MEDIUM_CONFIDENCE_THRESHOLD) {
                level = "MEDIUM CONFIDENCE";
                suggestedAction = synthesizeAction(candidate.ticket, false);
            } else {
                level = "LOW CONFIDENCE (NO DIRECT MATCH)";
                suggestedAction = "Novel or emerging issue. No direct historical precedent found above threshold. "
                                + "Verify with engineering or internal knowledge base before responding.";
            }

            results.add(new MatchResult(
                candidate.ticket,
                candidate.score,
                level,
                candidate.matchedTerms,
                suggestedAction
            ));
        }

        return results;
    }

    private String synthesizeAction(Ticket pastTicket, boolean isHighConfidence) {
        String resolution = pastTicket.getResolutionNotes();
        if (resolution == null || resolution.isEmpty()) {
            return "Review historical ticket " + pastTicket.getTicketId() + " for context.";
        }

        if (isHighConfidence) {
            return "Apply precedent from [" + pastTicket.getTicketId() + "]: " + resolution;
        } else {
            return "Potential reference [" + pastTicket.getTicketId() + "]: " + resolution;
        }
    }
}
