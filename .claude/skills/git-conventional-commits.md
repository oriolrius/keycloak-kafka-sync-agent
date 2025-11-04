# Git with Conventional Commits Skill

## Overview
You are an expert in Git version control with strict adherence to the Conventional Commits standard. This skill ensures all commits follow a consistent, semantic format that enables automated changelog generation, semantic versioning, and clear project history.

## 🔴 CRITICAL: Mandatory Conventional Commits

**ALL commits MUST follow the Conventional Commits specification v1.0.0**

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Required Structure

1. **Header** (mandatory): `<type>(<scope>): <subject>`
   - **type**: REQUIRED - Commit type (see types below)
   - **scope**: OPTIONAL - Component or module affected
   - **subject**: REQUIRED - Brief description (lowercase, no period, max 72 chars)

2. **Body** (optional): Detailed explanation of the changes
   - Separated from header by blank line
   - Wrap at 72 characters
   - Explain WHAT and WHY, not HOW

3. **Footer** (optional): Breaking changes and issue references
   - `BREAKING CHANGE:` for breaking changes
   - Issue references: `Closes #123`, `Fixes #456`

## Commit Types

### Primary Types (Use These Most Often)

| Type | Purpose | Example |
|------|---------|---------|
| `feat` | New feature | `feat(auth): add OAuth2 login support` |
| `fix` | Bug fix | `fix(api): handle null pointer in user endpoint` |
| `docs` | Documentation only | `docs(readme): update installation instructions` |
| `style` | Code style changes (formatting, no logic change) | `style(java): apply google java format` |
| `refactor` | Code refactoring (no feature/bug change) | `refactor(database): extract query builder` |
| `perf` | Performance improvements | `perf(cache): optimize Redis connection pooling` |
| `test` | Adding or updating tests | `test(integration): add Kafka consumer tests` |
| `build` | Build system changes | `build(maven): upgrade to Java 21` |
| `ci` | CI/CD changes | `ci(github): add automated release workflow` |
| `chore` | Maintenance tasks | `chore(deps): update dependencies` |

### Secondary Types (Use When Appropriate)

| Type | Purpose | Example |
|------|---------|---------|
| `revert` | Reverts a previous commit | `revert: feat(auth): add OAuth2 login` |
| `security` | Security fixes | `security(crypto): upgrade bcrypt to v5.1.0` |
| `i18n` | Internationalization | `i18n(es): add Spanish translations` |
| `config` | Configuration changes | `config(kafka): update broker settings` |

## Scope Guidelines

### Use Scopes for Large Projects

Common scopes for this project:
- `keycloak` - Keycloak integration
- `kafka` - Kafka messaging
- `sync-agent` - Sync agent service
- `api` - API endpoints
- `database` - Database layer
- `auth` - Authentication
- `config` - Configuration
- `docker` - Docker/containerization
- `testing` - Testing infrastructure
- `docs` - Documentation
- `ci` - CI/CD

### Scope Format
- Lowercase
- Use kebab-case for multi-word scopes: `sync-agent`, `user-service`
- Be consistent across the project

## Subject Line Rules

✅ **DO:**
- Start with lowercase letter
- Use imperative mood ("add" not "added" or "adds")
- No period at the end
- Keep under 72 characters
- Be specific and descriptive

❌ **DON'T:**
- Start with uppercase
- Use past tense
- End with period
- Be vague ("fix stuff", "update code")

### Good Examples
```
feat(kafka): add SSL certificate validation
fix(api): resolve race condition in user creation
docs(readme): clarify environment setup steps
refactor(database): simplify connection pooling logic
```

### Bad Examples
```
Fixed bug                          # ❌ Not conventional, vague
feat(kafka): Added new feature.    # ❌ Past tense, has period
Update stuff                       # ❌ Not conventional, vague
FEAT: NEW FEATURE                  # ❌ All caps, not specific
```

## Body Guidelines

### When to Include a Body

