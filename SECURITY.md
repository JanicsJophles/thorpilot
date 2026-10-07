# Security

Thorpilot is pre-release software. Security fixes target the current development branch; older prototypes do not have a separate maintenance commitment.

## Report a vulnerability privately

Use the repository's **Security → Report a vulnerability** option if it is available. If private reporting is unavailable, open an issue titled **Private security contact requested**, with no vulnerability details, credentials, or exploit material. Wait for a private channel before sharing sensitive information. There is no guaranteed response time yet.

Include affected versions, a minimal reproduction, likely impact, and any suggested fix. Redact API keys, tokens, pairing codes, private hosts, and personal library information. Never attach real secrets. Revoke exposed credentials immediately through their issuing service.

## Areas we take seriously

Credential disclosure, unauthorized device actions, unsafe model tool execution, storage permission escapes, untrusted server responses, and CI access to private devices or networks are security issues.

Public fork code must not run automatically on private self-hosted runners. Private hardware runs require maintainer review of the complete code and an explicitly approved same-repository branch. Production credentials and personal game libraries must not be available to untrusted tests.

The app is a companion, not an authorization boundary for a public backend. Protect connected services independently and grant only the access needed.
