"""Immutable filesystem snapshots; SQLite contains only audit/idempotency metadata.

X-Install-Id remains a namespace, not patient identity or authentication. All new
publication/deletion operations serialize through short SQLite transactions;
model inference and registration never run inside those transactions.
"""
import hashlib
import json
import os
import re
import shutil
import sqlite3
import tempfile
import time
import uuid
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from . import config

REPORT_FILE = "report.json"
SOURCE_FILE = "source.jpg"
MASK_FILE = "mask.png"
# "attention" 是 Android 端历史字段名；在乳腺超声链路中它承载真实病灶分割掩码。
ASSET_FILES = {"source": SOURCE_FILE, "attention": MASK_FILE}
_ID = re.compile(r"[A-Za-z0-9_-]{1,128}\Z", re.ASCII)
_MAX_ASSET_BYTES = 96 * 1024 * 1024


class StoreError(Exception):
    def __init__(self, status_code: int, detail: str):
        super().__init__(detail)
        self.status_code = status_code
        self.detail = detail


def strict_id(value: str, field="id", nonanonymous=False) -> str:
    if not isinstance(value, str) or not _ID.fullmatch(value):
        raise StoreError(422, "INVALID_" + field.upper())
    if nonanonymous and value.lower() == "anonymous":
        raise StoreError(422, "NONANONYMOUS_INSTALL_REQUIRED")
    return value


def canonical(value) -> bytes:
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"),
                      allow_nan=False).encode("utf-8")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def revision(report: dict) -> str:
    return sha256(canonical({k: v for k, v in report.items() if k not in ("reportRevision", "_sortKey")}))


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _root() -> Path:
    return Path(os.path.abspath(config.DATA_ROOT))


def checked(path: Path) -> Path:
    path = Path(os.path.abspath(path))
    root = _root()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise StoreError(422, "PATH_OUTSIDE_STORE") from exc
    # Include existing ancestors of DATA_ROOT, not just its children.
    for part in (path, *path.parents):
        if part.is_symlink():
            raise StoreError(409, "SYMLINK_NOT_ALLOWED")
    return path


def install_dir(install_id: str) -> Path:
    # Legacy read spelling remains intact; new writes/comparisons call strict_id first.
    safe = "".join(ch for ch in install_id if ch.isalnum() or ch in "-_") or "anonymous"
    return checked(_root() / "installs" / safe)


def record_dir(install_id: str, record_id: str) -> Path:
    safe = "".join(ch for ch in record_id if ch.isalnum() or ch in "-_") or "record"
    return checked(install_dir(install_id) / "records" / safe)


def _install_hash(install_id):
    return sha256(install_id.encode("utf-8"))


@contextmanager
def _transaction():
    root = checked(_root())
    root.mkdir(parents=True, exist_ok=True)
    db_path = checked(root / "audit.sqlite3")
    connection = sqlite3.connect(str(db_path), timeout=10, isolation_level=None)
    try:
        connection.execute("PRAGMA busy_timeout=10000")
        connection.execute("""CREATE TABLE IF NOT EXISTS audit_events (
            event_id TEXT PRIMARY KEY, created_at TEXT NOT NULL, install_hash TEXT NOT NULL,
            event_type TEXT NOT NULL, payload TEXT NOT NULL)""")
        connection.execute("""CREATE TABLE IF NOT EXISTS requests (
            install_hash TEXT NOT NULL, kind TEXT NOT NULL, request_id TEXT NOT NULL,
            content_hash TEXT NOT NULL, resource_id TEXT NOT NULL, state TEXT NOT NULL,
            owner TEXT NOT NULL, record_ids TEXT NOT NULL, updated_at TEXT NOT NULL,
            PRIMARY KEY (install_hash, kind, request_id))""")
        connection.execute("BEGIN IMMEDIATE")
        yield connection
        connection.commit()
    except BaseException:
        connection.rollback()
        raise
    finally:
        connection.close()


def _event(connection, install_id, event_type, payload, event_id=None):
    event_id = event_id or uuid.uuid4().hex
    connection.execute("INSERT INTO audit_events VALUES (?, ?, ?, ?, ?)", (
        event_id, utc_now(), _install_hash(install_id), event_type,
        canonical(payload).decode("utf-8"),
    ))
    return event_id


def audit(install_id: str, event_type: str, payload: dict, event_id=None) -> str:
    with _transaction() as connection:
        return _event(connection, install_id, event_type, payload, event_id)


def public_report(report: dict) -> dict:
    return {key: value for key, value in report.items() if not key.startswith("_")}


