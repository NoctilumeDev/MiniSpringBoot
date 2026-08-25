#!/usr/bin/env python3
"""Verify public dependency, source-metric, and frozen-evidence contracts."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
KERNEL_MODULES = (
    "mini-spring-core",
    "mini-spring-config",
    "mini-spring-context",
    "mini-spring-aop",
    "mini-spring-web",
    "mini-spring-jdbc",
    "mini-spring-autoconfigure",
    "mini-spring-boot",
)
EXPECTED_KERNEL_JAVA_FILES = 155
EXPECTED_KERNEL_PHYSICAL_LINES = 7_766
BUNDLE_MANIFESTS = (
    "docs/evidence/m10/veritrail/bundle/bundle-manifest.json",
    "docs/evidence/m10/veritrail/negative-control-bundle/bundle-manifest.json",
)


class ContractError(RuntimeError):
    """Raised when an executable repository contract has drifted."""


def git(*arguments: str) -> bytes:
    completed = subprocess.run(
        ["git", "-C", str(ROOT), *arguments],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if completed.returncode != 0:
        stderr = completed.stderr.decode("utf-8", errors="replace").strip()
        raise ContractError(f"git {' '.join(arguments)} failed: {stderr}")
    return completed.stdout


def tracked_paths() -> list[str]:
    return [
        os.fsdecode(path)
        for path in git("ls-files", "-z").split(b"\0")
        if path
    ]


def verify_metrics(paths: list[str]) -> None:
    prefixes = tuple(f"{module}/src/main/" for module in KERNEL_MODULES)
    java_paths = sorted(
        path
        for path in paths
        if path.endswith(".java") and path.startswith(prefixes)
    )
    physical_lines = sum(
        len((ROOT / PurePosixPath(path)).read_text(encoding="utf-8").splitlines())
        for path in java_paths
    )

    print(f"kernel_java_files={len(java_paths)}")
    print(f"kernel_java_physical_lines={physical_lines}")
    if len(java_paths) != EXPECTED_KERNEL_JAVA_FILES:
        raise ContractError(
            "kernel Java file count drifted: "
            f"expected {EXPECTED_KERNEL_JAVA_FILES}, observed {len(java_paths)}"
        )
    if physical_lines != EXPECTED_KERNEL_PHYSICAL_LINES:
        raise ContractError(
            "kernel physical line count drifted: "
            f"expected {EXPECTED_KERNEL_PHYSICAL_LINES}, observed {physical_lines}"
        )


def direct_dependencies(module: str) -> list[tuple[str, str, str, bool]]:
    pom = ET.parse(ROOT / module / "pom.xml").getroot()
    namespace = ""
    if pom.tag.startswith("{"):
        namespace = pom.tag[: pom.tag.index("}") + 1]
    dependencies = pom.find(f"{namespace}dependencies")
    if dependencies is None:
        return []

    result: list[tuple[str, str, str, bool]] = []
    for dependency in dependencies.findall(f"{namespace}dependency"):
        def value(name: str, default: str = "") -> str:
            element = dependency.find(f"{namespace}{name}")
            return default if element is None or element.text is None else element.text.strip()

        result.append(
            (
                value("groupId"),
                value("artifactId"),
                value("scope", "compile"),
                value("optional", "false").lower() == "true",
            )
        )
    return result


def verify_dependency_contract() -> None:
    mandatory_third_party: list[str] = []
    hikari: tuple[str, str, str, bool] | None = None
    for module in KERNEL_MODULES:
        for group, artifact, scope, optional in direct_dependencies(module):
            if module == "mini-spring-autoconfigure" and (
                group,
                artifact,
            ) == ("com.zaxxer", "HikariCP"):
                hikari = (group, artifact, scope, optional)
            runtime_relevant = scope not in {"test", "provided", "system", "import"}
            if runtime_relevant and group != "com.minispring" and not optional:
                mandatory_third_party.append(f"{module}:{group}:{artifact}:{scope}")

    if mandatory_third_party:
        raise ContractError(
            "mandatory transitive third-party runtime dependencies found: "
            + ", ".join(mandatory_third_party)
        )
    if hikari != ("com.zaxxer", "HikariCP", "compile", True):
        raise ContractError(
            "mini-spring-autoconfigure must declare HikariCP as a direct optional "
            f"compile dependency; observed {hikari!r}"
        )

    print("kernel_mandatory_transitive_third_party_runtime_dependencies=0")
    print("autoconfigure_hikari_dependency=direct_optional_compile")


def verify_evidence_attributes(paths: list[str]) -> None:
    evidence_paths = sorted(
        path
        for path in paths
        if path.startswith("docs/evidence/m10/")
        and Path(path).suffix.lower() in {".json", ".md", ".log"}
    )
    output = git("check-attr", "-z", "text", "eol", "--", *evidence_paths)
    fields = output.split(b"\0")
    if fields and not fields[-1]:
        fields.pop()
    if len(fields) % 3 != 0:
        raise ContractError("unexpected git check-attr -z output")

    attributes: dict[str, dict[str, str]] = {}
    for offset in range(0, len(fields), 3):
        path = os.fsdecode(fields[offset])
        attribute = fields[offset + 1].decode("ascii")
        value = fields[offset + 2].decode("ascii")
        attributes.setdefault(path, {})[attribute] = value

    failures = [
        f"{path}: text={attributes.get(path, {}).get('text')}, "
        f"eol={attributes.get(path, {}).get('eol')}"
        for path in evidence_paths
        if attributes.get(path, {}).get("text") != "set"
        or attributes.get(path, {}).get("eol") != "lf"
    ]
    if failures:
        raise ContractError("M10 evidence LF attributes missing: " + "; ".join(failures))
    print(f"m10_lf_attributed_text_files={len(evidence_paths)}")


def index_bytes(path: str) -> bytes:
    return git("show", f":{path}")


def verify_bundle_hashes() -> None:
    verified = 0
    for manifest_path in BUNDLE_MANIFESTS:
        manifest = json.loads(index_bytes(manifest_path).decode("utf-8"))
        base = PurePosixPath(manifest_path).parent
        seen: set[str] = set()
        for entry in manifest.get("files", []):
            relative = PurePosixPath(entry["path"])
            if relative.is_absolute() or ".." in relative.parts:
                raise ContractError(f"unsafe bundle manifest path: {entry['path']}")
            if entry["path"] in seen:
                raise ContractError(f"duplicate bundle manifest path: {entry['path']}")
            seen.add(entry["path"])
            repository_path = (base / relative).as_posix()
            payload = index_bytes(repository_path)
            observed_hash = hashlib.sha256(payload).hexdigest()
            observed_size = len(payload)
            if observed_hash != entry["sha256"] or observed_size != entry["size"]:
                raise ContractError(
                    f"canonical bundle bytes drifted for {repository_path}: "
                    f"expected {entry['sha256']}/{entry['size']}, "
                    f"observed {observed_hash}/{observed_size}"
                )
            verified += 1
    print(f"canonical_bundle_files_verified={verified}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metrics", action="store_true", help="verify source metrics")
    parser.add_argument(
        "--dependencies", action="store_true", help="verify kernel dependency boundary"
    )
    parser.add_argument(
        "--evidence", action="store_true", help="verify LF attributes and bundle hashes"
    )
    arguments = parser.parse_args()
    verify_all = not (arguments.metrics or arguments.dependencies or arguments.evidence)

    try:
        paths = tracked_paths()
        if verify_all or arguments.metrics:
            verify_metrics(paths)
        if verify_all or arguments.dependencies:
            verify_dependency_contract()
        if verify_all or arguments.evidence:
            verify_evidence_attributes(paths)
            verify_bundle_hashes()
    except (ContractError, OSError, ET.ParseError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print("repository_contracts=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
