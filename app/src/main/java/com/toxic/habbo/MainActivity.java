package com.toxic.habbo;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.*;
import android.net.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String API = "https://atoxic.com.br/api.php";
    private static final String ROOM_API = "https://atoxic.com.br/busca?ajax_room_name=1&roomId=";
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    private FrameLayout screen;
    private LinearLayout root, resultWrap;
    private EditText searchInput;
    private Button searchBtn;
    private TextView statusText;
    private ProgressBar progress;

    private final int bg = Color.rgb(13, 13, 18);
    private final int bg2 = Color.rgb(18, 18, 26);
    private final int purple = Color.rgb(139, 52, 217);
    private final int purple2 = Color.rgb(106, 51, 143);
    private final int pink = Color.rgb(255, 79, 131);
    private final int blue = Color.rgb(53, 167, 255);
    private final int green = Color.rgb(73, 230, 160);
    private final int red = Color.rgb(255, 92, 92);
    private final int muted = Color.argb(178, 255, 255, 255);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(Color.rgb(10, 10, 15));
        buildUi();
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        screen = new FrameLayout(this);
        screen.setBackground(makeBg());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView logo = text("Toxic Habbo", 31, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setLetterSpacing(0.02f);
        root.addView(logo, lp(-1, -2, 0, 0, 0, 6));

        TextView subtitle = text("Busca nativa usando seu site como backend para manter os dados completos do Toxic.", 14, muted, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(2), 1f);
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, 18));

        LinearLayout searchCard = card(dp(22));
        searchCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.addView(searchCard, lp(-1, -2, 0, 0, 0, 16));

        TextView title = text("Buscar Habbo", 22, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        searchCard.addView(title, lp(-1, -2, 0, 0, 0, 14));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.HORIZONTAL);
        form.setGravity(Gravity.CENTER_VERTICAL);
        form.setBaselineAligned(false);
        searchCard.addView(form, lp(-1, dp(54), 0, 0, 0, 0));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Digite um nick");
        searchInput.setHintTextColor(Color.argb(150,255,255,255));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(15);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        searchInput.setBackground(round(Color.argb(14,255,255,255), dp(14), Color.argb(26,255,255,255), 1));
        form.addView(searchInput, new LinearLayout.LayoutParams(0, -1, 1));

        searchBtn = new Button(this);
        searchBtn.setText("Buscar");
        searchBtn.setTextColor(Color.WHITE);
        searchBtn.setTextSize(13);
        searchBtn.setAllCaps(false);
        searchBtn.setTypeface(Typeface.DEFAULT_BOLD);
        searchBtn.setBackground(grad(dp(14), purple2, purple));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(96), -1);
        bp.leftMargin = dp(10);
        form.addView(searchBtn, bp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        progress.setVisibility(View.GONE);
        root.addView(progress, lp(-1, dp(32), 0, 0, 0, 2));

        statusText = text("", 14, Color.argb(210,255,255,255), false);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, lp(-1, -2, 0, 0, 0, 12));

        resultWrap = new LinearLayout(this);
        resultWrap.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultWrap, lp(-1, -2, 0, 0, 0, 0));

        setContentView(screen);

        searchBtn.setOnClickListener(v -> search());
        searchInput.setOnEditorActionListener((v, actionId, event) -> { search(); return true; });
        showStartState();
    }

    private void showStartState() {
        resultWrap.removeAllViews();
        LinearLayout c = card(dp(20));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 0));
        c.addView(text("O que esta versão busca", 19, Color.WHITE, true), lp(-1, -2, 0, 0, 0, 10));
        c.addView(smallItem("Perfil + Habbodex", "Nick, missão, avatar, online/offline, banido, emblemas e dados públicos."));
        c.addView(smallItem("Históricos", "Nomes anteriores, missões anteriores e visuais anteriores quando o backend retornar."));
        c.addView(smallItem("Extras", "Fotos, amigos, amigos removidos, quartos, quartos antigos e grupos."));
    }

    private void search() {
        final String nick = searchInput.getText().toString().trim();
        if (nick.isEmpty()) { toast("Digite um nick do Habbo."); return; }
        hideKeyboard();
        setLoading(true, "Buscando " + nick + " pelo backend do Toxic...");
        resultWrap.removeAllViews();

        executor.execute(() -> {
            try {
                ProfileResult r = loadProfile(nick);
                runOnUiThread(() -> renderProfile(r));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false, "");
                    showError(e.getMessage() == null ? "Falha ao buscar perfil." : e.getMessage());
                });
            }
        });
    }

    private ProfileResult loadProfile(String nick) throws Exception {
        ProfileResult r = new ProfileResult();
        r.searchedNick = nick;

        JSONObject habboPublic = tryJson("https://www.habbo.com.br/api/public/users?name=" + enc(nick));
        JSONObject dexByName = unwrap(tryJson(API + "?name=" + enc(nick)));
        JSONObject suggest = unwrap(tryJson(API + "?endpoint=habbos-suggest&name=" + enc(nick) + "&includePreviousNames=true&hotel=br"));

        r.habboPublic = habboPublic;
        r.dex = dexByName;
        r.suggest = suggest;

        JSONObject base = firstObject(dexByName, habboPublic, firstFromList(suggest));
        if (base == null) throw new Exception("Perfil não encontrado no Habbo/Habbodex.");

        r.uniqueId = firstNonEmpty(base, "uniqueId", "id", "habboId");
        if (r.uniqueId.isEmpty() && habboPublic != null) r.uniqueId = habboPublic.optString("uniqueId", "");
        r.name = firstText(base, "name", "username", "habboName");
        if (r.name.isEmpty()) r.name = nick;
        r.figure = firstText(base, "figureString", "figure", "figure_string");
        if (r.figure.isEmpty() && habboPublic != null) r.figure = habboPublic.optString("figureString", "");
        if (r.figure.isEmpty()) r.figure = "hd-180-1";
        r.motto = firstText(base, "motto", "mission");
        if (r.motto.isEmpty() && habboPublic != null) r.motto = habboPublic.optString("motto", "");
        r.online = optBoolAny(base, false, "online", "isOnline");
        if (habboPublic != null && habboPublic.has("online")) r.online = habboPublic.optBoolean("online", r.online);
        r.privateProfile = !optBoolAny(base, true, "profileVisible", "visible");
        if (habboPublic != null && habboPublic.has("profileVisible")) r.privateProfile = !habboPublic.optBoolean("profileVisible", true);
        r.banned = optBoolAny(base, false, "isBanned", "banned");
        r.memberSince = firstText(base, "memberSince", "creationTime", "createdAt", "registeredAt");
        if (r.memberSince.isEmpty() && habboPublic != null) r.memberSince = habboPublic.optString("memberSince", "");
        r.lastAccess = firstText(base, "lastAccessTime", "lastLoginTime", "lastOnline", "lastVisit");
        r.level = firstText(base, "currentLevel", "level");
        r.starGems = firstText(base, "starGemCount", "starGems");

        r.previousNames = mergeLists(extractList(dexByName, "previousNames"), extractPreviousNamesFromSuggest(suggest, r.name));
        r.selectedBadges = extractListFromKeys(dexByName, "selectedBadges", "badges");

        if (!r.uniqueId.isEmpty()) {
            JSONObject dexProfile = unwrap(tryJson(API + "?uniqueId=" + enc(r.uniqueId)));
            if (dexProfile != null) {
                r.dexProfile = dexProfile;
                r.previousNames = mergeLists(r.previousNames, extractList(dexProfile, "previousNames"));
                r.selectedBadges = mergeLists(r.selectedBadges, extractListFromKeys(dexProfile, "selectedBadges", "badges"));
            }

            r.previousMottos = fetchAll(r.uniqueId, "previous-mottos", null, 100, 5);
            r.previousStyles = fetchAll(r.uniqueId, "previous-styles", null, 100, 5);
            r.photos = fetchAll(r.uniqueId, "photos", null, 100, 3);
            r.friends = fetchAll(r.uniqueId, "friends", "friends", 100, 5);
            r.oldFriends = fetchAll(r.uniqueId, "previous-friends", null, 100, 3);
            r.rooms = fetchAll(r.uniqueId, "rooms", "rooms", 100, 4);
            r.oldRooms = fetchAll(r.uniqueId, "previous-rooms", "rooms", 100, 4);
            r.groups = fetchAll(r.uniqueId, "groups", "groups", 100, 4);
        }

        return r;
    }

    private ArrayList<JSONObject> fetchAll(String uniqueId, String endpoint, String primaryKey, int limit, int maxPages) {
        ArrayList<JSONObject> out = new ArrayList<>();
        int page = 1;
        for (int i = 0; i < maxPages; i++) {
            try {
                JSONObject pageData = unwrap(getJson(API + "?uniqueId=" + enc(uniqueId) + "&endpoint=" + enc(endpoint) + "&page=" + page + "&limit=" + limit));
                if (pageData == null) break;
                ArrayList<JSONObject> items = extractList(pageData, primaryKey);
                if (items.isEmpty()) break;
                out.addAll(items);
                JSONObject next = pageData.optJSONObject("next");
                int nextPage = next == null ? 0 : next.optInt("page", 0);
                if (nextPage <= 0 || nextPage == page) break;
                page = nextPage;
            } catch (Exception ignored) { break; }
        }
        return out;
    }

    private void renderProfile(ProfileResult r) {
        setLoading(false, "");
        resultWrap.removeAllViews();

        LinearLayout profile = card(dp(22));
        profile.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultWrap.addView(profile, lp(-1, -2, 0, 0, 0, 14));

        ImageView avatar = new ImageView(this);
        avatar.setAdjustViewBounds(true);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatar.setPadding(dp(8), dp(8), dp(8), dp(8));
        avatar.setBackground(round(Color.argb(18,255,255,255), dp(18), Color.argb(16,255,255,255), 1));
        profile.addView(avatar, lp(-1, dp(252), 0, 0, 0, 14));
        loadImage(avatar, avatarFull(r.figure));

        LinearLayout headline = new LinearLayout(this);
        headline.setGravity(Gravity.CENTER);
        headline.setOrientation(LinearLayout.HORIZONTAL);
        profile.addView(headline, lp(-1, -2, 0, 0, 0, 6));
        TextView name = text(r.name, 30, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        headline.addView(name, new LinearLayout.LayoutParams(-2, -2));
        TextView online = pill(r.online ? "online" : "offline", r.online ? green : red);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, -2); op.leftMargin = dp(8);
        headline.addView(online, op);

        TextView motto = text(r.motto.isEmpty() ? "Sem missão definida." : r.motto, 15, Color.argb(220,255,255,255), false);
        motto.setGravity(Gravity.CENTER);
        motto.setLineSpacing(dp(2), 1f);
        profile.addView(motto, lp(-1, -2, 0, 0, 0, 12));

        LinearLayout badges = new LinearLayout(this);
        badges.setGravity(Gravity.CENTER);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setBaselineAligned(false);
        profile.addView(badges, lp(-1, -2, 0, 0, 0, 14));
        badges.addView(pill(r.privateProfile ? "Privado" : "Público", r.privateProfile ? red : purple));
        if (r.banned) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.leftMargin=dp(8); badges.addView(pill("Banido", red), p); }
        TextView open = pill("Abrir perfil", blue);
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-2, -2); openLp.leftMargin = dp(8); badges.addView(open, openLp);
        open.setOnClickListener(v -> openUrl("https://www.habbo.com.br/profile/" + Uri.encode(r.name)));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.VERTICAL);
        profile.addView(stats, lp(-1, -2, 0, 0, 0, 0));
        stats.addView(infoRow("Criado em", niceDate(r.memberSince)));
        if (!r.lastAccess.isEmpty()) stats.addView(infoRow("Último acesso", niceDate(r.lastAccess)));
        if (!r.level.isEmpty()) stats.addView(infoRow("Nível", r.level));
        if (!r.starGems.isEmpty()) stats.addView(infoRow("Star gems", r.starGems));
        stats.addView(infoRow("Figure", r.figure));

        addSelectedBadges(r.selectedBadges);
        addPreviousNames(r.previousNames);
        addPreviousMottos(r.previousMottos);
        addPreviousStyles(r.previousStyles);
        addPhotos(r.photos);
        addFriends("Amigos", r.friends, false);
        addFriends("Amigos removidos", r.oldFriends, true);
        addRooms("Quartos", r.rooms);
        addRooms("Quartos antigos", r.oldRooms);
        addGroups(r.groups);
    }

    private void addSelectedBadges(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = section("Emblemas selecionados", list.size());
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(row);
        c.addView(hsv, lp(-1, dp(92), 0, 0, 0, 0));
        for (int i=0; i<Math.min(list.size(), 8); i++) {
            JSONObject b = list.get(i);
            LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER); item.setPadding(dp(8),dp(6),dp(8),dp(6)); item.setBackground(round(Color.argb(12,255,255,255), dp(14), Color.argb(18,255,255,255),1));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(88), dp(82)); ip.rightMargin = dp(8); row.addView(item, ip);
            ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_INSIDE); item.addView(img, new LinearLayout.LayoutParams(dp(38), dp(38)));
            String code = firstText(b, "code", "badgeCode");
            if (!code.isEmpty()) loadImage(img, "https://images.habbo.com/c_images/album1584/" + enc(code) + ".png");
            TextView label = text(code.isEmpty() ? "emblema" : code, 11, Color.WHITE, true); label.setGravity(Gravity.CENTER); label.setMaxLines(2); item.addView(label, lp(-1,-2,0,4,0,0));
        }
    }

    private void addPreviousNames(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = section("Nomes anteriores", list.size());
        for (int i=0; i<Math.min(list.size(), 30); i++) {
            JSONObject o = list.get(i);
            String n = firstText(o, "name", "oldName", "username");
            String d = firstText(o, "changedAt", "date", "timestamp", "createdAt");
            c.addView(smallItem(n.isEmpty()?"Nome anterior":n, niceDate(d)));
        }
    }

    private void addPreviousMottos(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = section("Missões anteriores", list.size());
        for (int i=0; i<Math.min(list.size(), 25); i++) {
            JSONObject o = list.get(i);
            String m = firstText(o, "motto", "mission", "text", "value");
            String d = firstText(o, "changedAt", "date", "createdAt", "timestamp");
            c.addView(smallItem(m.isEmpty()?"Missão anterior":m, niceDate(d)));
        }
    }

    private void addPreviousStyles(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = section("Visuais anteriores", list.size());
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(row);
        c.addView(hsv, lp(-1, dp(160), 0, 0, 0, 0));
        for (int i=0; i<Math.min(list.size(), 18); i++) {
            JSONObject o = list.get(i);
            String fig = firstText(o, "figureString", "figure", "look");
            if (fig.isEmpty()) continue;
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(8),dp(8),dp(8),dp(8)); box.setBackground(round(Color.argb(12,255,255,255), dp(16), Color.argb(18,255,255,255),1));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(96), dp(150)); bp.rightMargin = dp(10); row.addView(box, bp);
            ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.FIT_CENTER); box.addView(img, new LinearLayout.LayoutParams(-1, dp(108)));
            loadImage(img, avatarSmall(fig));
            TextView dt = text(niceDate(firstText(o, "changedAt", "date", "createdAt")), 11, Color.argb(180,255,255,255), false); dt.setGravity(Gravity.CENTER); dt.setMaxLines(2); box.addView(dt, lp(-1,-2,0,4,0,0));
            final String finalFig = fig;
            box.setOnClickListener(v -> showClothesDialog(finalFig));
        }
    }

    private void showClothesDialog(String figure) {
        final Dialog dialog = new Dialog(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(18), dp(18), dp(18), dp(18));
        wrap.setBackground(round(Color.rgb(12, 9, 24), dp(22), Color.argb(40,255,255,255), 1));
        dialog.setContentView(wrap);
        Window w = dialog.getWindow();
        if (w != null) { w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); w.setLayout(-1, -2); }
        wrap.addView(text("Peças do visual", 20, Color.WHITE, true), lp(-1,-2,0,0,0,8));
        wrap.addView(text(figure, 12, Color.argb(190,255,255,255), false), lp(-1,-2,0,0,0,12));
        TextView loading = text("Carregando roupas...", 14, Color.WHITE, false); wrap.addView(loading, lp(-1,-2,0,0,0,8));
        Button close = new Button(this); close.setText("Fechar"); close.setAllCaps(false); close.setTextColor(Color.WHITE); close.setBackground(grad(dp(14), purple2, purple)); wrap.addView(close, lp(-1, dp(48), 0, 10, 0, 0));
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        executor.execute(() -> {
            try {
                JSONObject data = unwrap(getJson(API + "?endpoint=from-figure-string&figureString=" + enc(figure)));
                final ArrayList<JSONObject> clothes = extractList(data, null);
                runOnUiThread(() -> {
                    wrap.removeView(loading);
                    if (clothes.isEmpty()) {
                        wrap.addView(smallItem("Nenhuma peça encontrada", "O backend não retornou dados para este visual."), wrap.getChildCount()-1);
                        return;
                    }
                    for (int i=0; i<Math.min(clothes.size(), 20); i++) {
                        JSONObject o = clothes.get(i);
                        String name = firstText(o, "name", "publicName", "classname", "code");
                        String code = firstText(o, "code", "classname", "id");
                        wrap.addView(smallItem(name.isEmpty()?"Peça":name, code), wrap.getChildCount()-1);
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> { loading.setText("Não foi possível carregar as peças."); });
            }
        });
    }

    private void addPhotos(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = section("Fotos", list.size());
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(row);
        c.addView(hsv, lp(-1, dp(150), 0, 0, 0, 0));
        for (int i=0; i<Math.min(list.size(), 12); i++) {
            JSONObject o = list.get(i);
            String url = firstText(o, "url", "previewUrl", "imageUrl", "photoUrl");
            ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setBackground(round(Color.argb(20,255,255,255), dp(14), Color.argb(20,255,255,255), 1));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(145), dp(130)); p.rightMargin = dp(10); row.addView(img, p);
            if (!url.isEmpty()) { loadImage(img, url); final String finalUrl = url; img.setOnClickListener(v -> openUrl(finalUrl)); }
        }
    }

    private void addFriends(String title, ArrayList<JSONObject> list, boolean old) {
        if (list.isEmpty()) return;
        LinearLayout c = section(title, list.size());
        for (int i=0; i<Math.min(list.size(), 35); i++) {
            JSONObject f = list.get(i);
            String n = firstText(f, "name", "username", "habboName");
            String m = firstText(f, "motto", "mission");
            String dt = firstText(f, "friendSince", "createdAt", "date", "removedAt");
            c.addView(smallItem((n.isEmpty()?"Habbo":n) + (old ? "  • removido" : ""), (m.isEmpty()?"":m) + (dt.isEmpty()?"":"\n" + niceDate(dt))));
        }
    }

    private void addRooms(String title, ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = section(title, list.size());
        for (int i=0; i<Math.min(list.size(), 30); i++) {
            JSONObject room = list.get(i);
            String name = firstText(room, "name", "roomName", "caption", "title");
            String id = firstText(room, "id", "roomId");
            c.addView(smallItem(name.isEmpty()?"Quarto":name, id.isEmpty()?"":"ID " + id));
        }
    }

    private void addGroups(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = section("Grupos", list.size());
        for (int i=0; i<Math.min(list.size(), 25); i++) {
            JSONObject g = list.get(i);
            String n = firstText(g, "name", "groupName");
            String d = firstText(g, "description", "desc", "badgeCode");
            c.addView(smallItem(n.isEmpty()?"Grupo":n, d));
        }
    }

    private LinearLayout section(String title, int count) {
        LinearLayout c = card(dp(18));
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 14));
        TextView t = text(title + " (" + count + ")", 20, Color.WHITE, true);
        c.addView(t, lp(-1, -2, 0, 0, 0, 10));
        return c;
    }

    private TextView smallItem(String main, String sub) {
        String body = main == null ? "—" : main;
        if (sub != null && !sub.trim().isEmpty()) body += "\n" + sub.trim();
        TextView v = text(body, 14, Color.WHITE, false);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setLineSpacing(dp(2), 1f);
        v.setBackground(round(Color.argb(10,255,255,255), dp(14), Color.argb(18,255,255,255), 1));
        v.setLayoutParams(lp(-1, -2, 0, 0, 0, 8));
        return v;
    }

    private TextView infoRow(String label, String value) {
        return smallItem(label, value == null || value.isEmpty() ? "Não informado" : value);
    }

    private void showError(String msg) {
        resultWrap.removeAllViews();
        LinearLayout c = card(dp(18)); c.setPadding(dp(16), dp(16), dp(16), dp(16)); resultWrap.addView(c, lp(-1,-2,0,0,0,0));
        TextView t = text(msg, 15, Color.WHITE, true); t.setGravity(Gravity.CENTER); c.addView(t);
    }

    private void setLoading(boolean loading, String message) {
        searchBtn.setEnabled(!loading);
        searchBtn.setText(loading ? "..." : "Buscar");
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        statusText.setText(message == null ? "" : message);
    }

    private JSONObject getJson(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(22000);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setRequestProperty("User-Agent", "ToxicHabboApp/1.3 Android");
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String body = readAll(is);
        if (code < 200 || code >= 300 || body == null || body.trim().isEmpty()) throw new IOException("HTTP " + code);
        return new JSONObject(body);
    }

    private JSONObject tryJson(String u) {
        try { return getJson(u); } catch (Exception e) { return null; }
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
        while ((n = is.read(buf)) > 0) out.write(buf,0,n);
        return out.toString("UTF-8");
    }

    private void loadImage(ImageView view, String url) {
        executor.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(12000); c.setReadTimeout(22000);
                c.setRequestProperty("User-Agent", "ToxicHabboApp/1.3 Android");
                Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream());
                runOnUiThread(() -> { if (bmp != null) view.setImageBitmap(bmp); });
            } catch (Exception ignored) {}
        });
    }

    private JSONObject unwrap(JSONObject obj) {
        if (obj == null) return null;
        if (obj.has("ok") && obj.has("data")) return obj.optJSONObject("data") != null ? obj.optJSONObject("data") : obj;
        return obj;
    }

    private JSONObject firstObject(JSONObject... objects) {
        for (JSONObject o : objects) if (o != null && o.length() > 0) return o;
        return null;
    }

    private JSONObject firstFromList(JSONObject obj) {
        ArrayList<JSONObject> list = extractList(obj, null);
        return list.isEmpty() ? null : list.get(0);
    }

    private ArrayList<JSONObject> extractPreviousNamesFromSuggest(JSONObject suggest, String currentName) {
        ArrayList<JSONObject> out = new ArrayList<>();
        ArrayList<JSONObject> users = extractList(suggest, null);
        String low = currentName == null ? "" : currentName.toLowerCase(Locale.ROOT);
        for (JSONObject user : users) {
            String uname = firstText(user, "name", "username").toLowerCase(Locale.ROOT);
            if (!low.isEmpty() && !uname.equals(low)) continue;
            out.addAll(extractList(user, "previousNames"));
        }
        return out;
    }

    private ArrayList<JSONObject> extractListFromKeys(JSONObject obj, String... keys) {
        ArrayList<JSONObject> out = new ArrayList<>();
        if (obj == null) return out;
        for (String k : keys) out = mergeLists(out, extractList(obj, k));
        return out;
    }

    private ArrayList<JSONObject> extractList(JSONObject data, String primaryKey) {
        ArrayList<JSONObject> out = new ArrayList<>();
        if (data == null) return out;
        JSONArray arr = null;
        if (primaryKey != null && !primaryKey.isEmpty()) arr = data.optJSONArray(primaryKey);
        if (arr == null) arr = data.optJSONArray("result");
        if (arr == null) arr = data.optJSONArray("results");
        if (arr == null) arr = data.optJSONArray("data");
        if (arr == null) arr = data.optJSONArray("items");
        JSONObject d = data.optJSONObject("data");
        if (arr == null && d != null) {
            if (primaryKey != null && !primaryKey.isEmpty()) arr = d.optJSONArray(primaryKey);
            if (arr == null) arr = d.optJSONArray("result");
            if (arr == null) arr = d.optJSONArray("results");
            if (arr == null) arr = d.optJSONArray("items");
        }
        if (arr != null) {
            for (int i=0; i<arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(o);
            }
        }
        return out;
    }

    private ArrayList<JSONObject> mergeLists(ArrayList<JSONObject> a, ArrayList<JSONObject> b) {
        ArrayList<JSONObject> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        if (a != null) addUnique(out, seen, a);
        if (b != null) addUnique(out, seen, b);
        return out;
    }

    private void addUnique(ArrayList<JSONObject> out, HashSet<String> seen, ArrayList<JSONObject> src) {
        for (JSONObject o : src) {
            String key = firstText(o, "id", "name", "figureString", "motto", "code") + o.toString().hashCode();
            if (seen.add(key)) out.add(o);
        }
    }

    private String firstText(JSONObject o, String... keys) {
        if (o == null) return "";
        for (String k : keys) {
            Object v = o.opt(k);
            if (v == null || v == JSONObject.NULL) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
        }
        return "";
    }

    private String firstNonEmpty(JSONObject o, String... keys) { return firstText(o, keys); }

    private boolean optBoolAny(JSONObject o, boolean fallback, String... keys) {
        if (o == null) return fallback;
        for (String k : keys) if (o.has(k)) return o.optBoolean(k, fallback);
        return fallback;
    }

    private String avatarFull(String figure) { return "https://www.habbo.com.br/habbo-imaging/avatarimage?figure=" + enc(figure) + "&size=l&direction=2&head_direction=2&gesture=sml&action=std&headonly=0"; }
    private String avatarSmall(String figure) { return "https://www.habbo.com.br/habbo-imaging/avatarimage?figure=" + enc(figure) + "&size=m&direction=2&head_direction=2&gesture=sml&action=std&headonly=0"; }

    private Drawable makeBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(13,13,18), Color.rgb(26,13,38), Color.rgb(10,10,15)});
    }
    private LinearLayout card(int radius) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackground(round(Color.argb(13,255,255,255), radius, Color.argb(22,255,255,255), 1)); return l; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s == null ? "" : s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView pill(String s, int color) { TextView v = text(s, 12, Color.WHITE, true); v.setPadding(dp(10), dp(7), dp(10), dp(7)); v.setBackground(round(adjustAlpha(color, 0.28f), dp(999), adjustAlpha(color,0.55f), 1)); return v; }
    private GradientDrawable round(int fill, int radius, int stroke, int sw) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(radius); if (sw > 0) d.setStroke(dp(sw), stroke); return d; }
    private GradientDrawable grad(int radius, int c1, int c2) { GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1,c2}); d.setCornerRadius(radius); return d; }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private int adjustAlpha(int color, float f) { return Color.argb(Math.round(Color.alpha(color)*f), Color.red(color), Color.green(color), Color.blue(color)); }
    private String enc(String s) { try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch(Exception e){ return s; } }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard(){ try{ ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(searchInput.getWindowToken(),0);}catch(Exception ignored){} }
    private void openUrl(String url){ try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch(Exception ignored){} }
    private String niceDate(String in) { if (in == null || in.trim().isEmpty()) return "Não informado"; return in.replace('T',' ').replace(".000+0000", "").replace("Z", ""); }

    private static class ProfileResult {
        String searchedNick = "";
        String uniqueId = "";
        String name = "";
        String motto = "";
        String figure = "";
        String memberSince = "";
        String lastAccess = "";
        String level = "";
        String starGems = "";
        boolean online = false;
        boolean privateProfile = false;
        boolean banned = false;
        JSONObject habboPublic, dex, suggest, dexProfile;
        ArrayList<JSONObject> previousNames = new ArrayList<>();
        ArrayList<JSONObject> previousMottos = new ArrayList<>();
        ArrayList<JSONObject> previousStyles = new ArrayList<>();
        ArrayList<JSONObject> photos = new ArrayList<>();
        ArrayList<JSONObject> friends = new ArrayList<>();
        ArrayList<JSONObject> oldFriends = new ArrayList<>();
        ArrayList<JSONObject> rooms = new ArrayList<>();
        ArrayList<JSONObject> oldRooms = new ArrayList<>();
        ArrayList<JSONObject> groups = new ArrayList<>();
        ArrayList<JSONObject> selectedBadges = new ArrayList<>();
    }
}