Include a body when:
- The change is complex
- You need to explain WHY, not just WHAT
- There are multiple logical changes
- Breaking changes need explanation
- Context is needed for future maintainers

### Body Format

```
feat(auth): implement OAuth2 authentication flow

Implement OAuth2 authorization code flow for user authentication.
This replaces the previous basic auth system to improve security
and enable SSO integration with corporate identity providers.

The implementation includes:
- Authorization server integration
- Token refresh mechanism
- Session management with Redis
- Logout endpoint with token revocation

This change improves security by using industry-standard OAuth2
instead of custom authentication, reducing potential vulnerabilities.
```

### Body Best Practices

- Wrap at 72 characters
- Use bullet points for multiple changes
- Explain motivation and context
- Reference related issues or docs
- Focus on WHY, not WHAT (code shows WHAT)

## Footer Guidelines

### Breaking Changes

**ALWAYS** document breaking changes in the footer:

```
feat(api): change user endpoint response format

BREAKING CHANGE: The /api/users endpoint now returns an array
under the 'users' key instead of a direct array. Update all clients
to access response.users instead of response directly.

Before: GET /api/users -> [{id: 1}, {id: 2}]
After:  GET /api/users -> {users: [{id: 1}, {id: 2}]}
```

### Issue References

Link commits to issues:

```
fix(kafka): prevent message duplication

Fixes #123
Closes #456
Relates to #789
```

### Multiple Footers

```
feat(payment): add Stripe integration

BREAKING CHANGE: Payment configuration now requires STRIPE_SECRET_KEY
environment variable.

Closes #234
Reviewed-by: John Doe <john@example.com>
```

## Complete Commit Examples

### Feature with Body and Footer

```
feat(kafka): add SSL/TLS encryption support

Implement SSL/TLS encryption for Kafka broker connections to ensure
secure data transmission. This includes:

- SSL configuration via environment variables
- Certificate validation
- Truststore and keystore management
- Automatic certificate rotation support

The implementation follows Kafka best practices and supports both
self-signed certificates for development and CA-signed certificates
for production environments.

Closes #123
```

### Bug Fix with Breaking Change

```
fix(api)!: correct user role validation logic

The previous role validation was case-sensitive, causing authorization
failures for users with roles in different cases. This fix normalizes
all roles to lowercase before validation.

BREAKING CHANGE: All role checks now case-insensitive. If you have
roles stored in mixed case in your database, they will no longer match.
Migrate all roles to lowercase before upgrading.

Fixes #456
```

### Simple Documentation Update

```
docs(readme): add SSL configuration examples
```

### Configuration Change

```
config(docker): enable HTTPS for Keycloak

Update docker-compose.yml to configure Keycloak with HTTPS-only mode.
HTTP endpoint is now disabled for security compliance.

Relates to #789
```

## Breaking Changes Indicator

### Using `!` in Type

You can indicate breaking changes with `!` after the type/scope:

```
feat(api)!: redesign authentication endpoints
```

This is equivalent to:

```
feat(api): redesign authentication endpoints

BREAKING CHANGE: Authentication endpoints have been redesigned.
```

## Revert Commits

Format for reverting commits:

```
revert: feat(auth): add OAuth2 login support

This reverts commit abc123def456.

Reason: OAuth2 integration causing session timeout issues in
production. Reverting until root cause is identified and fixed.
```

## Git Workflow Integration

### Before Committing

1. **Review changes**: `git diff` or `git diff --staged`
2. **Stage files**: `git add <files>`
3. **Write conventional commit**: Follow the format
4. **Verify**: Re-read your commit message

### Commit Command

```bash
git commit -m "$(cat <<'EOF'
feat(kafka): add SSL encryption support

Implement SSL/TLS for secure Kafka communication including
certificate management and auto-rotation.

Closes #123

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

### Multi-file Commits

Group related changes in a single commit:

```bash
# Bad: Multiple commits for one logical change
git commit -m "fix(api): update endpoint"
git commit -m "fix(api): update tests"
git commit -m "fix(api): update docs"

