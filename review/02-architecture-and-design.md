

## 5) Excessive broad exception swallowing (**P2**)
### Evidence
- Multiple `catch (Exception)` blocks in core flows and repositories.

### Impact
- Silent failures and degraded observability.
- Hard to debug user-reported issues.

### Recommendation
- Catch specific exception types where expected.
- Emit structured logs/events for non-fatal failures.
- Avoid returning empty results for all failures without surfacing reason.