def _read_json(path: Path):
    path = checked(path)
    if not path.is_file():
        return None
    if path.stat().st_size > 4 * 1024 * 1024:
        raise StoreError(409, "JSON_RESOURCE_TOO_LARGE")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeError) as exc:
        raise StoreError(409, "INVALID_STORED_JSON") from exc
    if not isinstance(value, dict):
        raise StoreError(409, "INVALID_STORED_JSON")
    return value


def read_report(directory: Path) -> dict | None:
    return _read_json(directory / REPORT_FILE)


def resource_bytes(path: Path) -> bytes:
    path = checked(path)
    if not path.is_file():
        raise StoreError(404, "RESOURCE_NOT_FOUND")
    if path.stat().st_size > _MAX_ASSET_BYTES:
        raise StoreError(409, "RESOURCE_TOO_LARGE")
    return path.read_bytes()


def list_records(install_id: str, limit: int, offset: int):
    root = checked(install_dir(install_id) / "records")
    if not root.exists():
        return [], 0
    reports = []
    for directory in root.iterdir():
        if directory.name.startswith(".") or not checked(directory).is_dir():
            continue
        report = read_report(directory)
        if report:
            reports.append(report)
    reports.sort(key=lambda r: r.get("_sortKey", 0.0), reverse=True)
    return [public_report(r) for r in reports[offset:offset + limit]], len(reports)


@dataclass(frozen=True)
class Snapshot:
    report: dict
    files: dict
    hashes: dict
    fingerprint: str


def _snapshot(install_id: str, record_id: str, missing_status=404) -> Snapshot:
    strict_id(install_id, "install", nonanonymous=True)
    strict_id(record_id, "record")
    directory = record_dir(install_id, record_id)
    report = read_report(directory)
    if report is None:
        raise StoreError(missing_status, "RECORD_DELETED" if missing_status == 410 else "RECORD_NOT_FOUND")
    report = public_report(report)
    if report.get("recordId") != record_id:
        raise StoreError(409, "RECORD_ID_MISMATCH")
    if report.get("schemaVersion") == 2 and report.get("reportRevision") != revision(report):
        raise StoreError(409, "REPORT_REVISION_INVALID")
    files, hashes = {}, {}
    for name, filename in ASSET_FILES.items():
        path = checked(directory / filename)
        files[name] = resource_bytes(path) if path.is_file() else None
        hashes[name] = sha256(files[name]) if files[name] is not None else None
        expected = report.get("assets", {}).get(name, {}).get("sha256")
        if report.get("schemaVersion") == 2:
            # Declared schema-2 assets are part of the snapshot contract; absence is not a zero.
            if files[name] is None:
                raise StoreError(409, "ASSET_MISSING")
            if not isinstance(expected, str) or expected != hashes[name]:
                raise StoreError(409, "ASSET_HASH_MISMATCH")
    return Snapshot(report, files, hashes, sha256(canonical({"report": report, "assets": hashes})))


def snapshots(install_id, current_id, prior_id):
    with _transaction() as connection:
        tombstones = connection.execute(
            "SELECT record_ids FROM requests WHERE install_hash=? AND kind='deletion' AND state='DELETED'",
            (_install_hash(install_id),)).fetchall()
        deleted = set().union(*(json.loads(row[0]) for row in tombstones)) if tombstones else set()
        if current_id in deleted or prior_id in deleted:
            raise StoreError(410, "RECORD_DELETED")
        return _snapshot(install_id, current_id), _snapshot(install_id, prior_id)


def _check_snapshot(connection, install_id, record_id, expected):
    # Tombstones take precedence even if an interrupted cleanup left files behind.
    rows = connection.execute("SELECT record_ids FROM requests WHERE install_hash=? AND state='DELETED'",
                              (_install_hash(install_id),)).fetchall()
    if any(record_id in json.loads(row[0]) for row in rows):
        raise StoreError(410, "RECORD_DELETED")
    snapshot = _snapshot(install_id, record_id, 410)
    if snapshot.fingerprint != expected:
        raise StoreError(409, "SNAPSHOT_CHANGED")
    return snapshot


@dataclass(frozen=True)
class Claim:
    install_id: str
    kind: str
    request_id: str
    content_hash: str
    resource_id: str
    owner: str
    cached: bool = False


