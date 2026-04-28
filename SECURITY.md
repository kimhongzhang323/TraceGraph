# Security policy

## Supported versions

Pre-1.0 (`0.x`): only the latest minor receives security fixes. Once we hit `1.0.0` this policy will tighten.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security problems. Instead, use GitHub's [private vulnerability reporting](https://github.com/kimhongzhang323/TraceGraph/security/advisories/new) on this repository.

Include:
- A description of the issue and its impact
- A minimal reproducer (failing test, payload, or steps)
- The affected version (`0.x.y` or commit SHA)

You should expect an acknowledgement within 7 days. Disclosure timing will be agreed before any public advisory.

## Out of scope

- Issues that require an attacker who already has write access to your build configuration
- Vulnerabilities in third-party dependencies — please report those upstream; we will pick up patched versions
