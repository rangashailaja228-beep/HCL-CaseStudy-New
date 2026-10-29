package src;

import java.util.List;

/**
 * Encapsulates a matched historical ticket, its confidence score,
 * classification level, matched terms, and suggested action for the agent.
 */
public class MatchResult {
    private final Ticket historicalTicket;
    private final double confidenceScore;
    private final String confidenceLevel;
    private final List<String> matchedTerms;
    private final String suggestedAction;

    public MatchResult(Ticket historicalTicket, double confidenceScore,
                       String confidenceLevel, List<String> matchedTerms,
                       String suggestedAction) {
        this.historicalTicket = historicalTicket;
        this.confidenceScore = confidenceScore;
        this.confidenceLevel = confidenceLevel;
        this.matchedTerms = matchedTerms;
        this.suggestedAction = suggestedAction;
    }

    public Ticket getHistoricalTicket() { return historicalTicket; }
    public double getConfidenceScore() { return confidenceScore; }
    public String getConfidenceLevel() { return confidenceLevel; }
    public List<String> getMatchedTerms() { return matchedTerms; }
    public String getSuggestedAction() { return suggestedAction; }

    public double getScorePercentage() {
        return Math.round(confidenceScore * 1000.0) / 10.0;
    }
}
