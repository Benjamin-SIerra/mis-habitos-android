from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "app/src/main/assets/HabitosApp.html"
ACTIVITY = ROOT / "app/src/main/java/com/ritual/app/MainActivity.kt"


class LocalPersistenceAndCommuteTests(unittest.TestCase):
    def test_android_bridge_reads_and_writes_local_database(self):
        activity = ACTIVITY.read_text(encoding="utf-8")
        self.assertIn("class LocalStateDatabase", activity)
        self.assertIn("SQLiteOpenHelper", activity)
        self.assertIn("fun loadState()", activity)
        self.assertIn("fun saveState(stateJson: String)", activity)

    def test_web_app_migrates_existing_local_state_to_native_database(self):
        source = HTML.read_text(encoding="utf-8")
        self.assertIn("loadState?.()", source)
        self.assertIn("saveState?.(migrated)", source)
        self.assertIn("localStorage.getItem(STORAGE_KEY)", source)

    def test_commute_duration_counts_both_outbound_and_return_trips(self):
        source = HTML.read_text(encoding="utf-8")
        self.assertIn("commuteMinutes", source)
        self.assertIn("Ida y regreso", source)
        self.assertIn("2 * commuteMinutes", source)
        self.assertIn("Trabajo / estudio", source)

    def test_deleting_a_habit_does_not_open_browser_file_confirm(self):
        source = HTML.read_text(encoding="utf-8")
        self.assertNotIn("confirm(", source)
        self.assertIn('id="confirmDialog"', source)
        self.assertIn("Quitar hábito", source)


if __name__ == "__main__":
    unittest.main()
