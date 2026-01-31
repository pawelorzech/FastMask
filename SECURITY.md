# Security Policy

## Reporting a Vulnerability

The FastMask team takes security seriously. We appreciate your efforts to responsibly disclose your findings.

### How to Report

If you discover a security vulnerability, please report it by:

1. **Opening a private security advisory** on GitHub:
   - Go to the [Security tab](https://github.com/pawelorzech/FastMask/security/advisories)
   - Click "New draft security advisory"
   - Provide details about the vulnerability

2. **Or emailing directly** (if available in the repository owner's profile)

### What to Include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Response Timeline

- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 1 week
- **Resolution timeline**: Depends on severity, typically 30-90 days

## Security Measures in FastMask

### Data Storage

- API tokens are stored using Android's [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- Encryption uses AES-256-GCM for values and AES-256-SIV for keys
- No sensitive data is stored in plain text

### Network Security

- All communication with Fastmail uses HTTPS/TLS
- Certificate pinning is recommended for production builds
- No data is sent to third-party servers

### Privacy

- No analytics or tracking
- No data collection
- Direct communication with Fastmail API only

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x     | :white_check_mark: |

## Best Practices for Users

1. **Protect your API token**: Treat it like a password
2. **Use device security**: Enable screen lock on your device
3. **Keep the app updated**: Install updates for security fixes
4. **Review permissions**: The app only requests necessary permissions

## Scope

The following are **in scope** for security reports:

- Authentication and authorization issues
- Data leakage or exposure
- Cryptographic weaknesses
- API security issues

The following are **out of scope**:

- Social engineering attacks
- Physical attacks on user devices
- Denial of service attacks
- Issues in third-party dependencies (report to upstream)
