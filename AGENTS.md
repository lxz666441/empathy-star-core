# Reusable conversation business module

- `core/` contains Java business rules, prompts, result validation and model transport. It must not import Android or know specific chat applications, node IDs, pixels, accessibility services or account credentials.
- `ui-android/` contains optional Android presentation components. It consumes conversation data and callbacks only; no node reading, app launch, storage, automatic sending or background upload.
- `examples/` shows synthetic integration; it must run offline by default. All tests use synthetic messages and injected responses, never real credentials or network model calls.
- `docs/` contains the stable contract, extraction provenance, safety limits and verification status. Generated files and caches are ignored.
- Hosts provide messages, sender direction, optional display names, opaque source identity, revision and source/consent checks. Core cannot prove that caller-provided data or permissions are valid.
- Source spans must match the original text; summaries reference only explicitly selected IDs. Preserve duplicate messages as separate occurrences.
- Require explicit upload consent and a fresh source/cancellation guard before and during model requests. No provider fallback, automatic retry, persistence or logging of content/credentials.
- Before any model operation, verify explicit user authorization, endpoint, model and applicable price. SiliconFlow permits only verified-free models and forbids all DeepSeek models. This repository's verification authorizes no model calls.
- Use JDK17 and Gradle8.13 Wrapper. Run `./gradlew :core:test :examples:run` without Android, and `./gradlew -PwithAndroid=true :ui-android:assembleDebug :ui-android:lintDebug` with Android SDK36 after source changes. Windows Android builds require an ASCII-only checkout path. Examples use an injected offline client.
- Do not change the original installed application as part of this extraction. Public visibility was explicitly authorized for this cleaned repository.
