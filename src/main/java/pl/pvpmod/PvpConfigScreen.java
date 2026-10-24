package pl.pvpmod;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Menu NOVA: górny pasek z zakładkami, kafelki modułów, na dole wybór koloru GUI.
 * Wszystko rysowane ręcznie (DrawContext), więc łatwo zmienić wygląd.
 *
 * Zakładki: Moduły | Gracze | Ustawienia | Mody | Profil
 * Jak dodać nową opcję: dopisz wiersz w pageSettings(), a nowy moduł dodaj do MODULES i PvpConfig.
 */
public class PvpConfigScreen extends Screen {
    private static final int W = 440, H = 205;
    private static final String[] TABS = {"Moduły", "Gracze", "Ustawienia", "Mody", "Profil"};
    private static final String[] MODULES = {"Armor HUD", "Potion HUD", "Celownik", "FPS i XYZ", "Cień tekstu", "Bez trzęsienia"};
    // Kolory GUI do wyboru (kółka na dole).
    private static final int[] ACCENTS = {0x2DD4BF, 0x3B82F6, 0xA78BFA, 0xF472B6, 0xFBBF24, 0xF87171};

    /** Paleta kolorów motywu. */
    private record Theme(int panel, int border, int pill, int card, int cardBorder,
                         int text, int sub, int dim, int onAccent, int footer) {}
    private static final Theme[] THEMES = {
            // Ciemny (NOVA)
            new Theme(0xFF101318, 0xFF232A35, 0xFF0A0C0F, 0xFF161A21, 0xFF22262E,
                    0xFFE5E7EB, 0xFF8B94A1, 0xFF4B5563, 0xFF0B1012, 0xFF080A0C),
            // Jasny
            new Theme(0xFFF3F4F6, 0xFFD1D5DB, 0xFFE5E7EB, 0xFFFFFFFF, 0xFFD1D5DB,
                    0xFF111827, 0xFF6B7280, 0xFF9CA3AF, 0xFF0B1012, 0xFFE5E7EB)
    };

    /** Obszar klikalny zapamiętany podczas rysowania klatki. */
    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {}

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final List<String[]> installedMods = new ArrayList<>();
    private int tab = 0;
    private int scroll = 0;
    private int contentHeight = 0;
    private int listening = -1;   // indeks modułu, któremu ustawiamy bind (-1 = nic)

    private int cx, cx2, vy1, vy2; // obszar zawartości
    private TextRenderer font;
    private Theme t;