def claim_request(install_id, kind, request_id, content_hash, resource_id, record_ids) -> Claim:
    strict_id(install_id, "install")
    strict_id(request_id, "request")
    strict_id(resource_id, "resource")
    owner = uuid.uuid4().hex
    install_hash = _install_hash(install_id)
    with _transaction() as connection:
        tombstones = connection.execute(
            "SELECT record_ids FROM requests WHERE install_hash=? AND kind='deletion' AND state='DELETED'",
            (install_hash,)).fetchall()
        if any(set(record_ids).intersection(json.loads(row[0])) for row in tombstones):
            raise StoreError(410, "RECORD_DELETED")
        rows = connection.execute(
            "SELECT request_id, content_hash, resource_id, state FROM requests "
            "WHERE install_hash=? AND kind=? AND (request_id=? OR resource_id=?)",
            (install_hash, kind, request_id, resource_id)).fetchall()
        # Check the request binding before considering another request's cache entry.
        if any(old_request == request_id and old_hash != content_hash
               for old_request, old_hash, _, _ in rows):
            raise StoreError(409, "REQUEST_CONTENT_CONFLICT")
        if any(state == "DELETED" for _, _, _, state in rows):
            raise StoreError(410, "RECORD_DELETED")
        for old_request, old_hash, old_resource, state in rows:
            if old_hash != content_hash:
                raise StoreError(409, "REQUEST_CONTENT_CONFLICT")
            if state == "PUBLISHED":
                # Bind every request ID, including content-cache hits, so it cannot later
                # be reused with different confirmations or different records.
                connection.execute("INSERT OR REPLACE INTO requests VALUES (?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?)", (
                    install_hash, kind, request_id, content_hash, old_resource, owner,
                    canonical(record_ids).decode(), utc_now(),
                ))
                return Claim(install_id, kind, request_id, content_hash, old_resource, owner, True)
        if any(state in ("PENDING", "COMPUTED") for _, _, _, state in rows):
            raise StoreError(409, "REQUEST_IN_PROGRESS")
        destination = record_dir(install_id, resource_id)
        if destination.exists():
            # Existing legacy, foreign, or publication-interrupted directories are never overwritten.
            raise StoreError(409, "RESOURCE_ALREADY_EXISTS")
        connection.execute("INSERT OR REPLACE INTO requests VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", (
            install_hash, kind, request_id, content_hash, resource_id, "PENDING", owner,
            canonical(record_ids).decode(), utc_now(),
        ))
    return Claim(install_id, kind, request_id, content_hash, resource_id, owner)


def fail_request(claim: Claim, code: str):
    with _transaction() as connection:
        connection.execute("UPDATE requests SET state='FAILED', updated_at=? WHERE install_hash=? "
                           "AND kind=? AND request_id=? AND owner=? AND state IN ('PENDING','COMPUTED')",
                           (utc_now(), _install_hash(claim.install_id), claim.kind, claim.request_id, claim.owner))
        _event(connection, claim.install_id, claim.kind.upper() + "_FAILED", {
            "requestId": claim.request_id, "resourceId": claim.resource_id, "reasonCode": code,
            "contentHash": claim.content_hash,
        })


def computed(claim: Claim, payload: dict, event_id: str):
    with _transaction() as connection:
        updated = connection.execute("UPDATE requests SET state='COMPUTED', updated_at=? "
                                     "WHERE install_hash=? AND kind=? AND request_id=? AND owner=? AND state='PENDING'",
                                     (utc_now(), _install_hash(claim.install_id), claim.kind,
                                      claim.request_id, claim.owner)).rowcount
        if updated != 1:
            raise StoreError(410, "REQUEST_INVALIDATED")
        _event(connection, claim.install_id, claim.kind.upper() + "_COMPUTED", {
            "requestId": claim.request_id, "resourceId": claim.resource_id,
            "contentHash": claim.content_hash, **payload,
        }, event_id)


def _write_file(path: Path, data: bytes):
    with checked(path).open("xb") as output:
        output.write(data)
        output.flush()
        os.fsync(output.fileno())


def _sync_directory(path: Path):
    if os.name == "posix":
        descriptor = os.open(str(path), os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)


