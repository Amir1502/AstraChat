#!/usr/bin/env python3
"""Source policy only; never represented as a Kotlin/Android compiler."""
from pathlib import Path
import json
import re
import tomllib
import xml.etree.ElementTree as ET
r = Path(__file__).resolve().parents[1]
required = ['README.md', 'LICENSE', 'SECURITY.md', 'CONTRIBUTING.md', 'CHANGELOG.md', '.gitignore',
            '.github/workflows/android.yml', '.github/PULL_REQUEST_TEMPLATE.md', '.github/ISSUE_TEMPLATE/bug_report.yml',
            'docs/ARCHITECTURE.md', 'docs/PROVIDERS.md', 'settings.gradle.kts', 'gradle/libs.versions.toml']
for name in required:
    assert (r / name).is_file(), name
with (r / 'gradle/libs.versions.toml').open('rb') as f:
    catalog = tomllib.load(f)
assert catalog['versions']['agp'] == '9.1.1'
count = 0
for p in r.rglob('*'):
    if not p.is_file() or any(x in ('build', '.gradle', '.git', '.kotlin') for x in p.relative_to(r).parts):
        continue
    count += 1
    assert p.suffix not in ('.jks', '.keystore', '.apk', '.aab', '.pem', '.p12'), p
    assert p.name not in ('local.properties', '.env', 'secrets.properties'), p
    if p.suffix == '.xml':
        ET.parse(p)
    if p.suffix == '.json':
        json.loads(p.read_text())
    if p.suffix in ('.kt', '.java') and '/src/main/' in str(p):
        text = p.read_text()
        assert not re.search(r'\b(?:TO' + r'DO|FIX' + r'ME)\b|NotImplementedError', text), p
        assert not re.search(r'Log\.[vdiew]\(|HttpLoggingInterceptor|fallbackToDestructiveMigration|trustAllCert', text), p
        assert not re.search(r'gh[pousr]_[A-Za-z0-9]{30,}|sk-[A-Za-z0-9]{24,}', text), p
manifest = ET.parse(r / 'app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
assert manifest.find('application').get(ns+'allowBackup') == 'false'
codec = (r / 'core/src/main/kotlin/com/folzi/astrachat/core/ProviderCodec.kt').read_text()
assert all('Protocol.' + name in codec for name in ['CHAT_COMPLETIONS', 'RESPONSES', 'ANTHROPIC', 'GEMINI'])
network = (r / 'core/src/main/kotlin/com/folzi/astrachat/core/ProviderClient.kt').read_text()
assert '.followRedirects(false)' in network and '.retryOnConnectionFailure(false)' in network
print(f'PASS: source policy; {count} files inspected. Not a compile or full secret audit.')
