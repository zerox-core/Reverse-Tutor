from __future__ import annotations

import os
from copy import deepcopy
from datetime import datetime
from threading import RLock
from typing import Any, Callable

from .content_activity_memory import MemoryContentActivityPort
from .content_activity_ports import ActivityPort, PublicContentPort
from .content_activity_service import ContentActivityService
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
    def __init__(
        self,
        content_port: PublicContentPort | None = None,
        activity_port: ActivityPort | None = None,
        clock: Callable[[], datetime] | None = None,
    ) -> None:
        self._lock = RLock()
        self._clock = clock
        self._default_content_activity_port = MemoryContentActivityPort()
        self._content_port = content_port or self._default_content_activity_port
        self._activity_port = activity_port or self._default_content_activity_port
        self._content_activity = ContentActivityService(
            self._content_port,
            self._activity_port,
            self._clock,
        )
        self.reset()

    def reset(self) -> None:
        with self._lock:
            self._default_content_activity_port.reset()
            self._sync_results: dict[tuple[str, str], dict[str, Any]] = {}
            self._sync_records: list[dict[str, Any]] = []
            self.sync_write_count = 0

    @property
    def activity_write_count(self) -> int:
        return int(getattr(self._activity_port, "write_count", 0))

    def set_content_activity_ports(
        self,
        *,
        content_port: PublicContentPort | None = None,
        activity_port: ActivityPort | None = None,
    ) -> None:
        with self._lock:
            if content_port is not None:
                self._content_port = content_port
            if activity_port is not None:
                self._activity_port = activity_port
            self._content_activity = ContentActivityService(
                self._content_port,
                self._activity_port,
                self._clock,
            )

    def reset_content_activity_ports(self) -> None:
        with self._lock:
            self._default_content_activity_port.reset()
            self._content_port = self._default_content_activity_port
            self._activity_port = self._default_content_activity_port
            self._content_activity = ContentActivityService(
                self._content_port,
                self._activity_port,
                self._clock,
            )

    def content_feed(
        self,
        *,
        cursor: str | None,
        limit: int,
        content_types: frozenset[str],
    ):
        return self._content_activity.content_feed(
            cursor=cursor,
            limit=limit,
            content_types=content_types,
        )

    def content_detail(self, slug: str):
        return self._content_activity.content_detail(slug)

    def list_activities(
        self, *, cursor: str | None = None, limit: int = 20
    ):
        return self._content_activity.list_activities(cursor=cursor, limit=limit)

    def get_activity(self, activity_id: str):
        return self._content_activity.get_activity(activity_id)

    def join_activity(
        self,
        activity_id: str,
        account_id: str,
        request: OnlineWriteIdentity,
    ):
        return self._content_activity.join_activity(activity_id, account_id, request)

    def update_activity_progress(
        self,
        activity_id: str,
        account_id: str,
        request: ActivityProgressRequest,
    ):
        return self._content_activity.update_activity_progress(
            activity_id,
            account_id,
            request,
        )

    def leave_activity(
        self,
        activity_id: str,
        account_id: str,
        request: OnlineWriteIdentity,
    ):
        return self._content_activity.leave_activity(activity_id, account_id, request)

    def activity_leaderboard(
        self,
        activity_id: str,
        *,
        cursor: str | None = None,
        limit: int = 50,
    ):
        return self._content_activity.activity_leaderboard(
            activity_id,
            cursor=cursor,
            limit=limit,
        )

    def catalog_availability(self) -> tuple[bool, bool]:
        content = self.content_feed(
            cursor=None,
            limit=1,
            content_types=frozenset(),
        )
        activities = self.list_activities(cursor=None, limit=1)
        return bool(content.body.items), bool(activities.items)

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
