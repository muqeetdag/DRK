# Release key (public convenience key)

`release-key.pem` / `release-cert.pem` sign every offline-lane build since
v1.2.1. They are committed **on purpose**:

* Every rebuild keeps the **same signature**, so new APKs install as normal
  upgrades — no more "uninstall the old version first", no more
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / "signature failed" errors.
* Anyone can reproduce the build and get an APK that upgrades the app they
  already have.

⚠ Because the private key is public, **never reuse it for anything sensitive**
(other apps, accounts, stores). It exists purely to give this personal app's
side-loaded builds a stable identity — exactly like a debug key, just fixed.

Signer certificate SHA-256 fingerprint:
`f2b4af11fb6fff5117a3cf3683ea57340162dc0597408f36c163bb801fd35452`
