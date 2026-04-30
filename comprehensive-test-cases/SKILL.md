---
name: comprehensive-test-cases
description: Use when writing tests for features, especially security-critical, user-facing, or business-critical functionality. Ensures edge cases, error paths, and boundary conditions are covered.
---

# Comprehensive Test Cases

## Overview

Write tests that find bugs before users do. Cover happy paths, error paths, boundaries, and interactions.

**Core principle:** Untested code is broken code you haven't found yet.

## When to Use

- Writing tests for new features
- Testing bug fixes
- Code review: "Are these tests sufficient?"
- Security-critical code (auth, payments, data access)
- User-facing functionality

**Red flags - add more tests:**
- Only happy path tested
- No error case coverage
- Security code without negative tests
- "Edge cases are obvious"
- Test coverage <80% for critical code

## The Test Pyramid

```
       /\
      /  \     E2E (few)
     /----\
    /      \   Integration (some)
   /--------\
  /          \ Unit (many)
 /------------\
```

**Distribution:**
- **Unit tests:** 70% - Fast, isolated, test logic
- **Integration tests:** 20% - Test component interactions
- **E2E tests:** 10% - Test critical user flows

## Test Case Categories

For each feature, write tests in ALL categories:

### 1. Happy Path (The "Should Work" Cases)

```typescript
// What should happen with valid input
it('should calculate total with valid items', () => {
  const cart = new Cart([{ price: 10 }, { price: 20 }]);
  expect(cart.total()).toBe(30);
});

it('should return user when credentials valid', async () => {
  const user = await auth.login('valid@email.com', 'correct-password');
  expect(user.id).toBeDefined();
});
```

**Coverage:** 1-2 tests per public method

### 2. Error Cases (The "Should Fail" Cases)

```typescript
// What happens with invalid input
it('should throw when cart is empty', () => {
  const cart = new Cart([]);
  expect(() => cart.checkout()).toThrow(EmptyCartError);
});

it('should reject invalid email format', async () => {
  await expect(auth.login('not-an-email', 'password'))
    .rejects.toThrow(ValidationError);
});

it('should throw when user not found', async () => {
  await expect(auth.login('unknown@email.com', 'password'))
    .rejects.toThrow(InvalidCredentialsError);
  // SECURITY: Don't reveal user doesn't exist (same error as wrong password)
});
```

**Coverage:** ALL error paths (null, undefined, invalid, unauthorized)

### 3. Boundary Conditions (The Edge Cases)

```typescript
// Test at boundaries and just beyond
it('should accept age exactly at minimum (18)', () => {
  expect(validator.isValidAge(18)).toBe(true);
});

it('should reject age just below minimum (17)', () => {
  expect(validator.isValidAge(17)).toBe(false);
});

it('should handle empty string', () => {
  expect(validator.isValidEmail('')).toBe(false);
});

it('should handle maximum array size', () => {
  const hugeArray = new Array(10000).fill('item');
  expect(() => processor.process(hugeArray)).not.toThrow();
});

it('should handle exact timeout duration', async () => {
  // Test at boundary: exactly at timeout vs 1ms over
  const result = await fetchWithTimeout(url, 1000);
  expect(result.timedOut).toBe(false);
});
```

**Boundaries to test:**
- Minimum/maximum values
- Empty collections
- Empty strings
- Zero
- One item vs many items
- Maximum limits
- Timeout durations

### 4. Interaction Cases (Combined Conditions)

```typescript
// Multiple conditions interact
it('should lock account after 5 failed attempts', async () => {
  // Try 4 times - should work
  for (let i = 0; i < 4; i++) {
    await expect(auth.login('user@test.com', 'wrong'))
      .rejects.toThrow(InvalidCredentialsError);
  }
  
  // 5th attempt - account locked
  await expect(auth.login('user@test.com', 'correct'))
    .rejects.toThrow(AccountLockedError);
});

it('should apply both discount and tax', () => {
  // Interaction: discount then tax vs tax then discount
  const order = new Order({ subtotal: 100, discount: 0.1, taxRate: 0.08 });
  // Expected: (100 - 10) * 1.08 = 97.20
  expect(order.total()).toBe(97.20);
});
```

**Interactions to test:**
- Multiple flags/options combined
- Sequential operations
- Concurrent operations
- Order-dependent operations
- State changes over time

## The Checklist

Before submitting PR, verify:

```markdown
## Test Coverage Checklist

### Functionality
- [ ] Happy path works
- [ ] Each error path throws correctly
- [ ] Each validation rule enforced
- [ ] Each branch tested (if/else, switches)

### Boundaries
- [ ] Minimum values
- [ ] Maximum values
- [ ] Empty inputs
- [ ] Maximum size limits
- [ ] Timeout boundaries

### Security (if applicable)
- [ ] Unauthorized access blocked
- [ ] Invalid tokens rejected
- [ ] SQL injection prevented
- [ ] XSS prevented (output encoded)
- [ ] Rate limiting enforced

### State
- [ ] Initial state correct
- [ ] State transitions work
- [ ] Cleanup happens (connections closed, temp files deleted)

### Integration
- [ ] External service failures handled
- [ ] Database transactions roll back on error
- [ ] API contracts maintained
```

