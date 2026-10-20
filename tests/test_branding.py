from pathlib import Path
import html
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP_HTML = ROOT / "app/src/main/assets/HabitosApp.html"
APP_LABEL = ROOT / "app/src/main/res/values/strings.xml"
APP_CONFIG = ROOT / "app/build.gradle.kts"
MAIN_ACTIVITY = ROOT / "app/src/main/java/com/ritual/app/MainActivity.kt"


def visible_text():
    source = APP_HTML.read_text(encoding="utf-8")
    source = re.sub(
        r"<!--.*?-->|<script.*?</script>|<style.*?</style>",
        "",
        source,
        flags=re.I | re.S,
    )
    return html.unescape(re.sub(r"<[^>]+>", " ", source))


class BrandingTests(unittest.TestCase):
    def test_visible_app_name_is_mis_habitos(self):
        self.assertIn("Mis Hábitos", visible_text())

    def test_default_avatar_matches_new_brand(self):
        source = APP_HTML.read_text(encoding="utf-8")
        self.assertIn('aria-label="Perfil">M</button>', source)

    def test_launcher_label_is_mis_habitos(self):
        self.assertIn('<string name="app_name">Mis Hábitos</string>', APP_LABEL.read_text(encoding="utf-8"))

    def test_visible_copy_has_no_prototype_or_editorial_notes(self):
        visible = visible_text().lower()
        unwanted = ("ritual", "prototipo", "texto original", "video compartido", "página permanezca abierta")
        found = [phrase for phrase in unwanted if phrase in visible]
        self.assertEqual([], found, f"Textos visibles que deben retirarse: {found}")

    def test_dynamic_reminder_copy_has_no_prototype_disclaimer(self):
        source = APP_HTML.read_text(encoding="utf-8").lower()
        unwanted = ("prototipo web", "deja la página abierta", "alarmas exactas de ritual")
        found = [phrase for phrase in unwanted if phrase in source]
        self.assertEqual([], found, f"Textos de recordatorios pendientes: {found}")

    def test_old_local_storage_keys_are_kept_for_migration(self):
        source = APP_HTML.read_text(encoding="utf-8")
        for old_key in ("ritual-habitos-v3", "ritual-habitos-v2", "ritual-habitos-v1"):
            with self.subTest(old_key=old_key):
                self.assertIn(old_key, source)

    def test_android_package_id_is_preserved_for_updates(self):
        config = APP_CONFIG.read_text(encoding="utf-8")
        self.assertIn('applicationId = "com.ritual.app"', config)

    def test_native_notification_uses_new_brand(self):
        source = MAIN_ACTIVITY.read_text(encoding="utf-8")
        self.assertIn('.setContentTitle(if (isMotivation) "Una idea para acompañarte" else "Mis Hábitos")', source)
        self.assertIn('NotificationChannel(channelId, "Recordatorios de hábitos"', source)


if __name__ == "__main__":
    unittest.main()