# Good: One commit for one logical change
git add api/ tests/ docs/
git commit -m "fix(api): resolve timeout in user endpoint

Update endpoint logic, add timeout tests, and document new behavior."
```

## Commit Frequency Guidelines

### Atomic Commits

Each commit should:
- ✅ Represent ONE logical change
- ✅ Be self-contained and functional
- ✅ Pass all tests
- ✅ Be revertible independently

### When to Commit

**DO commit when:**
- Feature is complete and tested
- Bug is fixed and verified
- Refactoring maintains functionality
- Documentation is updated for a change

**DON'T commit when:**
- Code doesn't compile
- Tests are failing
- Work is incomplete (use branches/stash)
- Debugging statements are still present

## Validation Tools

### Pre-commit Hook

Create `.git/hooks/commit-msg`:

```bash
#!/bin/sh
# Validate conventional commit format

commit_msg_file=$1
commit_msg=$(cat "$commit_msg_file")

# Regex for conventional commits
pattern="^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert|security|i18n|config)(\(.+\))?(!)?: .{1,72}"

if ! echo "$commit_msg" | grep -qE "$pattern"; then
    echo "❌ ERROR: Commit message does not follow Conventional Commits format"
    echo ""
    echo "Expected format: <type>(<scope>): <subject>"
    echo ""
    echo "Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore"
    echo ""
    echo "Example: feat(kafka): add SSL encryption support"
    exit 1
fi
```

### Commitlint Configuration

`commitlint.config.js`:

```javascript
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      [
        'feat',
        'fix',
        'docs',
        'style',
        'refactor',
        'perf',
        'test',
        'build',
        'ci',
        'chore',
        'revert',
        'security',
        'config'
      ]
    ],
    'subject-case': [2, 'always', 'lower-case'],
    'subject-full-stop': [2, 'never', '.'],
    'header-max-length': [2, 'always', 72]
  }
};
```

## Common Scenarios

### Scenario 1: Adding New Feature

```
feat(sync-agent): implement real-time user synchronization

Add bidirectional sync between Keycloak and external systems with
conflict resolution and retry logic.

Features:
- Real-time change detection via Keycloak events
- Automatic retry with exponential backoff
- Conflict resolution strategies
- Monitoring and alerting integration

Closes #45
```

### Scenario 2: Fixing Production Bug

```
fix(kafka): prevent consumer group rebalancing storm

The consumer was triggering unnecessary rebalances due to aggressive
session timeout settings. Increased session.timeout.ms to 30s and
heartbeat.interval.ms to 3s to prevent false-positive failures.

This resolves the cascading rebalances observed in production that
caused message processing delays.

Fixes #789
```

### Scenario 3: Updating Dependencies

```
chore(deps): upgrade Kafka client to 3.6.0

Update kafka-clients from 3.5.1 to 3.6.0 for security patches and
performance improvements.

Notable changes:
- CVE-2023-xxxxx security fix
- Improved consumer rebalancing
- Better SSL/TLS performance

Relates to #890
```

### Scenario 4: Configuration Changes

```
config(keycloak): enable HTTPS-only mode

Disable HTTP endpoint and configure HTTPS with proper SSL certificates
for security compliance.

