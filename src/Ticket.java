package src;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data model representing both historical resolved tickets and incoming new tickets.
 */
public class Ticket {
    private final String ticketId;
    private final String category;
    private final String subject;
    private final String description;
    private final String resolutionNotes;
    private final List<String> tags;
    private final String createdAt;
    private final String status;

    public Ticket(String ticketId, String category, String subject, String description,
                  String resolutionNotes, List<String> tags, String createdAt, String status) {
        this.ticketId = ticketId != null ? ticketId : "UNKNOWN";
        this.category = category != null ? category : "general";
        this.subject = subject != null ? subject : "";
        this.description = description != null ? description : "";
        this.resolutionNotes = resolutionNotes != null ? resolutionNotes : "";
        this.tags = tags != null ? tags : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : "";
        this.status = status != null ? status : "new";
    }

    @SuppressWarnings("unchecked")
    public static Ticket fromMap(Map<String, Object> map) {
        String id = (String) map.get("ticket_id");
        String cat = (String) map.get("category");
        String subj = (String) map.get("subject");
        String desc = (String) map.get("description");
        String res = (String) map.get("resolution_notes");
        String created = (String) map.get("created_at");
        String stat = (String) map.get("status");

        List<String> tags = new ArrayList<>();
        Object rawTags = map.get("tags");
        if (rawTags instanceof List<?>) {
            for (Object item : (List<?>) rawTags) {
                if (item != null) {
                    tags.add(item.toString());
                }
            }
        }

        return new Ticket(id, cat, subj, desc, res, tags, created, stat);
    }

    public String getTicketId() { return ticketId; }
    public String getCategory() { return category; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public String getResolutionNotes() { return resolutionNotes; }
    public List<String> getTags() { return tags; }
    public String getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }

    public String getCombinedQueryText() {
        return subject + " " + description;
    }

    @Override
    public String toString() {
        return String.format("[%s | %s] %s", ticketId, category, subject);
    }
}
