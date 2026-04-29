# Self-Signed Code Signing for APiX TV (Windows)

This **does not** eliminate SmartScreen warnings completely — only a paid EV
Code Signing Certificate ($200-400/year) does that. But self-signing helps:

- ✅ Removes "Unknown Publisher" red banner → shows "APiX Media" instead
- ✅ Lowers heuristic score in Avast/AVG/Kaspersky/Bitdefender
- ✅ Lets corporate AppLocker policies whitelist your cert thumbprint
- ❌ SmartScreen will still show a yellow "Don't run / Run anyway" dialog the first few times until reputation builds

## One-time certificate generation (run as Administrator on the build PC)

```powershell
$cert = New-SelfSignedCertificate `
    -Subject "CN=APiX Media, O=APiX Media, C=IQ" `
    -Type CodeSigningCert `
    -KeyAlgorithm RSA `
    -KeyLength 4096 `
    -HashAlgorithm SHA256 `
    -CertStoreLocation "Cert:\CurrentUser\My" `
    -NotAfter (Get-Date).AddYears(5)

# Export PFX (keep secret)
$pwd = ConvertTo-SecureString -String "STRONG_PASSWORD_HERE" -Force -AsPlainText
Export-PfxCertificate -Cert $cert -FilePath "$HOME\apix-codesign.pfx" -Password $pwd

# Save thumbprint for the sign script
$cert.Thumbprint | Out-File "$HOME\apix-thumbprint.txt"
```

## End-user trust (optional — for corporate fleet installs)

Distribute the public certificate (`.cer`) and import into:
- `Trusted Root Certification Authorities` (machine store)
- `Trusted Publishers` (machine store)

After this, SmartScreen and most AV engines treat the installer as fully trusted.

## CI / Local build

The `windows/sign-self.ps1` script runs automatically after `packageMsi` (see
`windows/app/build.gradle.kts`). It uses signtool from the Windows SDK.
