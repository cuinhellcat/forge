package forge.gamemodes.match;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.tinylog.Logger;

import forge.localinstance.properties.ForgeConstants;

/**
 * Writes every rewind point to disk as well as into memory.
 *
 * Forge can end up with no window while the game itself is still running — a panel that
 * throws while a screen is being switched aborts the layout load, and if the frame is then
 * closed the position is only reachable by attaching to the live process. These files are
 * the cheap way out of that: the last few turns are always on disk, loadable through
 * <i>Forge &rarr; Load saved game</i>.
 *
 * The folder keeps a rolling window of the newest {@link #KEEP} files and deletes the rest,
 * so it cannot grow without bound while still leaving a short history to pick from.
 */
public final class RewindAutosave {

    /** How many autosaves to keep. Roughly the last hour of a game. */
    private static final int KEEP = 12;

    private static final String DIR = ForgeConstants.USER_GAMES_DIR + "autosave" + ForgeConstants.PATH_SEPARATOR;
    private static final String PREFIX = "autosave_";
    private static final String SUFFIX = ".txt";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private RewindAutosave() { }

    /** Where the files land, so callers can tell the user. */
    public static String getFolder() {
        return DIR;
    }

    public static void write(final int turn, final List<String> stateText) {
        final Path dir = Paths.get(DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Logger.warn(e, "Could not create the autosave folder {}", DIR);
            return;
        }

        final Path file = dir.resolve(String.format("%s%s_turn%d%s",
                PREFIX, LocalDateTime.now().format(STAMP), turn, SUFFIX));
        try {
            Files.write(file, String.join("\n", stateText).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Logger.warn(e, "Could not write the autosave {}", file);
            return;
        }

        prune(dir);
    }

    /** Drops everything past the newest KEEP files. Failure here is not worth reporting twice. */
    private static void prune(final Path dir) {
        final File[] saves = dir.toFile().listFiles(
                (d, name) -> name.startsWith(PREFIX) && name.endsWith(SUFFIX));
        if (saves == null || saves.length <= KEEP) {
            return;
        }
        Arrays.sort(saves, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = KEEP; i < saves.length; i++) {
            if (!saves[i].delete()) {
                Logger.warn("Could not remove the old autosave {}", saves[i]);
            }
        }
    }
}
