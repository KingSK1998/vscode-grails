---
name: architecture-for-performance
description: Use when designing systems for scale, choosing technology stacks, or making architectural decisions. Prevents over-engineering and ensures simple solutions are considered first.
---

# Architecture for Performance

## Overview

Simple scales better than complex. Start with what works today, add complexity only when metrics prove it's needed.

**Core principle:** It's easier to scale a simple system than fix a complex broken one.

## When to Use

- Designing systems for "high scale"
- Choosing between monolith vs microservices
- Evaluating cloud providers or infrastructure
- Selecting databases or message queues
- "We need to handle X events per second"

**Red flags - STOP and simplify:**

- Proposing microservices with <10 developers
- Adding Kubernetes for <5 services
- Choosing Kafka over simpler queues "for scale"
- Optimizing for 10x current traffic "just in case"
- Architecture diagram has 15+ boxes

## The Decision Framework

### Step 1: Define Actual Requirements

**NOT:** "Handle 100,000 events/second"

**YES:** Answer these specifics:

```markdown
## Performance Requirements

**Throughput:** 100,000 events/sec sustained or peak?
**Latency:** P50, P95, P99 requirements? (e.g., P95 < 100ms)
**Consistency:** Strong, eventual, or relaxed?
**Durability:** Can lose 1s of data? 1min? None?
**Query patterns:** Read-heavy? Write-heavy? Balanced?
**Growth rate:** 2x/year? 10x/year? (affects timeline)
**Team size:** How many people to operate this?
```

**Reality check questions:**

- When do we predict hitting this scale? (next month? next year?)
- What's the cost of being wrong? (can we migrate in 2 weeks?)
- What's the simplest thing that might work?

### Step 2: Benchmark Your Current Stack

Before adding new systems, test what you have:

```bash
# Test throughput of existing database
pgbench -i -s 10 mydb
pgbench -c 10 -j 2 -t 10000 mydb

# Test your API
npm install -g autocannon
autocannon -c 100 -d 30 http://localhost:3000/api/events

# Test message processing
# Write a script that publishes N messages, measure throughput
```

**You might discover:**

- PostgreSQL with partitioning handles 100k writes/sec
- Node.js can process 50k events/sec with proper async patterns
- Your bottleneck is JSON serialization, not the database

### Step 3: Choose Technology

**Decision matrix:**

| Requirement        | Simple Choice                     | Complex Choice          |
| ------------------ | --------------------------------- | ----------------------- |
| 1-10k events/sec   | In-memory queue                   | Kafka                   |
| 10-100k events/sec | Redis Streams/RabbitMQ            | Kafka cluster           |
| 100k+ events/sec   | Kafka or cloud-managed            | Self-managed Kafka      |
| 3-5 services       | Monolith with modules             | Microservices           |
| 10+ services       | Modular monolith or SOA           | Microservices + K8s     |
| Team <5 people     | Managed services                  | Self-hosted anything    |
| Team >20 people    | Platform team builds abstractions | Everyone deploys to K8s |

**The rule:** Add complexity only when simpler option is PROVEN insufficient.

### Step 4: Design for Migration, Not Perfection

```markdown
## Architecture Decision: Event Processing

**Phase 1 (Now):** Redis Streams → Node.js workers → PostgreSQL

- Can handle: 50k events/sec
- Migration cost if wrong: 2 days to add Kafka
- Operational burden: Low (team knows Redis)

**Phase 2 (When metrics show):** Add Kafka

- Trigger: Hitting Redis memory limits OR need persistence
- Migration: Gradual cutover with dual-write period
- Timeline: When we have 2 more engineers

**Phase 3 (Future):** Consider event sourcing

- Trigger: Need audit trail, not performance
- This is a data model change, not scaling need
```

### Step 5: Document Trade-offs

