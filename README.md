# Solari Analytics — Support Ticket Similarity Assistant (Java Edition)

A high-performance, zero-dependency Java solution for finding similar historical support tickets, surfacing verified resolution notes, and identifying novel issues with calibrated confidence scoring.

---

## Quick Start (Run in 1 Step)

No build tools (Maven/Gradle) or external dependencies are required. Standard Java 17+ (or OpenJDK 17/21/26) is all you need.

### 1. Run Batch Evaluation (Default)
Evaluates incoming tickets in `data/new_tickets_example.json` against `data/tickets.json`:
```bash
./run.sh
```

### 2. Run Comparative Benchmark
Compares the naive token-overlap baseline against the Solari Hybrid Similarity Engine:
```bash
./run.sh --benchmark
```

### 3. Run Automated Unit Test Suite
Runs the built-in test suite verifying JSON parsing, text enrichment, thresholding, and sub-millisecond query latency:
```bash
./run.sh test
```

### 4. Evaluate an Ad-Hoc Ticket
Test any custom subject and description interactively:
```bash
./run.sh --query "Dashboard widget blank" "The revenue chart shows nothing on refresh"
```

---

## Project Structure

```
java_solution/
├── README.md               # Quickstart, architecture overview, test results
├── SOLUTION_DESIGN.md      # 1-2 page engineering design document for the panel
├── run.sh                  # One-click build and execution script
├── data/
│   ├── tickets.json        # 40 synthetic historical resolved tickets
│   └── new_tickets_example.json # Test incoming tickets (including edge cases)
└── src/
    ├── App.java            # Main entry point and CLI commands
    ├── TestRunner.java     # Automated test suite (zero-dependency)
    ├── JsonParser.java     # Recursive-descent pure Java JSON parser
    ├── Ticket.java         # Data model representing support tickets
    ├── TextProcessor.java  # Stopwords, bi-grams, normalization, domain synonyms
    ├── SimilarityEngine.java # TF-IDF / BM25 weighting, vector math, field boosts
    ├── SupportAssistant.java # Threshold gating, agent suggestion synthesis
    └── MatchResult.java    # Output record with confidence percentages & actions
```

---

## Evaluation Results on Provided Test Set

| Incoming Ticket | Subject Summary | Matched Historical Precedent | Confidence Score | Verdict & Action |
| :--- | :--- | :--- | :--- | :--- |
| **`NT-1`** | Password reset email never arrives | `T-1001` (Auth / Password reset) | 34.3% (Medium) | **Related Precedent**: Case-sensitive token styling in email template. |
| **`NT-2`** | Revenue chart shows nothing / white space | `T-1201` (Render / Chart shows blank) | 31.1% (Medium) | **Related Precedent**: Empty dataset handling on zero-row filter results. |
| **`NT-3`** | Charged twice for single subscription | `T-1303` (Billing / Duplicate charge) | 53.9% (High) | **Direct Match**: Idempotency key issue in payment retry mechanism; refund duplicate. |
| **`NT-4`** | Snowflake warehouse integration failing | None (`T-1701` at 12.7%) | 12.7% (Low) | **NO DIRECT MATCH (Novel Issue Alert)**: Alerts agent that this is an emerging issue; avoids giving incorrect canned advice. |

---

## Why This Beats the Starter-Kit Baseline

The starter kit's `ticket_utils.py` uses unweighted token set overlap. This causes two major failure modes:
1. **False Negatives on Synonyms:** A customer asking why a chart "shows nothing" fails to match a ticket titled "shows blank" because the token sets don't intersect. Our `TextProcessor` bridges this gap using domain-aware synonym expansion and bi-grams.
2. **Dangerous False Positives on Novel Tickets:** When presented with `NT-4` (Snowflake integration), the naive matcher reports a spurious ~20% overlap on filler words (`"with"`, `"is"`, `"to"`). Our engine applies strict stopword pruning, IDF down-weighting of generic terms, and a 30% confidence floor to explicitly reject `NT-4` as unprecedented.

---

## Known Limitations & Production Readiness

1. **Current In-Memory Scale:** Designed for up to ~100,000 tickets in memory. For enterprise scale beyond 1M tickets, an embedded SQLite FTS5 database or a vector database (e.g. pgvector / Qdrant) would be integrated.
2. **Single-Node Execution:** The current engine runs in-process. For production deployment, wrap `SupportAssistant` in a lightweight HTTP REST controller (or gRPC) with containerized deployment.
3. **No Automatic PII Anonymization:** In production, customer identifiers, credit card fragments, and private tokens should be scrubbed before indexing.
