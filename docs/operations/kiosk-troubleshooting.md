# Kiosk Troubleshooting

## Common Symptoms

- Terminal cannot start a session.
- Verification always fails.
- Session token rejected.
- Session expired.

## Checks

1. Confirm terminal status is active.
2. Confirm terminal belongs to the expected organization and branch.
3. Confirm local clock and backend clock are aligned.
4. Confirm `X-Self-Service-Session-Token` is sent after verification.
5. Check rate limiting and blocked-until state.

## Data Safety

Do not expose whether a specific email or phone exists. Kiosk responses must remain generic.
