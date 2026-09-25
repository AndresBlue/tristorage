package com.andresblue.tristorage.client;

import com.andresblue.tristorage.storage.TerminalFilter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class TerminalClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("TriStorage/ClientConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get()
            .resolve("tristorage-client.json");
    private static final int[] THRESHOLDS = {0, 54, 108, 216, 432};
    private static final TerminalClientConfig INSTANCE = load();

    private boolean searchEnabled = true;
    private boolean categoriesEnabled = true;
    private TerminalFilter.CategoryMode categoryMode = TerminalFilter.CategoryMode.TYPE;
    private boolean wheelPagingEnabled = true;
    private int categoryThreshold = 108;

    static TerminalClientConfig get() {
        return INSTANCE;
    }

    public static void initialize() {
        get();
    }

    boolean searchEnabled() {
        return searchEnabled;
    }

    boolean categoriesEnabled() {
        return categoriesEnabled;
    }

    TerminalFilter.CategoryMode categoryMode() {
        return categoryMode;
    }

    boolean wheelPagingEnabled() {
        return wheelPagingEnabled;
    }

    int categoryThreshold() {
        return categoryThreshold;
    }

    void toggleSearch() {
        searchEnabled = !searchEnabled;
        save();
    }

    void toggleCategories() {
        categoriesEnabled = !categoriesEnabled;
        save();
    }

    void cycleCategoryMode() {
        categoryMode = categoryMode == TerminalFilter.CategoryMode.TYPE
                ? TerminalFilter.CategoryMode.MOD
                : TerminalFilter.CategoryMode.TYPE;
        save();
    }

    void toggleWheelPaging() {
        wheelPagingEnabled = !wheelPagingEnabled;
        save();
    }

    void cycleThreshold() {
        for (int index = 0; index < THRESHOLDS.length; index++) {
            if (THRESHOLDS[index] == categoryThreshold) {
                categoryThreshold = THRESHOLDS[(index + 1) % THRESHOLDS.length];
                save();
                return;
            }
        }
        categoryThreshold = 108;
        save();
    }

    private static TerminalClientConfig load() {
        if (!Files.exists(PATH)) {
            TerminalClientConfig defaults = new TerminalClientConfig();
            defaults.save();
            return defaults;
        }
        try {
            TerminalClientConfig loaded = GSON.fromJson(
                    Files.readString(PATH, StandardCharsets.UTF_8), TerminalClientConfig.class);
            if (loaded == null) {
                return new TerminalClientConfig();
            }
            loaded.validate();
            return loaded;
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("Could not read {}; using safe defaults", PATH, exception);
            return new TerminalClientConfig();
        }
    }

    private void validate() {
        if (categoryMode != TerminalFilter.CategoryMode.TYPE
                && categoryMode != TerminalFilter.CategoryMode.MOD) {
            categoryMode = TerminalFilter.CategoryMode.TYPE;
        }
        boolean thresholdValid = false;
        for (int threshold : THRESHOLDS) {
            thresholdValid |= threshold == categoryThreshold;
        }
        if (!thresholdValid) {
            categoryThreshold = 108;
        }
    }

    private void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not save {}", PATH, exception);
        }
    }
}
