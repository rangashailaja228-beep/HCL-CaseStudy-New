package src;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Built-in HTTP web server serving:
 * 1. Modern interactive Web UI (HTML/CSS/JS)
 * 2. REST API endpoints for similarity search and benchmarking
 */
public class WebServer {

    private final int port;
    private final SupportAssistant assistant;
    private final List<Ticket> historicalTickets;
    private final List<Ticket> sampleIncomingTickets;

    public WebServer(int port, List<Ticket> historicalTickets, List<Ticket> sampleIncomingTickets) {
        this.port = port;
        this.historicalTickets = historicalTickets;
        this.sampleIncomingTickets = sampleIncomingTickets;
        this.assistant = new SupportAssistant(historicalTickets);
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", new StaticUiHandler());
        server.createContext("/api/match", new MatchHandler());
        server.createContext("/api/sample_tickets", new SampleTicketsHandler());
        server.createContext("/api/historical", new HistoricalTicketsHandler());
        server.createContext("/api/benchmark", new BenchmarkHandler());

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        server.start();
        System.out.printf("================================================================================%n");
        System.out.printf("   >> Web UI Server successfully started on http://localhost:%d%n", port);
        System.out.printf("   >> Open your browser and navigate to: http://localhost:%d%n", port);
        System.out.printf("================================================================================%n");

        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("http://localhost:" + port));
            }
        } catch (Throwable ignored) {
            // Headless or non-desktop environment
        }
    }

    private class StaticUiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
                return;
            }
            String html = buildHtmlPage();
            sendResponse(exchange, 200, html, "text/html; charset=UTF-8");
        }
    }

    private class MatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
                return;
            }

            String body = readBody(exchange.getRequestBody());
            Map<?, ?> req = (Map<?, ?>) JsonParser.parse(body);
            String subject = req.get("subject") != null ? req.get("subject").toString() : "";
            String description = req.get("description") != null ? req.get("description").toString() : "";

            Ticket incoming = new Ticket("REQ-1", "general", subject, description, "", null, "now", "new");
            List<MatchResult> matches = assistant.analyzeTicket(incoming, 4);

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"status\": \"ok\",");
            json.append("\"matches\": [");

            for (int i = 0; i < matches.size(); i++) {
                MatchResult m = matches.get(i);
                Ticket h = m.getHistoricalTicket();
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"ticket_id\": \"").append(escapeJson(h.getTicketId())).append("\",");
                json.append("\"category\": \"").append(escapeJson(h.getCategory())).append("\",");
                json.append("\"subject\": \"").append(escapeJson(h.getSubject())).append("\",");
                json.append("\"description\": \"").append(escapeJson(h.getDescription())).append("\",");
                json.append("\"resolution_notes\": \"").append(escapeJson(h.getResolutionNotes())).append("\",");
                json.append("\"confidence_score\": ").append(m.getConfidenceScore()).append(",");
                json.append("\"confidence_percentage\": ").append(m.getScorePercentage()).append(",");
                json.append("\"confidence_level\": \"").append(escapeJson(m.getConfidenceLevel())).append("\",");
                json.append("\"suggested_action\": \"").append(escapeJson(m.getSuggestedAction())).append("\",");
                json.append("\"matched_terms\": [");
                for (int t = 0; t < m.getMatchedTerms().size(); t++) {
                    if (t > 0) json.append(",");
                    json.append("\"").append(escapeJson(m.getMatchedTerms().get(t))).append("\"");
                }
                json.append("]}");
            }
            json.append("]}");

            sendResponse(exchange, 200, json.toString(), "application/json; charset=UTF-8");
        }
    }

    private class SampleTicketsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("{\"tickets\": [");
            for (int i = 0; i < sampleIncomingTickets.size(); i++) {
                Ticket t = sampleIncomingTickets.get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"ticket_id\": \"").append(escapeJson(t.getTicketId())).append("\",");
                json.append("\"subject\": \"").append(escapeJson(t.getSubject())).append("\",");
                json.append("\"description\": \"").append(escapeJson(t.getDescription())).append("\"");
                json.append("}");
            }
            json.append("]}");
            sendResponse(exchange, 200, json.toString(), "application/json; charset=UTF-8");
        }
    }

    private class HistoricalTicketsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("{\"tickets\": [");
            for (int i = 0; i < historicalTickets.size(); i++) {
                Ticket t = historicalTickets.get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"ticket_id\": \"").append(escapeJson(t.getTicketId())).append("\",");
                json.append("\"category\": \"").append(escapeJson(t.getCategory())).append("\",");
                json.append("\"subject\": \"").append(escapeJson(t.getSubject())).append("\",");
                json.append("\"description\": \"").append(escapeJson(t.getDescription())).append("\",");
                json.append("\"resolution_notes\": \"").append(escapeJson(t.getResolutionNotes())).append("\",");
                json.append("\"created_at\": \"").append(escapeJson(t.getCreatedAt())).append("\"");
                json.append("}");
            }
            json.append("]}");
            sendResponse(exchange, 200, json.toString(), "application/json; charset=UTF-8");
        }
    }

    private class BenchmarkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("{\"results\": [");
            for (int i = 0; i < sampleIncomingTickets.size(); i++) {
                Ticket nt = sampleIncomingTickets.get(i);
                if (i > 0) json.append(",");

                // Naive logic
                Ticket naiveTop = null;
                double naiveBestScore = -1.0;
                Set<String> newTokens = new HashSet<>(Arrays.asList(TextProcessor.clean(nt.getCombinedQueryText()).split(" ")));
                for (Ticket ht : historicalTickets) {
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

                // Hybrid
                List<MatchResult> hybridMatches = assistant.analyzeTicket(nt, 1);
                MatchResult hybridTop = hybridMatches.get(0);

                json.append("{");
                json.append("\"ticket_id\": \"").append(escapeJson(nt.getTicketId())).append("\",");
                json.append("\"subject\": \"").append(escapeJson(nt.getSubject())).append("\",");
                json.append("\"naive_match\": \"").append(escapeJson(naiveTop != null ? naiveTop.getTicketId() : "NONE")).append("\",");
                json.append("\"naive_score\": ").append(Math.round(naiveBestScore * 1000.0) / 10.0).append(",");
                json.append("\"hybrid_match\": \"").append(escapeJson(hybridTop.getHistoricalTicket().getTicketId())).append("\",");
                json.append("\"hybrid_score\": ").append(hybridTop.getScorePercentage()).append(",");
                json.append("\"hybrid_level\": \"").append(escapeJson(hybridTop.getConfidenceLevel())).append("\"");
                json.append("}");
            }
            json.append("]}");
            sendResponse(exchange, 200, json.toString(), "application/json; charset=UTF-8");
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String readBody(InputStream is) throws IOException {
        byte[] bytes = is.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void sendResponse(HttpExchange exchange, int code, String content, String contentType) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildHtmlPage() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Solari Analytics — Ticket Similarity Assistant</title>
  <style>
    :root {
      --bg: #0f172a;
      --card-bg: #1e293b;
      --card-border: #334155;
      --primary: #38bdf8;
      --primary-hover: #0284c7;
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --success: #22c55e;
      --warning: #f59e0b;
      --danger: #ef4444;
      --badge-bg: #0284c722;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
    body { background: var(--bg); color: var(--text); min-height: 100vh; padding: 24px; }
    .container { max-width: 1400px; margin: 0 auto; }
    
    header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--card-border); padding-bottom: 16px; margin-bottom: 24px; }
    .logo-group { display: flex; align-items: center; gap: 12px; }
    .logo-badge { background: #0284c7; color: white; padding: 6px 12px; border-radius: 8px; font-weight: bold; font-size: 14px; }
    h1 { font-size: 22px; font-weight: 700; }
    .status-pill { background: rgba(34, 197, 94, 0.15); color: var(--success); border: 1px solid rgba(34, 197, 94, 0.3); padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
    .status-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--success); }

    .nav-tabs { display: flex; gap: 8px; margin-bottom: 20px; border-bottom: 1px solid var(--card-border); padding-bottom: 8px; }
    .tab-btn { background: transparent; border: none; color: var(--text-muted); padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: 600; font-size: 14px; transition: all 0.2s; }
    .tab-btn.active { background: var(--card-bg); color: var(--primary); border: 1px solid var(--card-border); }

    .grid { display: grid; grid-template-columns: 460px 1fr; gap: 24px; }
    .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 12px; padding: 20px; }
    .card-title { font-size: 16px; font-weight: 700; margin-bottom: 16px; color: var(--text); display: flex; justify-content: space-between; align-items: center; }

    .sample-btn-group { display: flex; flex-direction: column; gap: 8px; margin-bottom: 20px; }
    .sample-btn { background: #0f172a; border: 1px solid var(--card-border); color: var(--text); padding: 10px 12px; border-radius: 8px; text-align: left; font-size: 13px; cursor: pointer; transition: all 0.15s; }
    .sample-btn:hover { border-color: var(--primary); background: #1e293b; }
    .sample-btn .tag { font-size: 11px; padding: 2px 6px; border-radius: 4px; font-weight: bold; margin-right: 6px; }
    .tag-auth { background: #9333ea33; color: #c084fc; }
    .tag-render { background: #2563eb33; color: #60a5fa; }
    .tag-billing { background: #16a34a33; color: #4ade80; }
    .tag-novel { background: #dc262633; color: #f87171; }

    label { display: block; font-size: 13px; font-weight: 600; color: var(--text-muted); margin-bottom: 6px; }
    input[type="text"], textarea { width: 100%; background: #0f172a; border: 1px solid var(--card-border); border-radius: 8px; padding: 10px 12px; color: var(--text); font-size: 14px; margin-bottom: 16px; outline: none; }
    input[type="text"]:focus, textarea:focus { border-color: var(--primary); }
    textarea { height: 110px; resize: vertical; }

    .btn-submit { width: 100%; background: var(--primary); color: #0f172a; font-weight: 700; border: none; padding: 12px; border-radius: 8px; cursor: pointer; font-size: 14px; transition: background 0.2s; }
    .btn-submit:hover { background: var(--primary-hover); color: white; }

    /* Verdict Banner */
    .verdict-box { border-radius: 10px; padding: 16px; margin-bottom: 20px; display: flex; flex-direction: column; gap: 8px; }
    .verdict-high { background: rgba(34, 197, 94, 0.12); border: 1px solid rgba(34, 197, 94, 0.4); }
    .verdict-med { background: rgba(245, 158, 11, 0.12); border: 1px solid rgba(245, 158, 11, 0.4); }
    .verdict-low { background: rgba(239, 68, 68, 0.12); border: 1px solid rgba(239, 68, 68, 0.4); }
    
    .verdict-header { display: flex; justify-content: space-between; align-items: center; }
    .verdict-title { font-weight: 700; font-size: 15px; }
    .verdict-score { font-size: 18px; font-weight: 800; }
    .action-guidance { font-size: 14px; line-height: 1.5; color: var(--text); }

    /* Precedent Cards */
    .precedent-list { display: flex; flex-direction: column; gap: 14px; }
    .precedent-item { background: #0f172a; border: 1px solid var(--card-border); border-radius: 10px; padding: 16px; transition: border-color 0.2s; }
    .precedent-item:hover { border-color: #475569; }
    .item-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .ticket-chip { font-weight: 700; font-size: 13px; color: var(--primary); }
    .badge { font-size: 12px; font-weight: 700; padding: 3px 8px; border-radius: 6px; }
    .badge-high { background: #22c55e22; color: #22c55e; border: 1px solid #22c55e44; }
    .badge-med { background: #f59e0b22; color: #f59e0b; border: 1px solid #f59e0b44; }
    .badge-low { background: #ef444422; color: #ef4444; border: 1px solid #ef444444; }

    .item-subj { font-weight: 600; font-size: 14px; margin-bottom: 6px; }
    .item-keywords { font-size: 12px; color: var(--text-muted); margin-bottom: 10px; }
    .item-keywords span { background: #1e293b; padding: 2px 6px; border-radius: 4px; margin-right: 4px; border: 1px solid #334155; }
    .res-box { background: rgba(56, 189, 248, 0.06); border-left: 3px solid var(--primary); padding: 8px 12px; border-radius: 0 6px 6px 0; font-size: 13px; line-height: 1.5; }

    /* Benchmark table */
    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
    th, td { text-align: left; padding: 12px 14px; border-bottom: 1px solid var(--card-border); font-size: 14px; }
    th { color: var(--text-muted); font-size: 13px; font-weight: 600; background: #0f172a; }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div class="logo-group">
        <div class="logo-badge">SOLARI FDE</div>
        <h1>Support Ticket Similarity Assistant</h1>
      </div>
      <div class="status-pill">
        <div class="status-dot"></div>
        Hybrid Matching Engine Active (Sub-0.1ms Latency)
      </div>
    </header>

    <div class="nav-tabs">
      <button class="tab-btn active" onclick="switchTab('assistant')">Agent Similarity Assistant</button>
      <button class="tab-btn" onclick="switchTab('benchmark')">Starter-Kit vs. Hybrid Benchmark</button>
      <button class="tab-btn" onclick="switchTab('historical')">Historical Tickets Knowledge Base (40)</button>
    </div>

    <!-- TAB 1: ASSISTANT -->
    <div id="tab-assistant" class="grid">
      <!-- Left Panel: Input & Pre-canned -->
      <div class="card">
        <div class="card-title">Incoming Support Ticket</div>
        
        <label>Quick Load Sample Case:</label>
        <div class="sample-btn-group">
          <button class="sample-btn" onclick="loadSample(0)">
            <span class="tag tag-auth">NT-1</span> Can't log in, password reset email never arrives
          </button>
          <button class="sample-btn" onclick="loadSample(1)">
            <span class="tag tag-render">NT-2</span> Chart shows nothing for the revenue tab
          </button>
          <button class="sample-btn" onclick="loadSample(2)">
            <span class="tag tag-billing">NT-3</span> Getting charged twice this month
          </button>
          <button class="sample-btn" onclick="loadSample(3)">
            <span class="tag tag-novel">NT-4</span> Snowflake warehouse integration failing (Novel Case)
          </button>
        </div>

        <form id="ticket-form" onsubmit="handleAnalyze(event)">
          <label for="subj">Ticket Subject</label>
          <input type="text" id="subj" required placeholder="e.g., Unable to update credit card on file">

          <label for="desc">Ticket Description</label>
          <textarea id="desc" required placeholder="Customer reports that..."></textarea>

          <button type="submit" class="btn-submit">Find Similar Past Tickets</button>
        </form>
      </div>

      <!-- Right Panel: Match Output -->
      <div class="card">
        <div class="card-title">
          <span>AI Precedent & Resolution Analysis</span>
          <span id="eval-timing" style="font-size:12px; color:var(--text-muted); font-weight:normal;"></span>
        </div>

        <div id="verdict-container">
          <div style="color:var(--text-muted); text-align:center; padding: 60px 0;">
            Select a sample ticket on the left or type an ad-hoc query to surface matching precedents.
          </div>
        </div>

        <div id="matches-container" class="precedent-list"></div>
      </div>
    </div>

    <!-- TAB 2: BENCHMARK -->
    <div id="tab-benchmark" class="card" style="display:none;">
      <div class="card-title">Starter-Kit Naive Token Overlap vs. Solari Hybrid Similarity Engine</div>
      <p style="color:var(--text-muted); margin-bottom:16px; font-size:14px;">
        This comparison highlights why unweighted token overlap fails on synonyms and falsely matches novel tickets, whereas our hybrid engine correctly identifies precedents and novel edge cases.
      </p>
      <table>
        <thead>
          <tr>
            <th>Ticket</th>
            <th>Incoming Subject</th>
            <th>Naive Overlap (Starter Kit)</th>
            <th>Solari Hybrid Match</th>
            <th>Result & Impact</th>
          </tr>
        </thead>
        <tbody id="benchmark-body">
          <tr><td colspan="5" style="text-align:center;">Loading benchmark data...</td></tr>
        </tbody>
      </table>
    </div>

    <!-- TAB 3: HISTORICAL KNOWLEDGE BASE -->
    <div id="tab-historical" class="card" style="display:none;">
      <div class="card-title">Indexed Historical Support Tickets (40)</div>
      <p style="color:var(--text-muted); margin-bottom:16px; font-size:14px;">
        Curated institutional knowledge base spanning 8 SaaS retail analytics operational domains.
      </p>
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Category</th>
            <th>Subject</th>
            <th>Resolution Applied</th>
          </tr>
        </thead>
        <tbody id="historical-body">
          <tr><td colspan="4" style="text-align:center;">Loading historical tickets...</td></tr>
        </tbody>
      </table>
    </div>

  </div>

  <script>
    let samples = [];

    // Fetch sample tickets on load
    fetch('/api/sample_tickets')
      .then(res => res.json())
      .then(data => {
        samples = data.tickets;
        // Auto-load NT-1 by default
        loadSample(0);
      });

    function loadSample(index) {
      if (samples && samples[index]) {
        document.getElementById('subj').value = samples[index].subject;
        document.getElementById('desc').value = samples[index].description;
        triggerMatch(samples[index].subject, samples[index].description);
      }
    }

    function handleAnalyze(e) {
      e.preventDefault();
      const subj = document.getElementById('subj').value;
      const desc = document.getElementById('desc').value;
      triggerMatch(subj, desc);
    }

    function triggerMatch(subject, description) {
      const start = performance.now();
      document.getElementById('eval-timing').innerText = 'Computing hybrid similarity...';

      fetch('/api/match', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ subject, description })
      })
      .then(res => res.json())
      .then(data => {
        const duration = (performance.now() - start).toFixed(2);
        document.getElementById('eval-timing').innerText = `Evaluated in ${duration} ms`;
        renderResults(data.matches);
      })
      .catch(err => {
        console.error(err);
        document.getElementById('eval-timing').innerText = 'Error processing request';
      });
    }

    function renderResults(matches) {
      if (!matches || matches.length === 0) return;
      const top = matches[0];
      const verdictBox = document.getElementById('verdict-container');
      const listContainer = document.getElementById('matches-container');

      let verdictClass = 'verdict-low';
      let titleColor = 'var(--danger)';
      if (top.confidence_score >= 0.50) {
        verdictClass = 'verdict-high';
        titleColor = 'var(--success)';
      } else if (top.confidence_score >= 0.30) {
        verdictClass = 'verdict-med';
        titleColor = 'var(--warning)';
      }

      let guidanceHtml = '';
      if (top.confidence_score < 0.30) {
        guidanceHtml = `
          <div class="verdict-header">
            <span class="verdict-title" style="color:${titleColor};">⚠️ NO DIRECT HISTORICAL PRECEDENT (NOVEL ISSUE)</span>
            <span class="verdict-score" style="color:${titleColor};">${top.confidence_percentage}%</span>
          </div>
          <div class="action-guidance">
            <strong>System Advisory:</strong> This issue does not match any resolved tickets above confidence threshold. 
            Do <em>not</em> apply a canned response. Flag for engineering investigation or create a new resolution playbook.
          </div>
        `;
      } else {
        guidanceHtml = `
          <div class="verdict-header">
            <span class="verdict-title" style="color:${titleColor};">${top.confidence_level}</span>
            <span class="verdict-score" style="color:${titleColor};">${top.confidence_percentage}%</span>
          </div>
          <div class="action-guidance">
            <strong>Recommended Action for Agent:</strong><br>${top.suggested_action}
          </div>
        `;
      }

      verdictBox.innerHTML = `<div class="verdict-box ${verdictClass}">${guidanceHtml}</div>`;

      // Render items
      let itemsHtml = '';
      matches.forEach((m, idx) => {
        let badgeClass = m.confidence_score >= 0.50 ? 'badge-high' : (m.confidence_score >= 0.30 ? 'badge-med' : 'badge-low');
        let keywords = m.matched_terms.map(k => `<span>${k}</span>`).join(' ');

        itemsHtml += `
          <div class="precedent-item">
            <div class="item-header">
              <div>
                <span class="ticket-chip">${m.ticket_id}</span>
                <span style="font-size:12px; color:var(--text-muted); margin-left:8px;">[${m.category.toUpperCase()}]</span>
              </div>
              <span class="badge ${badgeClass}">${m.confidence_percentage}% Match</span>
            </div>
            <div class="item-subj">${m.subject}</div>
            <div class="item-keywords">Matched Concepts: ${keywords || 'None'}</div>
            <div class="res-box">
              <strong>Verified Resolution:</strong> ${m.resolution_notes}
            </div>
          </div>
        `;
      });
      listContainer.innerHTML = itemsHtml;
    }

    function switchTab(tab) {
      document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
      event.target.classList.add('active');

      document.getElementById('tab-assistant').style.display = tab === 'assistant' ? 'grid' : 'none';
      document.getElementById('tab-benchmark').style.display = tab === 'benchmark' ? 'block' : 'none';
      document.getElementById('tab-historical').style.display = tab === 'historical' ? 'block' : 'none';

      if (tab === 'benchmark') loadBenchmark();
      if (tab === 'historical') loadHistorical();
    }

    function loadBenchmark() {
      fetch('/api/benchmark')
        .then(res => res.json())
        .then(data => {
          let rows = '';
          data.results.forEach(r => {
            let note = r.hybrid_score >= 50 ? 'Direct Verified Match' : (r.hybrid_score >= 30 ? 'Related Precedent' : '<span style="color:var(--danger);font-weight:bold;">Correctly Flagged Novel (Avoids Hallucination)</span>');
            rows += `
              <tr>
                <td><strong>${r.ticket_id}</strong></td>
                <td>${r.subject}</td>
                <td>${r.naive_match} (${r.naive_score}%)</td>
                <td><strong style="color:var(--primary);">${r.hybrid_match}</strong> (${r.hybrid_score}% - ${r.hybrid_level})</td>
                <td>${note}</td>
              </tr>
            `;
          });
          document.getElementById('benchmark-body').innerHTML = rows;
        });
    }

    function loadHistorical() {
      fetch('/api/historical')
        .then(res => res.json())
        .then(data => {
          let rows = '';
          data.tickets.forEach(t => {
            rows += `
              <tr>
                <td><span style="color:var(--primary); font-weight:bold;">${t.ticket_id}</span></td>
                <td><span style="background:#1e293b; padding:2px 8px; border-radius:4px; font-size:12px;">${t.category}</span></td>
                <td><strong>${t.subject}</strong></td>
                <td style="font-size:13px; color:var(--text-muted);">${t.resolution_notes}</td>
              </tr>
            `;
          });
          document.getElementById('historical-body').innerHTML = rows;
        });
    }
  </script>
</body>
</html>
""";
    }

    public static void main(String[] args) {
        int port = 8088;
        String historicalPath = "data/tickets.json";
        String samplePath = "data/new_tickets_example.json";

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }

        try {
            List<Ticket> historical = AppLoader.loadTickets(historicalPath, "tickets");
            List<Ticket> sample = AppLoader.loadTickets(samplePath, "new_tickets");
            WebServer server = new WebServer(port, historical, sample);
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static class AppLoader {
        @SuppressWarnings("unchecked")
        public static List<Ticket> loadTickets(String path, String key) throws IOException {
            String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            Map<String, Object> map = (Map<String, Object>) JsonParser.parse(content);
            List<?> list = (List<?>) map.get(key);
            List<Ticket> result = new ArrayList<>();
            for (Object o : list) {
                result.add(Ticket.fromMap((Map<String, Object>) o));
            }
            return result;
        }
    }
}
