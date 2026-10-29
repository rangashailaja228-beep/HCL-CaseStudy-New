package src;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Text preprocessing, tokenization, stopword removal, bi-gram generation,
 * and domain synonym expansion for SaaS support tickets.
 */
public class TextProcessor {

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and",
        "any", "are", "aren't", "as", "at", "be", "because", "been", "before", "being",
        "below", "between", "both", "but", "by", "can't", "cannot", "could", "couldn't",
        "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during",
        "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
        "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here",
        "here's", "hers", "herself", "him", "himself", "his", "how", "how's", "i",
        "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's",
        "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself",
        "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought",
        "our", "ours", "ourselves", "out", "over", "own", "same", "shan't", "she",
        "she'd", "she'll", "she's", "should", "shouldn't", "so", "some", "such", "than",
        "that", "that's", "the", "their", "theirs", "them", "themselves", "then",
        "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've",
        "this", "those", "through", "to", "too", "under", "until", "up", "very", "was",
        "wasn't", "we", "we'd", "we'll", "we're", "we've", "were", "weren't", "what",
        "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's",
        "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd",
        "you'll", "you're", "you've", "your", "yours", "yourself", "yourselves"
    ));

    // Common SaaS / customer support synonym expansions
    private static final Map<String, List<String>> SYNONYMS = new HashMap<>();
    static {
        SYNONYMS.put("nothing", Arrays.asList("blank", "empty"));
        SYNONYMS.put("white", Arrays.asList("blank", "empty"));
        SYNONYMS.put("twice", Arrays.asList("duplicate", "double"));
        SYNONYMS.put("charged", Arrays.asList("billing", "charge", "payment"));
        SYNONYMS.put("card", Arrays.asList("payment", "billing"));
        SYNONYMS.put("login", Arrays.asList("auth", "signin", "password"));
        SYNONYMS.put("arrives", Arrays.asList("received", "delivery"));
        SYNONYMS.put("failing", Arrays.asList("failure", "error", "broken"));
        SYNONYMS.put("slow", Arrays.asList("performance", "timeout", "latency"));
        SYNONYMS.put("spinning", Arrays.asList("loading", "timeout", "hang"));
    }

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s]");

    /**
     * Clean and normalize a string into lower-case alphanumeric space-separated words.
     */
    public static String clean(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase();
        return NON_ALPHANUMERIC.matcher(lower).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Tokenize string, filter stopwords, add bi-grams, and expand synonyms.
     */
    public static List<String> tokenize(String text) {
        String cleaned = clean(text);
        if (cleaned.isEmpty()) return Collections.emptyList();

        String[] rawTokens = cleaned.split(" ");
        List<String> validTokens = new ArrayList<>();

        for (String t : rawTokens) {
            if (t.length() > 1 && !STOPWORDS.contains(t)) {
                validTokens.add(t);
            }
        }

        List<String> enriched = new ArrayList<>(validTokens);

        // Add Bi-grams to capture key phrases ("password reset", "duplicate charge", "revenue chart")
        for (int i = 0; i < validTokens.size() - 1; i++) {
            enriched.add(validTokens.get(i) + "_" + validTokens.get(i + 1));
        }

        // Add domain synonym expansions
        for (String token : validTokens) {
            List<String> syns = SYNONYMS.get(token);
            if (syns != null) {
                enriched.addAll(syns);
            }
        }

        return enriched;
    }

    /**
     * Compute term frequency map for a list of tokens.
     */
    public static Map<String, Integer> termFrequencies(List<String> tokens) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) {
            tf.put(t, tf.getOrDefault(t, 0) + 1);
        }
        return tf;
    }
}
