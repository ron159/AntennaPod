# :storage:preferences

User settings, but not the actual subscription database.
Settings are accessed through static methods on classes like `UserPreferences`, which wrap Android SharedPreferences.
Audio interruption handling uses `UserPreferences.getAudioInterruptionBehavior()` to distinguish pause, duck, and
experimental keep-playing behavior for focus changes that allow ducking.
