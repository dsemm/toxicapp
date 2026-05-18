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
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private LinearLayout root, resultWrap;
    private EditText searchInput;
    private Button searchBtn;
    private TextView statusText;
    private final int purple = Color.rgb(139, 52, 217);
    private final int purple2 = Color.rgb(106, 51, 143);
    private final int pink = Color.rgb(255, 79, 131);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(13,13,18));
        getWindow().setNavigationBarColor(Color.rgb(10,10,15));
        buildUi();
    }

    private void buildUi() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackground(makeBg());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll);

        TextView app = text("Toxic Habbo", 30, Color.WHITE, true);
        app.setGravity(Gravity.CENTER);
        app.setLetterSpacing(0.02f);
        root.addView(app, lp(-1, -2, 0, 0, 0, 6));

        TextView sub = text("Busque perfis do Habbo Hotel com visual inspirado na versão mobile do Toxic.", 14, argb(178,255,255,255), false);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(2), 1.0f);
        root.addView(sub, lp(-1, -2, 0, 0, 0, 18));

        LinearLayout card = card(dp(22));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.addView(card, lp(-1, -2, 0, 0, 0, 18));

        TextView title = text("Buscar Habbo", 22, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, lp(-1, -2, 0, 0, 0, 14));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.HORIZONTAL);
        form.setGravity(Gravity.CENTER_VERTICAL);
        form.setBaselineAligned(false);
        card.addView(form, lp(-1, dp(52), 0, 0, 0, 0));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Digite um nick");
        searchInput.setHintTextColor(argb(150,255,255,255));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(15);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        searchInput.setBackground(round(argb(14,255,255,255), dp(14), argb(26,255,255,255), 1));
        form.addView(searchInput, new LinearLayout.LayoutParams(0, -1, 1));

        searchBtn = new Button(this);
        searchBtn.setText("Buscar");
        searchBtn.setTextColor(Color.WHITE);
        searchBtn.setTextSize(13);
        searchBtn.setAllCaps(false);
        searchBtn.setTypeface(Typeface.DEFAULT_BOLD);
        searchBtn.setBackground(grad(dp(14), purple2, purple));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(96), -1);
        blp.leftMargin = dp(10);
        form.addView(searchBtn, blp);

        statusText = text("", 14, argb(210,255,255,255), false);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, lp(-1, -2, 0, 0, 0, 12));

        resultWrap = new LinearLayout(this);
        resultWrap.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultWrap, lp(-1, -2, 0, 0, 0, 0));

        searchBtn.setOnClickListener(v -> search());
        searchInput.setOnEditorActionListener((v, actionId, event) -> { search(); return true; });
    }

    private void search() {
        String nick = searchInput.getText().toString().trim();
        if (nick.isEmpty()) { toast("Digite um nick do Habbo."); return; }
        hideKeyboard();
        setLoading(true, "Buscando perfil de " + nick + "...");
        resultWrap.removeAllViews();
        executor.execute(() -> {
            try {
                JSONObject user = getJson("https://www.habbo.com.br/api/public/users?name=" + enc(nick));
                if (user == null || !user.has("uniqueId")) throw new Exception("Perfil não encontrado.");
                String id = user.optString("uniqueId", "");
                JSONObject profile = null;
                if (!id.isEmpty()) profile = getJson("https://www.habbo.com.br/api/public/users/" + enc(id) + "/profile");
                JSONObject photos = null;
                if (!id.isEmpty()) photos = getJson("https://www.habbo.com.br/extradata/public/users/" + enc(id) + "/photos");
                JSONObject finalProfile = profile;
                JSONObject finalPhotos = photos;
                runOnUiThread(() -> showProfile(user, finalProfile, finalPhotos));
            } catch (Exception e) {
                runOnUiThread(() -> { setLoading(false, ""); showError(e.getMessage()); });
            }
        });
    }

    private void showProfile(JSONObject user, JSONObject profile, JSONObject photos) {
        setLoading(false, "");
        resultWrap.removeAllViews();

        JSONObject u = user;
        if (profile != null && profile.optJSONObject("user") != null) u = profile.optJSONObject("user");
        String name = u.optString("name", searchInput.getText().toString().trim());
        String motto = u.optString("motto", "Sem missão definida.");
        String figure = u.optString("figureString", "hd-180-1");
        boolean visible = u.optBoolean("profileVisible", true);
        String memberSince = humanDate(u.optString("memberSince", ""));
        boolean online = u.optBoolean("online", false);

        LinearLayout profileCard = card(dp(22));
        profileCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultWrap.addView(profileCard, lp(-1, -2, 0, 0, 0, 14));

        ImageView avatar = new ImageView(this);
        avatar.setAdjustViewBounds(true);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatar.setBackground(round(argb(18,255,255,255), dp(18), argb(16,255,255,255), 1));
        avatar.setPadding(dp(12), dp(12), dp(12), dp(12));
        profileCard.addView(avatar, lp(-1, dp(260), 0, 0, 0, 14));
        loadImage(avatar, "https://www.habbo.com.br/habbo-imaging/avatarimage?figure=" + enc(figure) + "&size=l&direction=2&head_direction=2&gesture=sml&action=std&headonly=0");

        LinearLayout headline = new LinearLayout(this);
        headline.setGravity(Gravity.CENTER);
        headline.setOrientation(LinearLayout.HORIZONTAL);
        profileCard.addView(headline, lp(-1, -2, 0, 0, 0, 6));

        TextView nameText = text(name, 30, Color.WHITE, true);
        nameText.setGravity(Gravity.CENTER);
        headline.addView(nameText, new LinearLayout.LayoutParams(-2, -2));

        TextView onlinePill = pill(online ? "online" : "offline", online ? Color.rgb(73,230,160) : Color.rgb(255,92,92));
        LinearLayout.LayoutParams onlp = new LinearLayout.LayoutParams(-2, -2); onlp.leftMargin = dp(8);
        headline.addView(onlinePill, onlp);

        TextView mottoText = text(motto, 15, argb(215,255,255,255), false);
        mottoText.setGravity(Gravity.CENTER);
        mottoText.setLineSpacing(dp(2), 1f);
        profileCard.addView(mottoText, lp(-1, -2, 0, 0, 0, 12));

        LinearLayout pills = new LinearLayout(this);
        pills.setGravity(Gravity.CENTER);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        profileCard.addView(pills, lp(-1, -2, 0, 0, 0, 14));
        pills.addView(pill(visible ? "Perfil público" : "Perfil privado", visible ? purple : Color.rgb(255,70,70)));
        TextView link = pill("Abrir perfil", Color.rgb(47,124,255));
        LinearLayout.LayoutParams lpa = new LinearLayout.LayoutParams(-2, -2); lpa.leftMargin = dp(8); pills.addView(link, lpa);
        link.setOnClickListener(v -> openUrl("https://www.habbo.com.br/profile/" + Uri.encode(name)));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.VERTICAL);
        profileCard.addView(stats, lp(-1, -2, 0, 0, 0, 0));
        stats.addView(infoRow("Criado em", memberSince.isEmpty() ? "Não informado" : memberSince));
        stats.addView(infoRow("Figure", figure));
        if (!visible) stats.addView(infoRow("Aviso", "Algumas informações podem estar ocultas porque o perfil é privado."));

        addSection("Emblemas", profile, "badges", "code", "name", 8);
        addSection("Amigos", profile, "friends", "name", "motto", 12);
        addSection("Grupos", profile, "groups", "name", "description", 8);
        addRooms(profile);
        addPhotos(photos);
    }

    private void addSection(String title, JSONObject profile, String arrayName, String mainKey, String subKey, int limit) {
        if (profile == null) return;
        JSONArray arr = profile.optJSONArray(arrayName);
        if (arr == null || arr.length() == 0) return;
        LinearLayout c = card(dp(18)); c.setPadding(dp(14), dp(14), dp(14), dp(14));
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 14));
        TextView t = text(title, 20, Color.WHITE, true); c.addView(t, lp(-1, -2, 0,0,0,10));
        for (int i=0; i<Math.min(arr.length(), limit); i++) {
            JSONObject o = arr.optJSONObject(i); if (o == null) continue;
            c.addView(smallItem(o.optString(mainKey, "—"), o.optString(subKey, "")));
        }
        if (arr.length() > limit) c.addView(smallItem("+" + (arr.length()-limit) + " itens", "Abra o site para ver a lista completa nesta versão inicial."));
    }

    private void addRooms(JSONObject profile) {
        if (profile == null) return;
        JSONArray arr = profile.optJSONArray("rooms");
        if (arr == null || arr.length() == 0) return;
        LinearLayout c = card(dp(18)); c.setPadding(dp(14), dp(14), dp(14), dp(14));
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 14));
        c.addView(text("Quartos", 20, Color.WHITE, true), lp(-1,-2,0,0,0,10));
        for (int i=0; i<Math.min(arr.length(), 8); i++) {
            JSONObject o = arr.optJSONObject(i); if (o == null) continue;
            c.addView(smallItem(o.optString("name", "Quarto"), "ID " + o.optString("id", "—")));
        }
    }

    private void addPhotos(JSONObject photos) {
        if (photos == null) return;
        JSONArray arr = photos.optJSONArray("photos");
        if (arr == null && photos.optJSONArray("items") != null) arr = photos.optJSONArray("items");
        if (arr == null || arr.length() == 0) return;
        LinearLayout c = card(dp(18)); c.setPadding(dp(14), dp(14), dp(14), dp(14));
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 14));
        c.addView(text("Fotos recentes", 20, Color.WHITE, true), lp(-1,-2,0,0,0,10));
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(row);
        c.addView(hsv, lp(-1, dp(150), 0,0,0,0));
        for (int i=0; i<Math.min(arr.length(), 8); i++) {
            JSONObject o = arr.optJSONObject(i); if (o == null) continue;
            String url = o.optString("url", o.optString("previewUrl", ""));
            ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setBackground(round(argb(20,255,255,255), dp(14), argb(20,255,255,255),1));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(140), dp(130)); p.rightMargin=dp(10); row.addView(img,p);
            if (!url.isEmpty()) loadImage(img, url);
        }
    }

    private TextView smallItem(String main, String sub) {
        TextView v = text(main + (sub == null || sub.isEmpty() ? "" : "\n" + sub), 14, Color.WHITE, false);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setLineSpacing(dp(2), 1f);
        v.setBackground(round(argb(10,255,255,255), dp(14), argb(18,255,255,255), 1));
        LinearLayout.LayoutParams p = lp(-1, -2, 0,0,0,8); v.setLayoutParams(p);
        return v;
    }

    private TextView infoRow(String a, String b) {
        TextView v = text(a + "\n" + b, 14, Color.WHITE, false);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setLineSpacing(dp(2), 1f);
        v.setBackground(round(argb(10,255,255,255), dp(14), argb(18,255,255,255), 1));
        LinearLayout.LayoutParams p = lp(-1, -2, 0,0,0,8); v.setLayoutParams(p);
        return v;
    }

    private void showError(String msg) {
        resultWrap.removeAllViews();
        LinearLayout c = card(dp(18)); c.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultWrap.addView(c, lp(-1,-2,0,0,0,0));
        TextView t = text(msg == null ? "Não foi possível buscar esse perfil." : msg, 15, Color.WHITE, true);
        t.setGravity(Gravity.CENTER); c.addView(t);
    }

    private void setLoading(boolean loading, String message) {
        searchBtn.setEnabled(!loading);
        searchBtn.setText(loading ? "..." : "Buscar");
        statusText.setText(message == null ? "" : message);
    }

    private JSONObject getJson(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(9000); c.setReadTimeout(12000);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setRequestProperty("User-Agent", "ToxicHabboApp/1.0 Android");
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String body = readAll(is);
        if (code < 200 || code >= 300 || body == null || body.trim().isEmpty()) return null;
        return new JSONObject(body);
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
                c.setConnectTimeout(9000); c.setReadTimeout(12000);
                Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream());
                runOnUiThread(() -> { if (bmp != null) view.setImageBitmap(bmp); });
            } catch (Exception ignored) {}
        });
    }

    private Drawable makeBg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(13,13,18), Color.rgb(18,18,26), Color.rgb(10,10,15)});
        return g;
    }
    private LinearLayout card(int radius) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackground(round(argb(13,255,255,255), radius, argb(22,255,255,255), 1)); return l; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView pill(String s, int color) { TextView v = text(s, 12, Color.WHITE, true); v.setPadding(dp(10), dp(7), dp(10), dp(7)); v.setBackground(round(adjustAlpha(color, 0.28f), dp(999), adjustAlpha(color,0.55f), 1)); return v; }
    private GradientDrawable round(int fill, int radius, int stroke, int sw) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(radius); if (sw > 0) d.setStroke(dp(sw), stroke); return d; }
    private GradientDrawable grad(int radius, int c1, int c2) { GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1,c2}); d.setCornerRadius(radius); return d; }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private int argb(int a, int r, int g, int b) { return Color.argb(a,r,g,b); }
    private int adjustAlpha(int color, float f) { return Color.argb(Math.round(Color.alpha(color)*f), Color.red(color), Color.green(color), Color.blue(color)); }
    private String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch(Exception e){ return s; } }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard(){ try{ ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(searchInput.getWindowToken(),0);}catch(Exception ignored){} }
    private void openUrl(String url){ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
    private String humanDate(String in) { if (in == null || in.isEmpty()) return ""; try { return in.replace('T',' ').replace(".000+0000",""); } catch(Exception e){ return in; } }
}
