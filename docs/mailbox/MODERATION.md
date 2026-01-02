# Mailbox Moderation Workflow

This document describes the moderation system for the DevMod mailbox, including spam detection, content filtering, and ticket workflow management.

## Overview

The mailbox moderation system consists of three main components:

1. **SpamDetector** - Scores messages for spam likelihood
2. **ContentFilter** - Blocks or censors prohibited content
3. **TicketWorkflow** - Manages report lifecycle with role-based access

## Spam Detection

### Scoring System

Each message is scored based on multiple signals. Higher score = more likely spam.

| Score Range | Action |
|-------------|--------|
| 0-49 | Clean - deliver normally |
| 50-99 | Suspicious - flag for review |
| 100+ | Spam - block delivery |

### Scoring Signals

| Signal | Points | Trigger |
|--------|--------|---------|
| Very High Frequency | 60 | >10 messages/minute |
| High Frequency | 30 | >5 messages/minute |
| Duplicate Content | 50 | Same message repeated |
| Mass Recipient | 40 | Same sender, many recipients |
| Excessive Caps | 15 | >70% uppercase (length >20) |
| Excessive Symbols | 20 | >30% symbols |
| Excessive Length | 10 | Very long message |
| New Account | 15 | First-time sender |
| Empty Subject | 10 | No subject line |
| Short Body | 5 | Very short body |

### Configuration

```java
SpamDetector.INSTANCE.setSpamThreshold(100);
SpamDetector.INSTANCE.setSuspiciousThreshold(50);
SpamDetector.INSTANCE.setMaxMessagesPerWindow(10);
SpamDetector.INSTANCE.setTrackingWindowMs(60_000);
SpamDetector.INSTANCE.setEnabled(true);
```

## Content Filtering

### Filter Actions

| Action | Behavior |
|--------|----------|
| `BLOCK` | Reject the message entirely |
| `FLAG` | Allow but flag for review |
| `CENSOR` | Replace prohibited words with asterisks |

### Word Filtering

Content filter uses word boundary matching (not substring):
- "badword" matches "hello badword there"
- "badword" does NOT match "verybadwordly"

### Pattern Matching

Custom regex patterns can be added for complex filtering:

```java
ContentFilter.INSTANCE.addProhibitedPattern("\\$\\d+\\.\\d{2}");  // Prices
ContentFilter.INSTANCE.addProhibitedWord("spam");
```

### Censoring

When using `CENSOR` action, prohibited words are replaced with asterisks:
- Replacement length is capped at 64 characters
- Original message structure is preserved

## Ticket Workflow

### State Diagram

```
                 ┌─────────────────────────────────────────────┐
                 │                                             ▼
 [OPEN] ───────► [ASSIGNED] ───────► [IN_PROGRESS] ───────► [RESOLVED] ───► [CLOSED]
   │                 │                      │                    │              │
   │                 │                      │                    │              │
   ▼                 ▼                      ▼                    ▼              │
 [CLOSED]◄─────────────────────────────────────────────────[REOPEN]◄──────────┘
```

### Actor Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| `REPORTER` | Original ticket submitter | Close own tickets, reopen |
| `MODERATOR` | Staff member | Assign, update status, comment |
| `ADMIN` | Elevated privileges | All transitions, override guards |
| `SYSTEM` | Automated actions | SLA escalation, auto-close |

### Ticket Priorities

| Priority | SLA Response Time | Escalation After |
|----------|-------------------|------------------|
| `CRITICAL` | 1 hour | 2 hours |
| `HIGH` | 4 hours | 8 hours |
| `MEDIUM` | 24 hours | 48 hours |
| `LOW` | 72 hours | 1 week |

### Auto-Transitions

The `AutoTransitionService` handles automatic state changes:

1. **Auto-Close**: Resolved tickets close after 7 days of inactivity
2. **SLA Escalation**: Tickets breaching SLA are escalated in priority
3. **Auto-Assign**: Open tickets can be assigned to available moderators

Configuration:
```java
AutoTransitionService.INSTANCE.setAutoCloseDuration(Duration.ofDays(7));
AutoTransitionService.INSTANCE.setAutoCloseEnabled(true);
AutoTransitionService.INSTANCE.setSlaEscalationEnabled(true);
AutoTransitionService.INSTANCE.setAutoAssignEnabled(false);
```

## Redaction

### Field-Level Redaction

Sensitive fields can be redacted for non-privileged viewers:

```java
TicketRedaction.redactForViewer(ticket, viewerRole);
```

Redacted fields by role:
- `REPORTER`: Can see own ticket fully
- `MODERATOR`: Can see all except internal notes
- `PUBLIC`: Limited to public fields only

## Rate Limiting

Per-IP and per-account rate limits protect against abuse:

| Action | Limit | Block Duration |
|--------|-------|----------------|
| Mailbox Send | 30/min | 5 min |
| Ticket Create | 5/min | 15 min |

## Audit Logging

All moderation actions are logged for audit:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "action": "ticket.transition",
  "actor": "uuid-of-moderator",
  "actorRole": "MODERATOR",
  "ticketId": "ticket-uuid",
  "fromStatus": "OPEN",
  "toStatus": "ASSIGNED",
  "comment": "Assigned to review"
}
```

### Audit Export

Admins can export audit logs:

```
GET /api/mailbox/audit/export?from=2024-01-01&to=2024-01-31
```

## Reputation System

High-risk actions are gated by sender reputation:

| Reputation | Actions Allowed |
|------------|-----------------|
| NEW | 5 messages/day, no attachments |
| TRUSTED | 50 messages/day, small attachments |
| VERIFIED | 200 messages/day, all features |

Reputation increases with:
- Account age
- Successful message deliveries
- No spam/abuse reports

Reputation decreases with:
- Spam detection hits
- Content filter blocks
- User reports

## Metrics

### Available Metrics

| Metric | Description |
|--------|-------------|
| `totalMessagesScored` | Messages processed by spam detector |
| `messagesBlockedAsSpam` | Messages blocked (score >= 100) |
| `messagesFlagged` | Messages flagged for review |
| `contentFilterBlocks` | Messages blocked by content filter |
| `ticketTransitions` | Total state transitions |
| `slaBreaches` | Tickets that exceeded SLA |

### Monitoring Dashboard

Access via admin panel: `/admin/moderation/dashboard`

## Configuration Files

### spam-rules.json
```json
{
  "enabled": true,
  "spamThreshold": 100,
  "suspiciousThreshold": 50,
  "maxMessagesPerWindow": 10,
  "trackingWindowMs": 60000,
  "weights": {
    "highFrequency": 30,
    "duplicateContent": 50,
    "excessiveCaps": 15
  }
}
```

### content-filter.json
```json
{
  "enabled": true,
  "action": "BLOCK",
  "prohibitedWords": ["spam", "scam"],
  "prohibitedPatterns": ["https?://[^\\s]+"]
}
```

## Troubleshooting

### High Spam Rates
1. Review spam threshold settings
2. Check for new attack patterns
3. Consider adding new detection signals

### False Positives
1. Review flagged messages
2. Adjust scoring weights
3. Add whitelist for known-good senders

### Ticket Backlog
1. Enable auto-assign
2. Review SLA settings
3. Check moderator availability