    public PvpConfigScreen(Screen parent) {
        super(Text.literal("NovaPvP"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Lista zainstalowanych modów do zakładki "Mody".
        installedMods.clear();
        for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
            if (m.getContainingMod().isPresent() || m.getMetadata().getId().equals("java")) continue;
            installedMods.add(new String[]{m.getMetadata().getName(), m.getMetadata().getVersion().getFriendlyString()});
        }
        installedMods.sort(Comparator.comparing(a -> a[0].toLowerCase()));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------
    // RYSOWANIE
    // ------------------------------------------------------------------
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        font = this.textRenderer;
        t = THEMES[Math.floorMod(PvpConfig.THEME, THEMES.length)];
        hits.clear();

        c.fill(0, 0, width, height, 0x88000000); // przyciemnione tło

        int px = (width - W) / 2, py = (height - H) / 2;
        rrBorder(c, px, py, px + W, py + H, 8, t.border(), t.panel());

        // --- nagłówek: logo + nazwa ---
        rr(c, px + 12, py + 10, px + 28, py + 26, 4, accent());
        txt(c, "N", px + 12 + (16 - font.getWidth("N")) / 2, py + 14, t.onAccent());
        txt(c, "NOVA", px + 34, py + 14, t.text());

        // --- zakładki (pigułka na środku) ---
        int pillW = 6;
        int[] tw = new int[TABS.length];
        for (int i = 0; i < TABS.length; i++) { tw[i] = font.getWidth(TABS[i]) + 16; pillW += tw[i] + (i < TABS.length - 1 ? 2 : 0); }
        int pillX = px + (W - pillW) / 2;
        rr(c, pillX, py + 8, pillX + pillW, py + 30, 11, t.pill());
        int tx = pillX + 3;
        for (int i = 0; i < TABS.length; i++) {
            boolean sel = i == tab;
            boolean hov = mx >= tx && mx < tx + tw[i] && my >= py + 11 && my < py + 27;
            if (sel) rr(c, tx, py + 11, tx + tw[i], py + 27, 8, accent());
            txt(c, TABS[i], tx + 8, py + 15, sel ? t.onAccent() : (hov ? t.text() : t.sub()));
            final int idx = i;
            addHit(tx, py + 11, tx + tw[i], py + 27, () -> { tab = idx; scroll = 0; listening = -1; }, false);
            tx += tw[i] + 2;
        }

        // --- "[ X ] zamknij" ---
        String closeTxt = "[" + keyName(PvpModClient.openMenuKey == null ? -1 : keyOf(PvpModClient.openMenuKey)) + "] zamknij";
        int closeX = px + W - 12 - font.getWidth(closeTxt);
        boolean closeHov = mx >= closeX && mx < px + W - 12 && my >= py + 12 && my < py + 26;
        txt(c, closeTxt, closeX, py + 15, closeHov ? t.text() : t.dim());
        addHit(closeX, py + 12, px + W - 12, py + 26, this::close, false);

        // --- zawartość ---
        cx = px + 12;
        cx2 = px + W - 12;
        vy1 = py + 40;
        int footerY1 = py + H - 42;
        vy2 = footerY1 - 6;

        int maxScroll = Math.max(0, contentHeight - (vy2 - vy1));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        c.enableScissor(cx - 1, vy1 - 1, cx2 + 1, vy2 + 1);
        int endY = switch (tab) {
            case 0 -> pageModules(c, mx, my);
            case 1 -> pageUsers(c, mx, my);
            case 2 -> pageSettings(c, mx, my);
            case 3 -> pageInstalled(c, mx, my);
            default -> pageProfile(c, mx, my);
        };
        c.disableScissor();
        contentHeight = endY + scroll - vy1;

        // --- stopka: Kolor GUI ---
        rr(c, cx, footerY1, cx2, py + H - 10, 6, t.footer());
        int fmid = footerY1 + 16;
        txt(c, "Kolor GUI", cx + 10, fmid - 4, t.sub());
        int startX = (cx + cx2) / 2 - (ACCENTS.length * 20 - 10) / 2;
        for (int i = 0; i < ACCENTS.length; i++) {
            int ccx = startX + i * 20 + 5;
            if (ACCENTS[i] == PvpConfig.GUI_COLOR) circle(c, ccx, fmid, 6, 0xFFFFFFFF); // biała obwódka wybranego
            circle(c, ccx, fmid, 5, 0xFF000000 | ACCENTS[i]);
            final int col = ACCENTS[i];
            addHit(ccx - 7, fmid - 7, ccx + 8, fmid + 8, () -> PvpConfig.GUI_COLOR = col, false);
        }
        String hex = String.format("#%06X", PvpConfig.GUI_COLOR);
        int hw = font.getWidth(hex) + 16;
        rrBorder(c, cx2 - 10 - hw, fmid - 9, cx2 - 10, fmid + 9, 4, t.cardBorder(), t.card());
        txt(c, hex, cx2 - 10 - hw + 8, fmid - 4, t.text());
    }

    // ------------------------------------------------------------------
    // STRONY
    // ------------------------------------------------------------------
    /** Kafelki modułów (3 kolumny x 2 wiersze). */
    private int pageModules(DrawContext c, int mx, int my) {
        int gap = 6, cols = 3, ch = 52;
        int cw = (cx2 - cx - gap * (cols - 1)) / cols;
        int top = vy1 - scroll;
        for (int i = 0; i < MODULES.length; i++) {
            final int idx = i;
            int x = cx + (i % cols) * (cw + gap);
            int y = top + (i / cols) * (ch + gap);
            boolean on = PvpConfig.isModule(i);
            boolean hov = mx >= x && mx < x + cw && my >= Math.max(y, vy1) && my < Math.min(y + ch, vy2);
            int border = on ? accent() : (hov ? t.dim() : t.cardBorder());
            rrBorder(c, x, y, x + cw, y + ch, 6, border, t.card());

            // ikonka (puste pudełko) + stan
            outline(c, x + 9, y + 9, x + 17, y + 20, on ? accent() : t.dim());
            Text state = Text.literal(on ? "WŁ." : "WYŁ.").formatted(Formatting.BOLD);
            c.drawText(font, state, x + cw - 9 - font.getWidth(state), y + 10, on ? accent() : t.dim(), PvpConfig.TEXT_SHADOW);

            // nazwa + bind
            txt(c, MODULES[i], x + 9, y + ch - 25, t.text());
            boolean listen = listening == i;
            String bind = "Bind: [ " + (listen ? "..." : keyName(PvpConfig.BINDS[i])) + " ]";
            txt(c, bind, x + 9, y + ch - 13, listen ? accent() : t.sub());

            addHit(x, y, x + cw, y + ch, () -> PvpConfig.setModule(idx, !PvpConfig.isModule(idx)), true);
            // bind ma pierwszeństwo przed kafelkiem (dodany później)
            addHit(x + 7, y + ch - 15, x + 11 + font.getWidth(bind), y + ch - 3,
                    () -> listening = (listening == idx ? -1 : idx), true);
        }
        int rows = (MODULES.length + cols - 1) / cols;
        return top + rows * (ch + gap);
    }

    private int pageUsers(DrawContext c, int mx, int my) {
        int y = vy1 - scroll;
        List<AbstractClientPlayerEntity> players = client.world == null ? List.of() : client.world.getPlayers();
        if (players.isEmpty()) {
            txt(c, "Brak graczy w pobliżu.", cx + 2, y + 4, t.sub());
            return y + 16;
        }
        for (AbstractClientPlayerEntity p : players) {
            row(c, y, 22, mx, my);
            PlayerSkinDrawer.draw(c, p.getSkinTextures(), cx + 6, y + 3, 16); // głowa gracza
            txt(c, p.getName().getString(), cx + 30, y + 7, t.text());
            String d = (client.player == p) ? "Ty" : (int) p.distanceTo(client.player) + " m";
            txt(c, d, cx2 - 8 - font.getWidth(d), y + 7, t.sub());
            y += 25;
        }
        return y;
    }

    private int pageSettings(DrawContext c, int mx, int my) {
        int y = vy1 - scroll;
        y = toggleRow(c, mx, my, y, "Zaokrąglone ramki", PvpConfig.ROUNDED, () -> PvpConfig.ROUNDED = !PvpConfig.ROUNDED);
        y = toggleRow(c, mx, my, y, "Dźwięki menu", PvpConfig.MENU_SOUNDS, () -> PvpConfig.MENU_SOUNDS = !PvpConfig.MENU_SOUNDS);

        // Motyw: dwa przyciski od prawej.
        row(c, y, 22, mx, my);
        txt(c, "Motyw", cx + 8, y + 7, t.text());
        String[] names = {"Ciemny", "Jasny"};
        int bx = cx2 - 6;
        for (int i = names.length - 1; i >= 0; i--) {
            int bw = font.getWidth(names[i]) + 14;
            bx -= bw;
            boolean sel = PvpConfig.THEME == i;
            rr(c, bx, y + 4, bx + bw, y + 18, 5, sel ? accent() : t.pill());
            txt(c, names[i], bx + 7, y + 7, sel ? t.onAccent() : t.text());
            final int idx = i;
            addHit(bx, y + 4, bx + bw, y + 18, () -> PvpConfig.THEME = idx, true);
            bx -= 4;
        }
        y += 25;

        txt(c, "Moduły i bindy ustawiasz w zakładce Moduły.", cx + 2, y + 2, t.sub());
        return y + 14;
    }

    private int pageInstalled(DrawContext c, int mx, int my) {
        int y = vy1 - scroll;
        for (String[] m : installedMods) {
            row(c, y, 20, mx, my);
            txt(c, m[0], cx + 8, y + 6, t.text());
            txt(c, m[1], cx2 - 8 - font.getWidth(m[1]), y + 6, t.sub());
            y += 23;
        }
        return y;
    }

    private int pageProfile(DrawContext c, int mx, int my) {
        int y = vy1 - scroll;
        if (client.player != null) {
            PlayerSkinDrawer.draw(c, client.player.getSkinTextures(), cx + 6, y + 4, 32);
        }
        txt(c, client.getSession().getUsername(), cx + 46, y + 9, t.text());
        txt(c, "Minecraft 1.21.4 (Fabric)", cx + 46, y + 23, t.sub());
        y += 46;
        int active = 0;
        for (int i = 0; i < MODULES.length; i++) if (PvpConfig.isModule(i)) active++;
        row(c, y, 22, mx, my);
        txt(c, "Aktywne moduły", cx + 8, y + 7, t.text());
        String s = active + "/" + MODULES.length;
        txt(c, s, cx2 - 8 - font.getWidth(s), y + 7, t.sub());
        return y + 25;
    }

    // ------------------------------------------------------------------
    // ELEMENTY
    // ------------------------------------------------------------------
    private int toggleRow(DrawContext c, int mx, int my, int y, String label, boolean on, Runnable click) {
        row(c, y, 22, mx, my);
        txt(c, label, cx + 8, y + 7, t.text());
        int sx = cx2 - 8 - 26;
        rr(c, sx, y + 5, sx + 26, y + 17, 6, on ? accent() : t.dim());
        int kx = on ? sx + 15 : sx + 2;
        rr(c, kx, y + 7, kx + 9, y + 15, 4, 0xFFFFFFFF);
        addHit(cx, y, cx2, y + 22, click, true);
        return y + 25;
    }

    /** Wiersz w stylu kafelka. */
    private void row(DrawContext c, int y, int h, int mx, int my) {
        boolean hov = mx >= cx && mx < cx2 && my >= Math.max(y, vy1) && my < Math.min(y + h, vy2);
        rrBorder(c, cx, y, cx2, y + h, 5, hov ? t.dim() : t.cardBorder(), t.card());
    }

    private void txt(DrawContext c, String s, int x, int y, int color) {
        c.drawText(font, s, x, y, color, PvpConfig.TEXT_SHADOW);
    }

    private static String keyName(int key) {
        if (key < 0) return "—";
        return InputUtil.fromKeyCode(key, -1).getLocalizedText().getString();
    }

    /** Kod klawisza przypisanego do KeyBindingu (do napisu "[ X ] zamknij"). */
    private static int keyOf(net.minecraft.client.option.KeyBinding kb) {
        return InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey()).getCode();
    }

