# Download to Thor

The native Android app can bring game files from your own library to an SD card or internal storage. This is a separate stage from requesting a game, downloading it on a server, and importing it into RomM. A server import does not mean the game is on the handheld.

## Connect and choose storage

1. Run the optional [library gateway](../companion/README.md) against the library directory. Put it behind authenticated HTTPS with a certificate trusted by Android.
2. In **My Thor → Download to Thor**, open **Connection** and save the gateway URL and bearer access key. This is a separate credential from the ROMarr connection; Android Keystore encrypts it at rest.
3. Optionally configure **Local route** with an HTTPS address reachable on your home network. It must serve the same library and content hashes. Its credential is stored separately; credentials are never forwarded between origins or redirects.
4. Select **SD card** or **Internal**, then choose that storage's **ROMs** folder using Android's folder picker. Grant read and write access. Previously granted writable folders are reused.
5. Refresh the library and choose games. Each selection joins a saved transfer queue. Choose **Download at once** to allow **1**, **2** (default), or **3** active transfers. Each game’s platform determines the destination folder, using the existing ES-DE directory mappings (`n3ds`, `nds`, `psp`, `switch`, and others).

The transfer tries the local route first, then the primary server if the initial local connection fails. A mid-transfer interruption preserves the partial file; use that game’s **Resume** to retry. The library catalog currently uses the primary connection. Local and primary endpoints must expose identical file identities and ETags.

## What completion means

Downloads write to a hidden, job-owned partial file. The app validates Range responses before appending resumed bytes, checks the expected size, then reads the file back and verifies SHA-256. Only then does it rename the file into the platform folder. An existing final file is read and verified, never overwritten. If it differs, the app reports a conflict.

Each transfer card shows its own progress, actual storage destination, and status. Queued games start as slots become available. **Pause** affects only that game and preserves its partial file. **Resume** returns it to the queue. **Cancel** is available for queued or stopped jobs and deletes only that job's recorded partial; pause an active job before cancelling it. Re-selecting an unfinished game for the same destination does not create another transfer. Completed transfers collapse into a summary; expand it to see the five most recent completions. **Clear record** removes a completed queue entry without deleting the game.

A foreground service keeps transfers active while navigating away; Android may still stop it. After process termination, reopen the app and resume saved jobs manually, including jobs that had been queued. Reducing the parallel limit lets existing workers finish and limits subsequent starts; it does not interrupt them. Multiple transfers share the connection and storage bandwidth, so raising the limit may not increase total speed.

The app does not modify saves or emulator state. Archives and unsupported multi-file game layouts are excluded. The first implementation handles self-contained game files; cue/bin sets, playlists, and bundles need a future protocol. A copied CIA still needs installation through Azahar. Configure Cocoon and each emulator to use the destination folders; downloading does not automatically launch or install a game.

## Test on an Android device

Run JVM protocol checks and build/lint with the regular Android workflow. The gateway has independent HTTP tests:

```sh
python3 -m unittest discover -s companion -p 'test_*.py'
```

A separate `DownloadChecks` instrumentation runner supports explicit physical-device checks. Build it with:

```sh
cd android
./gradlew assembleDebugAndroidTest -PthorpilotTestRunner=dev.thorpilot.DownloadChecks
```

The default mode only reports saved transfer status. `ui` walks the native screen. `configure` consumes and deletes `files/download-test-config.json` in the target app's private storage, with `primary` and optional `local` objects containing `url` and `token`. Never commit this fixture or put keys in shell arguments or logs. `catalog` checks manifest parsing. `startInternal` requires an exact `title`; `pauseAfterBytes` can pause a transfer for a later `resume` test. `verifyExistingSd` requires the expected file to exist before starting and exercises the no-overwrite checksum path. Use test libraries or explicitly chosen files; preserve user saves and existing games.

For concurrency testing, `queue` uses three synthetic files to assert two simultaneous transfers, a waiting third item, duplicate rejection, individual pause/resume, automatic queue advancement, and independent readback hashes. `queueRecoveryStage` starts two more fixture transfers; force-stop the app, then run `queueRecoveryFinish` to assert that saved partials recover as paused before resuming. These modes require a private `files/download-queue-test.json` containing `primary` and `local` connections, `treeUri`, `entryIds` (three), and `recoveryEntryIds` (two). They accept only `thorpilot-fixture-` files in the explicitly granted internal `Documents/ThorpilotSyncTest/Target/ROMs` test tree. The harness restores the normal connections and concurrency setting; the recovery stage keeps a private backup until the finish step. `queueCleanup` clears only completed fixture queue records, leaving files for explicit test cleanup.
