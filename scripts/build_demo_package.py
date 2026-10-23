#!/usr/bin/env python3
"""Build a precompiled demo ZIP; never publish a Release."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "dist" / "demo-package"


def run(*args: str, cwd: Path = ROOT, capture: bool = False, env=None) -> str:
    executable = shutil.which(args[0])
    if not executable:
        raise RuntimeError(f"Required build tool not found: {args[0]}")
    result = subprocess.run(
        [executable, *args[1:]], cwd=cwd, check=True, env=env,
        stdout=subprocess.PIPE if capture else None,
        encoding="utf-8", errors="replace",
    )
    return result.stdout.strip() if capture else ""


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def frontend_licenses(destination: Path) -> None:
    frontend = ROOT / "demo-frontend"
    paths = run("npm", "ls", "--omit=dev", "--all", "--parseable",
                cwd=frontend, capture=True).splitlines()
    for entry in paths:
        package = Path(entry)
        if package.resolve() == frontend.resolve():
            continue
        metadata = json.loads((package / "package.json").read_text(encoding="utf-8"))
        name = metadata["name"]
        target = destination / name.replace("/", "__")
        target.mkdir(parents=True)
        (target / "package.json").write_text(
            json.dumps({key: metadata.get(key) for key in ("name", "version", "license")},
                       indent=2) + "\n", encoding="utf-8")
        notices = [p for p in package.iterdir() if p.is_file()
                   and p.name.lower().startswith(("license", "licence", "copying", "notice"))]
        if not notices:
            raise RuntimeError(f"No bundled license found for frontend dependency: {name}")
        for notice in notices:
            shutil.copy2(notice, target / notice.name)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-dirty", action="store_true",
                        help="Build a visibly marked local preview from an uncommitted tree")
    args = parser.parse_args()
    commit = run("git", "rev-parse", "HEAD", capture=True)
    dirty = bool(run("git", "status", "--porcelain", "--untracked-files=normal", capture=True))
    if dirty and not args.allow_dirty:
        raise RuntimeError("Commit source changes first, or use --allow-dirty for a local preview.")
    tags = run("git", "tag", "--points-at", "HEAD", capture=True).splitlines()
    name = f"minispringboot-demo-{commit[:12]}" + ("-dirty" if dirty else "")
    run("mvn", "-B", "clean", "install", "-DskipTests")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    # Temporary staging stays inside this repository's ignored output directory.
    with tempfile.TemporaryDirectory(prefix="stage-", dir=OUTPUT) as temporary:
        bundle = Path(temporary) / name
        bundle.mkdir()
        for filename in ("compose.yaml", "nginx.conf", ".env.example", "README.md"):
            shutil.copy2(ROOT / "deploy" / "demo-package" / filename, bundle / filename)
        readme = bundle / "README.md"
        readme.write_text(readme.read_text(encoding="utf-8").replace(
            "/blob/main/docs/demo-package.md", f"/blob/{commit}/docs/demo-package.md"),
            encoding="utf-8")
        run("mvn", "-B", "-pl", "mini-spring-demo",
            "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies",
            "-DincludeScope=runtime", f"-DoutputDirectory={bundle / 'lib'}")
        # The directory scanner deliberately consumes loose demo classes.
        shutil.copytree(ROOT / "mini-spring-demo" / "target" / "classes", bundle / "app")
        frontend = ROOT / "demo-frontend"
        run("npm", "ci", "--registry=https://registry.npmjs.org", cwd=frontend)
        run("npm", "run", "build", cwd=frontend,
            env={**os.environ, "VITE_DEMO_PACKAGE": "true"})
        shutil.copytree(frontend / "dist", bundle / "frontend")
        frontend_licenses(bundle / "licenses" / "frontend")
        (bundle / "mysql").mkdir()
        shutil.copy2(ROOT / "deploy" / "mysql" / "init.sql", bundle / "mysql" / "init.sql")
        shutil.copy2(ROOT / "LICENSE", bundle / "LICENSE")
        metadata = {
            "sourceCommit": commit, "sourceDirty": dirty, "exactTags": tags,
            "sourceUrl": f"https://github.com/NoctilumeDev/MiniSpringBoot/tree/{commit}",
            "kind": "single-instance-precompiled-demo",
            "runtime": "Docker Compose v2 with Linux containers; network required for images",
        }
        (bundle / "BUILD-INFO.json").write_text(
            json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        if run("git", "rev-parse", "HEAD", capture=True) != commit:
            raise RuntimeError("Source commit changed during the build")
        if not dirty and run("git", "status", "--porcelain", capture=True):
            raise RuntimeError("Source tree changed during the build")
        files = sorted(p for p in bundle.rglob("*") if p.is_file())
        (bundle / "SHA256SUMS").write_text(
            "".join(f"{digest(p)}  {p.relative_to(bundle).as_posix()}\n" for p in files),
            encoding="utf-8")
        archive = OUTPUT / f"{name}.zip"
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
            for path in sorted(bundle.rglob("*")):
                if path.is_file():
                    output.write(path, path.relative_to(bundle.parent).as_posix())
        archive.with_suffix(".zip.sha256").write_text(
            f"{digest(archive)}  {archive.name}\n", encoding="utf-8")
        print(f"Demo package: {archive}")
        print(f"Source: {commit}; dirty={dirty}")


if __name__ == "__main__":
    main()