    // ---- prymitywy rysowania ----
    /** Prostokąt z zaokrąglonymi rogami (promień r); gdy ramki wyłączone - zwykły prostokąt. */
    private static void rr(DrawContext c, int x1, int y1, int x2, int y2, int r, int color) {
        if (!PvpConfig.ROUNDED || r <= 0 || (y2 - y1) < 2 * r || (x2 - x1) < 2 * r) {
            c.fill(x1, y1, x2, y2, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
        c.fill(x1, y1 + r, x2, y2 - r, color);
    }

    private static void rrBorder(DrawContext c, int x1, int y1, int x2, int y2, int r, int border, int fill) {
        rr(c, x1, y1, x2, y2, r, border);
        rr(c, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(r - 1, 0), fill);
    }

    private static void outline(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        c.fill(x1, y1, x2, y1 + 1, color);
        c.fill(x1, y2 - 1, x2, y2, color);
        c.fill(x1, y1, x1 + 1, y2, color);
        c.fill(x2 - 1, y1, x2, y2, color);
    }

    private static void circle(DrawContext c, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(r * r + r - dy * dy));
            c.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    private static int accent() {
        return 0xFF000000 | PvpConfig.GUI_COLOR;
    }

    private void addHit(int x1, int y1, int x2, int y2, Runnable action, boolean scrollable) {
        if (scrollable) { // obetnij do widocznego okna
            y1 = Math.max(y1, vy1);
            y2 = Math.min(y2, vy2);
            if (y1 >= y2) return;
        }
        hits.add(new Hit(x1, y1, x2, y2, action));
    }

    private void playClick() {
        if (PvpConfig.MENU_SOUNDS) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    // ------------------------------------------------------------------
    // WEJŚCIE
    // ------------------------------------------------------------------
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // od końca - elementy dodane później (np. bind na kafelku) mają pierwszeństwo
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (mouseX >= h.x1() && mouseX < h.x2() && mouseY >= h.y1() && mouseY < h.y2()) {
                    h.action().run();
                    playClick();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentHeight - (vy2 - vy1));
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - verticalAmount * 12));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ustawianie bindu: ESC czyści, inny klawisz ustawia.
        if (listening >= 0) {
            PvpConfig.BINDS[listening] = (keyCode == GLFW.GLFW_KEY_ESCAPE) ? -1 : keyCode;
            listening = -1;
            PvpConfig.save();
            return true;
        }
        // Klawisz menu (X) zamyka menu.
        if (PvpModClient.openMenuKey != null && PvpModClient.openMenuKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        PvpConfig.save();
        this.client.setScreen(parent);
    }
}
