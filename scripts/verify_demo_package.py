#!/usr/bin/env python3
"""Verify the actual ZIP in a fresh directory and an isolated Compose project."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import tempfile
from urllib.error import HTTPError
from urllib.request import Request, build_opener, ProxyHandler
import uuid
import zipfile


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--keep-running", action="store_true",
                        help="Keep the isolated successful demo for browser verification")
    args = parser.parse_args()
    archive = args.archive.resolve()
    expected = archive.with_suffix(".zip.sha256").read_text().split()[0]
    require(hashlib.sha256(archive.read_bytes()).hexdigest() == expected, "ZIP checksum mismatch")
    # Intentionally outside the checkout, including a space in the directory name.
    directory = Path(tempfile.mkdtemp(prefix="MiniSpring demo smoke "))
    with zipfile.ZipFile(archive) as package:
        for member in package.infolist():
            require((directory / member.filename).resolve().is_relative_to(directory),
                    "ZIP contains a path outside its destination")
        package.extractall(directory)
    roots = list(directory.iterdir())
    require(len(roots) == 1 and roots[0].is_dir(), "Expected one package root")
    bundle = roots[0]
    for line in (bundle / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        checksum, relative = line.split("  ", 1)
        path = (bundle / relative).resolve()
        require(path.is_relative_to(bundle), "Checksum path escapes package")
        require(hashlib.sha256(path.read_bytes()).hexdigest() == checksum,
                f"Content checksum mismatch: {relative}")
    metadata = json.loads((bundle / "BUILD-INFO.json").read_text(encoding="utf-8"))
    project = "msb-demo-check-" + uuid.uuid4().hex[:12]
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    environment = {**os.environ, "DEMO_PORT": str(port), "COMPOSE_PROJECT_NAME": project}
    command = ["docker", "compose", "-p", project, "-f", str(bundle / "compose.yaml")]
    evidence = {"sourceCommit": metadata["sourceCommit"], "sourceDirty": metadata["sourceDirty"],
                "directory": str(bundle), "project": project, "url": f"http://127.0.0.1:{port}",
                "checks": [], "status": "RUNNING"}
    print(json.dumps(evidence), flush=True)

    def compose(*arguments: str, check: bool = True) -> str:
        result = subprocess.run([*command, *arguments], cwd=bundle, env=environment,
                                check=False, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, encoding="utf-8", errors="replace")
        if result.returncode and check:
            raise RuntimeError(result.stdout)
        return result.stdout

    def sql(query: str) -> str:
        return compose("exec", "-T", "mysql", "env", "MYSQL_PWD=demo_local",
                       "mysql", "-u", "minispring", "-N", "-B", "minispring_demo",
                       "-e", query).strip()

    opener = build_opener(ProxyHandler({}))

    def http(path: str, method: str = "GET", body=None, status: int = 200):
        data = json.dumps(body).encode() if body is not None else None
        request = Request(evidence["url"] + path, data=data, method=method,
                          headers={"Content-Type": "application/json"} if data else {})
        try:
            response = opener.open(request, timeout=40)
        except HTTPError as error:
            response = error
        with response:
            payload = response.read()
            require(response.status == status, f"{method} {path}: HTTP {response.status}")
        return payload

    def balances():
        return [json.loads(http(f"/api/accounts/{key}"))["balance"] for key in (1, 2)]

    successful = False
    try:
        require(not compose("ps", "-aq").strip(), "Unexpected existing verification project")
        compose("up", "-d", "--wait", "--wait-timeout", "180")
        require(json.loads(http("/health"))["database"] == "UP", "Database not ready")
        html = http("/").decode()
        assets = re.findall(r'(?:src|href)="(/assets/[^"]+)"', html)
        require("MiniSpringBoot" in html and len(assets) >= 2, "Frontend entry missing")
        for asset in assets:
            require(len(http(asset)) > 0, f"Empty frontend asset: {asset}")
        evidence["checks"].append("frontend-assets-and-database-readiness")
        require(balances() == [1000, 1000], "Unexpected fresh database")
        user = json.loads(http("/api/users", "POST",
                              {"name": "Package smoke", "email": "package-smoke@example.invalid"}))
        user_id = int(user["id"])
        require(sql(f"SELECT name FROM users WHERE id={user_id}") == "Package smoke",
                "Created user absent from SQL")
        http(f"/api/users/{user_id}", "PUT",
             {"name": "Package updated", "email": "package-smoke@example.invalid"})
        require(json.loads(http(f"/api/users/{user_id}"))["name"] == "Package updated",
                "User update not visible")
        http("/api/accounts/transfer?from=1&to=2&amount=10", "POST")
        require(balances() == [990, 1010], "Transfer did not commit")
        require(sql("SELECT balance FROM accounts ORDER BY id").splitlines()
                == ["990.00", "1010.00"], "Committed balances differ in SQL")
        http("/api/accounts/transfer-fail?from=1&to=2&amount=10", "POST", status=500)
        require(balances() == [990, 1010], "Failed transfer changed balances")
        require(sql("SELECT balance FROM accounts ORDER BY id").splitlines()
                == ["990.00", "1010.00"], "Rollback differs in SQL")
        evidence["checks"].append("crud-and-transaction-commit-rollback-with-sql")
        compose("down")
        compose("up", "-d", "--wait", "--wait-timeout", "180")
        require(balances() == [990, 1010], "Balances lost across stop/start")
        require(sql(f"SELECT name FROM users WHERE id={user_id}") == "Package updated",
                "User lost across stop/start")
        http(f"/api/users/{user_id}", "DELETE")
        require(sql(f"SELECT COUNT(*) FROM users WHERE id={user_id}") == "0", "Delete failed")
        http("/api/accounts/transfer?from=2&to=1&amount=10", "POST")
        require(balances() == [1000, 1000], "Demo baseline was not restored")
        evidence["checks"].append("persistent-volume-restart-and-baseline-restored")
        evidence["status"] = "PASS"
        successful = True
    except Exception as error:
        evidence["status"] = "FAIL"
        evidence["error"] = str(error)
        raise
    finally:
        (directory / "compose.log").write_text(
            compose("logs", "--no-color", "--tail", "150", check=False), encoding="utf-8")
        if successful and not args.keep_running:
            # This UUID project was created only for this verification.
            compose("down", "-v")
        (directory / "result.json").write_text(json.dumps(evidence, indent=2) + "\n",
                                              encoding="utf-8")
        print(json.dumps(evidence), flush=True)
        print(f"Retained verification evidence: {directory}", flush=True)


if __name__ == "__main__":
    main()
