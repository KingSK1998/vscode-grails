---
name: read-tests-before-change
description: Use when modifying existing code, adding features to existing modules, or refactoring. Ensures understanding of existing test coverage and patterns before changes.
---

# Read Tests Before Feature Change

## Overview

Understand what exists before changing it. Read existing tests to learn intent, edge cases, and patterns before modifying code.

**Core principle:** Tests are the specification. Read them first.

## When to Use

- Adding features to existing code
- Modifying existing functions
- Refactoring working code
- Bug fixes
- Code review: "Does this change break existing expectations?"

**Red flags - STOP and read tests:**
- You're about to modify code with existing tests
- "I'll just add the feature and write tests after"
- You haven't looked at `*.test.ts` / `*_spec.rb` / `test_*.py`
- The file you're editing has >100 lines but you haven't seen tests

## The Process

### Step 1: Find Existing Tests

```bash
# Find test files for the code you're changing
# Convention patterns:
find . -name "*.test.ts" | grep -i "userService"
find . -name "*_test.go" | grep -i "handler"
find . -name "test_*.py" | grep -i "auth"
find . -name "*.spec.js" | grep -i "component"
```

**Test file locations:**
| Framework | Test Location |
|-----------|---------------|
| Jest/Vitest | `src/feature/service.test.ts` or `__tests__/service.test.ts` |
| Go | `src/feature/service_test.go` |
| Python pytest | `tests/test_service.py` or `src/test_service.py` |
| Ruby RSpec | `spec/feature/service_spec.rb` |

### Step 2: Read the Tests

**Read for these patterns:**

```markdown
## Test Reading Checklist

### Setup Patterns
- [ ] How are dependencies mocked?
- [ ] What's the test data fixture pattern?
- [ ] What's the database/transaction setup?

### Test Structure
- [ ] What edge cases are already covered?
- [ ] What error scenarios are tested?
- [ ] What are the boundary conditions?
- [ ] What's NOT tested (gaps)?

### Behavior Contracts
- [ ] What does the code promise to do?
- [ ] What happens on invalid input?
- [ ] What side effects are expected?
- [ ] What's the expected performance?
```

**Example: Reading AuthService tests**

```typescript
// BEFORE adding "filter by age range", read existing tests:

it('should return user by email', async () => {
  // Learn: Uses test database, not mocks
});

it('should throw NotFound when user missing', async () => {
  // Learn: Throws NotFound, not null
});

it('should validate email format', async () => {
  // Learn: Validation happens here, not in controller
});

// GAP: No tests for filtering, list operations
// → New feature needs tests, but follow existing patterns
```

### Step 3: Document Your Understanding

```markdown
## Test Analysis: UserService
**File:** `src/services/userService.test.ts`

**Existing Coverage:**
- ✓ Get user by ID
- ✓ Get user by email  
- ✓ Create user with validation
- ✗ List/filter users (NOT COVERED - this is my change)

**Patterns Used:**
- Uses in-memory test database (not mocks)
- Throws domain errors (NotFoundError, ValidationError)
- Setup creates test user in beforeEach

**My Feature: Age Range Filter**
- Need to add: `getUsersByAgeRange(min, max)`
- Follow pattern: Same test setup, same error handling
- Edge cases to cover: Invalid range (min > max), negative ages, boundaries
```

### Step 4: Implement Following Patterns

```typescript
// Following existing test patterns:

describe('getUsersByAgeRange', () => {
  beforeEach(async () => {
    // Same setup pattern as existing tests
    await db.users.create({ name: 'Alice', age: 25 });
    await db.users.create({ name: 'Bob', age: 35 });
    await db.users.create({ name: 'Charlie', age: 45 });
  });

  it('should return users within age range', async () => {
    const users = await service.getUsersByAgeRange(30, 40);
    expect(users).toHaveLength(1);
    expect(users[0].name).toBe('Bob');
  });

  it('should throw ValidationError when min > max', async () => {
    // Following error handling pattern from existing tests
    await expect(service.getUsersByAgeRange(40, 30))
      .rejects.toThrow(ValidationError);
  });

  it('should include boundary ages', async () => {
    // Edge case learned from existing boundary tests
    const users = await service.getUsersByAgeRange(25, 35);
    expect(users.map(u => u.name)).toContain('Alice');
    expect(users.map(u => u.name)).toContain('Bob');
  });
});
```

## Quick Reference

| Situation | What to Read First |
|-----------|-------------------|
| Adding endpoint | Read existing endpoint tests for patterns |
| Modifying service | Read service unit tests for contracts |
| Refactoring UI | Read component tests for expected behavior |
| Bug fix | Read tests around the bug area - what should happen? |
| Database change | Read integration tests for transaction patterns |

## Common Mistakes

### ❌ Skip Tests, Break Contracts
```typescript
// Didn't read tests - broke existing contract
async getUser(id: string) {
  return this.db.query('SELECT * FROM users WHERE id = ?', [id]);
  // Returns null on miss - but tests expect NotFoundError thrown!
}
```

### ✅ Read Tests, Maintain Contract
```typescript
// Read tests first - knows to throw NotFoundError
async getUser(id: string): Promise<User> {
  const user = await this.db.query('SELECT * FROM users WHERE id = ?', [id]);
  if (!user) throw new NotFoundError(`User ${id} not found`);
  return user;
}
```

### ❌ Inconsistent Test Patterns
```typescript
// Added feature with different test style
it('tests the filter', () => {
  // Different setup, different assertions
  // Hard to maintain
});
```

### ✅ Consistent Patterns
```typescript
// Follows existing test structure exactly
it('should filter users by age range', async () => {
  // Same setup, same assertion style
  // Maintains codebase consistency
});
```

## Red Flags - STOP and Read Tests

- You're about to modify tested code without reading tests
- You're writing tests in a different style than existing ones
- Your "fix" breaks existing tests you haven't looked at
- You don't know what error the code should throw
- You can't explain what the existing code does based on its tests

## Edge Cases to Check

**When tests exist but coverage is incomplete:**
- Document gaps: "No tests for null input"
- Don't fix unrelated gaps in same PR
- Write tests for YOUR changes following existing patterns

**When there are NO tests:**
- Write tests for existing behavior BEFORE changing (characterization tests)
- Document: "No existing tests - wrote characterization tests first"
- Then modify and add tests for new behavior

**When tests are outdated:**
- Update tests if they no longer reflect intended behavior
- Discuss with team if contract should change
- Don't just delete failing tests

## Real-World Impact

| Scenario | Without This Skill | With This Skill |
|----------|------------------|-----------------|
| Add filter feature | Broke error handling contract | Followed existing throw pattern |
| Refactor service | Removed validation tests needed | Read tests, preserved validations |
| Fix bug | Fixed symptom, broke other tests | Understood intent, fixed root cause |
| Add validation | Inconsistent error types | Matched existing error hierarchy |

**Bottom line:** Read tests first. They're the living specification.
