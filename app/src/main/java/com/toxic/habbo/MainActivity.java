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
import com.bumptech.glide.Glide;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String API = "https://atoxic.com.br/api.php";
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private FrameLayout screen;
    private LinearLayout root, resultWrap;
    private EditText searchInput;
    private Button searchBtn;
    private TextView statusText;
    private ProgressBar progress;
    private LinearLayout suggestionsBox;
    private int suggestionRequestId = 0;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private int avatarDirection = 2;
    private ImageView currentAvatarImage;
    private String currentProfileFigure = "";

    private final int bg = Color.rgb(13, 13, 18);
    private final int purple = Color.rgb(139, 52, 217);
    private final int purple2 = Color.rgb(106, 51, 143);
    private final int pink = Color.rgb(255, 79, 131);
    private final int blue = Color.rgb(53, 167, 255);
    private final int green = Color.rgb(73, 230, 160);
    private final int red = Color.rgb(255, 92, 92);
    private final int cardFill = Color.argb(22, 255, 255, 255);
    private final int cardStroke = Color.argb(28, 255, 255, 255);
    private final int muted = Color.argb(178, 255, 255, 255);
    private Typeface habboFont;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        habboFont = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        getWindow().setStatusBarColor(Color.rgb(20, 10, 30));
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
        root.setPadding(dp(18), dp(26), dp(18), dp(42));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView logo = text("Toxic Habbo", 31, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setLetterSpacing(0.02f);
        root.addView(logo, lp(-1, -2, 0, 0, 0, 4));
        TextView subtitle = text("Buscar Habbos", 14, muted, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, 18));

        LinearLayout searchOuter = card(dp(24));
        searchOuter.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.addView(searchOuter, lp(-1, -2, 0, 0, 0, 16));
        LinearLayout searchCard = card(dp(18));
        searchCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        searchOuter.addView(searchCard, lp(-1, -2, 0, 0, 0, 0));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Digite um nick");
        searchInput.setHintTextColor(Color.argb(135,255,255,255));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(16);
        searchInput.setTypeface(habboFont);
        searchInput.setGravity(Gravity.CENTER_VERTICAL);
        searchInput.setPadding(dp(16), 0, dp(16), 0);
        searchInput.setBackground(round(Color.argb(28,255,255,255), dp(14), Color.argb(35,255,255,255), 1));
        searchCard.addView(searchInput, lp(-1, dp(42), 0, 0, 0, 8));

        suggestionsBox = new LinearLayout(this);
        suggestionsBox.setOrientation(LinearLayout.VERTICAL);
        suggestionsBox.setVisibility(View.GONE);
        searchCard.addView(suggestionsBox, lp(-1, -2, 0, 0, 0, 10));

        searchBtn = new Button(this);
        searchBtn.setText("Pesquisar");
        searchBtn.setTextColor(Color.WHITE);
        searchBtn.setTextSize(16);
        searchBtn.setAllCaps(false);
        searchBtn.setTypeface(Typeface.DEFAULT_BOLD);
        searchBtn.setBackground(grad(dp(16), purple2, Color.rgb(166, 42, 235)));
        searchCard.addView(searchBtn, lp(-1, dp(58), 0, 0, 0, 0));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        progress.setVisibility(View.GONE);
        root.addView(progress, lp(-1, dp(34), 0, 0, 0, 2));
        statusText = text("", 14, Color.argb(210,255,255,255), false);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, lp(-1, -2, 0, 0, 0, 10));

        resultWrap = new LinearLayout(this);
        resultWrap.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultWrap, lp(-1, -2, 0, 0, 0, 0));
        setContentView(screen);
        searchBtn.setOnClickListener(v -> search());
        searchInput.setOnEditorActionListener((v, actionId, event) -> { search(); return true; });
        bindNickSuggestions();
        showStartState();
    }

    private void showStartState() {
        resultWrap.removeAllViews();
        LinearLayout c = sectionCard("Pronto para buscar", 0, false);
        c.addView(centerNote("Digite um nick do Habbo BR para consultar perfil, fotos, missões anteriores, visuais, amigos, quartos e grupos usando o backend do Toxic."));
    }

    private void search() {
        final String nick = searchInput.getText().toString().trim();
        if (nick.isEmpty()) { toast("Digite um nick do Habbo."); return; }
        hideKeyboard();
        suggestionsBox.setVisibility(View.GONE);
        setLoading(true, "Buscando " + nick + "...");
        resultWrap.removeAllViews();
        executor.execute(() -> {
            try {
                final ProfileResult r = loadProfile(nick, false);
                runOnUiThread(() -> {
                    renderProfile(r);
                    showInlineLoading("Carregando detalhes do perfil...");
                });
                completeProfileSections(r);
                runOnUiThread(() -> {
                    renderProfile(r);
                    statusText.setText("");
                });
            } catch (ProfileNotFoundException e) {
                runOnUiThread(() -> {
                    setLoading(false, "");
                    showNotFoundState(e.nick, e.suggestions);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false, "");
                    showError(e.getMessage() == null ? "Falha ao buscar perfil." : e.getMessage());
                });
            }
        });
    }

    private ProfileResult loadProfile(String nick, boolean includeSections) throws Exception {
        ProfileResult r = new ProfileResult();
        r.searchedNick = nick;
        JSONObject habboPublic = tryJson("https://www.habbo.com.br/api/public/users?name=" + enc(nick));
        JSONObject dexByName = unwrap(tryJson(API + "?name=" + enc(nick)));
        JSONObject suggest = unwrap(tryJson(API + "?endpoint=habbos-suggest&name=" + enc(nick) + "&includePreviousNames=true&hotel=br"));
        r.habboPublic = habboPublic; r.dex = dexByName; r.suggest = suggest;
        JSONObject base = firstObject(validProfileObject(habboPublic), validProfileObject(dexByName));
        if (base == null) throw new ProfileNotFoundException(nick, filterPreviousNickSuggestions(suggest, nick));

        r.uniqueId = firstText(base, "uniqueId", "id", "habboId");
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
        r.privateProfile = !optBoolAny(base, true, "profileVisible", "isProfileVisible", "visible");
        if (habboPublic != null && habboPublic.has("profileVisible")) r.privateProfile = !habboPublic.optBoolean("profileVisible", true);
        r.banned = optBoolTrue(base, "isBanned", "banned", "ban", "is_banned");
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
                if (r.motto.isEmpty()) r.motto = firstText(dexProfile, "motto", "mission");
                if (r.memberSince.isEmpty()) r.memberSince = firstText(dexProfile, "memberSince", "creationTime", "createdAt");
                if (r.lastAccess.isEmpty()) r.lastAccess = firstText(dexProfile, "lastAccessTime", "lastLoginTime", "lastOnline");
                if (r.level.isEmpty()) r.level = firstText(dexProfile, "currentLevel", "level");
                if (r.starGems.isEmpty()) r.starGems = firstText(dexProfile, "starGemCount", "starGems");
                r.previousNames = mergeLists(r.previousNames, extractList(dexProfile, "previousNames"));
                r.selectedBadges = mergeLists(r.selectedBadges, extractListFromKeys(dexProfile, "selectedBadges", "badges"));
            }

            JSONObject officialProfile = tryJson("https://www.habbo.com.br/api/public/users/" + enc(r.uniqueId) + "/profile");
            r.officialProfile = officialProfile;
            if (officialProfile != null) {
                JSONObject user = officialProfile.optJSONObject("user");
                if (user != null) {
                    if (r.level.isEmpty()) r.level = firstText(user, "currentLevel", "level");
                    if (r.starGems.isEmpty()) r.starGems = firstText(user, "starGemCount", "starGems");
                    if (r.memberSince.isEmpty()) r.memberSince = firstText(user, "memberSince", "creationTime", "createdAt");
                    if (r.lastAccess.isEmpty()) r.lastAccess = firstText(user, "lastAccessTime", "lastLoginTime", "lastOnline");
                    r.online = optBoolAny(user, r.online, "online", "isOnline");
                    r.selectedBadges = mergeLists(r.selectedBadges, extractListFromKeys(user, "selectedBadges", "badges"));
                }
                r.friends = mergeLists(r.friends, extractList(officialProfile, "friends"));
                r.rooms = mergeLists(r.rooms, extractList(officialProfile, "rooms"));
                r.groups = mergeLists(r.groups, extractList(officialProfile, "groups"));
            }
            if (includeSections) {
                completeProfileSections(r);
            }
        }
        return r;
    }

    private void completeProfileSections(ProfileResult r) {
        if (r == null || r.uniqueId == null || r.uniqueId.isEmpty()) return;
        try { r.photos = mergeLists(fetchOfficialPhotos(r.uniqueId), fetchAll(r.uniqueId, "photos", null, 100, 3)); } catch(Exception ignored) {}
        runOnUiThread(() -> { renderProfile(r); showInlineLoading("Carregando histórico..."); });
        try { r.previousMottos = fetchAll(r.uniqueId, "previous-mottos", null, 100, 3); } catch(Exception ignored) {}
        runOnUiThread(() -> { renderProfile(r); showInlineLoading("Carregando visuais e amigos..."); });
        try { r.previousStyles = fetchAll(r.uniqueId, "previous-styles", null, 100, 3); } catch(Exception ignored) {}
        try { r.friends = mergeLists(fetchAll(r.uniqueId, "friends", "friends", 100, 3), r.friends); } catch(Exception ignored) {}
        runOnUiThread(() -> { renderProfile(r); showInlineLoading("Carregando quartos e grupos..."); });
        try { r.oldFriends = fetchAll(r.uniqueId, "previous-friends", null, 30, 5); } catch(Exception ignored) {}
        try { r.rooms = mergeLists(fetchAll(r.uniqueId, "rooms", "rooms", 100, 3), r.rooms); } catch(Exception ignored) {}
        try { r.oldRooms = fetchAll(r.uniqueId, "previous-rooms", "rooms", 100, 3); } catch(Exception ignored) {}
        try { r.groups = mergeLists(fetchAll(r.uniqueId, "groups", "groups", 100, 3), r.groups); } catch(Exception ignored) {}
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


    private ArrayList<JSONObject> fetchOfficialPhotos(String uniqueId) {
        ArrayList<JSONObject> out = new ArrayList<>();
        if (uniqueId == null || uniqueId.trim().isEmpty()) return out;
        try {
            Object data = getJsonAny("https://www.habbo.com.br/extradata/public/users/" + enc(uniqueId) + "/photos");
            if (data instanceof JSONArray) {
                JSONArray a = (JSONArray)data;
                for (int i=0; i<a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null) out.add(o);
                }
            } else if (data instanceof JSONObject) {
                out.addAll(extractList((JSONObject)data, null));
            }
        } catch(Exception ignored) {}
        return out;
    }

    private void renderProfile(ProfileResult r) {
        setLoading(false, "");
        resultWrap.removeAllViews();

        LinearLayout profile = card(dp(22));
        profile.setPadding(dp(18), dp(18), dp(18), dp(18));
        resultWrap.addView(profile, lp(-1, -2, 0, 0, 0, 18));

        FrameLayout avatarFrame = new FrameLayout(this);
        avatarFrame.setBackground(round(Color.rgb(15, 8, 25), dp(20), Color.argb(22,255,255,255), 1));
        profile.addView(avatarFrame, lp(-1, dp(280), 0, 0, 0, 16));
        ImageView avatar = new ImageView(this);
        avatar.setAdjustViewBounds(true);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatar.setPadding(dp(20), dp(12), dp(20), dp(58));
        avatarFrame.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
        currentAvatarImage = avatar;
        currentProfileFigure = r.figure;
        avatarDirection = 2;
        updateProfileAvatar();

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, dp(42), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cp.bottomMargin = dp(6);
        avatarFrame.addView(controls, cp);
        TextView left = roundIconButton("‹");
        TextView clothes = roundIconButton("shirt");
        TextView right = roundIconButton("›");
        controls.addView(left);
        controls.addView(clothes);
        controls.addView(right);
        left.setOnClickListener(v -> { avatarDirection = normalizeDirection(avatarDirection + 1); updateProfileAvatar(); });
        right.setOnClickListener(v -> { avatarDirection = normalizeDirection(avatarDirection - 1); updateProfileAvatar(); });
        clothes.setOnClickListener(v -> showClothesDialog(currentProfileFigure, "Visual atual"));

        TextView name = habboText(r.name, 31, true);
        name.setGravity(Gravity.CENTER);
        profile.addView(name, lp(-1, -2, 0, 0, 0, 10));
        if (!r.motto.isEmpty()) {
            TextView motto = habboText(r.motto, 16, false);
            motto.setGravity(Gravity.CENTER);
            motto.setTextColor(Color.argb(220,255,255,255));
            motto.setLineSpacing(dp(2), 1f);
            profile.addView(motto, lp(-1, -2, 0, 0, 0, 14));
        }
        LinearLayout badges = new LinearLayout(this);
        badges.setGravity(Gravity.CENTER);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        profile.addView(badges, lp(-1, -2, 0, 0, 0, 6));
        if (r.privateProfile) badges.addView(profileBadge("Privado", "lock", red));
        if (r.banned) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.leftMargin=dp(8); badges.addView(profileBadge("Banido", "status", red), p); }

        addSelectedBadges(r.selectedBadges);
        addPreviousNames(r.previousNames);
        addPhotos(r.photos);
        addPreviousMottos(r.previousMottos);
        addPreviousStyles(r.previousStyles);
        addStats(r);
        addFriendsTabs(r.friends, r.oldFriends);
        addRoomsTabs(r.rooms, r.oldRooms);
        addGroups(r.groups);
    }

    private LinearLayout profileBadge(String label, String icon, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(8), dp(5), dp(10), dp(5));
        row.setBackground(round(adjustAlpha(color, 0.32f), dp(999), adjustAlpha(color, 0.55f), 1));
        IconView iv = new IconView(this, icon);
        row.addView(iv, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView tv = text(label, 13, Color.WHITE, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); tp.leftMargin = dp(6);
        row.addView(tv, tp);
        return row;
    }

    private TextView roundIconButton(String label) {
        TextView v = text("shirt".equals(label) ? "" : label, 24, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setBackground(round(Color.argb(30,255,255,255), dp(999), Color.argb(70,255,255,255), 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(38), dp(38));
        p.setMargins(dp(6), 0, dp(6), 0);
        v.setLayoutParams(p);
        if ("shirt".equals(label)) {
            v.setBackground(new ShirtDrawable());
        }
        return v;
    }

    private void updateProfileAvatar() {
        if (currentAvatarImage != null && currentProfileFigure != null && !currentProfileFigure.isEmpty()) {
            loadImage(currentAvatarImage, avatarFull(currentProfileFigure, avatarDirection));
        }
    }

    private int normalizeDirection(int value) {
        while (value < 0) value += 8;
        while (value > 7) value -= 8;
        return value;
    }


    private void addStats(ProfileResult r) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        resultWrap.addView(wrap, lp(-1, -2, 0, 0, 0, 18));
        wrap.addView(statRow("status", "Status", r.online ? "Online" : "—"));
        wrap.addView(statRow("clock", "Último login", niceDate(r.lastAccess)));
        wrap.addView(statRow("calendar", "Criação", niceDate(r.memberSince)));
        wrap.addView(statRow("friends", "Amigos", String.valueOf(r.friends.size())));
        wrap.addView(statRow("rooms", "Quartos", String.valueOf(r.rooms.size())));
        wrap.addView(statRow("groups", "Grupos", String.valueOf(r.groups.size())));
        wrap.addView(statRow("photos", "Fotos", String.valueOf(r.photos.size())));
        wrap.addView(statRow("star", "Estrelas", emptyDash(r.starGems)));
        wrap.addView(statRow("level", "Level", emptyDash(r.level)));
    }

    private LinearLayout statRow(String icon, String label, String value) {
        LinearLayout row = card(dp(18));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams rp = lp(-1, dp(62), 0, 0, 0, 8);
        row.setLayoutParams(rp);
        if ("status".equals(icon)) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
            Glide.with(this).asGif().load("Online".equals(value) ? R.drawable.online : R.drawable.offline).into(iv);
        } else {
            IconView iv = new IconView(this, icon);
            row.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        LinearLayout texts = new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.leftMargin = dp(10); row.addView(texts, tp);
        texts.addView(text(label, 12, Color.argb(190,255,255,255), false));
        texts.addView(text(value == null || value.isEmpty() ? "—" : value, 15, Color.WHITE, true));
        return row;
    }

    private void addSelectedBadges(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setFillViewport(true);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
        row.setMinimumWidth(getResources().getDisplayMetrics().widthPixels - dp(36));
        row.setPadding(dp(2), dp(2), dp(2), dp(2));
        hsv.addView(row);
        resultWrap.addView(hsv, lp(-1, dp(74), 0, 0, 0, 14));
        for (int i=0; i<Math.min(list.size(), 12); i++) {
            JSONObject b = list.get(i); String code = firstText(b, "code", "badgeCode");
            ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(62), dp(62)); p.rightMargin = dp(10); row.addView(img, p);
            if (!code.isEmpty()) loadImage(img, "https://images.habbo.com/c_images/album1584/" + enc(code) + ".png");
        }
    }

    private void addPreviousNames(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = sectionCard("Nomes anteriores", list.size(), true);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(true);
        sv.setScrollbarFadingEnabled(false);
        tintScrollBar(sv);
        LinearLayout inner = new LinearLayout(this); inner.setOrientation(LinearLayout.VERTICAL);
        sv.addView(inner, new ScrollView.LayoutParams(-1, -2));
        c.addView(sv, lp(-1, dp(Math.min(205, Math.max(100, 52 * Math.min(list.size(), 4)))), 0, 0, 0, 0));
        for (int i=0; i<Math.min(list.size(), 40); i++) {
            JSONObject o = list.get(i);
            String n = firstText(o, "name", "oldName", "username");
            String d = firstText(o, "changedAt", "date", "timestamp", "createdAt", "creationTime");
            inner.addView(mottoItem(n.isEmpty()?"Nome anterior":n, niceDate(d)));
        }
    }

    private void addPreviousMottos(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = sectionCard("Missões anteriores", list.size(), true);
        for (int i=0; i<Math.min(list.size(), 5); i++) {
            JSONObject o = list.get(i);
            String m = firstText(o, "text", "motto", "mission", "value");
            String d = firstText(o, "changedAt", "updatedAt", "createdAt", "timestamp", "creationTime", "date");
            c.addView(mottoItem(m.isEmpty()?"Missão anterior":m, niceDate(d)));
        }
    }

    private TextView mottoItem(String main, String date) {
        TextView v = habboText(main + (date == null || date.isEmpty() || date.equals("—") ? "" : "\n" + date), 16, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        v.setLineSpacing(dp(4), 1f);
        v.setTextColor(Color.WHITE);
        v.setBackground(round(Color.argb(22,255,255,255), dp(16), Color.argb(24,255,255,255), 1));
        v.setLayoutParams(lp(-1, -2, 0, 0, 0, 10));
        return v;
    }

    private void addPreviousStyles(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = sectionCard("Visuais anteriores", list.size(), true);
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(row);
        c.addView(hsv, lp(-1, dp(172), 0, 0, 0, 8));
        for (int i=0; i<Math.min(list.size(), 20); i++) {
            JSONObject o = list.get(i);
            String fig = firstText(o, "figureString", "figure", "look");
            if (fig.isEmpty()) continue;
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(8),dp(8),dp(8),dp(8)); box.setBackground(round(Color.argb(18,255,255,255), dp(18), Color.argb(24,255,255,255),1));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(106), dp(162)); bp.rightMargin = dp(12); row.addView(box, bp);
            ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.FIT_CENTER); box.addView(img, new LinearLayout.LayoutParams(-1, dp(112)));
            loadImage(img, avatarSmall(fig));
            TextView dt = text(niceDate(firstText(o, "changedAt", "date", "createdAt", "creationTime")), 12, Color.argb(185,255,255,255), false); dt.setGravity(Gravity.CENTER); dt.setMaxLines(2); box.addView(dt, lp(-1,-2,0,4,0,0));
            final String finalFig = fig;
            box.setOnClickListener(v -> showClothesDialog(finalFig, niceDate(firstText(o, "changedAt", "date", "createdAt", "creationTime"))));
        }
    }

    private void showClothesDialog(String figure, String date) {
        final Dialog dialog = new Dialog(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(18), dp(18), dp(18), dp(18));
        wrap.setBackground(round(Color.rgb(28, 18, 42), dp(22), Color.argb(42,255,255,255), 1));
        dialog.setContentView(wrap);
        Window w = dialog.getWindow();
        if (w != null) { w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); w.setLayout(-1, -2); }
        TextView title = text("Visuais • " + (date == null ? "" : date), 18, Color.WHITE, true); title.setGravity(Gravity.CENTER); wrap.addView(title, lp(-1,-2,0,0,0,12));
        View line = new View(this); line.setBackgroundColor(Color.argb(35,255,255,255)); wrap.addView(line, lp(-1,1,6,0,6,12));
        TextView loading = text("Carregando roupas...", 14, Color.WHITE, false); wrap.addView(loading, lp(-1,-2,0,0,0,8));
        Button close = new Button(this); close.setText("Fechar"); close.setAllCaps(false); close.setTextColor(Color.WHITE); close.setBackground(grad(dp(14), purple2, purple)); wrap.addView(close, lp(-1, dp(48), 0, 12, 0, 0));
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        executor.execute(() -> {
            try {
                JSONObject data = unwrap(getJson(API + "?endpoint=from-figure-string&figureString=" + enc(figure)));
                final ArrayList<JSONObject> clothes = normalizeClothingEntries(data);
                runOnUiThread(() -> {
                    wrap.removeView(loading);
                    if (clothes.isEmpty()) { wrap.addView(mottoItem("Nenhuma peça encontrada", ""), wrap.getChildCount()-1); return; }
                    for (int i=0; i<Math.min(clothes.size(), 20); i++) wrap.addView(clothingRow(clothes.get(i)), wrap.getChildCount()-1);
                });
            } catch (Exception ex) { runOnUiThread(() -> loading.setText("Não foi possível carregar as peças.")); }
        });
    }

    private LinearLayout clothingRow(JSONObject o) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10),dp(8),dp(10),dp(8)); row.setBackground(round(Color.argb(26,255,255,255), dp(14), Color.argb(28,255,255,255),1));
        row.setLayoutParams(lp(-1, -2, 0, 0, 0, 10));
        ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_INSIDE); row.addView(img, new LinearLayout.LayoutParams(dp(70), dp(70)));
        String code = firstText(o, "code", "classname", "className", "id");
        String icon = firstText(o, "iconUrl", "imageUrl", "url", "thumbnail");
        if (icon.isEmpty() && !code.isEmpty()) icon = "https://habbodex.com/images/furni/" + enc(code) + "/" + enc(code) + "_icon.png";
        if (!icon.isEmpty()) loadImage(img, icon);
        LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.leftMargin = dp(12); row.addView(txt,tp);
        String name = clothingName(o, code);
        TextView nm = habboText(name.isEmpty()?"Peça":name, 15, true); nm.setMaxLines(2); nm.setEllipsize(TextUtils.TruncateAt.END); txt.addView(nm);
        String lineCode = firstNestedText(o, "line", "localeNames", "br"); if (lineCode.isEmpty()) lineCode = firstText(o, "lineCode", "category", "_slot");
        txt.addView(text(lineCode.isEmpty()?code:lineCode, 13, muted, false));
        return row;
    }


    private String clothingName(JSONObject o, String fallback) {
        String n = firstNestedText(o, "localeNames", "br");
        if (n.isEmpty()) n = firstNestedText(o, "localeNames", "pt");
        if (n.isEmpty()) n = firstNestedText(o, "localeNames", "us");
        if (n.isEmpty()) n = firstText(o, "name", "publicName", "furniName", "classname", "className", "code");
        return n.isEmpty() ? fallback : n;
    }

    private String firstNestedText(JSONObject o, String... path) {
        if (o == null || path == null || path.length == 0) return "";
        Object cur = o;
        for (String k : path) {
            if (!(cur instanceof JSONObject)) return "";
            cur = ((JSONObject)cur).opt(k);
            if (cur == null || cur == JSONObject.NULL) return "";
        }
        String s = String.valueOf(cur).trim();
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    private ArrayList<JSONObject> normalizeClothingEntries(JSONObject clothingData) {
        ArrayList<JSONObject> out = new ArrayList<>();
        if (clothingData == null) return out;
        String[] slots = {"hr","hd","ch","lg","sh","ha","he","fa","cp","ca","cc","ea","mc","pt","wa"};
        for (String slot : slots) {
            JSONObject item = clothingData.optJSONObject(slot);
            if (item == null) continue;
            String code = firstText(item, "code", "classname", "className", "id");
            if (code.isEmpty()) continue;
            try { item.put("_slot", slot); } catch(Exception ignored) {}
            out.add(item);
        }
        if (!out.isEmpty()) return out;
        return extractList(clothingData, null);
    }

    private void addPhotos(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = sectionCard("Fotos do usuário", list.size(), true);
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(row);
        c.addView(hsv, lp(-1, dp(165), 0, 0, 0, 0));
        for (int i=0; i<Math.min(list.size(), 24); i++) {
            JSONObject o = list.get(i);
            String url = getPhotoUrl(o);
            String date = getPhotoTimestamp(o);
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setBackground(round(Color.argb(18,255,255,255), dp(16), Color.argb(24,255,255,255), 1));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(160), dp(160)); bp.rightMargin = dp(12); row.addView(box, bp);
            ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); applyRoundedClip(img, dp(14)); box.addView(img, new LinearLayout.LayoutParams(-1, dp(112)));
            TextView dt = text(date, 12, Color.argb(190,255,255,255), false); dt.setGravity(Gravity.CENTER); box.addView(dt, lp(-1,-2,0,8,0,0));
            if (!url.isEmpty()) { loadImage(img, url); final JSONObject photoObj = o; box.setOnClickListener(v -> showPhotoDialog(photoObj)); }
        }
    }


    private String getPhotoUrl(JSONObject photo) {
        String url = firstText(photo, "previewUrl", "url", "imageUrl", "photoUrl");
        if (url.isEmpty()) url = findImageUrlDeep(photo);
        return normalizeUrl(url);
    }

    private String getPhotoTimestamp(JSONObject photo) {
        String formatted = firstText(photo, "formatted_time", "formattedTime");
        if (!formatted.isEmpty()) return formatted;
        return niceDate(firstText(photo, "creationTime", "time", "createdAt", "date", "timestamp"));
    }

    private void showPhotoDialog(JSONObject photo) {
        String url = getPhotoUrl(photo);
        if (url.isEmpty()) return;
        final Dialog dialog = new Dialog(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        wrap.setBackground(round(Color.rgb(28, 18, 42), dp(22), Color.argb(42,255,255,255), 1));
        dialog.setContentView(wrap);
        Window w = dialog.getWindow();
        if (w != null) { w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); w.setLayout(-1, -2); }
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        wrap.addView(img, lp(-1, dp(260), 0,0,0,12));
        loadImage(img, url);
        String creator = firstText(photo, "creator_name", "creatorName", "userName", "username", "ownerName");
        String room = firstText(photo, "room_name", "roomName", "roomname");
        JSONObject roomObj = photo.optJSONObject("room");
        if (room.isEmpty() && roomObj != null) room = firstText(roomObj, "name", "roomName", "caption");
        String roomOwner = firstText(photo, "roomOwner", "ownerName");
        if (roomOwner.isEmpty() && roomObj != null) {
            JSONObject owner = roomObj.optJSONObject("owner");
            if (owner != null) roomOwner = firstText(owner, "name", "username", "habboName");
        }
        int likes = -1;
        if (photo.has("likes_count")) likes = photo.optInt("likes_count", -1);
        if (likes < 0 && photo.has("likesCount")) likes = photo.optInt("likesCount", -1);
        JSONArray likesArray = photo.optJSONArray("likes");
        if (likes < 0 && likesArray != null) likes = likesArray.length();
        TextView meta = habboText(
            (creator.isEmpty()?"Usuário desconhecido":creator) + "\n" +
            getPhotoTimestamp(photo) +
            (room.isEmpty()?"":"\nQuarto: " + room) +
            (roomOwner.isEmpty()?"":"\nDono: " + roomOwner) +
            "\nCurtidas: " + Math.max(0, likes), 15, true);
        meta.setTextColor(Color.WHITE);
        meta.setLineSpacing(dp(4),1f);
        wrap.addView(meta, lp(-1, -2, 0,0,0,12));
        Button close = new Button(this); close.setText("Fechar"); close.setAllCaps(false); close.setTextColor(Color.WHITE); close.setBackground(grad(dp(14), purple2, purple)); wrap.addView(close, lp(-1, dp(46), 0, 0, 0, 0));
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addFriendsTabs(ArrayList<JSONObject> friendsList, ArrayList<JSONObject> removedList) {
        if (friendsList.isEmpty() && removedList.isEmpty()) return;
        LinearLayout c = sectionCard(null, 0, false);
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setGravity(Gravity.LEFT); c.addView(tabs, lp(-1, dp(58), 0, 0, 0, 14));
        TextView btFriends = tabButton("Amigos (" + friendsList.size() + ")", true);
        TextView btRemoved = tabButton("Removidos", false);
        tabs.addView(btFriends); tabs.addView(btRemoved);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); c.addView(content, lp(-1, -2, 0, 0, 0, 0));
        final boolean[] showingRemoved = {false}; final int[] page = {1};
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            content.removeAllViews();
            btFriends.setBackground(showingRemoved[0] ? tabBg(false) : tabBg(true));
            btFriends.setTextColor(showingRemoved[0] ? Color.argb(150,255,255,255) : Color.WHITE);
            btRemoved.setBackground(showingRemoved[0] ? tabBg(true) : tabBg(false));
            btRemoved.setTextColor(showingRemoved[0] ? Color.WHITE : Color.argb(150,255,255,255));
            ArrayList<JSONObject> data = showingRemoved[0] ? removedList : friendsList;
            renderFriendsPage(content, data, page[0], 10, showingRemoved[0]);
            renderPager(content, data.size(), 10, page, render[0]);
        };
        btFriends.setOnClickListener(v -> { showingRemoved[0] = false; page[0] = 1; render[0].run(); });
        btRemoved.setOnClickListener(v -> { showingRemoved[0] = true; page[0] = 1; render[0].run(); });
        render[0].run();
    }

    private TextView tabButton(String s, boolean active) {
        TextView v = habboText(s, 16, true); v.setTextColor(active ? Color.WHITE : Color.argb(150,255,255,255)); v.setGravity(Gravity.CENTER); v.setPadding(dp(13),0,dp(13),0); v.setBackground(tabBg(active));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(44)); p.rightMargin = dp(8); v.setLayoutParams(p); return v;
    }
    private Drawable tabBg(boolean active) { return active ? grad(dp(13), purple2, Color.rgb(166, 42, 235)) : round(Color.argb(12,255,255,255), dp(13), Color.argb(28,255,255,255), 1); }

    private void renderFriendsPage(LinearLayout content, ArrayList<JSONObject> data, int page, int per, boolean removed) {
        if (data.isEmpty()) { content.addView(centerNote(removed ? "Nenhum amigo removido encontrado." : "Nenhum amigo encontrado.")); return; }
        int start = Math.max(0, (page-1)*per), end = Math.min(data.size(), start+per);
        for (int i=start; i<end; i+=2) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); content.addView(row, lp(-1, -2, 0, 0, 0, 12));
            row.addView(friendCard(data.get(i), removed), new LinearLayout.LayoutParams(0, dp(124), 1));
            if (i+1<end) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(124), 1); p.leftMargin = dp(12); row.addView(friendCard(data.get(i+1), removed), p); }
            else { Space sp = new Space(this); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(124), 1); p.leftMargin=dp(12); row.addView(sp,p); }
        }
    }

    private LinearLayout friendCard(JSONObject f, boolean removed) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER); card.setPadding(dp(8),dp(6),dp(8),dp(8)); card.setBackground(round(Color.argb(20,255,255,255), dp(18), Color.argb(25,255,255,255), 1));
        String n = firstText(f, "name", "username", "habboName"); if (n.isEmpty()) n = "Habbo";
        String fig = firstText(f, "figureString", "figure", "look", "avatarFigureString");
        String date = firstText(f, "creationTime", "friendSince", "createdAt", "date", "removedAt");
        FrameLayout headWrap = new FrameLayout(this); card.addView(headWrap, new LinearLayout.LayoutParams(-1, dp(58)));
        ImageView head = new ImageView(this); head.setScaleType(ImageView.ScaleType.FIT_CENTER); headWrap.addView(head, new FrameLayout.LayoutParams(-1, -1));
        if (!fig.isEmpty()) loadImage(head, avatarHead(fig));
        if (isToday(date) && !removed) {
            TextView novo = text("NOVO", 9, Color.WHITE, true); novo.setGravity(Gravity.CENTER); novo.setBackground(grad(dp(999), Color.rgb(31,184,106), Color.rgb(54,210,127))); FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(dp(48), dp(18), Gravity.TOP|Gravity.CENTER_HORIZONTAL); headWrap.addView(novo,np);
        }
        if (optBoolAny(f, false, "online", "isOnline")) { IconView dot = new IconView(this, "dot"); FrameLayout.LayoutParams dpv = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.RIGHT|Gravity.TOP); dpv.topMargin=dp(8); dpv.rightMargin=dp(8); headWrap.addView(dot, dpv); }
        TextView name = habboText(n, 14, true); name.setGravity(Gravity.CENTER); name.setMaxLines(1); name.setEllipsize(TextUtils.TruncateAt.END); card.addView(name, lp(-1,-2,0,8,0,6));
        TextView d = text(niceDate(date), 12, Color.argb(185,255,255,255), false); d.setGravity(Gravity.CENTER); card.addView(d, lp(-1,-2,0,0,0,0));
        final String fname = n; card.setOnClickListener(v -> { searchInput.setText(fname); search(); });
        return card;
    }

    private void renderPager(LinearLayout content, int total, int per, int[] page, Runnable rerender) {
        int totalPages = Math.max(1, (int)Math.ceil(total/(double)per));
        if (totalPages <= 1) return;
        TextView label = text("Página " + page[0] + " de " + totalPages, 16, Color.WHITE, true); label.setGravity(Gravity.CENTER); content.addView(label, lp(-1,-2,0,6,0,12));
        LinearLayout p = new LinearLayout(this); p.setGravity(Gravity.CENTER); p.setOrientation(LinearLayout.HORIZONTAL); content.addView(p, lp(-1, dp(58), 0, 0, 0, 0));
        TextView prev = pageButton("‹", page[0] > 1); p.addView(prev);
        TextView one = pageButton(String.valueOf(page[0]), true); one.setBackground(grad(dp(14), purple2, purple)); p.addView(one);
        TextView next = pageButton("›", page[0] < totalPages); p.addView(next);
        prev.setOnClickListener(v -> { if (page[0] > 1) { page[0]--; rerender.run(); } });
        next.setOnClickListener(v -> { if (page[0] < totalPages) { page[0]++; rerender.run(); } });
    }

    private TextView pageButton(String s, boolean enabled) { TextView v = text(s, 20, enabled?Color.WHITE:Color.argb(70,255,255,255), true); v.setGravity(Gravity.CENTER); v.setBackground(round(Color.argb(14,255,255,255), dp(14), Color.argb(24,255,255,255), 1)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(44)); p.setMargins(dp(6),0,dp(6),0); v.setLayoutParams(p); return v; }

    private void addRoomsTabs(ArrayList<JSONObject> rooms, ArrayList<JSONObject> oldRooms) {
        if (rooms.isEmpty() && oldRooms.isEmpty()) return;
        LinearLayout c = sectionCard(null, 0, false);
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); c.addView(tabs, lp(-1, dp(58), 0, 0, 0, 14));
        TextView btRooms = tabButton("Quartos (" + rooms.size() + ")", true); TextView btOld = tabButton("Antigos", false); tabs.addView(btRooms); tabs.addView(btOld);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); c.addView(content, lp(-1,-2,0,0,0,0));
        final boolean[] old = {false}; final int[] page = {1}; Runnable[] render = new Runnable[1];
        render[0] = () -> { content.removeAllViews(); btRooms.setBackground(old[0]?tabBg(false):tabBg(true)); btRooms.setTextColor(old[0]?Color.argb(150,255,255,255):Color.WHITE); btOld.setBackground(old[0]?tabBg(true):tabBg(false)); btOld.setTextColor(old[0]?Color.WHITE:Color.argb(150,255,255,255)); ArrayList<JSONObject> data=old[0]?oldRooms:rooms; renderRoomsPage(content,data,page[0],4); renderPager(content,data.size(),4,page,render[0]); };
        btRooms.setOnClickListener(v->{old[0]=false;page[0]=1;render[0].run();}); btOld.setOnClickListener(v->{old[0]=true;page[0]=1;render[0].run();}); render[0].run();
    }

    private void renderRoomsPage(LinearLayout content, ArrayList<JSONObject> list, int page, int per) {
        if (list.isEmpty()) { content.addView(centerNote("Nenhum quarto encontrado.")); return; }
        int start=(page-1)*per, end=Math.min(list.size(), start+per);
        for (int i=start;i<end;i++) content.addView(roomRow(list.get(i)));
    }

    private LinearLayout roomRow(JSONObject room) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(10),dp(12),dp(10)); row.setBackground(round(Color.argb(18,255,255,255), dp(16), Color.argb(24,255,255,255), 1)); row.setLayoutParams(lp(-1, dp(116), 0, 0, 0, 12));
        ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setBackground(round(Color.argb(25,255,255,255), dp(12), Color.argb(20,255,255,255),1)); applyRoundedClip(img, dp(12)); row.addView(img, new LinearLayout.LayoutParams(dp(112), dp(78)));
        String image = normalizeUrl(firstText(room, "thumbnailUrl", "imageUrl", "roomImage", "url")); if (!image.isEmpty()) loadImage(img, image); else img.setImageResource(R.drawable.quarto);
        LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,-2,1); tp.leftMargin=dp(12); row.addView(txt,tp);
        TextView roomName = habboText(firstText(room,"name","roomName","caption","title").isEmpty()?"Quarto":firstText(room,"name","roomName","caption","title"), 16, true); roomName.setMaxLines(1); roomName.setEllipsize(TextUtils.TruncateAt.END); txt.addView(roomName);
        String visitors = firstText(room,"usersNow","users","visitors","userCount"); String score = firstText(room,"score","rating"); String date = niceDate(firstText(room,"createdAt","creationTime","date"));
        txt.addView(text("👥  " + emptyDash(visitors) + "   ☆  " + emptyDash(score) + "   " + date, 13, Color.argb(215,255,255,255), false));
        String desc = firstText(room,"description","desc"); if(!desc.isEmpty()) { TextView rd = habboText(desc, 13, false); rd.setTextColor(Color.argb(210,255,255,255)); rd.setMaxLines(1); rd.setEllipsize(TextUtils.TruncateAt.END); txt.addView(rd); }
        return row;
    }

    private void addGroups(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = sectionCard("Grupos", list.size(), true);
        for (int i=0;i<Math.min(list.size(), 12);i++) c.addView(groupRow(list.get(i)));
    }

    private LinearLayout groupRow(JSONObject g) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(12),dp(12),dp(12)); row.setBackground(round(Color.argb(18,255,255,255), dp(16), Color.argb(24,255,255,255), 1)); row.setLayoutParams(lp(-1, -2, 0, 0, 0, 12));
        ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_INSIDE); row.addView(img, new LinearLayout.LayoutParams(dp(64), dp(64)));
        String badge = firstText(g,"badgeCode","code"); String badgeUrl = normalizeUrl(firstText(g, "badgeUrl", "imageUrl", "url")); if(!badgeUrl.isEmpty()) loadImage(img, badgeUrl); else if(!badge.isEmpty()) loadImage(img,"https://www.habbo.com.br/habbo-imaging/badge/"+enc(badge)+".gif"); else img.setImageDrawable(new PlaceholderDrawable("groups"));
        LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1); tp.leftMargin=dp(12); row.addView(txt,tp);
        TextView groupName = habboText(firstText(g,"name","groupName").isEmpty()?"Grupo":firstText(g,"name","groupName"), 17, true); groupName.setMaxLines(1); groupName.setEllipsize(TextUtils.TruncateAt.END); txt.addView(groupName);
        String desc=firstText(g,"description","desc"); if(!desc.isEmpty()) { TextView gd = habboText(desc, 14, false); gd.setTextColor(Color.argb(220,255,255,255)); gd.setMaxLines(2); gd.setEllipsize(TextUtils.TruncateAt.END); txt.addView(gd); }
        txt.addView(text(niceDate(firstText(g,"createdAt","creationTime","date")), 13, Color.argb(190,255,255,255), false));
        return row;
    }

    private LinearLayout sectionCard(String title, int count, boolean showTitle) {
        LinearLayout c = card(dp(20));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 18));
        if (showTitle && title != null) {
            TextView t = habboText(title + " (" + count + ")", 20, true); c.addView(t, lp(-1, -2, 0, 0, 0, 14));
        }
        return c;
    }

    private TextView centerNote(String msg) { TextView v = text(msg, 14, muted, false); v.setGravity(Gravity.CENTER); v.setLineSpacing(dp(2),1f); v.setPadding(dp(8), dp(12), dp(8), dp(12)); return v; }


    private void bindNickSuggestions() {
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleSuggestions(String.valueOf(s)); }
            public void afterTextChanged(Editable e) {}
        });
    }

    private void scheduleSuggestions(String raw) {
        final String q = raw == null ? "" : raw.trim();
        suggestionRequestId++;
        final int requestId = suggestionRequestId;
        suggestionsBox.removeAllViews();
        suggestionsBox.setVisibility(View.GONE);
        if (q.length() < 2) return;
        uiHandler.postDelayed(() -> {
            if (requestId != suggestionRequestId) return;
            executor.execute(() -> {
                ArrayList<JSONObject> suggestions = fetchPreviousNickSuggestions(q);
                runOnUiThread(() -> {
                    if (requestId == suggestionRequestId) renderLiveSuggestions(q, suggestions);
                });
            });
        }, 320);
    }

    private void renderLiveSuggestions(String query, ArrayList<JSONObject> list) {
        suggestionsBox.removeAllViews();
        if (list == null || list.isEmpty()) { suggestionsBox.setVisibility(View.GONE); return; }
        suggestionsBox.setVisibility(View.VISIBLE);
        TextView title = text("Esse nick parece ter sido usado antes por:", 12, Color.argb(210,255,255,255), true);
        suggestionsBox.addView(title, lp(-1, -2, 2, 2, 2, 6));
        for (int i=0; i<Math.min(list.size(), 3); i++) suggestionsBox.addView(suggestionRow(query, list.get(i), true));
    }

    private ArrayList<JSONObject> fetchPreviousNickSuggestions(String query) {
        try {
            JSONObject payload = unwrap(getJson(API + "?endpoint=habbos-suggest&name=" + enc(query) + "&includePreviousNames=true&hotel=br"));
            return filterPreviousNickSuggestions(payload, query);
        } catch(Exception e) { return new ArrayList<>(); }
    }

    private ArrayList<JSONObject> filterPreviousNickSuggestions(JSONObject suggest, String query) {
        ArrayList<JSONObject> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2 || suggest == null) return out;
        ArrayList<JSONObject> users = extractList(suggest, null);
        for (JSONObject user : users) {
            String current = firstText(user, "name", "username", "habboName");
            if (current.isEmpty() || current.trim().toLowerCase(Locale.ROOT).equals(q)) continue;
            if (matchesPreviousNick(user, q)) out.add(user);
            if (out.size() >= 6) break;
        }
        return out;
    }

    private boolean matchesPreviousNick(JSONObject user, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) return false;
        for (JSONObject prev : extractList(user, "previousNames")) {
            String old = firstText(prev, "name", "oldName", "username").trim().toLowerCase(Locale.ROOT);
            if (!old.isEmpty() && (old.equals(q) || old.startsWith(q) || old.contains(q))) return true;
        }
        return false;
    }

    private JSONObject getExactPreviousNameMatch(JSONObject user, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (JSONObject prev : extractList(user, "previousNames")) {
            String old = firstText(prev, "name", "oldName", "username");
            if (!old.isEmpty() && old.trim().toLowerCase(Locale.ROOT).equals(q)) return prev;
        }
        return null;
    }

    private LinearLayout suggestionRow(String query, JSONObject user, boolean compact) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(round(Color.argb(24,255,255,255), dp(14), Color.argb(30,255,255,255), 1));
        row.setLayoutParams(lp(-1, compact ? dp(68) : dp(82), 0, 0, 0, 8));
        ImageView head = new ImageView(this); head.setScaleType(ImageView.ScaleType.FIT_CENTER); row.addView(head, new LinearLayout.LayoutParams(dp(compact?50:58), dp(compact?54:62)));
        String name = firstText(user, "name", "username", "habboName");
        String fig = firstText(user, "figureString", "figure", "look");
        if (!fig.isEmpty()) loadImage(head, avatarHead(fig));
        JSONObject previous = getExactPreviousNameMatch(user, query);
        String oldName = previous == null ? query : firstText(previous, "name", "oldName", "username");
        String changed = previous == null ? "" : niceDate(firstText(previous, "changedAt", "date", "timestamp", "createdAt"));
        LinearLayout texts = new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.leftMargin = dp(10); row.addView(texts, tp);
        TextView nm = habboText(name, compact ? 15 : 17, true); nm.setMaxLines(1); nm.setEllipsize(TextUtils.TruncateAt.END); texts.addView(nm);
        TextView old = text("Nick antigo: " + oldName, compact ? 12 : 13, Color.argb(210,255,255,255), false); old.setMaxLines(1); old.setEllipsize(TextUtils.TruncateAt.END); texts.addView(old);
        if (!changed.isEmpty() && !"—".equals(changed)) texts.addView(text("Alterado em: " + changed, compact ? 11 : 12, muted, false));
        TextView arrow = text("›", compact ? 24 : 28, Color.WHITE, true); row.addView(arrow, new LinearLayout.LayoutParams(dp(26), -1));
        row.setOnClickListener(v -> { suggestionsBox.setVisibility(View.GONE); searchInput.setText(name); searchInput.setSelection(searchInput.getText().length()); search(); });
        return row;
    }

    private void showNotFoundState(String nick, ArrayList<JSONObject> suggestions) {
        resultWrap.removeAllViews();
        LinearLayout c = sectionCard(null, 0, false);
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView title = habboText("Nenhum perfil encontrado", 22, true); title.setGravity(Gravity.CENTER); c.addView(title, lp(-1,-2,0,0,0,8));
        TextView body = text("Não encontrei uma conta atual com o nick " + nick + " no Habbo BR.", 14, muted, false); body.setGravity(Gravity.CENTER); body.setLineSpacing(dp(2),1f); c.addView(body, lp(-1,-2,0,0,0,14));
        if (suggestions != null && !suggestions.isEmpty()) {
            TextView st = habboText("Esse nick parece ter sido usado antes por:", 17, true); c.addView(st, lp(-1,-2,0,0,0,10));
            for (JSONObject user : suggestions) c.addView(suggestionRow(nick, user, false));
        } else {
            c.addView(centerNote("Também não encontrei sugestões de contas atuais que já usaram esse nick."));
        }
    }

    private void tintScrollBar(View v) {
        if (Build.VERSION.SDK_INT >= 29) {
            v.setVerticalScrollbarThumbDrawable(round(purple, dp(999), purple, 0));
            v.setVerticalScrollbarTrackDrawable(round(Color.argb(20,255,255,255), dp(999), Color.argb(20,255,255,255), 0));
        }
    }

    private void applyRoundedClip(View v, int radius) {
        if (Build.VERSION.SDK_INT >= 21) {
            v.setClipToOutline(true);
            v.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
        }
    }

    private void showError(String msg) { resultWrap.removeAllViews(); LinearLayout c = sectionCard("Erro", 0, false); TextView t = text(msg, 15, Color.WHITE, true); t.setGravity(Gravity.CENTER); c.addView(t); }
    private void setLoading(boolean loading, String message) {
        searchBtn.setEnabled(!loading);
        searchBtn.setText(loading ? "Pesquisando perfil..." : "Pesquisar");
        progress.setVisibility(View.GONE);
        statusText.setText(loading ? "" : (message == null ? "" : message));
        if (loading) showLoadingSkeleton(message == null ? "Buscando perfil..." : message);
    }

    private void showInlineLoading(String message) {
        statusText.setText(message == null ? "" : message);
        if (resultWrap == null) return;
        LinearLayout c = card(dp(18));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        row.addView(pb, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView tv = text(message == null ? "Carregando..." : message, 13, Color.argb(220,255,255,255), true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.leftMargin = dp(12);
        row.addView(tv, tp);
        c.addView(row);
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 18));
    }

    private void showLoadingSkeleton(String message) {
        resultWrap.removeAllViews();
        LinearLayout c = card(dp(22));
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 18));
        TextView title = habboText(message, 18, true); title.setGravity(Gravity.CENTER); c.addView(title, lp(-1,-2,0,0,0,10));
        ProgressBar bigLoader = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge); c.addView(bigLoader, lp(-1, dp(44), 0, 0, 0, 14));
        FrameLayout avatar = new FrameLayout(this); avatar.setBackground(round(Color.argb(22,255,255,255), dp(18), Color.argb(24,255,255,255), 1)); c.addView(avatar, lp(-1, dp(180), 0,0,0,14));
        ImageView walker = new ImageView(this); walker.setScaleType(ImageView.ScaleType.FIT_CENTER); avatar.addView(walker, new FrameLayout.LayoutParams(-1, -1));
        String nick = searchInput == null ? "" : searchInput.getText().toString().trim();
        if (!nick.isEmpty()) loadImage(walker, "https://www.habbo.com.br/habbo-imaging/avatarimage?user=" + enc(nick) + "&action=wlk&direction=2&head_direction=2&img_format=png&headonly=0&size=b");
        for (int i=0;i<4;i++) { View line = new View(this); line.setBackground(round(Color.argb(26,255,255,255), dp(999), Color.argb(16,255,255,255), 1)); c.addView(line, lp(-1, dp(i==0?30:18), i==0?50:18, 0, i==0?50:18, 12)); }
    }

    private Object getJsonAny(String u) throws Exception { HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(24000); c.setRequestProperty("Accept", "application/json, text/plain, */*"); c.setRequestProperty("User-Agent", "ToxicHabboApp/2.0 Android"); int code = c.getResponseCode(); InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(); String body = readAll(is); if (code < 200 || code >= 300 || body == null || body.trim().isEmpty()) throw new IOException("HTTP " + code); String clean = body.trim(); return clean.startsWith("[") ? new JSONArray(clean) : new JSONObject(clean); }
    private JSONObject getJson(String u) throws Exception { Object any = getJsonAny(u); if (any instanceof JSONObject) return (JSONObject)any; JSONObject wrap = new JSONObject(); wrap.put("data", any); return wrap; }
    private JSONObject tryJson(String u) { try { return getJson(u); } catch (Exception e) { return null; } }
    private String readAll(InputStream is) throws IOException { if (is == null) return ""; ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n; while ((n = is.read(buf)) > 0) out.write(buf,0,n); return out.toString("UTF-8"); }
    private void loadImage(ImageView view, String url) { if (view == null || url == null || url.trim().isEmpty()) return; String clean = normalizeUrl(url); runOnUiThread(() -> Glide.with(MainActivity.this).load(clean).into(view)); }


    private JSONObject validProfileObject(JSONObject obj) {
        if (obj == null) return null;
        if (obj.has("ok") && !obj.optBoolean("ok", true) && !obj.has("data")) return null;
        if (!firstText(obj, "uniqueId", "id", "habboId", "name", "username", "habboName", "figureString", "figure").isEmpty()) return obj;
        JSONObject d = obj.optJSONObject("data");
        if (d != null && !firstText(d, "uniqueId", "id", "habboId", "name", "username", "habboName", "figureString", "figure").isEmpty()) return d;
        return null;
    }

    private JSONObject unwrap(JSONObject obj) { if (obj == null) return null; if (obj.has("ok") && obj.has("data")) { Object data = obj.opt("data"); return data instanceof JSONObject ? (JSONObject)data : obj; } return obj; }
    private JSONObject firstObject(JSONObject... objects) { for (JSONObject o : objects) if (o != null && o.length() > 0) return o; return null; }
    private JSONObject firstFromList(JSONObject obj) { ArrayList<JSONObject> list = extractList(obj, null); return list.isEmpty() ? null : list.get(0); }
    private ArrayList<JSONObject> extractPreviousNamesFromSuggest(JSONObject suggest, String currentName) { ArrayList<JSONObject> out = new ArrayList<>(); ArrayList<JSONObject> users = extractList(suggest, null); String low = currentName == null ? "" : currentName.toLowerCase(Locale.ROOT); for (JSONObject user : users) { String uname = firstText(user, "name", "username").toLowerCase(Locale.ROOT); if (!low.isEmpty() && !uname.equals(low)) continue; out.addAll(extractList(user, "previousNames")); } return out; }
    private ArrayList<JSONObject> extractListFromKeys(JSONObject obj, String... keys) { ArrayList<JSONObject> out = new ArrayList<>(); if (obj == null) return out; for (String k : keys) out = mergeLists(out, extractList(obj, k)); return out; }
    private ArrayList<JSONObject> extractList(JSONObject data, String primaryKey) { ArrayList<JSONObject> out = new ArrayList<>(); if (data == null) return out; JSONArray arr = null; if (primaryKey != null && !primaryKey.isEmpty()) arr = data.optJSONArray(primaryKey); if (arr == null) arr = data.optJSONArray("result"); if (arr == null) arr = data.optJSONArray("results"); if (arr == null) arr = data.optJSONArray("data"); if (arr == null) arr = data.optJSONArray("items"); JSONObject d = data.optJSONObject("data"); if (arr == null && d != null) { if (primaryKey != null && !primaryKey.isEmpty()) arr = d.optJSONArray(primaryKey); if (arr == null) arr = d.optJSONArray("result"); if (arr == null) arr = d.optJSONArray("results"); if (arr == null) arr = d.optJSONArray("items"); } if (arr != null) for (int i=0; i<arr.length(); i++) { JSONObject o = arr.optJSONObject(i); if (o != null) out.add(o); } return out; }
    private ArrayList<JSONObject> mergeLists(ArrayList<JSONObject> a, ArrayList<JSONObject> b) { ArrayList<JSONObject> out = new ArrayList<>(); HashSet<String> seen = new HashSet<>(); if (a != null) addUnique(out, seen, a); if (b != null) addUnique(out, seen, b); return out; }
    private void addUnique(ArrayList<JSONObject> out, HashSet<String> seen, ArrayList<JSONObject> src) { for (JSONObject o : src) { String key = firstText(o, "uniqueId", "id", "name", "username", "habboName", "figureString", "motto", "code", "badgeCode"); if (key.isEmpty()) key = String.valueOf(o.toString().hashCode()); if (seen.add(key)) out.add(o); } }
    private String firstText(JSONObject o, String... keys) { if (o == null) return ""; for (String k : keys) { Object v = o.opt(k); if (v == null || v == JSONObject.NULL) continue; String s = String.valueOf(v).trim(); if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s; } return ""; }
    private boolean optBoolTrue(JSONObject o, String... keys) { if (o == null) return false; for (String k : keys) { if (!o.has(k)) continue; Object v = o.opt(k); if (v instanceof Boolean) return ((Boolean)v); String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT); if (s.equals("true") || s.equals("1") || s.equals("yes")) return true; } return false; }

    private boolean optBoolAny(JSONObject o, boolean fallback, String... keys) { if (o == null) return fallback; for (String k : keys) if (o.has(k)) return o.optBoolean(k, fallback); return fallback; }

    private String avatarFull(String figure) { return avatarFull(figure, 2); }
    private String avatarFull(String figure, int direction) { return "https://www.habbo.com.br/habbo-imaging/avatarimage?figure=" + enc(figure) + "&size=l&direction=" + direction + "&head_direction=" + direction + "&gesture=std&action=std&headonly=0"; }
    private String avatarSmall(String figure) { return "https://www.habbo.com.br/habbo-imaging/avatarimage?figure=" + enc(figure) + "&size=m&direction=2&head_direction=2&gesture=sml&action=std&headonly=0"; }
    private String avatarHead(String figure) { return "https://www.habbo.com.br/habbo-imaging/avatarimage?figure=" + enc(figure) + "&size=m&direction=2&head_direction=2&headonly=1"; }

    private Drawable makeBg() { return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(30, 11, 45), Color.rgb(24,14,35), Color.rgb(12,12,18)}); }
    private LinearLayout card(int radius) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackground(round(cardFill, radius, cardStroke, 1)); return l; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s == null ? "" : s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView habboText(String s, int sp, boolean bold) { TextView v = text(s, sp, Color.WHITE, bold); v.setTypeface(habboFont); return v; }
    private TextView pill(String s, int color) { TextView v = text(s, 13, Color.WHITE, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(14), dp(9), dp(14), dp(9)); v.setBackground(round(adjustAlpha(color, 0.32f), dp(999), adjustAlpha(color,0.55f), 1)); return v; }
    private GradientDrawable round(int fill, int radius, int stroke, int sw) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(radius); if (sw > 0) d.setStroke(dp(sw), stroke); return d; }
    private GradientDrawable grad(int radius, int c1, int c2) { GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1,c2}); d.setCornerRadius(radius); return d; }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private int adjustAlpha(int color, float f) { return Color.argb(Math.round(Color.alpha(color)*f), Color.red(color), Color.green(color), Color.blue(color)); }
    private String enc(String s) { try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch(Exception e){ return s; } }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard(){ try{ ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(searchInput.getWindowToken(),0);}catch(Exception ignored){} }
    private void openUrl(String url){ try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch(Exception ignored){} }
    private String normalizeUrl(String url) { String s = url == null ? "" : url.trim(); if (s.startsWith("//")) return "https:" + s; if (s.startsWith("/")) return "https://atoxic.com.br" + s; return s; }

    private String emptyDash(String s) { return s == null || s.trim().isEmpty() ? "—" : s.trim(); }

    private String niceDate(String in) {
        if (in == null || in.trim().isEmpty()) return "—";
        String s = in.trim();
        try {
            if (s.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                SimpleDateFormat only = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date d0 = only.parse(s);
                return new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR")).format(d0);
            }
            Date d;
            if (s.matches("^\\d{10,13}$")) {
                long ts = Long.parseLong(s); if (s.length() == 10) ts *= 1000; d = new Date(ts);
            } else {
                String iso = s.replace("Z", "+0000").replaceAll("([+-]\\d{2}):(\\d{2})$", "$1$2");
                String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss"};
                Date parsed = null;
                for (String p: patterns) {
                    try {
                        SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                        if (p.endsWith("Z")) f.setTimeZone(TimeZone.getTimeZone("UTC"));
                        else f.setTimeZone(TimeZone.getTimeZone("UTC"));
                        parsed = f.parse(iso); break;
                    } catch(Exception ignored){}
                }
                if (parsed == null) return s.replace('T',' ').replace("Z", "");
                d = parsed;
            }
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy, HH:mm", new Locale("pt", "BR"));
            out.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
            return out.format(d);
        } catch(Exception e) { return s.replace('T',' ').replace("Z", ""); }
    }

    private boolean isToday(String in) { if (in == null || in.trim().isEmpty()) return false; String d = niceDate(in); String today = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt","BR")).format(new Date()); return d.startsWith(today); }


    private String findImageUrlDeep(Object obj) {
        HashSet<Object> seen = new HashSet<>();
        return findImageUrlDeep(obj, seen);
    }

    private String findImageUrlDeep(Object obj, HashSet<Object> seen) {
        if (obj == null || obj == JSONObject.NULL || seen.contains(obj)) return "";
        seen.add(obj);
        if (obj instanceof String) {
            String s = ((String)obj).trim();
            if (s.startsWith("http") && (s.matches("(?i).*\\.(png|jpg|jpeg|gif|webp)(\\?.*)?$") || s.contains("habbo") || s.contains("habbodex"))) return s;
            return "";
        }
        if (obj instanceof JSONObject) {
            JSONObject jo = (JSONObject)obj;
            String[] priority = {"url","previewUrl","imageUrl","photoUrl","largeUrl","smallUrl","thumbnailUrl","thumbnail","image","photo","roomImage","badgeUrl"};
            for (String k : priority) {
                Object v = jo.opt(k);
                String found = findImageUrlDeep(v, seen);
                if (!found.isEmpty()) return found;
            }
            Iterator<String> it = jo.keys();
            while (it.hasNext()) {
                String found = findImageUrlDeep(jo.opt(it.next()), seen);
                if (!found.isEmpty()) return found;
            }
        }
        if (obj instanceof JSONArray) {
            JSONArray a = (JSONArray)obj;
            for (int i=0;i<a.length();i++) {
                String found = findImageUrlDeep(a.opt(i), seen);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private static class ProfileNotFoundException extends Exception {
        final String nick; final ArrayList<JSONObject> suggestions;
        ProfileNotFoundException(String nick, ArrayList<JSONObject> suggestions) { super("Perfil não encontrado."); this.nick = nick; this.suggestions = suggestions == null ? new ArrayList<>() : suggestions; }
    }

    private static class ProfileResult {
        String searchedNick = "", uniqueId = "", name = "", motto = "", figure = "", memberSince = "", lastAccess = "", level = "", starGems = "";
        boolean online = false, privateProfile = false, banned = false;
        JSONObject habboPublic, dex, suggest, dexProfile, officialProfile;
        ArrayList<JSONObject> previousNames = new ArrayList<>(), previousMottos = new ArrayList<>(), previousStyles = new ArrayList<>(), photos = new ArrayList<>(), friends = new ArrayList<>(), oldFriends = new ArrayList<>(), rooms = new ArrayList<>(), oldRooms = new ArrayList<>(), groups = new ArrayList<>(), selectedBadges = new ArrayList<>();
    }


    public class ShirtDrawable extends Drawable {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); float w=b.width(), h=b.height(), x=b.left, y=b.top;
            p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(30,255,255,255));
            c.drawRoundRect(new RectF(x,y,x+w,y+h), w/2, h/2, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(Color.argb(70,255,255,255)); c.drawRoundRect(new RectF(x+1,y+1,x+w-1,y+h-1), w/2, h/2, p);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(235, 64, 72));
            Path shirt = new Path();
            shirt.moveTo(x+w*.32f,y+h*.28f); shirt.lineTo(x+w*.22f,y+h*.36f); shirt.lineTo(x+w*.16f,y+h*.55f); shirt.lineTo(x+w*.30f,y+h*.60f); shirt.lineTo(x+w*.30f,y+h*.78f); shirt.lineTo(x+w*.70f,y+h*.78f); shirt.lineTo(x+w*.70f,y+h*.60f); shirt.lineTo(x+w*.84f,y+h*.55f); shirt.lineTo(x+w*.78f,y+h*.36f); shirt.lineTo(x+w*.68f,y+h*.28f); shirt.close();
            c.drawPath(shirt,p);
        }
        @Override public void setAlpha(int a){p.setAlpha(a);} @Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    public class PlaceholderDrawable extends Drawable {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); String type;
        PlaceholderDrawable(String t){type=t;}
        @Override public void draw(Canvas c){ Rect b=getBounds(); p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(32,255,255,255)); c.drawRoundRect(new RectF(b), dp(12), dp(12), p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(Color.argb(190,255,255,255)); float cx=b.centerX(), cy=b.centerY(); if("groups".equals(type)){ c.drawCircle(cx,cy,Math.min(b.width(),b.height())*.25f,p); c.drawCircle(cx,cy,Math.min(b.width(),b.height())*.12f,p);} else { Path path=new Path(); path.moveTo(cx,b.top+dp(12)); path.lineTo(b.right-dp(12),cy-dp(4)); path.lineTo(b.right-dp(12),cy+dp(18)); path.lineTo(cx,b.bottom-dp(10)); path.lineTo(b.left+dp(12),cy+dp(18)); path.lineTo(b.left+dp(12),cy-dp(4)); path.close(); c.drawPath(path,p);} }
        @Override public void setAlpha(int a){p.setAlpha(a);} @Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    public class IconView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); String type;
        public IconView(Context c, String t) { super(c); type = t; }
        @Override protected void onDraw(Canvas c) { super.onDraw(c); float w=getWidth(), h=getHeight(), cx=w/2, cy=h/2; p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.WHITE);
            if ("dot".equals(type)) { p.setStyle(Paint.Style.FILL); p.setColor(green); c.drawCircle(cx,cy,Math.min(w,h)/3,p); p.setStyle(Paint.Style.STROKE); p.setColor(Color.WHITE); p.setStrokeWidth(dp(2)); c.drawCircle(cx,cy,Math.min(w,h)/3,p); return; }
            if ("lock".equals(type)) { float r=Math.min(w,h); RectF body = new RectF(cx-r*.28f, cy-r*.02f, cx+r*.28f, cy+r*.32f); c.drawRoundRect(body, r*.08f, r*.08f, p); c.drawArc(new RectF(cx-r*.22f, cy-r*.38f, cx+r*.22f, cy+r*.12f), 200, 140, false, p); return; }
            if ("status".equals(type)) { p.setColor(Color.rgb(255,120,135)); c.drawCircle(cx,cy,dp(17),p); p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(240,40,54)); c.drawCircle(cx,cy,dp(9),p); return; }
            if ("clock".equals(type)) { c.drawCircle(cx,cy,dp(16),p); c.drawLine(cx,cy,cx,cy-dp(9),p); c.drawLine(cx,cy,cx+dp(8),cy+dp(5),p); return; }
            if ("calendar".equals(type)) { RectF r=new RectF(dp(8),dp(12),w-dp(8),h-dp(8)); c.drawRoundRect(r,dp(4),dp(4),p); c.drawLine(dp(8),dp(22),w-dp(8),dp(22),p); return; }
            if ("friends".equals(type)) { c.drawCircle(cx-dp(8),cy-dp(4),dp(5),p); c.drawCircle(cx+dp(8),cy-dp(4),dp(5),p); c.drawArc(new RectF(cx-dp(20),cy, cx, cy+dp(20)),200,140,false,p); c.drawArc(new RectF(cx,cy, cx+dp(20), cy+dp(20)),200,140,false,p); return; }
            if ("rooms".equals(type)) { Path path=new Path(); path.moveTo(cx,dp(8)); path.lineTo(w-dp(10),cy-dp(5)); path.lineTo(w-dp(10),cy+dp(15)); path.lineTo(cx,h-dp(8)); path.lineTo(dp(10),cy+dp(15)); path.lineTo(dp(10),cy-dp(5)); path.close(); c.drawPath(path,p); return; }
            if ("groups".equals(type)) { c.drawCircle(cx,cy,dp(17),p); c.drawCircle(cx,cy,dp(8),p); return; }
            if ("photos".equals(type)) { RectF r=new RectF(dp(8),dp(11),w-dp(8),h-dp(11)); c.drawRoundRect(r,dp(4),dp(4),p); c.drawCircle(dp(18),dp(21),dp(3),p); c.drawLine(dp(12),h-dp(15),dp(24),h-dp(25),p); c.drawLine(dp(24),h-dp(25),w-dp(10),h-dp(15),p); return; }
            if ("star".equals(type)) { Path path=new Path(); for(int i=0;i<10;i++){ double a=-Math.PI/2+i*Math.PI/5; float rr=(i%2==0)?dp(18):dp(8); float x=cx+(float)Math.cos(a)*rr, y=cy+(float)Math.sin(a)*rr; if(i==0) path.moveTo(x,y); else path.lineTo(x,y);} path.close(); c.drawPath(path,p); return; }
            if ("level".equals(type)) { p.setStyle(Paint.Style.FILL); Path path=new Path(); path.moveTo(cx,dp(8)); path.lineTo(w-dp(10),cy); path.lineTo(cx+dp(8),cy); path.lineTo(cx+dp(8),h-dp(10)); path.lineTo(cx-dp(8),h-dp(10)); path.lineTo(cx-dp(8),cy); path.lineTo(dp(10),cy); path.close(); c.drawPath(path,p); }
        }
    }
}