def publish(claim: Claim, payload: dict, files: dict[str, bytes], expected_snapshots=None):
    destination = record_dir(claim.install_id, claim.resource_id)
    checked(destination.parent).mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".pending-", dir=str(destination.parent)))
    published = False
    try:
        for filename, data in files.items():
            if filename not in ASSET_FILES.values():
                raise StoreError(422, "INVALID_ASSET_NAME")
            _write_file(staging / filename, data)
        _write_file(staging / REPORT_FILE, canonical(payload))
        _sync_directory(staging)
        with _transaction() as connection:
            row = connection.execute("SELECT state, owner FROM requests WHERE install_hash=? AND kind=? AND request_id=?",
                                     (_install_hash(claim.install_id), claim.kind, claim.request_id)).fetchone()
            if row != ("COMPUTED", claim.owner):
                raise StoreError(410, "REQUEST_INVALIDATED")
            for record_id, fingerprint in (expected_snapshots or {}).items():
                _check_snapshot(connection, claim.install_id, record_id, fingerprint)
            if checked(destination).exists():
                raise StoreError(409, "RESOURCE_ALREADY_EXISTS")
            # Serialized namespace prevents competing new writes/deletes; rename is same-filesystem.
            os.rename(staging, destination)
            published = True
            _sync_directory(destination.parent)
            connection.execute("UPDATE requests SET state='PUBLISHED', updated_at=? "
                               "WHERE install_hash=? AND kind=? AND request_id=? AND owner=?",
                               (utc_now(), _install_hash(claim.install_id), claim.kind, claim.request_id, claim.owner))
            _event(connection, claim.install_id, claim.kind.upper() + "_PUBLISHED", {
                "requestId": claim.request_id, "resourceId": claim.resource_id,
                "computedEventId": payload.get("evidenceAssessment", {}).get("auditEventId"),
            })
    except BaseException:
        # A rename followed by an audit/commit failure is not reported as successful.
        # Do not delete a possibly committed immutable resource or disguise it as COMPUTED.
        try:
            audit(claim.install_id, claim.kind.upper() + "_PUBLICATION_FAILED", {
                "requestId": claim.request_id, "resourceId": claim.resource_id,
                "directoryPublished": published,
            })
        except (sqlite3.Error, OSError):
            pass  # The original failure still reaches the caller; nothing claims success.
        raise
    finally:
        if staging.exists():
            shutil.rmtree(staging)


def cached_analysis(claim: Claim) -> dict:
    with _transaction() as connection:
        directory = record_dir(claim.install_id, claim.resource_id)
        report = read_report(directory)
        if report is None:
            raise StoreError(410, "RECORD_DELETED")
        rows = connection.execute("SELECT state FROM requests WHERE install_hash=? AND kind='analysis' AND resource_id=?",
                                  (_install_hash(claim.install_id), claim.resource_id)).fetchall()
        if not rows or any(row[0] == "DELETED" for row in rows):
            raise StoreError(410, "RECORD_DELETED")
        if report.get("reportRevision") != revision(report):
            raise StoreError(409, "REPORT_REVISION_INVALID")
        for name, filename in ASSET_FILES.items():
            if sha256(resource_bytes(directory / filename)) != report.get("assets", {}).get(name, {}).get("sha256"):
                raise StoreError(409, "ASSET_HASH_MISMATCH")
        return public_report(report)


def delete_record(install_id: str, record_id: str) -> bool:
    directory = record_dir(install_id, record_id)
    # Legacy identifiers retain their read spelling, but deletion coordinates use the actual directory.
    actual_install, actual_id = directory.parent.parent.name, directory.name
    related = []
    with _transaction() as connection:
        rows = connection.execute("SELECT kind, request_id, resource_id, record_ids, state FROM requests WHERE install_hash=?",
                                  (_install_hash(actual_install),)).fetchall()
        relevant = [row for row in rows if actual_id in json.loads(row[3])]
        if not directory.exists() and not any(row[4] == "DELETED" for row in relevant):
            return False
        comparison_root = checked(install_dir(actual_install) / "comparisons")
        for kind, request_id, resource_id, _, _ in relevant:
            connection.execute("UPDATE requests SET state='DELETED', updated_at=? WHERE install_hash=? AND kind=? AND request_id=?",
                               (utc_now(), _install_hash(actual_install), kind, request_id))
            if kind == "comparison":
                related.append(checked(comparison_root / resource_id))
        # A tombstone for legacy records also invalidates pending comparisons and prevents ID reuse.
        tombstone_id = "deleted-" + sha256(actual_id.encode())
        connection.execute("INSERT OR REPLACE INTO requests VALUES (?, 'deletion', ?, ?, ?, 'DELETED', '', ?, ?)", (
            _install_hash(actual_install), tombstone_id, sha256(actual_id.encode()), actual_id,
            canonical([actual_id]).decode(), utc_now(),
        ))
        _event(connection, actual_install, "DELETION_REQUESTED", {"recordId": actual_id})
    # Tombstones are durable before cleanup starts; readers cannot serve stale comparisons.
    try:
        for path in set(related):
            if checked(path).exists():
                shutil.rmtree(path)
        if checked(directory).exists():
            shutil.rmtree(directory)
    except OSError as exc:
        audit(actual_install, "DELETION_FAILED", {"recordId": actual_id, "reasonCode": "CLEANUP_FAILED"})
        raise StoreError(500, "DELETION_CLEANUP_FAILED") from exc
    audit(actual_install, "DELETION_COMPLETED", {"recordId": actual_id})
    return True


def record_exists(install_id: str, record_id: str) -> bool:
    return checked(record_dir(install_id, record_id) / REPORT_FILE).is_file()
