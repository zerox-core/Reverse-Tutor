from __future__ import annotations

from datetime import UTC, datetime
from threading import RLock

from .content_activity_ports import (
    ActivityPage,
    ActivityParticipationRecord,
    ActivityPortConflict,
    ActivityQuery,
    ActivityRecord,
    ActivityWriteCommand,
    ContentFeedQuery,
    ContentPage,
    ContentRecord,
    LeaderboardPage,
    LeaderboardQuery,
    LeaderboardRecord,
)


class MemoryContentActivityPort:
    def __init__(self) -> None:
        self._lock = RLock()
        self.reset()

    def reset(self) -> None:
        with self._lock:
            self.content: dict[str, ContentRecord] = {}
            self.activities = {
                "focus-week": ActivityRecord(
                    id="focus-week",
                    title="Focus Week",
                    description="Complete focused local study sessions this week.",
                    revision=1,
                    starts_at=datetime(1970, 1, 1, tzinfo=UTC),
                    ends_at=datetime(2100, 1, 1, tzinfo=UTC),
                    requires_online_confirmation=False,
                    allows_deferred_progress=True,
                    state="active",
                    session_template_id="focus-week-v1",
                )
            }
            self._updated_at = datetime.now(UTC)
            self._results: dict[
                tuple[str, str, str, str],
                tuple[tuple[object, ...], ActivityParticipationRecord],
            ] = {}
            self._participations: dict[
                tuple[str, str], ActivityParticipationRecord
            ] = {}
            self.write_count = 0

    def list_published(self, query: ContentFeedQuery) -> ContentPage:
        with self._lock:
            records = [
                record
                for record in self.content.values()
                if record.status == "published"
                and (not query.content_types or record.type in query.content_types)
            ]
            records.sort(key=lambda item: (-item.published_at.timestamp(), item.id))
            offset = self._offset(query.cursor)
            items = tuple(records[offset : offset + query.limit])
            next_offset = offset + len(items)
            next_cursor = str(next_offset) if next_offset < len(records) else None
            version = max((item.content_version for item in records), default=0)
            return ContentPage(version, self._updated_at, items, next_cursor)

    def get_by_slug(self, slug: str, at: datetime) -> ContentRecord | None:
        del at
        with self._lock:
            return self.content.get(slug)

    def list_activities(self, query: ActivityQuery) -> ActivityPage:
        with self._lock:
            records = sorted(self.activities.values(), key=lambda item: item.id)
            offset = self._offset(query.cursor)
            items = tuple(records[offset : offset + query.limit])
            next_offset = offset + len(items)
            next_cursor = str(next_offset) if next_offset < len(records) else None
            return ActivityPage(items, next_cursor, self._updated_at)

    def get_activity(self, activity_id: str, at: datetime) -> ActivityRecord | None:
        del at
        with self._lock:
            return self.activities.get(activity_id)

    def join(
        self, command: ActivityWriteCommand
    ) -> ActivityParticipationRecord | None:
        return self._write(command, "join", joined=True, progress=0, state="joined")

    def update_progress(
        self, command: ActivityWriteCommand
    ) -> ActivityParticipationRecord | None:
        with self._lock:
            previous = self._participations.get(
                (command.activity_id, command.account_id)
            )
            progress = max(previous.progress if previous else 0, command.progress or 0)
            return self._write(
                command,
                "progress",
                joined=True,
                progress=progress,
                state="joined",
            )

    def leave(
        self, command: ActivityWriteCommand
    ) -> ActivityParticipationRecord | None:
        with self._lock:
            previous = self._participations.get(
                (command.activity_id, command.account_id)
            )
            return self._write(
                command,
                "leave",
                joined=False,
                progress=previous.progress if previous else 0,
                state="left",
            )

    def leaderboard(self, query: LeaderboardQuery) -> LeaderboardPage | None:
        with self._lock:
            if query.activity_id not in self.activities:
                return None
            rows = [
                row
                for (activity_id, _), row in self._participations.items()
                if activity_id == query.activity_id and row.joined
            ]
            rows.sort(key=lambda row: (-row.progress, row.user_id))
            ranked = tuple(
                LeaderboardRecord(
                    rank=rank,
                    display_name="Learner",
                    avatar_url=None,
                    progress=row.progress,
                )
                for rank, row in enumerate(rows, start=1)
            )
            offset = self._offset(query.cursor)
            items = ranked[offset : offset + query.limit]
            next_offset = offset + len(items)
            next_cursor = str(next_offset) if next_offset < len(ranked) else None
            return LeaderboardPage(items, next_cursor, self._updated_at)

    def _write(
        self,
        command: ActivityWriteCommand,
        operation: str,
        *,
        joined: bool,
        progress: int,
        state: str,
    ) -> ActivityParticipationRecord | None:
        with self._lock:
            if command.activity_id not in self.activities:
                return None
            key = (
                operation,
                command.activity_id,
                command.account_id,
                command.idempotency_key,
            )
            fingerprint = (
                command.device_id,
                command.revision,
                command.progress,
            )
            replay = self._results.get(key)
            if replay:
                if replay[0] != fingerprint:
                    raise ActivityPortConflict(
                        "idempotency_key_reused",
                        "Idempotency key was reused with a different request",
                    )
                return replay[1]
            previous = self._participations.get(
                (command.activity_id, command.account_id)
            )
            result = ActivityParticipationRecord(
                activity_id=command.activity_id,
                user_id=command.account_id,
                joined=joined,
                progress=progress,
                revision=max((previous.revision + 1) if previous else 1, command.revision),
                state=state,
                idempotency_key=command.idempotency_key,
            )
            self._results[key] = (fingerprint, result)
            self._participations[(command.activity_id, command.account_id)] = result
            self._updated_at = command.at
            self.write_count += 1
            return result

    @staticmethod
    def _offset(cursor: str | None) -> int:
        try:
            return max(0, int(cursor or "0"))
        except ValueError:
            return 0