BREAKING CHANGE: HTTP endpoint no longer available. Update all client
configurations to use HTTPS URL (https://keycloak.example:57003).

Closes #234
```

### Scenario 5: Documentation

```
docs(api): add OpenAPI specification

Add comprehensive OpenAPI 3.0 specification for all REST endpoints
including request/response examples and authentication details.
```

### Scenario 6: Refactoring

```
refactor(database): extract repository pattern

Extract database access logic into repository classes to improve
testability and separation of concerns. No functional changes.
```

## Release Workflow

### Generating Changelogs

Conventional commits enable automatic changelog generation:

```bash
# Generate changelog from conventional commits
npx standard-version

# Or manually with git log
git log --pretty=format:"%s" v1.0.0..HEAD | grep "^feat"
git log --pretty=format:"%s" v1.0.0..HEAD | grep "^fix"
```

### Semantic Versioning

Conventional commits map to semantic versions:

- `fix:` → Patch release (1.0.1)
- `feat:` → Minor release (1.1.0)
- `BREAKING CHANGE:` or `!` → Major release (2.0.0)

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────────┐
│ CONVENTIONAL COMMITS QUICK REFERENCE                        │
├─────────────────────────────────────────────────────────────┤
│ Format:                                                     │
│   <type>(<scope>): <subject>                                │
│                                                             │
│ Types:                                                      │
│   feat     → New feature                                    │
│   fix      → Bug fix                                        │
│   docs     → Documentation                                  │
│   style    → Formatting                                     │
│   refactor → Code restructuring                             │
│   perf     → Performance                                    │
│   test     → Tests                                          │
│   build    → Build system                                   │
│   ci       → CI/CD                                          │
│   chore    → Maintenance                                    │
│                                                             │
│ Subject Rules:                                              │
│   ✓ lowercase, imperative mood                              │
│   ✓ no period, max 72 chars                                 │
│   ✗ past tense, vague descriptions                          │
│                                                             │
│ Breaking Changes:                                           │
│   type(scope)!: subject                                     │
│   OR: BREAKING CHANGE: in footer                            │
└─────────────────────────────────────────────────────────────┘
```

## Best Practices Summary

### ✅ DO

- Use conventional commit format for ALL commits
- Write commits in imperative mood
- Keep subject line under 72 characters
- Include body for complex changes
- Document breaking changes in footer
- Reference issues in footer
- Make atomic, self-contained commits
- Test before committing

### ❌ DON'T

- Use vague commit messages
- Commit broken code
- Mix multiple unrelated changes
- Use past tense
- End subject with period
- Forget breaking change documentation
- Commit without reviewing changes
- Use ALL CAPS or mixed case inconsistently

## Error Prevention

### Common Mistakes

| Mistake | Wrong | Correct |
|---------|-------|---------|
| No type | `add new feature` | `feat: add new feature` |
| Past tense | `feat: added feature` | `feat: add feature` |
| Period | `fix: resolve bug.` | `fix: resolve bug` |
| Uppercase | `Fix: Resolve Bug` | `fix: resolve bug` |
| Vague | `fix: fix issue` | `fix(auth): resolve token expiration` |

### Pre-commit Checklist

Before committing, verify:
- [ ] Follows conventional commit format
- [ ] Type is correct
- [ ] Scope is appropriate (if used)
- [ ] Subject is imperative, lowercase, no period
- [ ] Body explains WHY (if needed)
- [ ] Breaking changes documented
- [ ] Issues referenced
- [ ] Code compiles and tests pass

## Integration with Project Workflow

### When Creating Commits

1. **Stage changes**: `git add <files>`
2. **Review changes**: `git diff --staged`
3. **Write conventional commit** following this skill's guidelines
4. **Include co-authorship footer** (Claude Code standard)
5. **Push when ready**: `git push`

### Example Workflow

```bash
# 1. Make changes
vim src/main/java/UserService.java

# 2. Review
git diff

# 3. Stage
git add src/main/java/UserService.java

# 4. Commit with conventional format
git commit -m "$(cat <<'EOF'
feat(user): add email validation

Implement email format validation for user registration to prevent
invalid email addresses from being stored in the database.

Validation includes:
- RFC 5322 compliant format checking
- Domain MX record verification
- Disposable email detection

Closes #456

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"

# 5. Push
git push
```

## Remember

**Every commit is a permanent part of project history. Make it count.**

- Write commits for future maintainers (including yourself)
- Be specific, clear, and consistent
- Follow conventional commits without exception
- Document breaking changes thoroughly
- Reference issues for traceability

## Resources

- [Conventional Commits Specification](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- [Angular Commit Guidelines](https://github.com/angular/angular/blob/master/CONTRIBUTING.md#commit)
