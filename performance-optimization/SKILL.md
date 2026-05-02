---
name: performance-optimization
description: Use when asked to optimize slow code, improve performance, or speed up operations. Prevents premature optimization and ensures measurement before changes.
---

# Performance Optimization

## Overview

Measure first, optimize second. Profile before guessing. This skill prevents premature optimization and ensures changes actually improve performance.

**Core principle:** You cannot optimize what you haven't measured.

## When to Use

- Task says "optimize", "speed up", "improve performance"
- Code review comments about inefficiency
- User reports slowness
- You feel the urge to make code "more efficient"

**Red flags - STOP and measure first:**

- You don't know the bottleneck location
- You're optimizing without profiling data
- "This looks inefficient" (looks ≠ is)
- Optimizing code that runs rarely

## The Process

### Step 1: Profile Before Changing

```bash
# Node.js - built-in profiler
node --prof app.js
node --prof-process isolate-0x*.log > profile.txt

# Or use clinic.js
npm install -g clinic
doctor
clinic doctor -- node app.js
```

```python
# Python - cProfile
python -m cProfile -s cumulative script.py

# Or use py-spy for production
py-spy top -- python script.py
```

**What to look for:**

- Hot paths (functions called most often)
- Time per function call
- I/O wait vs CPU time

### Step 2: Form Hypothesis

Document your hypothesis before optimizing:

```markdown
## Performance Hypothesis

**Problem:** [What the profile shows]
**Hypothesis:** [What you think is slow and why]
**Expected improvement:** [Quantified goal: "reduce from 500ms to <100ms"]
**Measurement:** [How you'll verify]
```

### Step 3: Optimize

Apply ONE optimization at a time. Measure after each.

**Common optimizations (in order of impact):**

| Optimization                     | When to Apply                      |
| -------------------------------- | ---------------------------------- |
| Algorithmic (O(n²) → O(n log n)) | Profile shows algorithm dominates  |
| Batch I/O operations             | Profile shows many small I/O calls |
| Add caching                      | Same data fetched repeatedly       |
| Parallelize CPU work             | CPU-bound, independent work        |
| Pre-allocate arrays              | Known size upfront, hot path       |
| Micro-optimizations              | Everything else exhausted          |

**Micro-optimizations to AVOID unless profile proves need:**

- `for` loop vs `forEach` vs `map`
- `const` vs `let` vs `var`
- Property access caching (`const len = arr.length`)
- Manual inline expansion

### Step 4: Verify

```bash
# Before/after comparison
# Run 5 times, take median

# Before optimization:
median: 523ms
stddev: 12ms

# After optimization:
median: 487ms  ← Is this meaningful improvement?
stddev: 15ms
```

**Statistical significance:**

- Is improvement > 2× standard deviation?
- Is improvement > 10%? (below this, likely noise)
- Did you test with realistic data sizes?

### Step 5: Document or Revert

```markdown
## Optimization Result

**Applied:** [What changed]
**Measured improvement:** [Before → After, with context]
**Trade-offs:** [Code complexity, memory usage]
**Verdict:** [Kept / Reverted]
```

If improvement < 10% or not statistically significant → **REVERT**.

## Quick Reference

| Situation       | First Action                      |
| --------------- | --------------------------------- |
| "This is slow"  | Profile it                        |
| Database slow   | Check query plan, add index       |
| API slow        | Check N+1 queries, add caching    |
| Startup slow    | Lazy load, defer init             |
| Build slow      | Check incremental builds, caching |
| Test suite slow | Parallelize, check setup/teardown |

## Common Mistakes

### ❌ Premature Optimization

```typescript
// Optimized without profiling
function processUsers(users: User[]) {
  // Complex batching, worker threads, etc.
  // ...50 lines of optimization...
}
// Result: 2% improvement, 10x code complexity
```

### ✅ Measure First

```typescript
// Profile showed transformUser() takes 95% of time
function processUsers(users: User[]) {
  // Only optimize the actual bottleneck
  return users.map(u => optimizedTransform(u));
}
```

### ❌ Optimizing Cold Paths

```typescript
// This runs once on startup - don't optimize
function loadConfig() {
  // Complex caching, memoization...
}
```

### ✅ Optimizing Hot Paths

```typescript
// This runs 1000x/second - optimize carefully
function processEvent(event: Event) {
  // Profiled and optimized bottleneck
}
```

### ❌ Micro-optimizations

```typescript
// Don't do this without profiling proof
const len = array.length;
for (let i = 0; i < len; i++) {}
// vs
for (const item of array) {
}
```

## Red Flags - STOP and Verify

- Optimizing code that runs <1% of time
- Making code harder to read for unmeasured gains
- Applying optimizations "just in case"
- Not reverting optimizations that don't help
- Optimizing without before/after benchmarks

## Real-World Results

| Project     | What We Measured   | What Actually Helped                                    |
| ----------- | ------------------ | ------------------------------------------------------- |
| API latency | Assumed DB queries | Actual issue: JSON serialization (50x faster after fix) |
| Build time  | Assumed TypeScript | Actual issue: unused imports causing full rebuilds      |
| Test suite  | Assumed test logic | Actual issue: database setup/teardown per test          |

**Bottom line:** Profile first. 90% of assumptions about bottlenecks are wrong.