```markdown
## Architecture Decision Record (ADR)

**Decision:** Use PostgreSQL + Redis Streams (not Kafka)

**Context:**

- Need to handle 50k events/sec (measured)
- Team of 4 developers
- 2 months to production

**Options Considered:**

1. **Kafka cluster** - Rejected: 3 devs can't operate Kafka reliably
2. **AWS SQS** - Rejected: Latency too high (P99 > 1s)
3. **Redis Streams** - Accepted: Team knows Redis, sufficient throughput

**Consequences:**

- Positive: Ship in 2 months, team can operate it
- Positive: Can migrate to Kafka later if needed
- Risk: Redis is memory-only (mitigated: consumer commits offsets)
- Risk: No persistence (mitigated: events are transient)

**Migration Path:**
If we outgrow Redis: Add Kafka, dual-write period, cutover consumers.
Estimated effort: 1 week.
```

## Quick Reference

| Scenario      | Simple First         | Consider Complex When                     |
| ------------- | -------------------- | ----------------------------------------- |
| Message queue | Bull Queue (Redis)   | 100k+ msgs/sec, need persistence          |
| Database      | PostgreSQL           | 10TB+ data, specific query patterns       |
| Caching       | Redis                | High read load, simple caching not enough |
| Search        | PostgreSQL full-text | Complex search, faceting, relevance       |
| Async jobs    | Bull/Queue           | Thousands of jobs/sec, complex scheduling |
| Containers    | Docker + systemd     | Need auto-scaling, 10+ services           |
| Orchestration | Docker Compose       | Multi-node, need auto-healing             |
| API Gateway   | Nginx                | Complex auth, rate limiting, transforms   |

## Common Mistakes

### ❌ Over-Engineering

```yaml
# 3 developers, 2 months to ship
architecture:
  - Kafka cluster (3 brokers, ZooKeeper)
  - Kubernetes (EKS)
  - 8 microservices
  - Event sourcing
  - CQRS
# Result: Never shipped, team burned out
```

### ✅ Simple First

```yaml
# Same requirements
architecture:
  - Node.js monolith
  - PostgreSQL
  - Redis for caching
  - Deployed on ECS/VMs
# Result: Shipped in 6 weeks, handles 50k events/sec
# Can migrate to Kafka later if needed
```

### ❌ Resume-Driven Development

- Choosing tech because it's "hot" not because it fits
- "Netflix uses it" (you're not Netflix)
- "Good for my career" (shipping is good for your career)

### ✅ Team-Appropriate Tech

- What can the team operate at 3am?
- What can you debug when production is down?
- What's the learning curve vs timeline?

### ❌ Optimizing for Hypothetical Scale

```markdown
"What if we hit 1 million users next month?"
→ Current: 1,000 users
→ Growth: 10%/month
→ Reality: 18 months to 1M users
→ Decision: Don't optimize for month 18 today
```

### ✅ Optimize for Measured Pain

```markdown
"Database CPU at 90%, query latency spiking"
→ Measured pain
→ Add read replica (2 days work)
→ Problem solved
→ Move on
```

## Red Flags - STOP and Simplify

- Architecture diagram requires scrolling
- Explaining it takes >5 minutes
- Team can't draw it from memory
- Onboarding takes >1 week due to complexity
- "We'll need a dedicated DevOps team" (with <10 devs)
- Local development requires Kubernetes
- More time spent on infrastructure than features

## Migration Paths

**Good architecture is evolvable:**

| Phase | Architecture                          | When to Move On                         |
| ----- | ------------------------------------- | --------------------------------------- |
| 1     | Monolith + single DB                  | Team grows >8, deployment conflicts     |
| 2     | Modular monolith + read replicas      | Specific modules need different scaling |
| 3     | Extract 1-2 services (not everything) | Those services have different lifecycle |
| 4     | Add API gateway, service mesh         | 10+ services, need observability        |

**Rule:** Each phase should last 6-12 months minimum.

## Real-World Results

| Company   | Started With        | Grew To          | Migration Cost                |
| --------- | ------------------- | ---------------- | ----------------------------- |
| Startup A | Node.js + Postgres  | 100k users       | Add read replica: 1 day       |
| Startup B | Microservices day 1 | Didn't ship      | Rewrite to monolith: 3 months |
| Company C | Redis Streams       | 500k events/sec  | Migrate to Kafka: 2 weeks     |
| Company D | Kafka + K8s         | Constant outages | Simplify to SQS: 1 month      |

**Bottom line:** Start simple. Ship fast. Scale based on metrics, not fear.
