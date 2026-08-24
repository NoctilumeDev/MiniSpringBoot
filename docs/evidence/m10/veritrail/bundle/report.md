# VeriTrail run `minispringboot-m10-external-20260824`

- Execution status: `COMPLETED`
- Verdict: `PASS`
- Plan: `minispringboot-m10-imported-evidence-v1@1`
- Plan SHA-256: `5c84b64cf660434190dce96173a98d4a62f552fd606767f388c71d7dc53fc285`
- Baseline: `minispringboot-m10-source-commit` (`VALID`)
- Random seed: `20260824`
- Created at: `2026-08-24T14:09:23.373565Z`

## Reasons

- `ALL_DECISIVE_ASSERTIONS_PASSED` — All required evidence is present and every decisive assertion passed\.

## Assertions

| ID | Severity | Status | Actual | Expected |
| --- | --- | --- | --- | --- |
| source\-commit\-matches | HARD | PASS | true | true |
| tracked\-evidence\-hashes\-valid | HARD | PASS | true | true |
| self\-verified\-claims\-pass | HARD | PASS | true | true |
| fresh\-failover\-load\-completed | HARD | PASS | true | true |
| fresh\-failover\-load\-zero\-errors | HARD | PASS | true | true |
| fresh\-failover\-proxy\-zero\-failures | HARD | PASS | 0 | 0 |
| fresh\-failover\-rejoined | HARD | PASS | true | true |
| fresh\-failover\-accounts\-unchanged | HARD | PASS | true | true |
| transaction\-proof\-passed | HARD | PASS | true | true |
| transaction\-baseline\-restored | HARD | PASS | true | true |
| readiness\-proof\-passed | HARD | PASS | true | true |
| liveness\-remains\-up\-during\-database\-outage | HARD | PASS | "UP" | "UP" |
| readiness\-fails\-closed\-during\-database\-outage | HARD | PASS | 500 | 500 |
| readiness\-recovers\-after\-database\-recovery | HARD | PASS | "UP" | "UP" |
| full\-topology\-lifecycle\-is\-not\-overclaimed | HARD | PASS | false | false |

## Evidence

- `minispringboot.m10-verification` — `7ca12152c22b7357964021dc638eaee77211bae2216a48975b9dcc72ada0838c` (1665 bytes, redactions: 0)

## Evidence gaps and contamination

- None detected by the active deterministic rule set.

## Applicability boundary

- Subject: `{"id":"minispringboot-m10","source_ref":"NoctilumeDev/MiniSpringBoot@85c2b22dfdcb17cd2527f068f85542aca25d694c","version":"10.0.0"}`
- Primary variable: `{"name":"verification_scope","role":"PRIMARY","source":"frozen M10 verification boundary","value":"IMPORTED_EVIDENCE_AUDIT"}`
- Load model: `{"duration_seconds":1,"total_requests":1}`
- Resource budget: `{"max_artifact_bytes":1048576,"memory_hard_mb":1024,"memory_soft_mb":512}`
- Change scope: `{"consumers":["MiniSpringBoot maintainers","VeriTrail Core 0.12","public repository readers"],"expected_blast_radius":"M10 evidence manifest, bounded failover replay, transaction proof, readiness proof, and external verdict","level":"L3_SYSTEM","owner":"MiniSpringBoot M10"}`

## Reproduction and cleanup

1. Start the owned MiniSpringBoot M10 three\-instance cluster and its declared MySQL and Nginx dependencies\.
2. Run the bounded failover drill, transaction proof, and readiness proof into a fresh evidence directory\.
3. Generate imported Evidence with deploy/m10/new\-veritrail\-imported\-evidence\.ps1\.
4. Seal this Plan with VeriTrail Core 0\.12, evaluate the imported Evidence, and validate the resulting Bundle\.

Cleanup:

1. Stop only the MiniSpringBoot M10 processes recorded by deploy/m10/\.runtime and the minispring\-mysql container\.
2. Retain the frozen public evidence Bundle; remove only disposable replay output after review\.