## Test Structure Template

```typescript
describe('FeatureName', () => {
  // Setup
  beforeEach(() => {
    // Reset state before each test
  });

  describe('happy path', () => {
    it('should [expected behavior]', () => {
      // Test
    });
  });

  describe('error cases', () => {
    it('should throw [error] when [condition]', () => {
      // Test
    });
    
    it('should throw [error] when [other condition]', () => {
      // Test
    });
  });

  describe('boundaries', () => {
    it('should handle [minimum value]', () => {
      // Test
    });
    
    it('should handle [maximum value]', () => {
      // Test
    });
  });

  describe('interactions', () => {
    it('should [behavior] when [combined conditions]', () => {
      // Test
    });
  });
});
```

## Security-Critical Test Cases

Authentication/authorization requires EXTRA tests:

```typescript
describe('authentication', () => {
  // Happy path
  it('should login with valid credentials', () => { });

  // Error cases (same error message to prevent user enumeration)
  it('should reject invalid password', async () => {
    await expect(login('valid@email.com', 'wrong'))
      .rejects.toThrow('Invalid credentials');
  });

  it('should reject non-existent user (same error)', async () => {
    await expect(login('nonexistent@email.com', 'any'))
      .rejects.toThrow('Invalid credentials');  // Same message!
  });

  // Brute force protection
  it('should lock after max attempts', async () => { });
  it('should require CAPTCHA after 3 failures', async () => { });

  // Token security
  it('should reject expired tokens', async () => { });
  it('should reject tampered tokens', async () => { });
  it('should reject tokens from different issuer', async () => { });

  // Session security
  it('should invalidate all sessions on password change', async () => { });
  it('should expire inactive sessions', async () => { });
});
```

## Common Mistakes

### ❌ Only Happy Path
```typescript
// Missing error cases
it('should create user', async () => {
  const user = await createUser({ email: 'test@test.com' });
  expect(user).toBeDefined();
});
// What about duplicate email? Invalid format? Database error?
```

### ✅ Comprehensive Coverage
```typescript
it('should create user with valid data', async () => { });
it('should reject duplicate email', async () => { });
it('should reject invalid email format', async () => { });
it('should trim whitespace from inputs', async () => { });
it('should hash password before storing', async () => { });
```

### ❌ Testing Implementation Not Behavior
```typescript
// Brittle - tests internal details
it('should call database with correct query', () => {
  service.getUser(123);
  expect(db.query).toHaveBeenCalledWith(
    'SELECT * FROM users WHERE id = ?',
    [123]
  );
});
```

### ✅ Testing Behavior
```typescript
// Robust - tests what matters
it('should return user when found', async () => {
  const user = await service.getUser(123);
  expect(user.id).toBe(123);
  expect(user.email).toBeDefined();
});

it('should throw when user not found', async () => {
  await expect(service.getUser(999))
    .rejects.toThrow(NotFoundError);
});
```

### ❌ Missing Race Conditions
```typescript
// What's tested
it('should decrement stock', async () => {
  await purchase(item, 1);
  expect(item.stock).toBe(9);
});
```

### ✅ Testing Concurrency
```typescript
// What should be tested
it('should handle concurrent purchases', async () => {
  const promises = Array(10).fill(null).map(() => 
    purchase(item, 1)
  );
  await Promise.all(promises);
  expect(item.stock).toBe(0);  // Not negative!
});
```

## Quick Reference

| Feature Type | Minimum Test Count | Focus |
|--------------|-------------------|-------|
| Simple getter | 2 | Returns value, handles null |
| Calculation | 4-6 | Valid inputs, boundaries, edge cases |
| CRUD operation | 6-8 | Create, read, update, delete, errors, not found |
| Authentication | 10+ | Valid, invalid, locked, expired, concurrent |
| Payment | 12+ | Success, failure, retry, idempotency, fraud |
| API endpoint | 8-10 | Success, validation, auth, errors, rate limits |

## Red Flags - Add Tests

- "This is simple, it doesn't need tests"
- "I'll add tests later" (you won't)
- Only one test per method
- No error case tests
- Tests don't match code changes
- "The code is obvious"
- Security code without negative tests

## Real-World Impact

| Scenario | Without Tests | With Tests |
|----------|--------------|------------|
| Deploy Friday | Broken in production | Caught in CI |
| Refactor | Afraid to touch code | Confidence to improve |
| New developer | Breaks things unknowingly | Tests show intent |
| Security review | Vulnerabilities found | Prevents exploits |
| Bug reports | User finds bugs first | CI finds bugs first |

**Bottom line:** Write tests that would have caught your last bug. If you don't test it, it's broken.
