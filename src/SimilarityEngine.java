package src;

import java.util.*;

/**
 * Hybrid Similarity Engine combining:
 * 1. BM25 / Sublinear TF-IDF cosine similarity.
 * 2. Field weighting (Subject > Description > Tags > Category).
 * 3. Bi-gram phrase matching & domain synonym expansion.
 * 4. Calibrated confidence scoring.
 */
public class SimilarityEngine {
    private final List<Ticket> historicalTickets;
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private final List<Map<String, Double>> docVectors = new ArrayList<>();
    private final List<Double> docNorms = new ArrayList<>();
    private final int totalDocs;

    public SimilarityEngine(List<Ticket> tickets) {
        this.historicalTickets = new ArrayList<>(tickets);
        this.totalDocs = tickets.size();
        buildIndex();
    }

    private void buildIndex() {
        // Step 1: Count document frequencies
        for (Ticket ticket : historicalTickets) {
            Set<String> uniqueTermsInDoc = new HashSet<>();

            // Extract tokens from all fields
            uniqueTermsInDoc.addAll(TextProcessor.tokenize(ticket.getSubject()));
            uniqueTermsInDoc.addAll(TextProcessor.tokenize(ticket.getDescription()));
            uniqueTermsInDoc.addAll(TextProcessor.tokenize(ticket.getCategory()));
            for (String tag : ticket.getTags()) {
                uniqueTermsInDoc.addAll(TextProcessor.tokenize(tag));
            }

            for (String term : uniqueTermsInDoc) {
                documentFrequency.put(term, documentFrequency.getOrDefault(term, 0) + 1);
            }
        }

        // Step 2: Build weighted document vectors
        for (Ticket ticket : historicalTickets) {
            Map<String, Double> termWeights = buildWeightedTermMap(ticket);

            // Compute TF-IDF vector
            Map<String, Double> tfIdfVec = new HashMap<>();
            double sumSquares = 0.0;

            for (Map.Entry<String, Double> entry : termWeights.entrySet()) {
                String term = entry.getKey();
                double tf = entry.getValue();
                double idf = getIdf(term);
                // Sublinear TF scaling
                double weight = (1.0 + Math.log(tf)) * idf;
                tfIdfVec.put(term, weight);
                sumSquares += weight * weight;
            }

            double norm = Math.sqrt(sumSquares);
            docVectors.add(tfIdfVec);
            docNorms.add(norm > 0 ? norm : 1.0);
        }
    }

    private double getIdf(String term) {
        int df = documentFrequency.getOrDefault(term, 0);
        // Standard BM25-style smoothed IDF
        return Math.log(1.0 + (totalDocs - df + 0.5) / (df + 0.5));
    }

    private Map<String, Double> buildWeightedTermMap(Ticket ticket) {
        Map<String, Double> weights = new HashMap<>();

        // Subject has highest signal density (weight: 2.5)
        for (String t : TextProcessor.tokenize(ticket.getSubject())) {
            weights.put(t, weights.getOrDefault(t, 0.0) + 2.5);
        }

        // Description provides detailed context (weight: 1.0)
        for (String t : TextProcessor.tokenize(ticket.getDescription())) {
            weights.put(t, weights.getOrDefault(t, 0.0) + 1.0);
        }

        // Category provides strong domain filter (weight: 1.8)
        for (String t : TextProcessor.tokenize(ticket.getCategory())) {
            weights.put(t, weights.getOrDefault(t, 0.0) + 1.8);
        }

        // Tags provide verified human labels (weight: 2.2)
        for (String tag : ticket.getTags()) {
            for (String t : TextProcessor.tokenize(tag)) {
                weights.put(t, weights.getOrDefault(t, 0.0) + 2.2);
            }
        }

        return weights;
    }

    private Map<String, Double> buildQueryVector(Ticket queryTicket) {
        Map<String, Double> termWeights = new HashMap<>();

        // Subject is weighted higher
        for (String t : TextProcessor.tokenize(queryTicket.getSubject())) {
            termWeights.put(t, termWeights.getOrDefault(t, 0.0) + 2.5);
        }

        // Description
        for (String t : TextProcessor.tokenize(queryTicket.getDescription())) {
            termWeights.put(t, termWeights.getOrDefault(t, 0.0) + 1.0);
        }

        Map<String, Double> qVec = new HashMap<>();
        for (Map.Entry<String, Double> entry : termWeights.entrySet()) {
            String term = entry.getKey();
            double tf = entry.getValue();
            double idf = getIdf(term);
            double weight = (1.0 + Math.log(tf)) * idf;
            qVec.put(term, weight);
        }
        return qVec;
    }

    /**
     * Search for the most similar tickets in the historical database.
     */
    public List<ScoredCandidate> search(Ticket newTicket, int topN) {
        Map<String, Double> queryVec = buildQueryVector(newTicket);
        double qSumSquares = 0.0;
        for (double w : queryVec.values()) {
            qSumSquares += w * w;
        }
        double queryNorm = Math.sqrt(qSumSquares);
        if (queryNorm == 0) return Collections.emptyList();

        List<ScoredCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < totalDocs; i++) {
            Ticket docTicket = historicalTickets.get(i);
            Map<String, Double> docVec = docVectors.get(i);
            double docNorm = docNorms.get(i);

            // Compute dot product and find overlapping terms
            double dot = 0.0;
            List<String> matchedTerms = new ArrayList<>();

            for (Map.Entry<String, Double> qEntry : queryVec.entrySet()) {
                String term = qEntry.getKey();
                if (docVec.containsKey(term)) {
                    dot += qEntry.getValue() * docVec.get(term);
                    if (!term.contains("_")) {
                        matchedTerms.add(term);
                    }
                }
            }

            double cosineSim = dot / (queryNorm * docNorm);

            // Calibrated confidence score: bounded between 0.0 and 1.0
            double calibratedScore = Math.min(1.0, Math.max(0.0, cosineSim));

            candidates.add(new ScoredCandidate(docTicket, calibratedScore, matchedTerms));
        }

        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        return candidates.subList(0, Math.min(topN, candidates.size()));
    }

    public static class ScoredCandidate {
        public final Ticket ticket;
        public final double score;
        public final List<String> matchedTerms;

        public ScoredCandidate(Ticket ticket, double score, List<String> matchedTerms) {
            this.ticket = ticket;
            this.score = score;
            this.matchedTerms = matchedTerms;
        }
    }
}
