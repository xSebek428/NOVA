package pl.pvpmod;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * USTAWIENIA MODA.
 * - Moduły, wygląd menu i bindy zmieniasz w grze (klawisz X -> menu NOVA).
 *   Zapisują się w config/novapvp.properties.
 * - Pozycje HUD i kolory celownika edytujesz tutaj w kodzie (kolory 0xAARRGGBB).
 */
public final class PvpConfig {
    private PvpConfig() {}

    // ---- Moduły (indeksy 0..5 jak w menu) ----
    public static boolean ARMOR_HUD = true;         // 0
    public static boolean POTION_HUD = true;        // 1
    public static boolean CUSTOM_CROSSHAIR = true;  // 2
    public static boolean FPS_XYZ = true;           // 3
    public static boolean TEXT_SHADOW = true;       // 4  cień tekstu w menu i HUD
    public static boolean NO_HURT_CAM = true;       // 5  bez trzęsienia ekranu

    // ---- Wygląd menu ----
    public static int GUI_COLOR = 0x2DD4BF;   // kolor akcentu (RRGGBB)
    public static boolean ROUNDED = true;     // zaokrąglone ramki
    public static boolean MENU_SOUNDS = true; // dźwięki kliknięć w menu
    public static int THEME = 0;              // 0 = Ciemny, 1 = Jasny

    // ---- Domyślne bindy (kody GLFW): H, P, C, F, K, J ----
    public static final int[] DEFAULT_BINDS = {72, 80, 67, 70, 75, 74};
    public static final int[] BINDS = DEFAULT_BINDS.clone(); // -1 = brak

    // ---- Armor HUD ----
    public static final int ARMOR_HUD_X = 4;
    public static final int ARMOR_HUD_Y_OFFSET = -50;   // ujemne = wyżej, dodatnie = niżej od środka
    public static final boolean ARMOR_SHOW_EMPTY_SLOTS = false;

    // ---- FPS i XYZ (lewy górny róg) ----
    public static final int INFO_HUD_X = 4;
    public static final int INFO_HUD_Y = 4;

    // ---- Potion HUD (jeśli FPS i XYZ jest włączone, lista schodzi niżej o 24 px) ----
    public static final int POTION_HUD_X = 4;
    public static final int POTION_HUD_Y = 4;
    public static final int POTION_LOW_TIME_SECONDS = 10;
    public static final int POTION_LOW_TIME_COLOR = 0xFFFF5555;
    public static final int POTION_TIME_COLOR = 0xFFFFFFFF;

    // ---- Celownik (plusik) ----
    public static final int CROSSHAIR_COLOR = 0xFFFFFFFF;
    public static final int CROSSHAIR_ARM = 4;
    public static final int CROSSHAIR_THICKNESS = 1;
    public static final boolean CROSSHAIR_OUTLINE = true;
    public static final int CROSSHAIR_OUTLINE_COLOR = 0xFF000000;

    // ---- Dostęp do modułów po indeksie (0..5) ----
    public static boolean isModule(int i) {
        return switch (i) {
            case 0 -> ARMOR_HUD;
            case 1 -> POTION_HUD;
            case 2 -> CUSTOM_CROSSHAIR;
            case 3 -> FPS_XYZ;
            case 4 -> TEXT_SHADOW;
            default -> NO_HURT_CAM;
        };
    }

    public static void setModule(int i, boolean v) {
        switch (i) {
            case 0 -> ARMOR_HUD = v;
            case 1 -> POTION_HUD = v;
            case 2 -> CUSTOM_CROSSHAIR = v;
            case 3 -> FPS_XYZ = v;
            case 4 -> TEXT_SHADOW = v;
            default -> NO_HURT_CAM = v;
        }
    }

    // ---- Zapis / odczyt ----
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("novapvp.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            Properties p = new Properties();
            p.load(r);
            ARMOR_HUD = Boolean.parseBoolean(p.getProperty("armorHud", "true"));
            POTION_HUD = Boolean.parseBoolean(p.getProperty("potionHud", "true"));
            CUSTOM_CROSSHAIR = Boolean.parseBoolean(p.getProperty("customCrosshair", "true"));
            FPS_XYZ = Boolean.parseBoolean(p.getProperty("fpsXyz", "true"));
            TEXT_SHADOW = Boolean.parseBoolean(p.getProperty("textShadow", "true"));
            NO_HURT_CAM = Boolean.parseBoolean(p.getProperty("noHurtCam", "true"));
            ROUNDED = Boolean.parseBoolean(p.getProperty("rounded", "true"));
            MENU_SOUNDS = Boolean.parseBoolean(p.getProperty("menuSounds", "true"));
            GUI_COLOR = Integer.parseInt(p.getProperty("guiColor", "2DD4BF"), 16);
            THEME = Integer.parseInt(p.getProperty("theme", "0"));
            for (int i = 0; i < BINDS.length; i++) {
                BINDS[i] = Integer.parseInt(p.getProperty("mbind" + i, String.valueOf(DEFAULT_BINDS[i])));
            }
        } catch (IOException | NumberFormatException e) {
            PvpMod.LOGGER.warn("Nie udało się wczytać configu", e);
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("armorHud", String.valueOf(ARMOR_HUD));
        p.setProperty("potionHud", String.valueOf(POTION_HUD));
        p.setProperty("customCrosshair", String.valueOf(CUSTOM_CROSSHAIR));
        p.setProperty("fpsXyz", String.valueOf(FPS_XYZ));
        p.setProperty("textShadow", String.valueOf(TEXT_SHADOW));
        p.setProperty("noHurtCam", String.valueOf(NO_HURT_CAM));
        p.setProperty("rounded", String.valueOf(ROUNDED));
        p.setProperty("menuSounds", String.valueOf(MENU_SOUNDS));
        p.setProperty("guiColor", String.format("%06X", GUI_COLOR));
        p.setProperty("theme", String.valueOf(THEME));
        for (int i = 0; i < BINDS.length; i++) p.setProperty("mbind" + i, String.valueOf(BINDS[i]));
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "NovaPvP");
        } catch (IOException e) {
            PvpMod.LOGGER.warn("Nie udało się zapisać configu", e);
        }
    }
}
