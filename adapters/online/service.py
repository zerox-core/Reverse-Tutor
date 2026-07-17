from __future__ import annotations

import os
import time
from copy import deepcopy
from threading import RLock
from typing import Any

from .models import (
    ActivityProgressRequest,
    OnlineWriteIdentity,
    SyncItem,
    SyncPullRequest,
    SyncPushRequest,
    WeeklyInsightRequest,
)


SYNCABLE_ENTITY_TYPES = {
    "activity_progress",
    "study_plan",
    "sync_summary",
    "user_setting",
}


class OnlineHybridService:
    def __init__(self) -> None:
        self._lock = RLock()
        self.reset()

    def reset(self) -> None:
        with self._lock:
            self.activities = {
                "focus-week": {
                    "id": "focus-week",
                    "title": "Focus Week",
                    "description": "Complete focused local study sessions this week.",
                    "revision": 1,
                    "startsAtEpochMillis": 0,
                    "endsAtEpochMillis": 4_102_444_800_000,
                    "requiresOnlineConfirmation": False,
                    "allowsDeferredProgress": True,
                    "state": "active",
                    "sessionTemplateId": "focus-week-v1",
                }
            }
            self._updated_at_epoch_millis = int(time.time() * 1000)
            self._activity_results: dict[tuple[str, str, str], dict[str, Any]] = {}
            self._activity_progress: dict[tuple[str, str], dict[str, Any]] = {}
            self._sync_results: dict[tuple[str, str], dict[str, Any]] = {}
            self._sync_records: list[dict[str, Any]] = []
            self.activity_write_count = 0
            self.sync_write_count = 0

    def list_activities(self) -> dict[str, Any]:
        with self._lock:
            return {
                "items": deepcopy(list(self.activities.values())),
                "nextCursor": None,
                "updatedAtEpochMillis": self._updated_at_epoch_millis,
            }

    def get_activity(self, activity_id: str) -> dict[str, Any] | None:
        with self._lock:
            activity = self.activities.get(activity_id)
            return deepcopy(activity) if activity else None

    def join_activity(
        self,
        activity_id: str,
        account_id: str,
        request: OnlineWriteIdentity,
    ) -> dict[str, Any] | None:
        with self._lock:
            if activity_id not in self.activities:
                return None
            key = (f"join:{activity_id}", account_id, request.idempotency_key)
            if key in self._activity_results:
                return deepcopy(self._activity_results[key])
            result = {
                "activityId": activity_id,
                "userId": account_id,
                "joined": True,
                "progress": 0,
                "revision": max(1, request.revision),
                "state": "joined",
                "idempotencyKey": request.idempotency_key,
            }
            self._activity_results[key] = result
            self._activity_progress[(activity_id, account_id)] = result
            self.activity_write_count += 1
            return deepcopy(result)

    def update_activity_progress(
        self,
        activity_id: str,
        account_id: str,
        request: ActivityProgressRequest,
    ) -> dict[str, Any] | None:
        with self._lock:
            if activity_id not in self.activities:
                return None
            key = (f"progress:{activity_id}", account_id, request.idempotency_key)
            if key in self._activity_results:
                return deepcopy(self._activity_results[key])
            previous = self._activity_progress.get((activity_id, account_id), {})
            result = {
                "activityId": activity_id,
                "userId": account_id,
                "joined": True,
                "progress": max(int(previous.get("progress", 0)), request.progress),
                "revision": max(int(previous.get("revision", 0)) + 1, request.revision),
                "state": "joined",
                "idempotencyKey": request.idempotency_key,
            }
            self._activity_results[key] = result
            self._activity_progress[(activity_id, account_id)] = result
            self.activity_write_count += 1
            return deepcopy(result)

    def leave_activity(
        self,
        activity_id: str,
        account_id: str,
        request: OnlineWriteIdentity,
    ) -> dict[str, Any] | None:
        with self._lock:
            if activity_id not in self.activities:
                return None
            key = (f"leave:{activity_id}", account_id, request.idempotency_key)
            if key in self._activity_results:
                return deepcopy(self._activity_results[key])
            previous = self._activity_progress.get((activity_id, account_id), {})
            result = {
                "activityId": activity_id,
                "userId": account_id,
                "joined": False,
                "progress": int(previous.get("progress", 0)),
                "revision": max(int(previous.get("revision", 0)) + 1, request.revision),
                "state": "left",
                "idempotencyKey": request.idempotency_key,
            }
            self._activity_results[key] = result
            self._activity_progress[(activity_id, account_id)] = result
            self.activity_write_count += 1
            return deepcopy(result)

    def activity_leaderboard(self, activity_id: str) -> dict[str, Any] | None:
        with self._lock:
            if activity_id not in self.activities:
                return None
            rows = [
                deepcopy(progress)
                for (stored_activity_id, _), progress in self._activity_progress.items()
                if stored_activity_id == activity_id
            ]
            rows.sort(key=lambda row: (-int(row["progress"]), str(row["userId"])))
            # Leaderboards are public.  Keep the legacy userId for existing
            # callers while exposing the strict display/rank fields consumed by
            # the native client; user identity is never inferred from request
            # bodies for protected writes.
            items = []
            for rank, row in enumerate(rows, start=1):
                items.append(
                    {
                        **row,
                        "rank": rank,
                        "displayName": "Learner",
                        "avatarUrl": None,
                        "isCurrentUser": False,
                    }
                )
            return {
                "items": items,
                "nextCursor": None,
                "updatedAtEpochMillis": self._updated_at_epoch_millis,
            }

    def push_sync(self, account_id: str, request: SyncPushRequest) -> dict[str, Any]:
        results = []
        for item in request.items:
            try:
                results.append(self._push_sync_item(account_id, request, item))
            except Exception:
                results.append({
                    "envelopeId": item.envelope_id,
                    "entityId": item.entity_id,
                    "accepted": False,
                    "errorCode": "temporary_sync_failure",
                    "retryable": True,
                })
        return {
            "cursor": str(len(self._sync_records)),
            "items": results,
        }

    def _push_sync_item(
        self,
        account_id: str,
        request: SyncPushRequest,
        item: SyncItem,
    ) -> dict[str, Any]:
        with self._lock:
            if item.entity_type not in SYNCABLE_ENTITY_TYPES:
                return {
                    "envelopeId": item.envelope_id,
                    "entityId": item.entity_id,
                    "accepted": False,
                    "errorCode": "entity_type_not_syncable",
                    "retryable": False,
                }
            key = (account_id, item.idempotency_key)
            if key in self._sync_results:
                return deepcopy(self._sync_results[key])
            remote_revision = item.revision + 1
            record = {
                "entityId": item.entity_id,
                "entityType": item.entity_type,
                "ownerId": account_id,
                "deviceId": request.device_id,
                "revision": remote_revision,
                "idempotencyKey": item.idempotency_key,
                "payload": deepcopy(item.payload),
                "deletedAtEpochMillis": item.deleted_at_epoch_millis,
            }
            self._sync_records.append(record)
            result = {
                "envelopeId": item.envelope_id,
                "entityId": item.entity_id,
                "accepted": True,
                "remoteRevision": remote_revision,
                "retryable": False,
            }
            self._sync_results[key] = result
            self.sync_write_count += 1
            return deepcopy(result)

    def pull_sync(self, account_id: str, request: SyncPullRequest) -> dict[str, Any]:
        with self._lock:
            try:
                offset = max(0, int(request.cursor or "0"))
            except ValueError:
                offset = 0
            items = [
                deepcopy(record)
                for record in self._sync_records[offset:]
                if record["ownerId"] == account_id
            ]
            return {
                "cursor": str(len(self._sync_records)),
                "items": items,
            }

    def weekly_insight(
        self, account_id: str, request: WeeklyInsightRequest
    ) -> dict[str, Any]:
        del account_id
        active_days = max(0, int(request.statistics.get("activeDays", 0)))
        completed_tasks = max(0, int(request.statistics.get("completedTasks", 0)))
        return {
            "spaceId": request.space_id,
            "weekStartEpochMillis": request.week_start_epoch_millis,
            "sourceRevision": request.source_revision,
            "generatorVersion": "local-statistics-v1",
            "summary": (
                f"{active_days} active days and {completed_tasks} completed tasks "
                "were included in this weekly summary."
            ),
        }

    def latest_release(self) -> dict[str, Any]:
        return {
            "versionName": os.getenv("NATIVE_LATEST_VERSION_NAME", "0.1.0"),
            "versionCode": int(os.getenv("NATIVE_LATEST_VERSION_CODE", "1")),
            "minimumSupportedVersionCode": int(
                os.getenv("NATIVE_MINIMUM_SUPPORTED_VERSION_CODE", "1")
            ),
            "downloadUrl": os.getenv("NATIVE_RELEASE_DOWNLOAD_URL") or None,
            "sha256": os.getenv("NATIVE_RELEASE_SHA256") or None,
        }


online_service = OnlineHybridService()
