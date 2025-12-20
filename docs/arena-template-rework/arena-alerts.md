# Arena Alerts Runbook (DD68)

This document defines the monitoring thresholds, ownership, and escalation procedures for the Arena Template system.

## Alert Thresholds

| Metric | WARN Threshold | CRITICAL Threshold | Owner |
|--------|----------------|-------------------|-------|
| Build P95 | > 4s | > 5s | Core Dev |
| Build P99 | > 8s | > 10s | Core Dev |
| Rollback Rate | > 0.5% | > 1% | Core Dev |
| Completion Rate | < 80% | < 75% | Game Designer |
| Pool Miss Rate | > 20% | > 30% | Core Dev |
| Error Rate | > 1% | > 5% | Core Dev |
| Resolve P95 | > 400ms | > 500ms | Core Dev |

## Escalation Procedures

### Level 1: Automated Response (0-5 min)

1. **Alert fires** → Slack notification to `#arena-alerts`
2. **Dashboard link** included in alert
3. **Affected template(s)** identified automatically
4. **Recent changes** shown (git commits, config changes)

### Level 2: On-Call Response (5-30 min)

1. **On-call engineer** acknowledges alert
2. **Verify not false positive** using dashboard
3. **Check recent deploys** for correlation
4. **Apply quick fix** if obvious (config rollback, disable template)

### Level 3: Tech Lead Escalation (30+ min)

**Trigger**: Alert unresolved for 30 minutes

1. **Page Tech Lead** via PagerDuty
2. **War room** created if P0/P1
3. **Incident ticket** created automatically
4. **Stakeholder notification** for user-facing impact

## Automatic Actions

### Template Auto-Disable

**Trigger**: 10 rollbacks/hour for a single template

```
IF rollback_count(template_id, last_1_hour) >= 10 THEN
  disable_template(template_id)
  alert(CRITICAL, "Template auto-disabled: {template_id}")
  create_incident()
END
```

**Recovery**:
1. Template remains disabled until manual review
2. Root cause must be documented
3. Fix deployed to staging first
4. Re-enable requires explicit approval

### Pool Auto-Disable (DD65)

**Trigger**: Pool miss rate > 50% for 3 consecutive checks (15 min)

```
IF pool_miss_rate > 0.50 AND consecutive_high_miss_checks >= 3 THEN
  disable_pool()
  alert(WARN, "Pool auto-disabled due to high miss rate")
END
```

## Alert Definitions

### build.p95_exceeded

**Severity**: CRITICAL if > 8s, WARN if > 4s

**Actions**:
1. Check concurrent builds count
2. Check MSPT/TPS impact
3. Check template size (block count)
4. Review contention telemetry

**Dashboard Query**:
```sql
SELECT template_id, COUNT(*) as builds,
       PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY build_ms) as p95
FROM arena_builds
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY template_id
HAVING p95 > 5000
ORDER BY p95 DESC;
```

### rollback.rate_exceeded

**Severity**: CRITICAL if > 2%, WARN if > 0.5%

**Actions**:
1. Query failures by template
2. Check failure reason distribution
3. Review recent template changes
4. Check budget exceeded vs timeout vs error

**Dashboard Query**:
```sql
SELECT template_id,
       COUNT(*) FILTER (WHERE result = 'rollback') as rollbacks,
       COUNT(*) as total,
       rollbacks::float / total as rate
FROM arena_builds
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY template_id
HAVING rate > 0.005
ORDER BY rate DESC;
```

### completion.low

**Severity**: CRITICAL if < 70%, WARN if < 80%

**Investigation**:
1. Is this a difficulty issue or technical issue?
2. Check player feedback/reports
3. Compare with baseline per template
4. Review wave timing and spawn rates

**Owner**: Game Designer (primary), Core Dev (technical issues)

## Contacts

| Role | Contact | Schedule |
|------|---------|----------|
| Core Dev On-Call | PagerDuty rotation | 24/7 |
| Tech Lead | @tech-lead Slack | Business hours + escalation |
| Game Designer | @game-design Slack | Business hours |
| SRE | @sre-team Slack | 24/7 |

## Related Documents

- [TODO_ARENA_TEMPLATE.md](./TODO_ARENA_TEMPLATE.md) - Full design decisions
- [AnomalyThresholds.java](../../src/main/java/com/devmod/arena/monitoring/AnomalyThresholds.java) - Threshold implementation
- [AlertRouter.java](../../src/main/java/com/devmod/arena/alert/AlertRouter.java) - Alert routing implementation
