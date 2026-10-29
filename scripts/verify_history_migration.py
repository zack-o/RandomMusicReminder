"""用内存 SQLite 校验项目中的真实迁移及 DAO SQL，不连接任何设备或用户数据库。"""

import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/muyex/randommusicreminder"
SCHEMAS = ROOT / "app/schemas/com.muyex.randommusicreminder.data.local.AppDatabase"
OLD_SCHEMA = json.loads((SCHEMAS / "1.json").read_text(encoding="utf-8"))["database"]
MIGRATION_SOURCE = (SOURCE / "data/local/DatabaseMigrations.kt").read_text(encoding="utf-8")
MIGRATION_SQL = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\)', MIGRATION_SOURCE.split("val MIGRATION_1_2", 1)[1], re.S)
MIGRATION_2_3_SQL = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\)',
                             MIGRATION_SOURCE.split("val MIGRATION_2_3", 1)[1].split("val MIGRATION_1_2", 1)[0], re.S)
MIGRATION_3_4_SQL = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\)',
                             MIGRATION_SOURCE.split("val MIGRATION_3_4", 1)[1].split("val MIGRATION_2_3", 1)[0], re.S)
DAO_SOURCE = (SOURCE / "data/local/dao/RingHistoryDao.kt").read_text(encoding="utf-8")
MIGRATION_4_5_SQL = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\)',
                             MIGRATION_SOURCE.split("val MIGRATION_4_5", 1)[1].split("val MIGRATION_3_4", 1)[0], re.S)
MIGRATION_5_6_SQL = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\)',
                             MIGRATION_SOURCE.split("val MIGRATION_5_6", 1)[1].split("val MIGRATION_4_5", 1)[0], re.S)
MIGRATION_6_7_SQL = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\)',
                             MIGRATION_SOURCE.split("val MIGRATION_6_7", 1)[1].split("val MIGRATION_5_6", 1)[0], re.S)
MIGRATION_7_8_SQL = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\)',
                             MIGRATION_SOURCE.split("val MIGRATION_7_8", 1)[1].split("val MIGRATION_6_7", 1)[0], re.S)
MIGRATION_8_9_SQL = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\)',
                             MIGRATION_SOURCE.split("val MIGRATION_8_9", 1)[1].split("val MIGRATION_7_8", 1)[0], re.S)
MIGRATION_9_10_SQL = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\)',
                              MIGRATION_SOURCE.split("val MIGRATION_9_10", 1)[1].split("val MIGRATION_8_9", 1)[0], re.S)
MIGRATION_10_11_SQL = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\)',
                               MIGRATION_SOURCE.split("val MIGRATION_10_11",1)[1].split("val MIGRATION_9_10",1)[0],re.S)
FINISH_SQL = re.search(r'@Query\("""(.*?)"""\)\s*suspend fun finish', DAO_SOURCE, re.S).group(1)
MUSIC_SQL = re.search(r'@Query\("([^"\n]+)"\)\s*suspend fun updateMusic', DAO_SOURCE).group(1)


class HistoryMigrationTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA foreign_keys = ON")
        self.entities = {item["tableName"]: item for item in OLD_SCHEMA["entities"]}
        for name, entity in self.entities.items():
            self.db.execute(entity["createSql"].replace("${TABLE_NAME}", name))
            for index in entity.get("indices", []):
                self.db.execute(index["createSql"].replace("${TABLE_NAME}", name))
        self.insert("events", id=1, title="会议", scheduleType="DAILY", localDate="2026-10-19", localTime="12:00", musicPoolMode="GLOBAL", enabled=1)
        self.insert("events", id=2, title="喝水", scheduleType="DAILY", localDate="2026-10-19", localTime="12:00", musicPoolMode="GLOBAL", enabled=1)
        self.insert("reminder_rules", id=1, eventId=1, alertMode="STRONG", ringDurationSeconds=30)
        self.insert("reminder_rules", id=2, eventId=2, alertMode="GENTLE", ringDurationSeconds=60)
        self.insert("music_tracks", id=7, displayName="森林.mp3", uri="file:///test/forest.mp3", mimeType="audio/mpeg")
        for index in (1, 2):
            self.insert("scheduled_occurrences", id=f"occ-{index}", eventId=index, reminderRuleId=index,
                        baseDate="2026-10-19", kind="ORIGINAL", state="COMPLETED", scheduleMode="EXACT", sessionId="merged")
        self.insert("ring_sessions", id="merged", scheduledTriggerAtEpochMillis=900, startedAtEpochMillis=1000,
                    deadlineEpochMillis=61000, selectedTrackId=7, state="COMPLETED", stopReason="USER")
        self.insert("ring_sessions", id="orphan", scheduledTriggerAtEpochMillis=900, startedAtEpochMillis=2000,
                    deadlineEpochMillis=62000, state="COMPLETED", stopReason="TIMEOUT")
        self.before = {name: self.rows(name) for name in self.entities}
        self.migrate()

    def tearDown(self):
        self.db.close()

    def insert(self, table, **values):
        defaults = {field["columnName"]: 0 if field["affinity"] == "INTEGER" else ""
                    for field in self.entities[table]["fields"] if field.get("notNull", False)}
        defaults.update(values)
        columns = ",".join(f"`{key}`" for key in defaults)
        parameters = ",".join("?" for _ in defaults)
        self.db.execute(f"INSERT INTO `{table}` ({columns}) VALUES ({parameters})", tuple(defaults.values()))

    def rows(self, table):
        return [tuple(row) for row in self.db.execute(f"SELECT * FROM `{table}` ORDER BY rowid")]

    def migrate(self):
        self.assertEqual(4, len(MIGRATION_SQL), "迁移 SQL 提取不完整")
        with self.db:
            for sql in MIGRATION_SQL:
                self.db.execute(sql)

    def test_original_data_is_unchanged(self):
        self.assertEqual(self.before, {name: self.rows(name) for name in self.entities})
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_backfill_merged_snapshot_without_invented_end_time(self):
        row = self.db.execute("SELECT * FROM ring_history WHERE sessionId='merged'").fetchone()
        self.assertEqual({"会议", "喝水"}, set(row["title"].split(",")))
        self.assertEqual(2, row["eventCount"])
        self.assertEqual("森林.mp3", row["musicName"])
        self.assertEqual("STRONG", row["alertMode"])
        self.assertIsNone(row["endedAtEpochMillis"])
        orphan = self.db.execute("SELECT * FROM ring_history WHERE sessionId='orphan'").fetchone()
        self.assertEqual("已删除的提醒", orphan["title"])
        self.assertIsNone(orphan["musicName"])

    def test_migration_is_idempotent(self):
        previous = self.rows("ring_history")
        self.migrate()
        self.assertEqual(previous, self.rows("ring_history"))

    def test_snapshot_survives_original_objects_being_deleted(self):
        previous = self.rows("ring_history")
        self.db.execute("DELETE FROM events")
        self.db.execute("DELETE FROM music_tracks")
        self.assertEqual(previous, self.rows("ring_history"))

    def test_schema_matches_room_export(self):
        path = SCHEMAS / "2.json"
        self.assertTrue(path.is_file(), "请先运行 Gradle 构建生成版本 2 的 Room schema")
        target = json.loads(path.read_text(encoding="utf-8"))["database"]
        for entity in target["entities"]:
            columns = {row["name"]: (row["type"], bool(row["notnull"]))
                       for row in self.db.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
            expected = {field["columnName"]: (field["affinity"], field.get("notNull", False)) for field in entity["fields"]}
            self.assertEqual(expected, columns, entity["tableName"])
            keys = [row["name"] for row in self.db.execute(f"PRAGMA table_info(`{entity['tableName']}`)") if row["pk"]]
            self.assertEqual(entity["primaryKey"]["columnNames"], keys)
        indexes = {row["name"] for row in self.db.execute("PRAGMA index_list(ring_history)")}
        self.assertIn("index_ring_history_startedAtEpochMillis", indexes)
        self.assertIn("index_ring_history_anchorOccurrenceId", indexes)

    def test_finish_preserves_first_result_and_music_snapshot(self):
        self.db.execute("UPDATE ring_history SET state='ACTIVE',stopReason=NULL WHERE sessionId='merged'")
        self.db.execute(MUSIC_SQL, {"sessionId": "merged", "musicName": "兜底铃声"})
        args = {"sessionId": "merged", "state": "COMPLETED", "reason": "SNOOZED", "endedAt": 18000}
        self.assertEqual(1, self.db.execute(FINISH_SQL, args).rowcount)
        args.update(reason="TIMEOUT", endedAt=22000)
        self.assertEqual(0, self.db.execute(FINISH_SQL, args).rowcount)
        self.assertEqual(0, self.db.execute(MUSIC_SQL, {"sessionId": "merged", "musicName": "误更新"}).rowcount)
        row = self.db.execute("SELECT * FROM ring_history WHERE sessionId='merged'").fetchone()
        self.assertEqual("SNOOZED", row["stopReason"])
        self.assertEqual(18000, row["endedAtEpochMillis"])
        self.assertEqual("兜底铃声", row["musicName"])

    def upgrade_to_three(self):
        self.assertEqual(2, len(MIGRATION_2_3_SQL))
        with self.db:
            for sql in MIGRATION_2_3_SQL:
                self.db.execute(sql)

    def assert_version_three_schema(self, connection):
        target = json.loads((SCHEMAS / "3.json").read_text(encoding="utf-8"))["database"]
        for entity in target["entities"]:
            columns = {row["name"]: (row["type"], bool(row["notnull"]))
                       for row in connection.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
            expected = {field["columnName"]: (field["affinity"], field.get("notNull", False)) for field in entity["fields"]}
            self.assertEqual(expected, columns, entity["tableName"])
        policy = next(row for row in connection.execute("PRAGMA table_info(events)") if row["name"] == "holidayPolicy")
        self.assertEqual("'NONE'", policy["dflt_value"])
        self.assertEqual([], connection.execute("PRAGMA foreign_key_check").fetchall())

    def test_chained_upgrade_preserves_original_columns_and_history(self):
        history = self.rows("ring_history")
        self.upgrade_to_three()
        self.assert_version_three_schema(self.db)
        self.assertEqual(history, self.rows("ring_history"))
        self.assertEqual([("NONE",), ("NONE",)], [tuple(row) for row in self.db.execute("SELECT holidayPolicy FROM events ORDER BY id")])
        for name, entity in self.entities.items():
            columns = ",".join(f"`{field['columnName']}`" for field in entity["fields"])
            actual = [tuple(row) for row in self.db.execute(f"SELECT {columns} FROM `{name}` ORDER BY rowid")]
            self.assertEqual(self.before[name], actual, name)

    def test_direct_version_two_upgrade_matches_room_three(self):
        version_two = json.loads((SCHEMAS / "2.json").read_text(encoding="utf-8"))["database"]
        with sqlite3.connect(":memory:") as connection:
            connection.row_factory = sqlite3.Row
            for entity in version_two["entities"]:
                connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
                for index in entity.get("indices", []):
                    connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
            for sql in MIGRATION_2_3_SQL:
                connection.execute(sql)
            self.assert_version_three_schema(connection)

    def test_new_usage_table_does_not_backfill_or_touch_ring_history(self):
        history = self.rows("ring_history")
        self.upgrade_to_three()
        self.assertEqual([], self.rows("usage_daily"))
        self.db.execute("INSERT INTO usage_daily VALUES ('2026-10-20', 2, 15000)")
        self.assertEqual(history, self.rows("ring_history"))

    def assert_version_four(self, connection):
        target = json.loads((SCHEMAS / "4.json").read_text(encoding="utf-8"))["database"]
        for entity in target["entities"]:
            actual = {row["name"]: (row["type"], bool(row["notnull"]))
                      for row in connection.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
            expected = {field["columnName"]: (field["affinity"], field.get("notNull", False)) for field in entity["fields"]}
            self.assertEqual(expected, actual, entity["tableName"])
        self.assertEqual([], connection.execute("PRAGMA foreign_key_check").fetchall())

    def test_chained_upgrade_to_four_preserves_all_v3_data(self):
        self.upgrade_to_three()
        tables = list(self.entities) + ["ring_history", "usage_daily"]
        self.db.execute("INSERT INTO usage_daily VALUES ('2026-10-20', 2, 1000)")
        before = {name: self.rows(name) for name in tables}
        self.assertEqual(1, len(MIGRATION_3_4_SQL))
        for sql in MIGRATION_3_4_SQL:
            self.db.execute(sql)
        self.assert_version_four(self.db)
        self.assertEqual(before, {name: self.rows(name) for name in tables})
        self.assertEqual([], self.rows("calendar_overrides"))
        self.db.execute("INSERT INTO calendar_overrides VALUES ('2027-01-02','REST','自定义','手动设置',1000)")
        self.assertEqual(before, {name: self.rows(name) for name in tables})

    def test_version_four_upgrade_preserves_data_and_sets_safe_recurrence_defaults(self):
        self.upgrade_to_three()
        for sql in MIGRATION_3_4_SQL:
            self.db.execute(sql)
        self.db.execute("INSERT INTO calendar_overrides VALUES ('2027-01-02','REST','自定义','手动设置',1000)")
        history = self.rows("ring_history")
        calendar = self.rows("calendar_overrides")
        self.assertEqual(3, len(MIGRATION_4_5_SQL))
        for sql in MIGRATION_4_5_SQL:
            self.db.execute(sql)
        self.assertEqual([(1, 'SKIP', None), (1, 'SKIP', None)], [tuple(row) for row in self.db.execute("SELECT intervalDays,missingDayPolicy,endDate FROM events ORDER BY id")])
        self.assertEqual(history, self.rows("ring_history"))
        self.assertEqual(calendar, self.rows("calendar_overrides"))
        target = json.loads((SCHEMAS / "5.json").read_text(encoding="utf-8"))["database"]
        for entity in target["entities"]:
            actual = {row["name"]: (row["type"], bool(row["notnull"])) for row in self.db.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
            expected = {field["columnName"]: (field["affinity"], field.get("notNull", False)) for field in entity["fields"]}
            self.assertEqual(expected, actual, entity["tableName"])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_version_five_upgrade_preserves_existing_fields_and_safe_defaults(self):
        self.upgrade_to_three()
        for sql in MIGRATION_3_4_SQL + MIGRATION_4_5_SQL:
            self.db.execute(sql)
        tables = [r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        columns = {name: [r['name'] for r in self.db.execute(f"PRAGMA table_info(`{name}`)")] for name in tables}
        before = {name: self.rows(name) for name in tables}
        self.assertEqual(17, len(MIGRATION_5_6_SQL))
        for sql in MIGRATION_5_6_SQL:
            self.db.execute(sql)
        for name in tables:
            select = ','.join(f'`{col}`' for col in columns[name])
            self.assertEqual(before[name], [tuple(r) for r in self.db.execute(f"SELECT {select} FROM `{name}` ORDER BY rowid")], name)
        self.assertEqual([('FIXED', 0, None, 0)] * 2, [tuple(r) for r in self.db.execute("SELECT timingMode,randomSeed,windowSnoozeMinutes,windowBackupEnabled FROM events ORDER BY id")])
        target = json.loads((SCHEMAS / "6.json").read_text(encoding="utf-8"))["database"]
        for entity in target["entities"]:
            actual = {r['name']: (r['type'], bool(r['notnull']), r['dflt_value']) for r in self.db.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
            expected = {f['columnName']: (f['affinity'], f.get('notNull', False), f.get('defaultValue')) for f in entity['fields']}
            self.assertEqual(expected, actual, entity['tableName'])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_version_six_upgrade_preserves_data_and_adds_empty_shift_configuration(self):
        self.upgrade_to_three()
        for sql in MIGRATION_3_4_SQL + MIGRATION_4_5_SQL + MIGRATION_5_6_SQL:
            self.db.execute(sql)
        tables = [r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        columns = {name: [r['name'] for r in self.db.execute(f"PRAGMA table_info(`{name}`)")] for name in tables}
        before = {name: self.rows(name) for name in tables}
        self.assertEqual(2, len(MIGRATION_6_7_SQL))
        for sql in MIGRATION_6_7_SQL:
            self.db.execute(sql)
        for name in tables:
            select = ','.join(f'`{col}`' for col in columns[name])
            self.assertEqual(before[name], [tuple(r) for r in self.db.execute(f"SELECT {select} FROM `{name}` ORDER BY rowid")], name)
        self.assertEqual([('',), ('',)], [tuple(r) for r in self.db.execute("SELECT shiftCycleJson FROM events ORDER BY id")])
        target = json.loads((SCHEMAS / "7.json").read_text(encoding="utf-8"))["database"]
        for entity in target["entities"]:
            actual = {r['name']: (r['type'], bool(r['notnull']), r['dflt_value']) for r in self.db.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
            expected = {f['columnName']: (f['affinity'], f.get('notNull', False), f.get('defaultValue')) for f in entity['fields']}
            self.assertEqual(expected, actual, entity['tableName'])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def upgrade_to_eight(self):
        self.upgrade_to_three()
        for sql in MIGRATION_3_4_SQL + MIGRATION_4_5_SQL + MIGRATION_5_6_SQL + MIGRATION_6_7_SQL + MIGRATION_7_8_SQL:
            self.db.execute(sql)

    def test_version_seven_upgrade_preserves_fields_and_adds_safe_organizer_defaults(self):
        self.upgrade_to_three()
        for sql in MIGRATION_3_4_SQL + MIGRATION_4_5_SQL + MIGRATION_5_6_SQL + MIGRATION_6_7_SQL:
            self.db.execute(sql)
        tables = [r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        columns = {name: [r['name'] for r in self.db.execute(f"PRAGMA table_info(`{name}`)")] for name in tables}
        before = {name: self.rows(name) for name in tables}
        self.assertEqual(3, len(MIGRATION_7_8_SQL))
        for sql in MIGRATION_7_8_SQL:
            self.db.execute(sql)
        for name in tables:
            select = ','.join(f'`{col}`' for col in columns[name])
            self.assertEqual(before[name], [tuple(r) for r in self.db.execute(f"SELECT {select} FROM `{name}` ORDER BY rowid")], name)
        self.assertTrue(all(tuple(r) == ('[]', None, None) for r in self.db.execute("SELECT tagsJson,deletedAtEpochMillis,resumedAfterEpochMillis FROM events")))
        target = json.loads((SCHEMAS / "8.json").read_text(encoding="utf-8"))["database"]
        for entity in target["entities"]:
            actual = {r['name']: (r['type'], bool(r['notnull']), r['dflt_value']) for r in self.db.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
            expected = {f['columnName']: (f['affinity'], f.get('notNull', False), f.get('defaultValue')) for f in entity['fields']}
            self.assertEqual(expected, actual, entity['tableName'])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_version_eight_upgrade_preserves_fields_and_defaults_to_music(self):
        self.upgrade_to_eight()
        tables = [r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        columns = {name: [r['name'] for r in self.db.execute(f"PRAGMA table_info(`{name}`)")] for name in tables}
        before = {name: self.rows(name) for name in tables}
        self.assertEqual(5, len(MIGRATION_8_9_SQL))
        for sql in MIGRATION_8_9_SQL:
            self.db.execute(sql)
        for name in tables:
            select = ','.join(f'`{col}`' for col in columns[name])
            self.assertEqual(before[name], [tuple(r) for r in self.db.execute(f"SELECT {select} FROM `{name}` ORDER BY rowid")], name)
        self.assertTrue(all(tuple(r)==('MUSIC','PULSE','STANDARD',1000,0) for r in self.db.execute("SELECT outputMode,hapticPreset,hapticStrength,hapticGapMillis,hapticUseMusicRhythm FROM events")))
        target = json.loads((SCHEMAS / "9.json").read_text(encoding="utf-8"))["database"]
        for entity in target["entities"]:
            actual = {r['name']: (r['type'], bool(r['notnull']), r['dflt_value']) for r in self.db.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
            expected = {f['columnName']: (f['affinity'], f.get('notNull', False), f.get('defaultValue')) for f in entity['fields']}
            self.assertEqual(expected, actual, entity['tableName'])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def upgrade_to_ten(self):
        self.upgrade_to_eight()
        for sql in MIGRATION_8_9_SQL + MIGRATION_9_10_SQL:
            self.db.execute(sql)

    def test_version_nine_upgrade_preserves_fields_and_adds_opt_in_tasks(self):
        self.upgrade_to_eight()
        for sql in MIGRATION_8_9_SQL:
            self.db.execute(sql)
        tables = [r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        columns = {name: [r['name'] for r in self.db.execute(f"PRAGMA table_info(`{name}`)")] for name in tables}
        before = {name: self.rows(name) for name in tables}
        self.assertEqual(8, len(MIGRATION_9_10_SQL))
        for sql in MIGRATION_9_10_SQL:
            self.db.execute(sql)
        for name in tables:
            select = ','.join(f'`{col}`' for col in columns[name])
            self.assertEqual(before[name], [tuple(r) for r in self.db.execute(f"SELECT {select} FROM `{name}` ORDER BY rowid")], name)
        self.assertTrue(all(tuple(r)==(0,None) for r in self.db.execute("SELECT trackCompletion,completionTrackingStartedAt FROM events")))
        self.assertTrue(all(r[0]=='day:2026-10-19' for r in self.db.execute("SELECT taskOccurrenceKey FROM scheduled_occurrences")))
        target = json.loads((SCHEMAS / "10.json").read_text(encoding="utf-8"))["database"]
        for entity in target['entities']:
            name = entity['tableName']
            actual = {r['name']: (r['type'], bool(r['notnull']), r['dflt_value']) for r in self.db.execute(f"PRAGMA table_info(`{name}`)")}
            expected = {f['columnName']: (f['affinity'], f.get('notNull', False), f.get('defaultValue')) for f in entity['fields']}
            self.assertEqual(expected, actual, name)
            expected_indexes = {idx['name'] for idx in entity.get('indices', [])}
            actual_indexes = {r['name'] for r in self.db.execute(f"PRAGMA index_list(`{name}`)") if r['origin']!='pk'}
            self.assertEqual(expected_indexes, actual_indexes, name)
        self.assertEqual([], self.db.execute('PRAGMA foreign_key_check').fetchall())

    def test_version_ten_upgrade_preserves_data_and_has_no_day_adjustments_by_default(self):
        self.upgrade_to_ten()
        tables=[r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        columns={name:[r['name'] for r in self.db.execute(f"PRAGMA table_info(`{name}`)")] for name in tables}
        before={name:self.rows(name) for name in tables}
        self.assertEqual(3,len(MIGRATION_10_11_SQL))
        for sql in MIGRATION_10_11_SQL:
            self.db.execute(sql)
        for name in tables:
            select=','.join(f'`{c}`' for c in columns[name])
            self.assertEqual(before[name],[tuple(r) for r in self.db.execute(f"SELECT {select} FROM `{name}` ORDER BY rowid")],name)
        self.assertTrue(all(tuple(r)==('[]','{}') for r in self.db.execute('SELECT dayOverridesJson,dayOverrideBarriersJson FROM events')))
        self.assertTrue(all(r[0]=='' for r in self.db.execute('SELECT dayOverrideSignature FROM scheduled_occurrences')))
        target=json.loads((SCHEMAS/'11.json').read_text(encoding='utf-8'))['database']
        for entity in target['entities']:
            name=entity['tableName']
            actual={r['name']:(r['type'],bool(r['notnull']),r['dflt_value']) for r in self.db.execute(f"PRAGMA table_info(`{name}`)")}
            expected={f['columnName']:(f['affinity'],f.get('notNull',False),f.get('defaultValue')) for f in entity['fields']}
            self.assertEqual(expected,actual,name)
        self.assertEqual([],self.db.execute('PRAGMA foreign_key_check').fetchall())

    def test_task_latest_action_uses_id_and_history_survives_event_delete(self):
        self.upgrade_to_ten()
        key='day:2026-10-19'
        self.db.execute("INSERT INTO task_instances VALUES (1,?,'2026-10-19','09:00',1000,1000)", (key,))
        for action, time in [('DONE',2000),('REOPEN',1000)]:
            self.db.execute("INSERT INTO task_actions(eventId,taskKey,baseDate,occurrenceLabel,titleSnapshot,action,happenedAtEpochMillis) VALUES (1,?,'2026-10-19','09:00','会议',?,?)", (key,action,time))
        task_source=(SOURCE / 'data/local/dao/TaskDao.kt').read_text(encoding='utf-8')
        states=re.search(r'@Query\("([^"\n]+)"\)\s*suspend fun states',task_source).group(1)
        self.assertEqual(['REOPEN'],[r['action'] for r in self.db.execute(states)])
        self.db.execute('DELETE FROM events WHERE id=1')
        self.assertEqual([], self.rows('task_instances'))
        self.assertEqual(2, len(self.rows('task_actions')))
        self.assertEqual([],self.db.execute('PRAGMA foreign_key_list(task_actions)').fetchall())
        self.assertEqual([],self.db.execute('PRAGMA foreign_key_check').fetchall())

    def test_trash_queries_exclude_deleted_items_and_guard_enable_and_permanent_delete(self):
        self.upgrade_to_eight()
        source = (SOURCE / "data/local/dao/Daos.kt").read_text(encoding="utf-8")
        def query(method):
            return re.search(r'@Query\("([^"\n]+)"\)\s*(?:suspend\s+)?fun '+method+r'\(', source).group(1)
        history = self.rows('ring_history')
        self.db.execute("UPDATE events SET enabled=0, deletedAtEpochMillis=1000 WHERE id=1")
        self.assertEqual([2], [r['id'] for r in self.db.execute(query('getAllWithRelations'))])
        self.assertEqual(2, len(self.db.execute(query('getAllIncludingDeleted')).fetchall()))
        self.assertEqual(0, self.db.execute(query('setEnabled'), {'eventId':1,'enabled':1,'updatedAt':2000}).rowcount)
        self.assertEqual(0, self.db.execute(query('deleteEvent'), {'eventId':2}).rowcount)
        self.assertEqual(1, self.db.execute(query('deleteEvent'), {'eventId':1}).rowcount)
        self.assertEqual([2], [r['id'] for r in self.db.execute(query('getAllIncludingDeleted'))])
        self.assertEqual(history, self.rows('ring_history'))
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_direct_version_three_upgrade_matches_room_four(self):
        version_three = json.loads((SCHEMAS / "3.json").read_text(encoding="utf-8"))["database"]
        with sqlite3.connect(":memory:") as connection:
            connection.row_factory = sqlite3.Row
            for entity in version_three["entities"]:
                connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
                for index in entity.get("indices", []):
                    connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
            for sql in MIGRATION_3_4_SQL:
                connection.execute(sql)
            self.assert_version_four(connection)


if __name__ == "__main__":
    unittest.main(verbosity=2)
