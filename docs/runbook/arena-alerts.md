# Arena Alerts Runbook (DD68)

## Overview
This runbook provides guidance for handling arena system alerts during the 48h monitoring period and beyond.

## Alert Ownership

| Alert Type | Primary Owner | Escalation |
|------------|---------------|------------|
| Build Performance | Arena Team | Platform Lead |
| Rollback Rate | Arena Team | On-Call SRE |
| Pool Metrics | Arena Team | Platform Lead |
| Error Rate | On-Call SRE | Engineering Manager |
| Security | Security Team | CISO |

## Alert Thresholds

### Build P95 Latency
- **Warning**: > 4 seconds
- **Critical**: > 5 seconds (KPI breach)

### Rollback Rate
- **Warning**: > 0.5%
- **Critical**: > 1% (KPI breach)

### Completion Rate
- **Warning**: < 80%
- **Critical**: < 75% (KPI breach)

### Pool Miss Rate
- **Warning**: > 30%
- **Critical**: > 50% (auto-disable triggered)

### Error Rate
- **Warning**: > 1%
- **Critical**: > 5%

## Response Procedures

### Alert: Build P95 > 5s

1. **Immediate Actions**:
   - Check current MSPT (milliseconds per tick)
   - Check active build count
   - Verify chunk loading performance

2. **Investigation**:
   ```
   - Dashboard: Arena Build Performance
   - Logs: arena.build.*
   - Metrics: build_duration_p95, blocks_per_second
   ```

3. **Mitigation**:
   - Enable backpressure if not active
   - Reduce blocks_per_tick config
   - Consider pausing non-essential builds

4. **Resolution**:
   - Identify root cause (large templates, slow chunks, server load)
   - Apply permanent fix
   - Update thresholds if appropriate

### Alert: Rollback Rate > 1%

1. **Immediate Actions**:
   - Check recent deployment changes
   - Identify failing templates

2. **Investigation**:
   ```
   - Dashboard: Arena Rollback Analysis
   - Logs: arena.build.failure, arena.build.rollback
   - Query: SELECT template_id, count(*) FROM rollbacks GROUP BY template_id
   ```

3. **Mitigation**:
   - Disable problematic templates
   - Increase budget limits if appropriate
   - Enable fallback mode

4. **Resolution**:
   - Fix template issues
   - Add validation rules
   - Update test coverage

### Alert: Completion Rate < 75%

1. **Immediate Actions**:
   - Check player disconnect rate
   - Verify arena stability

2. **Investigation**:
   ```
   - Dashboard: Arena Session Completion
   - Logs: arena.session.*, arena.cleanup.*
   - Metrics: session_duration, disconnect_reason
   ```

3. **Mitigation**:
   - Enable graceful degradation
   - Increase session timeouts

4. **Resolution**:
   - Identify completion blockers
   - Fix arena stability issues
   - Improve error handling

### Alert: Pool Miss Rate > 50%

1. **Immediate Actions**:
   - Pool has auto-disabled
   - Verify build system is functioning

2. **Investigation**:
   ```
   - Dashboard: Arena Pool Performance
   - Logs: arena.pool.*
   - Metrics: pool_hit_rate, pool_size, eviction_count
   ```

3. **Mitigation**:
   - Pool auto-disabled, builds now on-demand
   - Monitor build performance

4. **Resolution**:
   - Analyze usage patterns
   - Adjust pool sizing
   - Re-enable with proper config

## Escalation Matrix

| Severity | Response Time | Escalation Time |
|----------|---------------|-----------------|
| P1 Critical | 5 min | 15 min |
| P2 High | 15 min | 1 hour |
| P3 Medium | 1 hour | 4 hours |
| P4 Low | 4 hours | Next business day |

## Contacts

- **Arena Team Slack**: #arena-team
- **On-Call SRE**: PagerDuty rotation
- **Platform Lead**: @platform-lead
- **Engineering Manager**: @eng-manager

## Post-Incident

After resolving any P1/P2 incident:

1. Create incident report within 24 hours
2. Update this runbook if needed
3. Add new alerts for gaps discovered
4. Review in weekly team meeting
