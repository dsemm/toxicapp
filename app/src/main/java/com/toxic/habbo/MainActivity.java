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
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String HABBODEX = "https://habbodex.com/api/v1";
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
    private boolean currentProfilePrivate = false;
    private volatile int activeSearchToken = 0;
    private volatile boolean searchInProgress = false;
    private volatile String activeSearchNick = "";
    private String currentLoadedNick = "";
    private int inlineProgressPct = 0;
    private String inlineProgressMessage = "";
    private final ConcurrentHashMap<String, ProfileResult> profileCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> profileCacheTimes = new ConcurrentHashMap<>();
    private static final long SESSION_CACHE_TTL_MS = 5L * 60L * 1000L;
    private ProfileResult activeRenderedProfile = null;
    private final ArrayDeque<ProfileResult> profileHistory = new ArrayDeque<>();
    private static final int PROFILE_HISTORY_LIMIT = 25;
    private int visiblePhotosCount = 20;
    private int visibleStylesCount = 20;
    private int photosScrollX = 0;
    private int stylesScrollX = 0;
    private static final int PAGE_CHUNK = 20;
    private static final String PREFS = "habbo_check_settings";
    private static final String PREF_MAX_PROFILES = "max_profiles";
    private static final String PREF_CACHE_DAYS = "cache_days";
    private static final String PREF_MAX_CACHE_MB = "max_cache_mb";
    private static final String PREF_HOTEL = "hotel";
    private static final String PREF_OPENED_HISTORY = "opened_profiles_history";
    private static final long PROFILE_REFRESH_COOLDOWN_MS = 60L * 1000L;
    private ScrollView mainScroll;
    private LinearLayout pullRefreshChip;
    private ProgressBar pullRefreshSpinner;
    private TextView pullRefreshText;
    private long lastSameNickRefreshAt = 0L;
    private float pullStartY = 0f;
    private boolean pullStartedAtTop = false;
    private final ArrayList<ProfileHistoryItem> openedProfilesHistory = new ArrayList<>();
    private String currentHotelKey = "br";

    private InterstitialAd interstitialAd;
    private boolean interstitialLoading = false;
    private long lastInterstitialShownAt = 0L;
    private int profileOpenActionsSinceAd = 0;
    private static final String REAL_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-8079226281001828/5039255014";
    private static final String TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712";
    private static final String REAL_REWARDED_AD_UNIT_ID = "ca-app-pub-8079226281001828/1283312609";
    private static final String TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";
    private static final boolean USE_TEST_ADS = true;
    private static final String INTERSTITIAL_AD_UNIT_ID = USE_TEST_ADS ? TEST_INTERSTITIAL_AD_UNIT_ID : REAL_INTERSTITIAL_AD_UNIT_ID;
    private static final String REWARDED_AD_UNIT_ID = USE_TEST_ADS ? TEST_REWARDED_AD_UNIT_ID : REAL_REWARDED_AD_UNIT_ID;
    private static final long INTERSTITIAL_COOLDOWN_MS = 120L * 1000L;
    private static final int ACTIONS_BETWEEN_INTERSTITIALS = 1;
    private RewardedAd rewardedAd;
    private boolean rewardedLoading = false;
    private TextView rewardAdBtn;
    private TextView rewardAdTimeLabel;
    private long adFreeRemainingMs = 0L;
    private long adFreeActiveStartedAt = 0L;
    private final Runnable adFreeTicker = new Runnable() {
        @Override public void run() {
            consumeAdFreeElapsed();
            updateRewardButtonText();
            uiHandler.postDelayed(this, 1000L);
        }
    };
    private static final String PREF_AD_FREE_REMAINING_MS = "ad_free_remaining_ms";
    private static final long REWARDED_AD_FREE_MS = 30L * 60L * 1000L;
    private static final long MAX_AD_FREE_MS = 24L * 60L * 60L * 1000L;

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
    private boolean lightTheme = false;

    private interface IntChangeListener {
        void onChange(int value);
    }

    @Override public void onCreate(Bundle b) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(b);
        try {
            if (getActionBar() != null) getActionBar().hide();
        } catch (Exception ignored) {}
        lightTheme = getSharedPreferences(PREFS, MODE_PRIVATE).getString("theme", "dark").equals("light");
        currentHotelKey = normalizeHotelKey(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_HOTEL, ""));
        if (currentHotelKey.isEmpty()) {
            currentHotelKey = defaultHotelForDeviceLocale();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_HOTEL, currentHotelKey).apply();
        }
        try {
            habboFont = Typeface.createFromAsset(getAssets(), "fonts/ubuntu_habbo.ttf");
        } catch (Exception e) {
            habboFont = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        }
        getWindow().setStatusBarColor(lightTheme ? Color.WHITE : bg);
        getWindow().setNavigationBarColor(lightTheme ? Color.rgb(245, 245, 245) : bg);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = lightTheme ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
            if (Build.VERSION.SDK_INT >= 26 && lightTheme) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        loadOpenedProfilesHistory();
        adFreeRemainingMs = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(PREF_AD_FREE_REMAINING_MS, 0L);
        buildUi();
        MobileAds.initialize(this, initializationStatus -> {});
        loadInterstitialAd();
        loadRewardedAd();
    }
    private void loadInterstitialAd() {
        if (interstitialLoading || interstitialAd != null) return;

        interstitialLoading = true;
        AdRequest adRequest = new AdRequest.Builder().build();

        InterstitialAd.load(
                this,
                INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialLoading = false;
                        interstitialAd = ad;
                        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                interstitialAd = null;
                                loadInterstitialAd();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                interstitialAd = null;
                                loadInterstitialAd();
                            }

                            @Override
                            public void onAdShowedFullScreenContent() {
                                interstitialAd = null;
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {                        interstitialLoading = false;
                        interstitialAd = null;
                    }
                }
        );
    }

    private void maybeShowProfileInterstitial() {
        profileOpenActionsSinceAd++;

        long now = System.currentTimeMillis();
        boolean cooldownOk = now - lastInterstitialShownAt >= INTERSTITIAL_COOLDOWN_MS;
        boolean actionCountOk = profileOpenActionsSinceAd >= ACTIONS_BETWEEN_INTERSTITIALS;

        if (hasAdFreeAccess()) { loadInterstitialAd(); return; }

        if (interstitialAd != null && cooldownOk && actionCountOk && !isFinishing()) {
            profileOpenActionsSinceAd = 0;
            lastInterstitialShownAt = now;
            interstitialAd.show(this);
        } else if (interstitialAd == null) {
            loadInterstitialAd();
        }
    }


    private void loadRewardedAd() {
        if (rewardedLoading || rewardedAd != null) return;

        rewardedLoading = true;
        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(
                this,
                REWARDED_AD_UNIT_ID,
                adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewardedLoading = false;
                        rewardedAd = ad;
                        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                rewardedAd = null;
                                loadRewardedAd();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                rewardedAd = null;
                                loadRewardedAd();
                                toast("Não foi possível exibir o vídeo agora.");
                            }

                            @Override
                            public void onAdShowedFullScreenContent() {
                                rewardedAd = null;
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        rewardedLoading = false;
                        rewardedAd = null;
                    }
                }
        );
    }

    private void showRewardedAdDialog() {
        consumeAdFreeElapsed();
        String remaining = formatAdFreeRemaining();
        String message = hasAdFreeAccess()
                ? "Você ainda tem " + remaining + " sem anúncios. Deseja assistir um vídeo para adicionar mais 30 minutos? O limite máximo é 24 horas."
                : "Deseja assistir um vídeo para liberar 30 minutos sem anúncios ao pesquisar perfis?";

        new AlertDialog.Builder(this)
                .setTitle("Acesso sem anúncios")
                .setMessage(message)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Assistir vídeo", (dialog, which) -> showRewardedAdForAdFreeTime())
                .show();
    }

    private void showRewardedAdForAdFreeTime() {
        consumeAdFreeElapsed();

        if (adFreeRemainingMs >= MAX_AD_FREE_MS) {
            toast("Você já atingiu o limite de 24 horas sem anúncios.");
            updateRewardButtonText();
            return;
        }

        if (rewardedAd == null) {
            toast("O vídeo ainda está carregando. Tente novamente em alguns segundos.");
            loadRewardedAd();
            return;
        }

        rewardedAd.show(this, (RewardItem rewardItem) -> grantAdFreeTime(REWARDED_AD_FREE_MS));
    }

    private void grantAdFreeTime(long millis) {
        consumeAdFreeElapsed();
        adFreeRemainingMs = Math.min(MAX_AD_FREE_MS, Math.max(0L, adFreeRemainingMs) + millis);
        saveAdFreeRemaining();
        updateRewardButtonText();
        toast("30 minutos sem anúncios liberados.");
    }

    private boolean hasAdFreeAccess() {
        consumeAdFreeElapsed();
        return adFreeRemainingMs > 0L;
    }

    private void consumeAdFreeElapsed() {
        if (adFreeRemainingMs <= 0L) {
            adFreeRemainingMs = 0L;
            adFreeActiveStartedAt = System.currentTimeMillis();
            return;
        }

        long now = System.currentTimeMillis();
        if (adFreeActiveStartedAt <= 0L) {
            adFreeActiveStartedAt = now;
            return;
        }

        long elapsed = Math.max(0L, now - adFreeActiveStartedAt);
        if (elapsed > 0L) {
            adFreeRemainingMs = Math.max(0L, adFreeRemainingMs - elapsed);
            adFreeActiveStartedAt = now;
        }
    }

    private void saveAdFreeRemaining() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(PREF_AD_FREE_REMAINING_MS, Math.max(0L, adFreeRemainingMs)).apply();
    }

    private void updateRewardButtonText() {
        if (rewardAdBtn == null) return;
        consumeAdFreeElapsed();

        rewardAdBtn.setText("");
        rewardAdBtn.setTextColor(Color.WHITE);

        if (rewardAdTimeLabel != null) {
            if (adFreeRemainingMs > 0L) {
                rewardAdTimeLabel.setText(formatAdFreeRemainingShort());
                rewardAdTimeLabel.setTextColor(lightTheme ? Color.rgb(45,45,45) : Color.WHITE);
                rewardAdTimeLabel.setVisibility(View.VISIBLE);
            } else {
                rewardAdTimeLabel.setText("");
                rewardAdTimeLabel.setVisibility(View.GONE);
            }
        }
    }

    private String formatAdFreeRemainingShort() {
        long totalSeconds = Math.max(0L, adFreeRemainingMs) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return String.format(Locale.ROOT, "%02d:%02dh", hours, minutes);
        return String.format(Locale.ROOT, "%02d:%02dm", minutes, seconds);
    }

    private String formatAdFreeRemaining() {
        long totalSeconds = Math.max(0L, adFreeRemainingMs) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return hours + "h " + minutes + "min";
        if (minutes > 0L) return minutes + "min " + seconds + "s";
        return seconds + "s";
    }

    @Override protected void onResume() {
        super.onResume();
        adFreeActiveStartedAt = System.currentTimeMillis();
        uiHandler.removeCallbacks(adFreeTicker);
        uiHandler.post(adFreeTicker);
    }

    @Override protected void onPause() {
        consumeAdFreeElapsed();
        saveAdFreeRemaining();
        uiHandler.removeCallbacks(adFreeTicker);
        adFreeActiveStartedAt = 0L;
        super.onPause();
    }

    @Override protected void onDestroy() {
        consumeAdFreeElapsed();
        saveAdFreeRemaining();
        uiHandler.removeCallbacks(adFreeTicker);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        screen = new FrameLayout(this);
        screen.setBackground(makeBg());
        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && searchInput != null && searchInput.hasFocus() && !isTouchInsideView(searchInput, event)) {
                clearSearchFocus();
            }
            handlePullToRefresh(scroll, event);
            return false;
        });
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(42));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        pullRefreshChip = new LinearLayout(this);
        pullRefreshChip.setOrientation(LinearLayout.HORIZONTAL);
        pullRefreshChip.setGravity(Gravity.CENTER_VERTICAL);
        pullRefreshChip.setPadding(dp(14), dp(10), dp(14), dp(10));
        pullRefreshChip.setBackground(round(lightTheme ? Color.WHITE : Color.rgb(36, 24, 54), dp(999), lightTheme ? Color.rgb(216,216,216) : Color.argb(36,255,255,255), 1));
        pullRefreshChip.setAlpha(0f);
        pullRefreshChip.setTranslationY(-dp(40));
        pullRefreshChip.setVisibility(View.GONE);
        pullRefreshSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        if (Build.VERSION.SDK_INT >= 21) pullRefreshSpinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(purple));
        pullRefreshChip.addView(pullRefreshSpinner, new LinearLayout.LayoutParams(dp(18), dp(18)));
        pullRefreshText = text("Atualizando perfil...", 13, lightTheme ? Color.rgb(33,33,33) : Color.WHITE, true);
        LinearLayout.LayoutParams pullTxtLp = new LinearLayout.LayoutParams(-2, -2);
        pullTxtLp.leftMargin = dp(8);
        pullRefreshChip.addView(pullRefreshText, pullTxtLp);
        FrameLayout.LayoutParams pullLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pullLp.topMargin = dp(12);
        screen.addView(pullRefreshChip, pullLp);
        screen.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && searchInput != null && searchInput.hasFocus() && !isTouchInsideView(searchInput, event)) {
                clearSearchFocus();
            }
            return false;
        });
        root.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && searchInput != null && searchInput.hasFocus() && !isTouchInsideView(searchInput, event)) {
                clearSearchFocus();
            }
            return false;
        });

        TextView historyBtn = text("", 22, lightTheme ? Color.rgb(33,33,33) : Color.argb(230,255,255,255), true);
        historyBtn.setGravity(Gravity.CENTER);
        historyBtn.setPadding(0, 0, 0, 0);
        historyBtn.setBackground(new HistoryClockDrawable());
        historyBtn.setOnClickListener(v -> showOpenedProfilesHistoryDialog());
        FrameLayout.LayoutParams historyLp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.LEFT);
        historyLp.topMargin = dp(14);
        historyLp.leftMargin = dp(8);
        screen.addView(historyBtn, historyLp);

        TextView settingsBtn = text("⚙", 22, lightTheme ? Color.rgb(33,33,33) : Color.argb(230,255,255,255), true);
        settingsBtn.setGravity(Gravity.CENTER);
        settingsBtn.setPadding(0, 0, 0, 0);
        settingsBtn.setBackgroundColor(Color.TRANSPARENT);
        settingsBtn.setOnClickListener(v -> showSettingsDialog());
        FrameLayout.LayoutParams settingsLp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.RIGHT);
        settingsLp.topMargin = dp(14);
        settingsLp.rightMargin = dp(8);
        screen.addView(settingsBtn, settingsLp);

        rewardAdBtn = text("", 22, Color.WHITE, true);
        rewardAdBtn.setGravity(Gravity.CENTER);
        rewardAdBtn.setPadding(0, 0, 0, 0);
        rewardAdBtn.setIncludeFontPadding(false);
        rewardAdBtn.setBackground(new RewardVideoDrawable());
        rewardAdBtn.setOnClickListener(v -> showRewardedAdDialog());
        FrameLayout.LayoutParams rewardLp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.RIGHT);
        rewardLp.topMargin = dp(60);
        rewardLp.rightMargin = dp(8);
        screen.addView(rewardAdBtn, rewardLp);

        rewardAdTimeLabel = text("", 9, lightTheme ? Color.rgb(45,45,45) : Color.WHITE, true);
        rewardAdTimeLabel.setGravity(Gravity.CENTER);
        rewardAdTimeLabel.setIncludeFontPadding(false);
        rewardAdTimeLabel.setSingleLine(true);
        rewardAdTimeLabel.setVisibility(View.GONE);
        FrameLayout.LayoutParams rewardTimeLp = new FrameLayout.LayoutParams(dp(58), dp(16), Gravity.TOP | Gravity.RIGHT);
        rewardTimeLp.topMargin = dp(102);
        rewardTimeLp.rightMargin = dp(0);
        screen.addView(rewardAdTimeLabel, rewardTimeLp);

        updateRewardButtonText();


        TextView logo = text("Toxic Search Tool", 31, lightTheme ? Color.rgb(35, 22, 45) : Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setLetterSpacing(0.02f);
        root.addView(logo, lp(-1, -2, 0, 0, 0, 4));
        TextView subtitle = text("Buscar Habbos • " + hotelLabel(currentHotelKey), 14, muted, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, 10));

        LinearLayout searchOuter = card(dp(24));
        searchOuter.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.addView(searchOuter, lp(-1, -2, 0, 0, 0, 16));
        LinearLayout searchCard = card(dp(18));
        searchCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        searchOuter.addView(searchCard, lp(-1, -2, 0, 0, 0, 0));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Digite um nick");
        searchInput.setHintTextColor(lightTheme ? Color.rgb(117, 117, 117) : Color.argb(135,255,255,255));
        searchInput.setTextColor(lightTheme ? Color.rgb(33, 33, 33) : Color.WHITE);
        searchInput.setTextSize(16);
        searchInput.setTypeface(habboFont);
        searchInput.setGravity(Gravity.CENTER_VERTICAL);
        searchInput.setPadding(dp(16), 0, dp(16), 0);
        searchInput.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(28,255,255,255), dp(14), lightTheme ? Color.rgb(210,210,210) : Color.argb(35,255,255,255), 1));
        searchInput.setCursorVisible(false);
        searchInput.setOnFocusChangeListener((v, hasFocus) -> searchInput.setCursorVisible(hasFocus));
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
        c.addView(centerNote("Digite um nick do " + hotelName(currentHotelKey) + " para consultar perfil, fotos, missões anteriores, visuais, amigos, quartos e grupos."));
    }

    private void search() {
        final String nick = searchInput.getText().toString().trim();
        final String nickKey = normalizeNickKey(nick);
        if (nickKey.isEmpty()) { hidePullRefreshIndicator(); toast("Digite um nick do Habbo."); return; }

        if (searchInProgress && nickKey.equals(activeSearchNick)) {
            hidePullRefreshIndicator();
            toast("Esse perfil já está sendo carregado.");
            return;
        }

        if (!searchInProgress && activeRenderedProfile != null && nickKey.equals(currentLoadedNick) && normalizeHotelKey(activeRenderedProfile.hotelKey).equals(currentHotelKey)) {
            long now = System.currentTimeMillis();
            long wait = PROFILE_REFRESH_COOLDOWN_MS - (now - lastSameNickRefreshAt);
            if (wait > 0) {
                hidePullRefreshIndicator();
                toast("Aguarde " + Math.max(1, (int)Math.ceil(wait / 1000.0)) + "s para atualizar este perfil novamente.");
                return;
            }
        }

        clearSearchFocus();
        suggestionsBox.setVisibility(View.GONE);

        final int token = ++activeSearchToken;
        activeSearchNick = nickKey;
        searchInProgress = true;
        currentLoadedNick = "";
        currentProfilePrivate = false;
        inlineProgressPct = 0;
        inlineProgressMessage = "";
        visiblePhotosCount = PAGE_CHUNK;
        visibleStylesCount = PAGE_CHUNK;
        photosScrollX = 0;
        stylesScrollX = 0;
        pushCurrentProfileToHistory(nickKey);

        resultWrap.removeAllViews();
        setLoading(true, "Buscando " + nick + "...");
        maybeShowProfileInterstitial();

        executor.execute(() -> {
            try {
                ProfileResult fresh = loadProfile(nick, false);
                if (!isActiveToken(token)) return;

                ProfileResult cached = getCachedProfile(nickKey);
                final ProfileResult r = mergeFreshIntoCachedSafely(cached, fresh);
                putProfileCache(r, nickKey);
                saveProfileCache(r, nickKey);

                runOnUiThread(() -> {
                    if (!isActiveToken(token)) return;
                    showInlineLoading("Carregando detalhes do perfil...");
                    renderProfile(r);
                });

                completeProfileSections(r, token);

                if (!isActiveToken(token)) return;
                putProfileCache(r, nickKey);
                saveProfileCache(r, nickKey);

                runOnUiThread(() -> {
                    if (!isActiveToken(token)) return;
                    inlineProgressPct = 0;
                    inlineProgressMessage = "";
                    renderProfile(r);
                    statusText.setText("");
                    searchInProgress = false;
                    activeSearchNick = "";
                    currentLoadedNick = normalizeNickKey(r.name);
                    lastSameNickRefreshAt = System.currentTimeMillis();
                    searchBtn.setEnabled(true);
                    searchBtn.setText("Pesquisar");
                    hidePullRefreshIndicator();
                });
            } catch (ProfileNotFoundException e) {
                runOnUiThread(() -> {
                    if (!isActiveToken(token)) return;
                    searchInProgress = false;
                    activeSearchNick = "";
                    setLoading(false, "");
                    hidePullRefreshIndicator();
                    hidePullRefreshIndicator();
                    showNotFoundState(e.nick, e.suggestions);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isActiveToken(token)) return;
                    searchInProgress = false;
                    activeSearchNick = "";
                    setLoading(false, "");
                    hidePullRefreshIndicator();
                    hidePullRefreshIndicator();
                    showError(e.getMessage() == null ? "Falha ao buscar perfil." : e.getMessage());
                });
            }
        });
    }

    private String normalizeNickKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isActiveToken(int token) {
        return token == activeSearchToken;
    }

    private ProfileResult loadProfile(String nick, boolean includeSections) throws Exception {
        ProfileResult r = new ProfileResult();
        r.searchedNick = nick;
        r.hotelKey = currentHotelKey;
        JSONObject habboPublic = tryJson(habboApiUrl("/api/public/users?name=" + enc(nick)));
        JSONObject dexByName = unwrap(tryJson(habbodexProfileByNameUrl(nick)));
        JSONObject suggest = unwrap(tryJson(habbodexSuggestUrl(nick)));
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
        r.banned = isSameProfileObject(base, habboPublic) ? false : optBoolTrue(base, "isBanned", "banned", "ban", "is_banned");
        r.memberSince = firstText(base, "memberSince", "creationTime", "createdAt", "registeredAt", "created_at", "registerDate", "registrationDate");
        if (r.memberSince.isEmpty() && habboPublic != null) r.memberSince = habboPublic.optString("memberSince", "");
        r.lastAccess = firstText(base, "lastAccessTime", "lastLoginTime", "lastOnline", "lastVisit");
        r.level = firstText(base, "currentLevel", "level");
        r.starGems = firstText(base, "starGemCount", "starGems");
        r.previousNames = mergeLists(extractList(dexByName, "previousNames"), extractPreviousNamesFromSuggest(suggest, r.name));
        r.selectedBadges = extractListFromKeys(dexByName, "selectedBadges", "badges");

        if (!r.uniqueId.isEmpty()) {
            JSONObject dexProfile = unwrap(tryJson(habbodexProfileByUniqueUrl(r.uniqueId)));
            if (dexProfile != null) {
                r.dexProfile = dexProfile;
                if (r.motto.isEmpty()) r.motto = firstText(dexProfile, "motto", "mission");
                if (r.memberSince.isEmpty()) r.memberSince = firstText(dexProfile, "memberSince", "creationTime", "createdAt", "registeredAt", "created_at", "registerDate", "registrationDate");
                if (r.lastAccess.isEmpty()) r.lastAccess = firstText(dexProfile, "lastAccessTime", "lastLoginTime", "lastOnline");
                if (r.level.isEmpty()) r.level = firstText(dexProfile, "currentLevel", "level");
                if (r.starGems.isEmpty()) r.starGems = firstText(dexProfile, "starGemCount", "starGems");
                r.previousNames = mergeLists(r.previousNames, extractList(dexProfile, "previousNames"));
                r.selectedBadges = mergeLists(r.selectedBadges, extractListFromKeys(dexProfile, "selectedBadges", "badges"));
            }

            JSONObject officialProfile = tryJson(habboApiUrl("/api/public/users/" + enc(r.uniqueId) + "/profile"));
            r.officialProfile = officialProfile;
            if (officialProfile != null) {
                JSONObject user = officialProfile.optJSONObject("user");
                if (user != null) {
                    if (r.level.isEmpty()) r.level = firstText(user, "currentLevel", "level");
                    if (r.starGems.isEmpty()) r.starGems = firstText(user, "starGemCount", "starGems");
                    if (r.memberSince.isEmpty()) r.memberSince = firstText(user, "memberSince", "creationTime", "createdAt", "registeredAt", "created_at", "registerDate", "registrationDate");
                    if (r.lastAccess.isEmpty()) r.lastAccess = firstText(user, "lastAccessTime", "lastLoginTime", "lastOnline");
                    r.online = optBoolAny(user, r.online, "online", "isOnline");
                    r.selectedBadges = mergeLists(r.selectedBadges, extractListFromKeys(user, "selectedBadges", "badges"));
                }
                r.friends = mergeLists(r.friends, extractList(officialProfile, "friends"));
                r.rooms = mergeLists(r.rooms, extractList(officialProfile, "rooms"));
                r.groups = mergeLists(r.groups, extractList(officialProfile, "groups"));
            }
            if (includeSections) {
                completeProfileSections(r, activeSearchToken);
            }
        }
        return r;
    }

    private void completeProfileSections(ProfileResult r, int token) {
        if (r == null || r.uniqueId == null || r.uniqueId.isEmpty() || !isActiveToken(token)) return;

        PageResult photosPage = null;
        try { photosPage = fetchPageChunk(r.uniqueId, "photos", "photos", 1, PAGE_CHUNK, PAGE_CHUNK); } catch(Exception ignored) {}
        if (photosPage != null) applyPhotosPage(r, photosPage, true);
        try { enrichPhotoRoomInfo(r); } catch(Exception ignored) {}
        if (!isActiveToken(token)) return;
        putProfileCache(r, activeSearchNick);
        saveProfileCache(r, activeSearchNick);
        runOnUiThread(() -> {
            if (!isActiveToken(token)) return;
            showInlineLoading("Carregando histórico...");
            renderProfile(r);
        });

        ArrayList<JSONObject> mottos = null;
        try { mottos = fetchAll(r.uniqueId, "previous-mottos", null, 100, 3); } catch(Exception ignored) {}
        if (mottos != null) r.previousMottos = mottos;
        if (!isActiveToken(token)) return;
        putProfileCache(r, activeSearchNick);
        saveProfileCache(r, activeSearchNick);
        runOnUiThread(() -> {
            if (!isActiveToken(token)) return;
            showInlineLoading("Carregando visuais e amigos...");
            renderProfile(r);
        });

        PageResult badgesPage = null;
        try { badgesPage = fetchPage(r.uniqueId, "selected-badges", null, 1, 20); } catch(Exception ignored) {}
        if (badgesPage != null && badgesPage.items != null && !badgesPage.items.isEmpty()) r.selectedBadges = mergeLists(badgesPage.items, r.selectedBadges);
        if (!isActiveToken(token)) return;

        PageResult stylesPage = null;
        try { stylesPage = fetchPageChunk(r.uniqueId, "previous-styles", null, 1, PAGE_CHUNK, PAGE_CHUNK); } catch(Exception ignored) {}
        if (stylesPage != null) applyStylesPage(r, stylesPage, true);
        if (!isActiveToken(token)) return;

        ArrayList<JSONObject> friendsNow = null;
        try { friendsNow = fetchAll(r.uniqueId, "friends", "friends", 100, 50); } catch(Exception ignored) {}
        if (friendsNow != null) r.friends = mergeLists(friendsNow, r.friends);
        if (!isActiveToken(token)) return;

        ArrayList<JSONObject> removedFriends = null;
        try { removedFriends = fetchAll(r.uniqueId, "previous-friends", null, 100, 50); } catch(Exception ignored) {}
        if (removedFriends != null) r.oldFriends = removedFriends;
        if (!isActiveToken(token)) return;
        putProfileCache(r, activeSearchNick);
        saveProfileCache(r, activeSearchNick);
        runOnUiThread(() -> {
            if (!isActiveToken(token)) return;
            showInlineLoading("Carregando quartos e grupos...");
            renderProfile(r);
        });

        ArrayList<JSONObject> roomsNow = null;
        try { roomsNow = fetchAll(r.uniqueId, "rooms", "rooms", 100, 3); } catch(Exception ignored) {}
        if (roomsNow != null) r.rooms = mergeLists(roomsNow, r.rooms);
        if (!isActiveToken(token)) return;

        ArrayList<JSONObject> oldRoomsNow = null;
        try { oldRoomsNow = fetchAll(r.uniqueId, "previous-rooms", "rooms", 100, 3); } catch(Exception ignored) {}
        if (oldRoomsNow != null) r.oldRooms = oldRoomsNow;
        if (!isActiveToken(token)) return;

        ArrayList<JSONObject> groupsNow = null;
        try { groupsNow = fetchAll(r.uniqueId, "groups", "groups", 100, 3); } catch(Exception ignored) {}
        if (groupsNow != null) r.groups = groupsNow;
        if (!isActiveToken(token)) return;

        try { enrichPhotoRoomInfo(r); } catch(Exception ignored) {}
        putProfileCache(r, activeSearchNick);
        saveProfileCache(r, activeSearchNick);
    }

    private ArrayList<JSONObject> fetchAll(String uniqueId, String endpoint, String primaryKey, int limit, int maxPages) {
        ArrayList<JSONObject> out = new ArrayList<>();
        int page = 1;
        for (int i = 0; i < maxPages; i++) {
            try {
                JSONObject pageData = unwrap(getJson(habbodexEndpointUrl(uniqueId, endpoint, page, limit)));
                if (pageData == null) break;
                ArrayList<JSONObject> items = extractList(pageData, primaryKey);
                if (items.isEmpty()) break;
                out.addAll(items);
                JSONObject next = pageData.optJSONObject("next");
                int nextPage = next == null ? 0 : next.optInt("page", 0);
                if (nextPage <= 0) {
                    int totalPages = pageData.optInt("totalPages", pageData.optInt("pages", 0));
                    JSONObject pagination = pageData.optJSONObject("pagination");
                    if (pagination != null) {
                        totalPages = Math.max(totalPages, pagination.optInt("totalPages", pagination.optInt("pages", 0)));
                        nextPage = pagination.optInt("nextPage", 0);
                    }
                    if (nextPage <= 0 && totalPages > page) nextPage = page + 1;
                    if (nextPage <= 0 && items.size() >= limit) nextPage = page + 1;
                }
                if (nextPage <= 0 || nextPage == page || nextPage > maxPages) break;
                page = nextPage;
            } catch (Exception ignored) { break; }
        }
        return out;
    }



    private PageResult fetchPage(String uniqueId, String endpoint, String primaryKey, int page, int limit) {
        PageResult out = new PageResult();
        out.page = Math.max(1, page);
        out.nextPage = 0;
        out.hasMore = false;
        out.total = 0;
        try {
            JSONObject pageData = unwrap(getJson(habbodexEndpointUrl(uniqueId, endpoint, out.page, limit)));
            if (pageData == null) return out;
            out.items = extractList(pageData, primaryKey);
            out.total = extractTotalCount(pageData);
            JSONObject next = pageData.optJSONObject("next");
            int nextPage = next == null ? 0 : next.optInt("page", 0);
            if (nextPage <= 0) {
                JSONObject pagination = pageData.optJSONObject("pagination");
                if (pagination != null) nextPage = pagination.optInt("nextPage", 0);
            }
            if (nextPage <= 0) {
                int totalPages = pageData.optInt("totalPages", pageData.optInt("pages", 0));
                JSONObject pagination = pageData.optJSONObject("pagination");
                if (pagination != null) totalPages = Math.max(totalPages, pagination.optInt("totalPages", pagination.optInt("pages", 0)));
                if (totalPages > out.page) nextPage = out.page + 1;
            }
            if (nextPage <= 0 && out.items.size() >= limit) nextPage = out.page + 1;
            out.nextPage = nextPage > out.page ? nextPage : 0;
            out.hasMore = out.nextPage > 0;
        } catch (Exception ignored) {}
        return out;
    }

    private PageResult fetchPageChunk(String uniqueId, String endpoint, String primaryKey, int startPage, int pageLimit, int desiredCount) {
        PageResult combined = new PageResult();
        combined.page = Math.max(1, startPage);
        combined.nextPage = 0;
        combined.hasMore = false;
        combined.total = 0;

        int page = combined.page;
        int safety = 0;
        int target = Math.max(1, desiredCount);
        int limit = Math.max(1, pageLimit);

        while (page > 0 && safety < 12 && combined.items.size() < target) {
            PageResult part = fetchPage(uniqueId, endpoint, primaryKey, page, limit);
            if (part == null) break;
            if (combined.total <= 0 && part.total > 0) combined.total = part.total;
            if (part.items == null || part.items.isEmpty()) {
                combined.nextPage = 0;
                combined.hasMore = false;
                break;
            }
            for (JSONObject item : part.items) {
                if (combined.items.size() >= target) break;
                combined.items.add(item);
            }
            combined.page = part.page;
            if (part.nextPage <= page || !part.hasMore) {
                combined.nextPage = 0;
                combined.hasMore = false;
                break;
            }
            page = part.nextPage;
            combined.nextPage = page;
            combined.hasMore = true;
            safety++;
        }

        if (combined.total > 0 && combined.items.size() < Math.min(target, combined.total) && combined.nextPage <= 0) {
            combined.nextPage = Math.max(startPage + 1, page + 1);
            combined.hasMore = true;
        }
        if (combined.total > 0 && combined.items.size() >= combined.total) {
            combined.nextPage = 0;
            combined.hasMore = false;
        }
        return combined;
    }

    private int extractTotalCount(JSONObject data) {
        if (data == null) return 0;
        int total = firstPositiveInt(data, "total", "totalItems", "totalCount", "count", "recordsTotal");
        JSONObject pagination = data.optJSONObject("pagination");
        if (total <= 0 && pagination != null) total = firstPositiveInt(pagination, "total", "totalItems", "totalCount", "count");
        JSONObject meta = data.optJSONObject("meta");
        if (total <= 0 && meta != null) total = firstPositiveInt(meta, "total", "totalItems", "totalCount", "count");
        return total;
    }

    private int firstPositiveInt(JSONObject data, String... keys) {
        if (data == null || keys == null) return 0;
        for (String key : keys) {
            if (data.has(key)) {
                int v = data.optInt(key, 0);
                if (v > 0) return v;
            }
        }
        return 0;
    }

    private void applyPhotosPage(ProfileResult r, PageResult page, boolean reset) {
        if (r == null || page == null) return;
        if (reset) r.photos.clear();
        r.photos = mergeLists(r.photos, page.items);
        if (page.total > 0) r.photosTotal = page.total;
        int total = r.photosTotal > 0 ? r.photosTotal : page.total;
        r.photosHasMore = page.hasMore || (total > 0 && r.photos.size() < total);
        r.photosNextPage = page.nextPage;
        if (r.photosHasMore && r.photosNextPage <= 0) r.photosNextPage = Math.max(2, page.page + 1);
        if (!r.photosHasMore) r.photosNextPage = 0;
    }

    private void applyStylesPage(ProfileResult r, PageResult page, boolean reset) {
        if (r == null || page == null) return;
        if (reset) r.previousStyles.clear();
        r.previousStyles = mergeLists(r.previousStyles, page.items);
        if (page.total > 0) r.stylesTotal = page.total;
        int total = r.stylesTotal > 0 ? r.stylesTotal : page.total;
        r.stylesHasMore = page.hasMore || (total > 0 && r.previousStyles.size() < total);
        r.stylesNextPage = page.nextPage;
        if (r.stylesHasMore && r.stylesNextPage <= 0) r.stylesNextPage = Math.max(2, page.page + 1);
        if (!r.stylesHasMore) r.stylesNextPage = 0;
    }

    private void loadMorePhotos(ProfileResult r, HorizontalScrollView photosHsv) {
        if (r == null || r.photosLoading || !r.photosHasMore || r.uniqueId == null || r.uniqueId.isEmpty()) return;
        final int token = activeSearchToken;
        final int page = r.photosNextPage <= 0 ? 2 : r.photosNextPage;
        r.photosLoading = true;
        photosScrollX = photosHsv == null ? 0 : photosHsv.getScrollX();
        renderProfile(r);
        executor.execute(() -> {
            try {
                PageResult next = fetchPageChunk(r.uniqueId, "photos", "photos", page, PAGE_CHUNK, PAGE_CHUNK);
                if (!isActiveToken(token)) return;
                applyPhotosPage(r, next, false);
                try { enrichPhotoRoomInfo(r); } catch(Exception ignored) {}
                putProfileCache(r, activeSearchNick);
                saveProfileCache(r, activeSearchNick);
            } catch (Exception ignored) {
            } finally {
                r.photosLoading = false;
                runOnUiThread(() -> {
                    if (!isActiveToken(token)) return;
                    renderProfile(r);
                });
            }
        });
    }

    private void loadMoreStyles(ProfileResult r, HorizontalScrollView stylesHsv) {
        if (r == null || r.stylesLoading || !r.stylesHasMore || r.uniqueId == null || r.uniqueId.isEmpty()) return;
        final int token = activeSearchToken;
        final int page = r.stylesNextPage <= 0 ? 2 : r.stylesNextPage;
        r.stylesLoading = true;
        stylesScrollX = stylesHsv == null ? 0 : stylesHsv.getScrollX();
        renderProfile(r);
        executor.execute(() -> {
            try {
                PageResult next = fetchPageChunk(r.uniqueId, "previous-styles", null, page, PAGE_CHUNK, PAGE_CHUNK);
                if (!isActiveToken(token)) return;
                applyStylesPage(r, next, false);
                putProfileCache(r, activeSearchNick);
                saveProfileCache(r, activeSearchNick);
            } catch (Exception ignored) {
            } finally {
                r.stylesLoading = false;
                runOnUiThread(() -> {
                    if (!isActiveToken(token)) return;
                    renderProfile(r);
                });
            }
        });
    }

    private ArrayList<JSONObject> fetchOfficialPhotos(String uniqueId) {
        ArrayList<JSONObject> out = new ArrayList<>();
        if (uniqueId == null || uniqueId.trim().isEmpty()) return out;
        try {
            Object data = getJsonAny(habboApiUrl("/extradata/public/users/" + enc(uniqueId) + "/photos"));
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
        activeRenderedProfile = r;
        rememberOpenedProfile(r);
        currentProfilePrivate = r != null && r.privateProfile;
        if (!searchInProgress) setLoading(false, "");
        resultWrap.removeAllViews();

        if (searchInProgress && inlineProgressMessage != null && !inlineProgressMessage.trim().isEmpty()) {
            resultWrap.addView(loadingProgressCard(inlineProgressMessage, inlineProgressPct), lp(-1, -2, 0, 0, 0, 12));
        }

        LinearLayout profile = card(dp(22));
        applyProfilePrivateBorder(profile, dp(22));
        profile.setPadding(dp(18), dp(18), dp(18), dp(18));
        resultWrap.addView(profile, lp(-1, -2, 0, 0, 0, 18));

        FrameLayout avatarFrame = new FrameLayout(this);
        avatarFrame.setBackground(round(Color.rgb(15, 8, 25), dp(20), Color.argb(22,255,255,255), 1));
        profile.addView(avatarFrame, lp(-1, dp(280), 0, 0, 0, 16));
        ImageView avatar = new ImageView(this);
        avatar.setAdjustViewBounds(true);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatar.setPadding(dp(20), dp(10), dp(20), dp(84));
        avatarFrame.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
        currentAvatarImage = avatar;
        currentProfileFigure = r.figure;
        avatarDirection = 2;
        updateProfileAvatar();

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, dp(40), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cp.bottomMargin = dp(10);
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
            motto.setTextColor(lightTheme ? Color.rgb(70,70,70) : Color.argb(220,255,255,255));
            motto.setLineSpacing(dp(2), 1f);
            profile.addView(motto, lp(-1, -2, 0, 0, 0, 14));
        }
        LinearLayout badges = new LinearLayout(this);
        badges.setGravity(Gravity.CENTER);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        profile.addView(badges, lp(-1, -2, 0, 0, 0, 6));
        if (r.privateProfile) badges.addView(profileBadge("Privado", "lock", red));
        if (r.banned) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.leftMargin=dp(8); badges.addView(profileBadge("Banido", "banned", red), p); }

        addSelectedBadges(r.selectedBadges);
        addPreviousNames(r.previousNames);
        addPhotos(r);
        addPreviousMottos(r.previousMottos);
        addPreviousStyles(r);
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
        View badgeIcon;
        if ("banned".equals(icon)) {
            TextView bannedChar = habboText("ª", 10, true);
            bannedChar.setGravity(Gravity.CENTER);
            bannedChar.setIncludeFontPadding(false);
            bannedChar.setTextColor(Color.WHITE);
            badgeIcon = bannedChar;
        } else {
            badgeIcon = new IconView(this, icon);
        }
        row.addView(badgeIcon, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView tv = text(label, 13, Color.WHITE, true);
        tv.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); tp.leftMargin = dp(6);
        row.addView(tv, tp);
        return row;
    }

    private TextView roundIconButton(String label) {
        TextView v = text("", 19, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setElevation(dp(3));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(38), dp(34));
        p.setMargins(dp(4), 0, dp(4), 0);
        v.setLayoutParams(p);
        if ("shirt".equals(label)) {
            v.setBackground(new ShirtDrawable());
        } else {
            v.setBackground(new ArrowButtonDrawable("‹".equals(label)));
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
        wrap.addView(statRow("status", "Status", r.online ? "Online" : "Offline"));
        wrap.addView(statRow("clock", "Último login", niceDate(r.lastAccess), timeAgoText(r.lastAccess)));
        wrap.addView(statRow("calendar", "Criação", niceDateOnly(r.memberSince), timeAgoText(r.memberSince)));
        wrap.addView(statRow("friends", "Amigos", String.valueOf(r.friends.size())));
        wrap.addView(statRow("rooms", "Quartos", String.valueOf(r.rooms.size())));
        wrap.addView(statRow("groups", "Grupos", String.valueOf(r.groups.size())));
        wrap.addView(statRow("photos", "Fotos", String.valueOf(r.photos.size())));
        wrap.addView(statRow("star", "Estrelas", emptyDash(r.starGems)));
        wrap.addView(statRow("level", "Level", emptyDash(r.level)));
    }

    private LinearLayout statRow(String icon, String label, String value) {
        return statRow(icon, label, value, "");
    }

    private LinearLayout statRow(String icon, String label, String value, String tooltip) {
        LinearLayout row = card(dp(18));
        applyProfilePrivateBorder(row, dp(18));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(10), dp(7));
        LinearLayout.LayoutParams rp = lp(-1, dp(54), 0, 0, 0, 7);
        row.setLayoutParams(rp);
        if ("status".equals(icon)) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(iv, new LinearLayout.LayoutParams(dp(20), dp(20)));
            Glide.with(this).asGif().load("Online".equals(value) ? R.drawable.online : R.drawable.offline).into(iv);
        } else {
            IconView iv = new IconView(this, icon);
            row.addView(iv, new LinearLayout.LayoutParams(dp(18), dp(18)));
        }
        LinearLayout texts = new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.leftMargin = dp(9); row.addView(texts, tp);
        texts.addView(text(label, 11, Color.argb(190,255,255,255), false));
        texts.addView(text(value == null || value.isEmpty() || "null".equalsIgnoreCase(value) ? "—" : value, 14, Color.WHITE, true));
        if (tooltip != null && !tooltip.trim().isEmpty() && !"—".equals(tooltip.trim())) {
            row.setOnClickListener(v -> toast(tooltip));
        }
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
        resultWrap.addView(hsv, lp(-1, dp(72), 0, 0, 0, 14));
        for (int i=0; i<Math.min(list.size(), 12); i++) {
            JSONObject b = list.get(i); String code = firstText(b, "code", "badgeCode");
            ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setPadding(dp(2), dp(2), dp(2), dp(2));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(50), dp(50)); p.rightMargin = dp(10); row.addView(img, p);
            if (!code.isEmpty()) loadImage(img, badgeImageUrl(code));
            final JSONObject badgeObj = b;
            img.setOnClickListener(v -> showBadgeDialog(badgeObj));
        }
    }

    private void addPreviousNames(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = sectionCard("Nomes anteriores", list.size(), true);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(true);
        sv.setScrollbarFadingEnabled(false);
        tintScrollBar(sv);
        sv.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        LinearLayout inner = new LinearLayout(this); inner.setOrientation(LinearLayout.VERTICAL);
        sv.addView(inner, new ScrollView.LayoutParams(-1, -2));
        c.addView(sv, lp(-1, dp(Math.min(220, Math.max(120, 58 * Math.min(list.size(), 4)))), 0, 0, 0, 0));
        for (int i=0; i<Math.min(list.size(), 40); i++) {
            JSONObject o = list.get(i);
            String n = firstText(o, "name");
            String d = firstText(o, "changedAt");
            inner.addView(historyItem(n.isEmpty()?"Nome anterior":n, niceDate(d)));
        }
    }

    private void addPreviousMottos(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;

        ArrayList<JSONObject> valid = new ArrayList<>();
        for (JSONObject item : list) {
            String m = firstText(item, "text");
            if (!m.isEmpty()) valid.add(item);
        }
        if (valid.isEmpty()) return;

        LinearLayout c = sectionCard("Missões anteriores", valid.size(), true);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(true);
        sv.setScrollbarFadingEnabled(false);
        tintScrollBar(sv);
        sv.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        sv.addView(inner, new ScrollView.LayoutParams(-1, -2));
        c.addView(sv, lp(-1, dp(Math.min(260, Math.max(130, 66 * Math.min(valid.size(), 4)))), 0, 0, 0, 0));
        for (int i=0; i<valid.size(); i++) {
            JSONObject o = valid.get(i);
            String m = firstText(o, "text");
            String d = firstText(o, "changedAt");
            inner.addView(historyItem(m, niceDate(d)));
        }
    }

    private LinearLayout historyItem(String main, String date) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(22,255,255,255), dp(16), lightTheme ? Color.rgb(220,220,220) : Color.argb(24,255,255,255), 1));
        box.setLayoutParams(lp(-1, -2, 0, 0, 0, 10));
        TextView title = habboText(main == null ? "" : main, 16, true);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(lightTheme ? Color.rgb(33,33,33) : Color.WHITE);
        title.setLineSpacing(dp(2), 1f);
        box.addView(title, lp(-1, -2, 0, 0, 0, 4));
        if (date != null && !date.isEmpty() && !date.equals("—")) {
            TextView d = text(date, 12, Color.argb(185,255,255,255), false);
            d.setGravity(Gravity.CENTER);
            box.addView(d, lp(-1, -2, 0, 0, 0, 0));
        }
        return box;
    }

    private TextView mottoItem(String main, String date) {
        TextView v = habboText(main + (date == null || date.isEmpty() || date.equals("—") ? "" : "\n" + date), 16, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        v.setLineSpacing(dp(4), 1f);
        v.setTextColor(lightTheme ? Color.rgb(33,33,33) : Color.WHITE);
        v.setBackground(round(lightTheme ? Color.rgb(245,245,245) : Color.argb(22,255,255,255), dp(16), Color.argb(24,255,255,255), 1));
        v.setLayoutParams(lp(-1, -2, 0, 0, 0, 10));
        return v;
    }

    private void addPreviousStyles(ProfileResult profileResult) {
        if (profileResult == null) return;
        ArrayList<JSONObject> list = profileResult.previousStyles;
        if (list.isEmpty() && !profileResult.stylesHasMore && !profileResult.stylesLoading) return;
        final int loaded = list.size();
        final int totalLabel = Math.max(profileResult.stylesTotal, loaded);
        LinearLayout c = sectionCardWithLoadMore("Visuais anteriores", loaded, totalLabel > 0 ? totalLabel : loaded, profileResult.stylesHasMore || profileResult.stylesLoading, profileResult.stylesLoading, () -> loadMoreStyles(profileResult, null));
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(row);
        c.addView(hsv, lp(-1, dp(172), 0, 0, 0, 8));
        final HorizontalScrollView stylesHsv = hsv;
        if (stylesScrollX > 0) stylesHsv.post(() -> stylesHsv.scrollTo(stylesScrollX, 0));
        for (int i=0; i<loaded; i++) {
            JSONObject o = list.get(i);
            String fig = firstText(o, "figureString", "figure", "look");
            if (fig.isEmpty()) continue;
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(8),dp(8),dp(8),dp(8)); box.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(18,255,255,255), dp(18), lightTheme ? Color.rgb(220,220,220) : Color.argb(24,255,255,255),1));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(106), dp(162)); bp.rightMargin = dp(12); row.addView(box, bp);
            ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.FIT_CENTER); box.addView(img, new LinearLayout.LayoutParams(-1, dp(112)));
            loadImage(img, avatarSmall(fig));
            TextView dt = text(niceDate(firstText(o, "changedAt", "date", "createdAt", "creationTime")), 12, Color.argb(185,255,255,255), false); dt.setGravity(Gravity.CENTER); dt.setMaxLines(2); box.addView(dt, lp(-1,-2,0,4,0,0));
            final String finalFig = fig;
            box.setOnClickListener(v -> showClothesDialog(finalFig, niceDate(firstText(o, "changedAt", "date", "createdAt", "creationTime"))));
        }
        if (profileResult.stylesHasMore && !profileResult.stylesLoading) {
            View more = c.findViewWithTag("load_more_header_button");
            if (more != null) more.setOnClickListener(v -> loadMoreStyles(profileResult, stylesHsv));
        }
    }

    private void showClothesDialog(String figure, String date) {
        final Dialog dialog = new Dialog(this);

        LinearLayout rootDialog = new LinearLayout(this);
        rootDialog.setOrientation(LinearLayout.VERTICAL);
        rootDialog.setPadding(dp(18), dp(18), dp(18), dp(18));
        rootDialog.setBackground(round(dialogFillColor(), dp(22), dialogStrokeColor(), 1));
        dialog.setContentView(rootDialog);

        TextView title = text("Visuais — " + (date == null ? "" : date), 18, lightTheme ? Color.rgb(33,33,33) : Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        rootDialog.addView(title, lp(-1,-2,0,0,0,12));

        View line = new View(this);
        line.setBackgroundColor(lightTheme ? Color.rgb(220,220,220) : Color.argb(35,255,255,255));
        rootDialog.addView(line, lp(-1,1,6,0,6,12));

        final ScrollView clothesScroll = new ScrollView(this);
        clothesScroll.setVerticalScrollBarEnabled(true);
        clothesScroll.setScrollbarFadingEnabled(false);
        tintScrollBar(clothesScroll);
        clothesScroll.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        final LinearLayout clothesContainer = new LinearLayout(this);
        clothesContainer.setOrientation(LinearLayout.VERTICAL);
        clothesScroll.addView(clothesContainer, new ScrollView.LayoutParams(-1, -2));
        rootDialog.addView(clothesScroll, lp(-1, dp(390), 0, 0, 0, 14));

        LinearLayout loadingBox = new LinearLayout(this);
        loadingBox.setOrientation(LinearLayout.HORIZONTAL);
        loadingBox.setGravity(Gravity.CENTER);
        ProgressBar clothesSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        if (Build.VERSION.SDK_INT >= 21) clothesSpinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(purple));
        loadingBox.addView(clothesSpinner, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView loading = text("Carregando roupas...", 14, lightTheme ? Color.rgb(33,33,33) : Color.WHITE, false);
        LinearLayout.LayoutParams ltp = new LinearLayout.LayoutParams(-2, -2);
        ltp.leftMargin = dp(10);
        loadingBox.addView(loading, ltp);
        clothesContainer.addView(loadingBox, lp(-1,-2,0,18,0,18));

        Button close = new Button(this);
        close.setText("Fechar");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        close.setBackground(grad(dp(14), purple2, purple));
        rootDialog.addView(close, lp(-1, dp(48), 0, 0, 0, 0));
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(w.getAttributes());
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            w.setAttributes(params);
        }

        executor.execute(() -> {
            try {
                JSONObject data = unwrap(getJson(habbodexFigureUrl(figure)));
                final ArrayList<JSONObject> clothes = normalizeClothingEntries(data);
                runOnUiThread(() -> {
                    clothesContainer.removeAllViews();
                    if (clothes.isEmpty()) {
                        clothesContainer.addView(mottoItem("Nenhuma peça encontrada", ""));
                        return;
                    }
                    for (int i=0; i<Math.min(clothes.size(), 40); i++) {
                        clothesContainer.addView(clothingRow(clothes.get(i)));
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> loading.setText("Não foi possível carregar as peças."));
            }
        });
    }

    private LinearLayout clothingRow(JSONObject o) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(10),dp(12),dp(10)); row.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(26,255,255,255), dp(14), lightTheme ? Color.rgb(220,220,220) : Color.argb(28,255,255,255),1));
        row.setLayoutParams(lp(-1, -2, 0, 0, 0, 10));
        ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.FIT_CENTER); row.addView(img, new LinearLayout.LayoutParams(dp(40), dp(40)));
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

    private void enrichPhotoRoomInfo(ProfileResult r) {
        if (r == null || r.photos == null || r.photos.isEmpty()) return;
        HashMap<String, JSONObject> byRoom = new HashMap<>();
        if (r.rooms != null) for (JSONObject room : r.rooms) {
            String id = firstText(room, "id", "roomId", "room_id");
            if (!id.isEmpty()) byRoom.put(id, room);
        }
        for (JSONObject photo : r.photos) {
            String rid = getPhotoRoomId(photo);
            if (rid.isEmpty()) continue;
            JSONObject room = byRoom.get(rid);
            if (room != null) {
                try {
                    String rn = firstText(room, "name", "roomName", "caption", "title");
                    String ro = firstNestedText(room, "owner", "name");
                    String roFig = firstNestedText(room, "owner", "figureString");
                    if (roFig.isEmpty()) roFig = firstNestedText(room, "owner", "figure");
                    if (ro.isEmpty()) ro = firstText(room, "ownerName", "owner_name", "roomOwner");
                    if (roFig.isEmpty()) roFig = firstText(room, "ownerFigureString", "ownerFigure", "owner_figure_string");
                    if (!rn.isEmpty() && firstText(photo, "room_name", "roomName", "roomname").isEmpty()) photo.put("room_name", rn);
                    if (!ro.isEmpty() && getPhotoRoomOwnerName(photo).isEmpty()) photo.put("roomOwner", ro);
                    if (!roFig.isEmpty() && getPhotoRoomOwnerFigure(photo).isEmpty()) photo.put("roomOwnerFigureString", roFig);
                } catch(Exception ignored) {}
            }
            if (getPhotoRoomName(photo).isEmpty() || getPhotoRoomOwner(photo).isEmpty()) {
                JSONObject info = fetchRoomInfoById(rid);
                if (info != null) {
                    try {
                        String rn = firstText(info, "name", "roomName", "room_name", "caption", "title");
                        String ro = extractNameFromUnknown(info.opt("owner"));
                        if (ro.isEmpty()) ro = firstText(info, "ownerName", "owner_name", "roomOwner");
                        String roFig = extractFigureFromUnknown(info.opt("owner"));
                        if (roFig.isEmpty()) roFig = firstText(info, "ownerFigureString", "ownerFigure", "owner_figure_string");
                        if (!rn.isEmpty()) photo.put("room_name", rn);
                        if (!ro.isEmpty()) photo.put("roomOwner", ro);
                        if (!roFig.isEmpty()) photo.put("roomOwnerFigureString", roFig);
                    } catch(Exception ignored) {}
                }
            }
        }
    }

    private JSONObject fetchRoomInfoById(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) return null;
        String id = roomId.trim();
        String[] urls = new String[] {
            HABBODEX + "/roominfo/" + enc(habbodexHotelCode(currentHotelKey)) + "/room/" + enc(id),
            habboApiUrl("/api/public/rooms/" + enc(id)),
            "https://www.habbo.com/api/public/rooms/" + enc(id),
            HABBODEX + "/rooms/" + enc(habbodexHotelCode(currentHotelKey)) + "/" + enc(id),
            HABBODEX + "/room/" + enc(habbodexHotelCode(currentHotelKey)) + "/" + enc(id)
        };
        for (String url : urls) {
            try {
                JSONObject o = unwrap(getJson(url));
                if (o != null) return o;
            } catch(Exception ignored) {}
        }
        return null;
    }

    private String getPhotoRoomId(JSONObject photo) {
        String rid = firstText(photo, "roomId", "room_id", "roomid");
        if (!rid.isEmpty()) return rid;
        JSONObject room = photo == null ? null : photo.optJSONObject("room");
        if (room != null) rid = firstText(room, "id", "roomId", "room_id");
        return rid;
    }

    private String getPhotoRoomName(JSONObject photo) {
        String room = firstText(photo, "room_name", "roomName", "roomname");
        JSONObject roomObj = photo == null ? null : photo.optJSONObject("room");
        if (room.isEmpty() && roomObj != null) room = firstText(roomObj, "name", "roomName", "caption", "title");
        return room;
    }

    private String getPhotoRoomOwner(JSONObject photo) {
        return getPhotoRoomOwnerName(photo);
    }

    private String getPhotoRoomOwnerName(JSONObject photo) {
        if (photo == null) return "";
        String[] directKeys = {"roomOwner", "roomOwnerName", "ownerName", "owner_name"};
        for (String key : directKeys) {
            Object value = photo.opt(key);
            String name = extractNameFromUnknown(value);
            if (!name.isEmpty()) return name;
        }

        JSONObject roomObj = photo.optJSONObject("room");
        if (roomObj != null) {
            JSONObject owner = roomObj.optJSONObject("owner");
            String name = extractNameFromUnknown(owner);
            if (!name.isEmpty()) return name;

            for (String key : directKeys) {
                name = extractNameFromUnknown(roomObj.opt(key));
                if (!name.isEmpty()) return name;
            }
        }
        return "";
    }

    private String getPhotoRoomOwnerFigure(JSONObject photo) {
        if (photo == null) return "";
        String[] directKeys = {"roomOwnerFigureString", "ownerFigureString", "ownerFigure", "figureString", "figure"};

        for (String key : directKeys) {
            String figure = extractFigureFromUnknown(photo.opt(key));
            if (!figure.isEmpty()) return figure;
        }

        JSONObject roomObj = photo.optJSONObject("room");
        if (roomObj != null) {
            JSONObject owner = roomObj.optJSONObject("owner");
            String figure = extractFigureFromUnknown(owner);
            if (!figure.isEmpty()) return figure;

            for (String key : directKeys) {
                figure = extractFigureFromUnknown(roomObj.opt(key));
                if (!figure.isEmpty()) return figure;
            }
        }
        return "";
    }

    private String extractNameFromUnknown(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String name = firstText(o, "name", "username", "habboName", "ownerName");
            if (!name.isEmpty()) return name;
            JSONObject owner = o.optJSONObject("owner");
            if (owner != null) return extractNameFromUnknown(owner);
            return "";
        }
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) {
                String name = extractNameFromUnknown(a.opt(i));
                if (!name.isEmpty()) return name;
            }
            return "";
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return "";
        if ((s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]"))) {
            try {
                if (s.startsWith("{")) return extractNameFromUnknown(new JSONObject(s));
                return extractNameFromUnknown(new JSONArray(s));
            } catch (Exception ignored) {}
        }
        return s;
    }

    private String extractFigureFromUnknown(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String figure = firstText(o, "figureString", "figure_string", "figure", "avatarFigureString", "ownerFigureString");
            if (!figure.isEmpty()) return figure;
            JSONObject owner = o.optJSONObject("owner");
            if (owner != null) return extractFigureFromUnknown(owner);
            return "";
        }
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) {
                String figure = extractFigureFromUnknown(a.opt(i));
                if (!figure.isEmpty()) return figure;
            }
            return "";
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return "";
        if ((s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]"))) {
            try {
                if (s.startsWith("{")) return extractFigureFromUnknown(new JSONObject(s));
                return extractFigureFromUnknown(new JSONArray(s));
            } catch (Exception ignored) {}
        }
        return s.contains("-") ? s : "";
    }

    private String getRoomImageUrl(JSONObject room) {
        String url = normalizeUrl(firstText(room, "thumbnailUrl", "url"));
        return url == null ? "" : url.trim();
    }

    private void addPhotos(ProfileResult profileResult) {
        if (profileResult == null) return;
        ArrayList<JSONObject> list = profileResult.photos;
        if (list.isEmpty() && !profileResult.photosHasMore && !profileResult.photosLoading) return;
        final int loaded = list.size();
        final int totalLabel = Math.max(profileResult.photosTotal, loaded);
        LinearLayout c = sectionCardWithLoadMore("Fotos do usuário", loaded, totalLabel > 0 ? totalLabel : loaded, profileResult.photosHasMore || profileResult.photosLoading, profileResult.photosLoading, () -> loadMorePhotos(profileResult, null));
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(row);
        c.addView(hsv, lp(-1, dp(165), 0, 0, 0, 0));
        final HorizontalScrollView photosHsv = hsv;
        if (photosScrollX > 0) photosHsv.post(() -> photosHsv.scrollTo(photosScrollX, 0));
        for (int i=0; i<loaded; i++) {
            JSONObject o = list.get(i);
            String url = getPhotoUrl(o);
            String date = getPhotoTimestamp(o);
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(18,255,255,255), dp(16), lightTheme ? Color.rgb(220,220,220) : Color.argb(24,255,255,255), 1));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(160), dp(160)); bp.rightMargin = dp(12); row.addView(box, bp);
            ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); applyRoundedClip(img, dp(14)); box.addView(img, new LinearLayout.LayoutParams(-1, dp(112)));
            TextView dt = text(date, 12, Color.argb(190,255,255,255), false); dt.setGravity(Gravity.CENTER); box.addView(dt, lp(-1,-2,0,8,0,0));
            if (!url.isEmpty()) { loadImage(img, url); final JSONObject photoObj = o; box.setOnClickListener(v -> showPhotoDialog(photoObj)); }
        }
        if (profileResult.photosHasMore && !profileResult.photosLoading) {
            View more = c.findViewWithTag("load_more_header_button");
            if (more != null) more.setOnClickListener(v -> loadMorePhotos(profileResult, photosHsv));
        }
    }

    private TextView loadMoreButton(String label, int shown, int total) {
        TextView more = new TextView(this);
        more.setGravity(Gravity.CENTER);
        more.setTextColor(Color.WHITE);
        more.setPadding(0, 0, 0, 0);
        more.setBackground(new AddButtonDrawable());
        return more;
    }

    private String getPhotoUrl(JSONObject photo) {
        String url = firstText(photo, "previewUrl", "url", "imageUrl", "photoUrl");
        if (url.isEmpty()) url = findImageUrlDeep(photo);
        return normalizeUrl(url);
    }

    private String getPhotoTimestamp(JSONObject photo) {
        String formatted = firstText(photo, "formatted_time", "formattedTime");
        if (!formatted.isEmpty()) return formatted;
        return niceDate(firstText(photo, "creationTime", "time"));
    }

    private int getPhotoLikesCount(JSONObject photo) {
        return getPhotoLikerNames(photo).size();
    }

    private void showPhotoDialog(JSONObject photo) {
        String url = getPhotoUrl(photo);
        if (url.isEmpty()) return;

        final Dialog dialog = new Dialog(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        wrap.setBackground(round(dialogFillColor(), dp(22), dialogStrokeColor(), 1));
        dialog.setContentView(wrap);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(-1, -2);
        }

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        applyRoundedClip(img, dp(16));
        wrap.addView(img, lp(-1, dp(260), 0,0,0,12));
        loadImage(img, url);

        String room = getPhotoRoomName(photo);
        String ownerName = getPhotoRoomOwnerName(photo);
        String ownerFigure = getPhotoRoomOwnerFigure(photo);
        ArrayList<String> likers = getPhotoLikerNames(photo);

        LinearLayout infoGrid = new LinearLayout(this);
        infoGrid.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(infoGrid, lp(-1, -2, 0, 0, 0, 12));

        infoGrid.addView(photoInfoCard("Data", getPhotoTimestamp(photo), "", ""));
        if (!room.isEmpty()) infoGrid.addView(photoInfoCard("Quarto", room, "", ""));
        if (!ownerName.isEmpty()) {
            LinearLayout ownerCard = photoInfoCard("Dono", ownerName, ownerFigure, ownerName);
            ownerCard.setOnClickListener(v -> {
                dialog.dismiss();
                searchInput.setText(ownerName);
                search();
            });
            infoGrid.addView(ownerCard);
        }
        infoGrid.addView(photoInfoCard("Curtidas", String.valueOf(likers.size()), "", ""));

        if (!likers.isEmpty()) {
            TextView likesTitle = habboText("Quem curtiu", 17, true);
            likesTitle.setTextColor(lightTheme ? Color.rgb(33,33,33) : Color.WHITE);
            wrap.addView(likesTitle, lp(-1, -2, 0, 0, 0, 8));

            ScrollView likesScroll = new ScrollView(this);
            likesScroll.setVerticalScrollBarEnabled(true);
            likesScroll.setScrollbarFadingEnabled(false);
            tintScrollBar(likesScroll);
            likesScroll.setOnTouchListener((view, event) -> {
                view.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });

            LinearLayout likesList = new LinearLayout(this);
            likesList.setOrientation(LinearLayout.VERTICAL);
            likesScroll.addView(likesList, new ScrollView.LayoutParams(-1, -2));
            wrap.addView(likesScroll, lp(-1, dp(Math.min(230, Math.max(82, 54 * Math.min(likers.size(), 4)))), 0, 0, 0, 12));

            for (String liker : likers) {
                likesList.addView(likerRow(liker, dialog));
            }
        }

        Button close = new Button(this);
        close.setText("Fechar");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        close.setBackground(grad(dp(14), purple2, purple));
        wrap.addView(close, lp(-1, dp(46), 0, 0, 0, 0));
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(shownWindow.getAttributes());
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            shownWindow.setAttributes(params);
        }
    }

    private LinearLayout photoInfoCard(String label, String value, String figure, String nickToOpen) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(24,255,255,255), dp(15), lightTheme ? Color.rgb(220,220,220) : Color.argb(30,255,255,255), 1));
        row.setLayoutParams(lp(-1, -2, 0, 0, 0, 8));

        boolean hasHead = (figure != null && !figure.isEmpty()) || (nickToOpen != null && !nickToOpen.trim().isEmpty());
        if (hasHead) {
            ImageView head = new ImageView(this);
            head.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(head, new LinearLayout.LayoutParams(dp(42), dp(42)));
            if (figure != null && !figure.isEmpty()) loadImage(head, avatarHead(figure));
            else loadImage(head, avatarHeadByName(nickToOpen.trim()));
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        if (hasHead) tp.leftMargin = dp(10);
        row.addView(texts, tp);

        TextView lb = text(label, 12, Color.argb(185,255,255,255), false);
        texts.addView(lb);
        TextView val = habboText(value == null || value.isEmpty() ? "—" : value, 15, true);
        val.setTextColor(lightTheme ? Color.rgb(33,33,33) : Color.WHITE);
        val.setMaxLines(2);
        val.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(val);

        if (nickToOpen != null && !nickToOpen.trim().isEmpty()) {
            final String nick = nickToOpen.trim();
            row.setOnClickListener(v -> {
                searchInput.setText(nick);
                search();
            });
        }
        return row;
    }

    private LinearLayout likerRow(String nick, Dialog dialogToClose) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(10), dp(7));
        row.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(20,255,255,255), dp(14), lightTheme ? Color.rgb(220,220,220) : Color.argb(25,255,255,255), 1));
        row.setLayoutParams(lp(-1, -2, 0, 0, 0, 7));

        ImageView head = new ImageView(this);
        head.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(head, new LinearLayout.LayoutParams(dp(42), dp(42)));
        loadImage(head, avatarHeadByName(nick));

        TextView name = habboText(nick, 15, true);
        name.setTextColor(lightTheme ? Color.rgb(33,33,33) : Color.WHITE);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1);
        np.leftMargin = dp(10);
        row.addView(name, np);

        row.setOnClickListener(v -> {
            if (dialogToClose != null) dialogToClose.dismiss();
            searchInput.setText(nick);
            search();
        });

        return row;
    }

    private ArrayList<String> getPhotoLikerNames(JSONObject photo) {
        ArrayList<String> names = new ArrayList<>();
        if (photo == null) return names;

        Object raw = photo.opt("likerNames");
        addLikerNamesFromUnknown(names, raw);

        if (names.isEmpty()) addLikerNamesFromUnknown(names, photo.opt("likes"));
        if (names.isEmpty()) addLikerNamesFromUnknown(names, photo.opt("likers"));

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String n : names) {
            String clean = n == null ? "" : n.trim();
            if (!clean.isEmpty() && !"null".equalsIgnoreCase(clean)) unique.add(clean);
        }
        return new ArrayList<>(unique);
    }

    private void addLikerNamesFromUnknown(ArrayList<String> out, Object raw) {
        if (out == null || raw == null || raw == JSONObject.NULL) return;

        if (raw instanceof JSONArray) {
            JSONArray a = (JSONArray) raw;
            for (int i = 0; i < a.length(); i++) addLikerNamesFromUnknown(out, a.opt(i));
            return;
        }

        if (raw instanceof JSONObject) {
            String name = extractNameFromUnknown(raw);
            if (!name.isEmpty()) out.add(name);
            return;
        }

        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return;

        if (s.startsWith("[") || s.startsWith("{")) {
            try {
                if (s.startsWith("[")) addLikerNamesFromUnknown(out, new JSONArray(s));
                else addLikerNamesFromUnknown(out, new JSONObject(s));
                return;
            } catch (Exception ignored) {}
        }

        out.add(s);
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
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(4), dp(8), dp(8));
        card.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(20,255,255,255), dp(18), (removed || currentProfilePrivate) ? Color.argb(75, 255, 64, 64) : (lightTheme ? Color.rgb(220,220,220) : Color.argb(25,255,255,255)), 1));

        String n = firstText(f, "name", "username", "habboName"); if (n.isEmpty()) n = "Habbo";
        String fig = firstText(f, "figureString", "figure", "look", "avatarFigureString");
        String date = firstText(f, "creationTime", "friendSince", "createdAt", "date", "removedAt");

        FrameLayout headWrap = new FrameLayout(this);
        card.addView(headWrap, new LinearLayout.LayoutParams(-1, dp(64)));

        ImageView head = new ImageView(this);
        head.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, dp(62), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hp.bottomMargin = dp(-2);
        headWrap.addView(head, hp);
        if (!fig.isEmpty()) loadImage(head, avatarHead(fig));

        if (isToday(date)) {
            TextView novo = text("NOVO", 9, Color.WHITE, true);
            novo.setGravity(Gravity.CENTER);
            novo.setBackground(removed
                ? grad(dp(999), Color.rgb(190, 45, 58), Color.rgb(255, 92, 92))
                : grad(dp(999), Color.rgb(31,184,106), Color.rgb(54,210,127)));
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(dp(48), dp(18), Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            headWrap.addView(novo,np);
        }
        if (optBoolAny(f, false, "online", "isOnline")) {
            IconView dot = new IconView(this, "dot");
            FrameLayout.LayoutParams dpv = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.RIGHT|Gravity.TOP);
            dpv.topMargin=dp(8); dpv.rightMargin=dp(8);
            headWrap.addView(dot, dpv);
        }

        TextView name = habboText(n, 14, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(name, lp(-1,-2,0,2,0,6));

        TextView d = text(niceDate(date), 12, Color.argb(185,255,255,255), false);
        d.setGravity(Gravity.CENTER);
        card.addView(d, lp(-1,-2,0,0,0,0));

        final String fname = n;
        card.setOnClickListener(v -> { searchInput.setText(fname); search(); });
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
        render[0] = () -> { content.removeAllViews(); btRooms.setBackground(old[0]?tabBg(false):tabBg(true)); btRooms.setTextColor(old[0]?Color.argb(150,255,255,255):Color.WHITE); btOld.setBackground(old[0]?tabBg(true):tabBg(false)); btOld.setTextColor(old[0]?Color.WHITE:Color.argb(150,255,255,255)); ArrayList<JSONObject> data=old[0]?oldRooms:rooms; renderRoomsPage(content,data,page[0],4,old[0]); renderPager(content,data.size(),4,page,render[0]); };
        btRooms.setOnClickListener(v->{old[0]=false;page[0]=1;render[0].run();}); btOld.setOnClickListener(v->{old[0]=true;page[0]=1;render[0].run();}); render[0].run();
    }

    private void renderRoomsPage(LinearLayout content, ArrayList<JSONObject> list, int page, int per, boolean oldRoom) {
        if (list.isEmpty()) { content.addView(centerNote("Nenhum quarto encontrado.")); return; }
        int start=(page-1)*per, end=Math.min(list.size(), start+per);
        for (int i=start;i<end;i++) content.addView(roomRow(list.get(i), oldRoom));
    }

    private LinearLayout roomRow(JSONObject room, boolean oldRoom) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(10),dp(12),dp(10)); row.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(18,255,255,255), dp(16), (oldRoom || currentProfilePrivate) ? Color.argb(75, 255, 64, 64) : (lightTheme ? Color.rgb(220,220,220) : Color.argb(24,255,255,255)), 1)); row.setLayoutParams(lp(-1, dp(116), 0, 0, 0, 12));
        ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setBackground(round(lightTheme ? Color.rgb(245,245,245) : Color.argb(25,255,255,255), dp(12), lightTheme ? Color.rgb(220,220,220) : Color.argb(20,255,255,255),1)); applyRoundedClip(img, dp(12)); row.addView(img, new LinearLayout.LayoutParams(dp(112), dp(78)));
        String image = getRoomImageUrl(room);
        if (!image.isEmpty()) Glide.with(this).load(image).error(R.drawable.quarto).into(img); else img.setImageResource(R.drawable.quarto);
        LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,-2,1); tp.leftMargin=dp(12); row.addView(txt,tp);
        TextView roomName = habboText(firstText(room,"name","roomName","caption","title").isEmpty()?"Quarto":firstText(room,"name","roomName","caption","title"), 16, true); roomName.setMaxLines(1); roomName.setEllipsize(TextUtils.TruncateAt.END); txt.addView(roomName);
        String score = firstText(room,"score","rating"); String date = niceDate(firstText(room,"createdAt","creationTime","date"));
        TextView meta = habboText("•  " + emptyDash(score) + "   " + date, 13, false);
        meta.setTextColor(lightTheme ? Color.rgb(97,97,97) : Color.argb(215,255,255,255));
        txt.addView(meta);
        String desc = firstText(room,"description","desc"); if(!desc.isEmpty()) { TextView rd = habboText(desc, 13, false); rd.setTextColor(Color.argb(210,255,255,255)); rd.setMaxLines(1); rd.setEllipsize(TextUtils.TruncateAt.END); txt.addView(rd); }
        return row;
    }

    private void addGroups(ArrayList<JSONObject> list) {
        if (list.isEmpty()) return;
        LinearLayout c = sectionCard("Grupos", list.size(), true);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(true);
        sv.setScrollbarFadingEnabled(false);
        tintScrollBar(sv);
        sv.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        sv.addView(inner, new ScrollView.LayoutParams(-1, -2));
        c.addView(sv, lp(-1, dp(Math.min(430, Math.max(120, 98 * Math.min(list.size(), 4)))), 0, 0, 0, 0));

        for (int i=0; i<Math.min(list.size(), 60); i++) {
            inner.addView(groupRow(list.get(i)));
        }
    }

    private LinearLayout groupRow(JSONObject g) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(12),dp(12),dp(12)); row.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(18,255,255,255), dp(16), currentProfilePrivate ? Color.argb(75, 255, 64, 64) : (lightTheme ? Color.rgb(220,220,220) : Color.argb(24,255,255,255)), 1)); row.setLayoutParams(lp(-1, -2, 0, 0, 0, 12));
        ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.FIT_CENTER); row.addView(img, new LinearLayout.LayoutParams(dp(58), dp(58)));
        String badge = firstText(g,"badgeCode","code"); String badgeUrl = normalizeUrl(firstText(g, "badgeUrl", "imageUrl", "url")); if(!badgeUrl.isEmpty()) loadImage(img, badgeUrl); else if(!badge.isEmpty()) loadImage(img,habboImagingUrl("/habbo-imaging/badge/"+enc(badge)+".gif")); else img.setImageDrawable(new PlaceholderDrawable("groups"));
        LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1); tp.leftMargin=dp(12); row.addView(txt,tp);
        TextView groupName = habboText(firstText(g,"name","groupName").isEmpty()?"Grupo":firstText(g,"name","groupName"), 17, true); groupName.setMaxLines(1); groupName.setEllipsize(TextUtils.TruncateAt.END); txt.addView(groupName);
        String desc=firstText(g,"description","desc"); if(!desc.isEmpty()) { TextView gd = habboText(desc, 14, false); gd.setTextColor(Color.argb(220,255,255,255)); gd.setMaxLines(2); gd.setEllipsize(TextUtils.TruncateAt.END); txt.addView(gd); }
        txt.addView(text(niceDate(firstText(g,"createdAt","creationTime","date")), 13, Color.argb(190,255,255,255), false));
        return row;
    }

    private LinearLayout sectionCard(String title, int count, boolean showTitle) {
        LinearLayout c = card(dp(20));
        applyProfilePrivateBorder(c, dp(20));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 18));
        if (showTitle && title != null) {
            TextView t = habboText(title + " (" + count + ")", 20, true); c.addView(t, lp(-1, -2, 0, 0, 0, 14));
        }
        return c;
    }

    private LinearLayout sectionCardWithLoadMore(String title, int shown, int total, boolean showButton, boolean loading, final Runnable action) {
        LinearLayout c = card(dp(20));
        applyProfilePrivateBorder(c, dp(20));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 18));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = habboText(title + " (" + shown + "/" + Math.max(shown, total) + ")", 20, true);
        header.addView(t, new LinearLayout.LayoutParams(0, -2, 1));

        if (showButton) {
            FrameLayout more = new FrameLayout(this);
            more.setTag("load_more_header_button");
            more.setBackground(new AddButtonDrawable());
            more.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(28), dp(28));
            mp.leftMargin = dp(8);
            header.addView(more, mp);

            if (loading) {
                more.setBackground(grad(dp(7), purple2, purple));
                ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
                if (Build.VERSION.SDK_INT >= 21) pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
                FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER);
                more.addView(pb, pp);
            } else if (action != null) {
                more.setOnClickListener(v -> action.run());
            }
        }

        c.addView(header, lp(-1, dp(38), 0, 0, 0, 12));
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
            JSONObject payload = unwrap(getJson(habbodexSuggestUrl(query)));
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
        row.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(24,255,255,255), dp(14), lightTheme ? Color.rgb(220,220,220) : Color.argb(30,255,255,255), 1));
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
        inlineProgressMessage = message == null ? "" : message;
        inlineProgressPct = loadingProgressFor(message);
        statusText.setText("");
    }

    private View inlineProgressBar(int pct) {
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(round(lightTheme ? Color.rgb(232,232,232) : Color.argb(34,255,255,255), dp(999), lightTheme ? Color.rgb(216,216,216) : Color.argb(28,255,255,255), 1));

        View fill = new View(this);
        fill.setBackground(grad(dp(999), purple2, purple));
        int available = Math.max(dp(80), getResources().getDisplayMetrics().widthPixels - dp(72));
        int width = Math.max(dp(22), (int)(available * (Math.max(0, Math.min(100, pct)) / 100f)));
        bar.addView(fill, new FrameLayout.LayoutParams(width, dp(9), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        return bar;
    }

    
    private LinearLayout loadingProgressCard(String message, int pct) {
        LinearLayout card = card(dp(18));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, lp(-1, -2, 0, 0, 0, 10));

        ProgressBar spinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        if (Build.VERSION.SDK_INT >= 21) {
            spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(purple));
        }
        row.addView(spinner, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView tv = text(message == null ? "Carregando..." : message, 13, Color.argb(230,255,255,255), true);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        tp.leftMargin = dp(10);
        row.addView(tv, tp);

        card.addView(inlineProgressBar(Math.max(8, pct)), lp(-1, dp(8), 0, 0, 0, 0));
        return card;
    }

private int loadingProgressFor(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (m.contains("detalhes")) return 20;
        if (m.contains("histórico") || m.contains("historico")) return 42;
        if (m.contains("visuais") || m.contains("amigos")) return 66;
        if (m.contains("quartos") || m.contains("grupos")) return 86;
        return 10;
    }

    private void showLoadingSkeleton(String message) {
        resultWrap.removeAllViews();

        LinearLayout c = card(dp(22));
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        resultWrap.addView(c, lp(-1, -2, 0, 0, 0, 18));

        TextView title = habboText(message, 18, true);
        title.setGravity(Gravity.CENTER);
        c.addView(title, lp(-1,-2,0,0,0,8));
        ProgressBar skeletonSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        if (Build.VERSION.SDK_INT >= 21) skeletonSpinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(purple));
        LinearLayout spinnerLine = new LinearLayout(this);
        spinnerLine.setGravity(Gravity.CENTER);
        spinnerLine.addView(skeletonSpinner, new LinearLayout.LayoutParams(dp(30), dp(30)));
        c.addView(spinnerLine, lp(-1, dp(34), 0,0,0,12));

        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.rgb(15, 8, 25), dp(20), lightTheme ? Color.rgb(220,220,220) : Color.argb(24,255,255,255), 1));
        c.addView(avatar, lp(-1, dp(280), 0,0,0,16));

        ImageView walker = new ImageView(this);
        walker.setScaleType(ImageView.ScaleType.FIT_CENTER);
        walker.setPadding(dp(20), dp(10), dp(20), dp(84));
        avatar.addView(walker, new FrameLayout.LayoutParams(-1, -1));
        String nick = searchInput == null ? "" : searchInput.getText().toString().trim();
        String cachedFigure = "";
        ProfileResult cachedProfile = getCachedProfile(nick);
        if (cachedProfile != null) cachedFigure = cachedProfile.figure;
        if (cachedFigure == null || cachedFigure.trim().isEmpty()) {
            cachedFigure = "hr-831-45.hd-180-1.ch-255-92.lg-280-82.sh-290-80";
        }
        String walkerUrl;
        if (nick != null && !nick.trim().isEmpty()) {
            walkerUrl = habboImagingUrl("/habbo-imaging/avatarimage?user=" + enc(nick.trim()) + "&direction=2&head_direction=2&img_format=png&headonly=0&size=l");
        } else {
            walkerUrl = habboImagingUrl("/habbo-imaging/avatarimage?figure=" + enc(cachedFigure) + "&direction=2&head_direction=2&headonly=0&size=l&img_format=png");
        }
        String fallbackUrl = habboImagingUrl("/habbo-imaging/avatarimage?figure=" + enc(cachedFigure) + "&direction=2&head_direction=2&headonly=0&size=l&img_format=png");
        try {
            Glide.with(this).load(walkerUrl).error(Glide.with(this).load(fallbackUrl)).into(walker);
        } catch (Exception ex) {
            loadImage(walker, fallbackUrl);
        }

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        c.addView(grid, lp(-1, -2, 0, 0, 0, 0));
        grid.addView(skeletonLine(dp(180), dp(28), true));
        grid.addView(skeletonLine(-1, dp(16), false));
        grid.addView(skeletonLine(-1, dp(16), false));

        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(row, lp(-1, dp(58), 0, 6, 0, 8));
            for (int col = 0; col < 2; col++) {
                LinearLayout mini = new LinearLayout(this);
                mini.setOrientation(LinearLayout.HORIZONTAL);
                mini.setGravity(Gravity.CENTER_VERTICAL);
                mini.setPadding(dp(10), dp(7), dp(10), dp(7));
                mini.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(22,255,255,255), dp(16), lightTheme ? Color.rgb(220,220,220) : Color.argb(24,255,255,255), 1));
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -1, 1);
                if (col == 1) mp.leftMargin = dp(8);
                row.addView(mini, mp);
                mini.addView(skeletonBlock(dp(24), dp(24), dp(999)));
                LinearLayout lines = new LinearLayout(this);
                lines.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lpLines = new LinearLayout.LayoutParams(0, -2, 1);
                lpLines.leftMargin = dp(9);
                mini.addView(lines, lpLines);
                lines.addView(skeletonLine(dp(70), dp(10), false));
                lines.addView(skeletonLine(dp(110), dp(14), false));
            }
        }
    }

    private View skeletonLine(int width, int height, boolean centered) {
        View v = skeletonBlock(width < 0 ? -1 : width, height, dp(999));
        LinearLayout.LayoutParams p = lp(width < 0 ? -1 : width, height, centered ? 40 : 0, 0, centered ? 40 : 0, 10);
        v.setLayoutParams(p);
        return v;
    }

    private View skeletonBlock(int width, int height, int radius) {
        View v = new View(this);
        v.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(28,255,255,255), radius, lightTheme ? Color.rgb(220,220,220) : Color.argb(18,255,255,255), 1));
        v.setAlpha(0.72f);
        v.animate().alpha(1f).setDuration(650).withEndAction(() -> v.animate().alpha(0.55f).setDuration(650).withEndAction(() -> pulseSkeleton(v)).start()).start();
        v.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return v;
    }

    private void pulseSkeleton(View v) {
        if (v == null || v.getWindowToken() == null) return;
        v.animate().alpha(1f).setDuration(650).withEndAction(() -> {
            if (v.getWindowToken() != null) v.animate().alpha(0.55f).setDuration(650).withEndAction(() -> pulseSkeleton(v)).start();
        }).start();
    }

    private void startFloating(View v) {
        if (v == null) return;
        v.setTranslationY(dp(5));
        v.animate().translationY(-dp(7)).setDuration(900).withEndAction(() -> {
            if (v.getWindowToken() != null) v.animate().translationY(dp(5)).setDuration(900).withEndAction(() -> startFloating(v)).start();
        }).start();
    }

    private String habbodexProfileByNameUrl(String name) {
        return HABBODEX + "/habboinfo/" + enc(habbodexHotelCode(currentHotelKey)) + "/habbo?name=" + enc(name);
    }

    private String habbodexProfileByUniqueUrl(String uniqueId) {
        return HABBODEX + "/habboinfo/" + enc(uniqueId) + "?hotel=" + enc(habbodexHotelCode(currentHotelKey));
    }

    private String habbodexEndpointUrl(String uniqueId, String endpoint, int page, int limit) {
        return HABBODEX + "/habboinfo/" + enc(uniqueId) + "/" + enc(endpoint) + "?page=" + page + "&limit=" + limit + "&hotel=" + enc(habbodexHotelCode(currentHotelKey));
    }

    private String habbodexFigureUrl(String figure) {
        return HABBODEX + "/furnidex/furni/from-figure-string?figureString=" + enc(figure);
    }

    private String habbodexSuggestUrl(String name) {
        return HABBODEX + "/habboinfo/habbos?name=" + enc(name) + "&includePreviousNames=true&hotel=" + enc(habbodexHotelCode(currentHotelKey));
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
    private void addUnique(ArrayList<JSONObject> out, HashSet<String> seen, ArrayList<JSONObject> src) { for (JSONObject o : src) { String key = stableItemKey(o); if (seen.add(key)) out.add(o); } }
    private String stableItemKey(JSONObject o) {
        if (o == null) return String.valueOf(System.identityHashCode(o));
        String key = firstText(o, "uniqueId", "id", "badgeCode", "code");
        if (!key.isEmpty()) return key;
        String figure = firstText(o, "figureString", "figure");
        String when = firstText(o, "changedAt", "date", "createdAt", "creationTime", "time");
        if (!figure.isEmpty() || !when.isEmpty()) return "fig:" + figure + "|" + when;
        String name = firstText(o, "name", "username", "habboName", "motto");
        if (!name.isEmpty() || !when.isEmpty()) return "txt:" + name + "|" + when;
        return String.valueOf(o.toString().hashCode());
    }
    private String firstText(JSONObject o, String... keys) { if (o == null) return ""; for (String k : keys) { Object v = o.opt(k); if (v == null || v == JSONObject.NULL) continue; String s = String.valueOf(v).trim(); if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s; } return ""; }
    private boolean optBoolTrue(JSONObject o, String... keys) { if (o == null) return false; for (String k : keys) { if (!o.has(k)) continue; Object v = o.opt(k); if (v instanceof Boolean) return ((Boolean)v); String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT); if (s.equals("true") || s.equals("1") || s.equals("yes")) return true; } return false; }

    private boolean optBoolAny(JSONObject o, boolean fallback, String... keys) { if (o == null) return fallback; for (String k : keys) if (o.has(k)) return o.optBoolean(k, fallback); return fallback; }

    private String avatarFull(String figure) { return avatarFull(figure, 2); }
    private String avatarFull(String figure, int direction) { return habboImagingUrl("/habbo-imaging/avatarimage?figure=" + enc(figure) + "&size=l&direction=" + direction + "&head_direction=" + direction + "&gesture=std&action=std&headonly=0"); }
    private String avatarSmall(String figure) { return habboImagingUrl("/habbo-imaging/avatarimage?figure=" + enc(figure) + "&size=m&direction=2&head_direction=2&gesture=sml&action=std&headonly=0"); }
    private String avatarHead(String figure) { return habboImagingUrl("/habbo-imaging/avatarimage?figure=" + enc(figure) + "&size=m&direction=2&head_direction=2&headonly=1"); }
    private String avatarHeadByName(String name) { return avatarHeadByNameForHotel(name, currentHotelKey); }
    private String avatarHeadByNameForHotel(String name, String hotelKey) { return "https://" + hotelDomain(hotelKey) + "/habbo-imaging/avatarimage?user=" + enc(name) + "&size=m&direction=2&head_direction=2&headonly=1"; }

    private Drawable makeBg() {
        if (lightTheme) return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(250, 250, 250), Color.rgb(242, 242, 242), Color.rgb(247, 247, 247)});
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(30, 11, 45), Color.rgb(24,14,35), Color.rgb(12,12,18)});
    }
    private LinearLayout card(int radius) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int stroke = currentProfilePrivate ? Color.argb(112, 211, 47, 47) : (lightTheme ? Color.rgb(216, 216, 216) : cardStroke);
        int fill = lightTheme ? Color.rgb(255,255,255) : cardFill;
        l.setBackground(round(fill, radius, stroke, 1));
        return l;
    }
    private void applyProfilePrivateBorder(LinearLayout view, int radius) {
        if (currentProfilePrivate && view != null) {
            view.setBackground(round(lightTheme ? Color.WHITE : cardFill, radius, Color.argb(112, 211, 47, 47), 1));
        }
    }
    private int themeTextColor(int color) {
        if (!lightTheme) return color;
        if (Color.alpha(color) < 255) {
            return Color.rgb(95, 95, 95);
        }
        if (color == Color.WHITE || (Color.red(color) > 180 && Color.green(color) > 180 && Color.blue(color) > 180)) {
            return Color.rgb(33, 33, 33);
        }
        return color;
    }
    private int themeMutedColor() { return lightTheme ? Color.rgb(97, 97, 97) : muted; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s == null ? "" : s); v.setTextSize(sp); v.setTextColor(themeTextColor(color)); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView habboText(String s, int sp, boolean bold) { TextView v = text(s, sp, lightTheme ? Color.rgb(33, 33, 33) : Color.WHITE, bold); v.setTypeface(habboFont); return v; }
    private TextView pill(String s, int color) { TextView v = text(s, 13, Color.WHITE, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(14), dp(9), dp(14), dp(9)); v.setBackground(round(adjustAlpha(color, 0.32f), dp(999), adjustAlpha(color,0.55f), 1)); return v; }
    private GradientDrawable round(int fill, int radius, int stroke, int sw) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(radius); if (sw > 0) d.setStroke(dp(sw), stroke); return d; }
    private GradientDrawable grad(int radius, int c1, int c2) { GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1,c2}); d.setCornerRadius(radius); return d; }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private int adjustAlpha(int color, float f) { return Color.argb(Math.round(Color.alpha(color)*f), Color.red(color), Color.green(color), Color.blue(color)); }
    private String enc(String s) { try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch(Exception e){ return s; } }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard(){ try{ ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(searchInput.getWindowToken(),0);}catch(Exception ignored){} }
    private void clearSearchFocus(){ try { if (searchInput != null) { searchInput.clearFocus(); searchInput.setCursorVisible(false); hideKeyboard(); } } catch(Exception ignored) {} }
    private boolean isTouchInsideView(View view, MotionEvent event) {
        if (view == null || event == null) return false;
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= loc[0] && x <= loc[0] + view.getWidth() && y >= loc[1] && y <= loc[1] + view.getHeight();
    }
    private void openUrl(String url){ try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch(Exception ignored){} }
    private String normalizeUrl(String url) { String s = url == null ? "" : url.trim(); if (s.startsWith("//")) return "https:" + s; if (s.startsWith("/")) return "https://atoxic.com.br" + s; return s; }

    private String emptyDash(String s) { return s == null || s.trim().isEmpty() ? "—" : s.trim(); }

    private Date parseHabboDate(String in) {
        if (in == null || in.trim().isEmpty()) return null;
        String s = in.trim();
        try {
            if (s.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                SimpleDateFormat only = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                only.setTimeZone(TimeZone.getTimeZone("UTC"));
                return only.parse(s);
            }
            if (s.matches("^\\d{10,13}$")) {
                long ts = Long.parseLong(s);
                if (s.length() == 10) ts *= 1000;
                return new Date(ts);
            }
            String iso = s.replace("Z", "+0000").replaceAll("([+-]\\d{2}):(\\d{2})$", "$1$2");
            String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss"};
            for (String pattern : patterns) {
                try {
                    SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
                    f.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return f.parse(iso);
                } catch(Exception ignored) {}
            }
        } catch(Exception ignored) {}
        return null;
    }

    private String niceDate(String in) {
        Date d = parseHabboDate(in);
        if (d == null) {
            if (in == null || in.trim().isEmpty()) return "—";
            return in.trim().replace('T',' ').replace("Z", "");
        }
        SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy, HH:mm", new Locale("pt", "BR"));
        out.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        return out.format(d);
    }

    private String niceDateOnly(String in) {
        Date d = parseHabboDate(in);
        if (d == null) {
            if (in == null || in.trim().isEmpty()) return "—";
            String clean = in.trim().replace('T',' ').replace("Z", "");
            return clean.length() >= 10 ? clean.substring(0, 10) : clean;
        }
        SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR"));
        out.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        return out.format(d);
    }

    private String timeAgoText(String in) {
        Date d = parseHabboDate(in);
        if (d == null) return "";
        long diff = Math.max(0L, System.currentTimeMillis() - d.getTime()) / 1000L;
        long value;
        String unit;
        if (diff < 60) { value = Math.max(1, diff); unit = value == 1 ? "segundo" : "segundos"; }
        else if (diff < 3600) { value = diff / 60; unit = value == 1 ? "minuto" : "minutos"; }
        else if (diff < 86400) { value = diff / 3600; unit = value == 1 ? "hora" : "horas"; }
        else if (diff < 604800) { value = diff / 86400; unit = value == 1 ? "dia" : "dias"; }
        else if (diff < 2629800) { value = diff / 604800; unit = value == 1 ? "semana" : "semanas"; }
        else if (diff < 31557600) { value = diff / 2629800; unit = value == 1 ? "mês" : "meses"; }
        else { value = diff / 31557600; unit = value == 1 ? "ano" : "anos"; }
        return "há " + value + " " + unit;
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


    private String profileCacheKey(String raw, String hotelKey) {
        String key = normalizeNickKey(raw);
        if (key.isEmpty()) return "";
        String hotel = normalizeHotelKey(hotelKey);
        if (hotel.isEmpty()) hotel = currentHotelKey;
        return hotel + ":" + key;
    }

    private ProfileResult getCachedProfile(String nickKey) {
        String key = profileCacheKey(nickKey, currentHotelKey);
        if (key.isEmpty()) return null;
        ProfileResult cached = profileCache.get(key);
        Long cachedAt = profileCacheTimes.get(key);
        if (cached == null || cachedAt == null) return null;
        if (System.currentTimeMillis() - cachedAt > SESSION_CACHE_TTL_MS) {
            profileCache.remove(key);
            profileCacheTimes.remove(key);
            return null;
        }
        return cached;
    }

    private void putProfileCache(ProfileResult r, String aliasKey) {
        if (r == null) return;
        cleanupSessionProfileCache();
        String hotel = normalizeHotelKey(r.hotelKey);
        if (hotel.isEmpty()) hotel = currentHotelKey;
        String alias = profileCacheKey(aliasKey, hotel);
        long now = System.currentTimeMillis();
        if (!alias.isEmpty()) { profileCache.put(alias, r); profileCacheTimes.put(alias, now); }
        String nameKey = profileCacheKey(r.name, hotel);
        if (!nameKey.isEmpty()) { profileCache.put(nameKey, r); profileCacheTimes.put(nameKey, now); }
        String searchedKey = profileCacheKey(r.searchedNick, hotel);
        if (!searchedKey.isEmpty()) { profileCache.put(searchedKey, r); profileCacheTimes.put(searchedKey, now); }
        String idKey = profileCacheKey(r.uniqueId, hotel);
        if (!idKey.isEmpty()) { profileCache.put(idKey, r); profileCacheTimes.put(idKey, now); }
    }

    private void cleanupSessionProfileCache() {
        long now = System.currentTimeMillis();
        ArrayList<String> expired = new ArrayList<>();
        for (Map.Entry<String, Long> e : profileCacheTimes.entrySet()) {
            if (now - e.getValue() > SESSION_CACHE_TTL_MS) expired.add(e.getKey());
        }
        for (String k : expired) {
            profileCache.remove(k);
            profileCacheTimes.remove(k);
        }
    }

    private File profileCacheDir() {
        File dir = new File(getFilesDir(), "habbo_profile_cache");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File profileCacheFile(String key) {
        String safe = normalizeNickKey(key).replaceAll("[^a-z0-9._-]", "_");
        if (safe.isEmpty()) safe = "profile";
        return new File(profileCacheDir(), safe + ".json");
    }

    private ProfileResult loadProfileCache(String key) {
        try {
            File f = profileCacheFile(key);
            if (!f.isFile()) return null;
            int days = getCacheDaysSetting();
            if (days > 0) {
                long age = System.currentTimeMillis() - f.lastModified();
                if (age > days * 86400000L) {
                    f.delete();
                    return null;
                }
            }
            String raw = readFile(f);
            if (raw == null || raw.trim().isEmpty()) return null;
            return profileFromJson(new JSONObject(raw));
        } catch (Exception ignored) { return null; }
    }

    private void saveProfileCache(ProfileResult r, String aliasKey) {
        // Cache apenas de sessão: gravar em disco foi removido para evitar dados pesados/desatualizados.
    }

    private void cleanupProfileCache() {
        try {
            File[] files = profileCacheDir().listFiles();
            if (files == null || files.length == 0) return;
            long now = System.currentTimeMillis();
            int days = getCacheDaysSetting();
            for (File f : files) {
                if (f.isFile() && days > 0 && now - f.lastModified() > days * 86400000L) f.delete();
            }
            files = profileCacheDir().listFiles();
            if (files == null) return;
            Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
            int maxProfiles = getMaxProfilesSetting();
            for (int i = maxProfiles; i < files.length; i++) if (files[i].isFile()) files[i].delete();
            int maxMb = getMaxCacheMbSetting();
            if (maxMb > 0) {
                long maxBytes = maxMb * 1024L * 1024L;
                files = profileCacheDir().listFiles();
                if (files == null) return;
                Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
                long total = cacheDirSize(profileCacheDir());
                for (int i = files.length - 1; i >= 0 && total > maxBytes; i--) {
                    if (files[i].isFile()) {
                        long len = files[i].length();
                        if (files[i].delete()) total -= len;
                    }
                }
            }
        } catch(Exception ignored) {}
    }


    private int getMaxProfilesSetting() {
        return 50;
    }

    private int getCacheDaysSetting() {
        return 1;
    }

    private int getMaxCacheMbSetting() {
        return 0;
    }

    private long cacheDirSize(File dir) {
        long total = 0;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) total += cacheDirSize(f);
            else total += Math.max(0, f.length());
        }
        return total;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }

    private String cacheStatsText() {
        cleanupSessionProfileCache();
        return "Perfis na sessão: " + profileCache.size() + "\nCache do app: " + formatBytes(cacheDirSize(getCacheDir()) + cacheDirSize(profileCacheDir()));
    }

    private void rebuildUiPreservingProfile() {
        ProfileResult keep = activeRenderedProfile;
        buildUi();
        if (keep != null) renderProfile(keep);
    }

    private void clearProfileCache() {
        profileCache.clear();
        profileCacheTimes.clear();
        try { Glide.get(this).clearMemory(); } catch (Exception ignored) {}
        executor.execute(() -> {
            try { Glide.get(MainActivity.this).clearDiskCache(); } catch (Exception ignored) {}
            deleteContents(profileCacheDir(), true);
            deleteContents(getCacheDir(), false);
            if (Build.VERSION.SDK_INT >= 21) deleteContents(getCodeCacheDir(), false);
            try { File ext = getExternalCacheDir(); if (ext != null) deleteContents(ext, false); } catch(Exception ignored) {}
        });
    }

    private void deleteContents(File dir, boolean deleteRoot) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f == null) continue;
                if (f.isDirectory()) deleteContents(f, true);
                else { try { f.delete(); } catch(Exception ignored) {} }
            }
        }
        if (deleteRoot) { try { dir.delete(); } catch(Exception ignored) {} }
    }

    private int dialogFillColor() { return lightTheme ? Color.rgb(255,255,255) : Color.rgb(28, 18, 42); }
    private int dialogStrokeColor() { return lightTheme ? Color.rgb(216,216,216) : Color.argb(42,255,255,255); }

    private void loadOpenedProfilesHistory() {
        openedProfilesHistory.clear();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_OPENED_HISTORY, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length() && openedProfilesHistory.size() < 50; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String nick = o.optString("nick", "").trim();
                if (nick.isEmpty()) continue;
                String hotel = normalizeHotelKey(o.optString("hotel", "br"));
                if (hotel.isEmpty()) hotel = "br";
                openedProfilesHistory.add(new ProfileHistoryItem(nick, o.optString("figure", ""), hotel));
            }
        } catch(Exception ignored) {}
    }

    private void saveOpenedProfilesHistory() {
        JSONArray arr = new JSONArray();
        try {
            for (ProfileHistoryItem item : openedProfilesHistory) {
                JSONObject o = new JSONObject();
                o.put("nick", item.nick);
                o.put("figure", item.figure);
                String hotel = normalizeHotelKey(item.hotelKey);
                o.put("hotel", hotel.isEmpty() ? "br" : hotel);
                arr.put(o);
            }
        } catch(Exception ignored) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_OPENED_HISTORY, arr.toString()).apply();
    }

    private void showSettingsDialog() {
        final Dialog dialog = new Dialog(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(18), dp(18), dp(18), dp(18));
        wrap.setBackground(round(dialogFillColor(), dp(22), dialogStrokeColor(), 1));
        dialog.setContentView(wrap);

        TextView title = habboText("Configurações", 24, true);
        title.setGravity(Gravity.CENTER);
        wrap.addView(title, lp(-1, -2, 0, 0, 0, 10));

        TextView hotelTitle = text("Hotel de busca", 13, themeMutedColor(), true);
        hotelTitle.setGravity(Gravity.CENTER);
        wrap.addView(hotelTitle, lp(-1, -2, 0, 0, 0, 8));

        LinearLayout hotelGrid = new LinearLayout(this);
        hotelGrid.setOrientation(LinearLayout.VERTICAL);
        addHotelButtonRow(hotelGrid, dialog, "br", "com", "es");
        addHotelButtonRow(hotelGrid, dialog, "de", "fr", "fi");
        addHotelButtonRow(hotelGrid, dialog, "it", "nl", "tr");
        wrap.addView(hotelGrid, lp(-1, -2, 0, 0, 0, 14));

        TextView info = text(cacheStatsText(), 13, muted, false);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(10), dp(10), dp(10), dp(10));
        info.setBackground(round(lightTheme ? Color.rgb(250,250,250) : Color.argb(18,255,255,255), dp(14), lightTheme ? Color.rgb(218,218,218) : Color.argb(28,255,255,255), 1));
        wrap.addView(info, lp(-1, -2, 0, 0, 0, 14));

        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setGravity(Gravity.CENTER);
        TextView lightBtn = dialogButton("Tema claro");
        TextView darkBtn = dialogButton("Tema escuro");
        lightBtn.setBackground(lightTheme ? grad(dp(14), purple2, purple) : round(Color.argb(20,255,255,255), dp(14), Color.argb(32,255,255,255), 1));
        darkBtn.setBackground(!lightTheme ? grad(dp(14), purple2, purple) : round(Color.rgb(250,250,250), dp(14), Color.rgb(218,218,218), 1));
        lightBtn.setTextColor(Color.WHITE);
        darkBtn.setTextColor(lightTheme ? Color.rgb(33,33,33) : Color.WHITE);
        LinearLayout.LayoutParams th1 = new LinearLayout.LayoutParams(0, dp(48), 1); th1.rightMargin = dp(6);
        LinearLayout.LayoutParams th2 = new LinearLayout.LayoutParams(0, dp(48), 1); th2.leftMargin = dp(6);
        themeRow.addView(lightBtn, th1);
        themeRow.addView(darkBtn, th2);
        wrap.addView(themeRow, lp(-1, dp(48), 0, 0, 0, 10));
        lightBtn.setOnClickListener(v -> { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("theme", "light").apply(); lightTheme = true; dialog.dismiss(); rebuildUiPreservingProfile(); });
        darkBtn.setOnClickListener(v -> { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("theme", "dark").apply(); lightTheme = false; dialog.dismiss(); rebuildUiPreservingProfile(); });

        TextView clear = dialogButton("Limpar cache do app");
        clear.setBackground(grad(dp(14), Color.rgb(120, 36, 46), Color.rgb(210, 54, 77)));
        wrap.addView(clear, lp(-1, dp(48), 0, 0, 0, 10));
        clear.setOnClickListener(v -> {
            clearProfileCache();
            info.setText(cacheStatsText());
            toast("Cache do app limpo.");
        });


        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(w.getAttributes());
            params.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(430));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            w.setAttributes(params);
        }
    }




    private void handlePullToRefresh(ScrollView scroll, MotionEvent event) {
        if (scroll == null || activeRenderedProfile == null || searchInProgress) return;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pullStartedAtTop = scroll.getScrollY() <= 0;
                pullStartY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pullStartedAtTop && event.getY() - pullStartY > dp(86)) {
                    refreshCurrentProfileWithCooldown(true);
                }
                pullStartedAtTop = false;
                break;
        }
    }

    private void refreshCurrentProfileWithCooldown(boolean fromPull) {
        if (activeRenderedProfile == null) return;
        String nick = activeRenderedProfile.name == null || activeRenderedProfile.name.trim().isEmpty() ? activeRenderedProfile.searchedNick : activeRenderedProfile.name;
        if (nick == null || nick.trim().isEmpty()) return;

        String nickKey = normalizeNickKey(nick);
        if (!searchInProgress && activeRenderedProfile != null && nickKey.equals(currentLoadedNick) && normalizeHotelKey(activeRenderedProfile.hotelKey).equals(currentHotelKey)) {
            long now = System.currentTimeMillis();
            long wait = PROFILE_REFRESH_COOLDOWN_MS - (now - lastSameNickRefreshAt);
            if (wait > 0) {
                hidePullRefreshIndicator();
                toast("Aguarde " + Math.max(1, (int)Math.ceil(wait / 1000.0)) + "s para atualizar este perfil novamente.");
                return;
            }
        }

        if (searchInput != null) {
            searchInput.setText(nick.trim());
            searchInput.setSelection(searchInput.getText().length());
        }
        if (fromPull) showPullRefreshIndicator();
        search();
    }

    private void showPullRefreshIndicator() {
        if (pullRefreshChip == null) return;
        if (pullRefreshText != null) pullRefreshText.setText("Atualizando perfil...");
        pullRefreshChip.setVisibility(View.VISIBLE);
        pullRefreshChip.animate().cancel();
        pullRefreshChip.setAlpha(0f);
        pullRefreshChip.setTranslationY(-dp(40));
        pullRefreshChip.animate().alpha(1f).translationY(0).setDuration(180).start();
        if (mainScroll != null) {
            mainScroll.animate().cancel();
            mainScroll.animate().translationY(dp(34)).setDuration(150).withEndAction(() -> mainScroll.animate().translationY(0).setDuration(220).start()).start();
        }
    }

    private void hidePullRefreshIndicator() {
        if (pullRefreshChip == null) return;
        pullRefreshChip.animate().cancel();
        pullRefreshChip.animate().alpha(0f).translationY(-dp(40)).setDuration(180).withEndAction(() -> pullRefreshChip.setVisibility(View.GONE)).start();
    }

    private String normalizeHotelKey(String hotel) {
        String h = hotel == null ? "" : hotel.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if ("us".equals(h)) h = "com";
        String[] allowed = {"br","com","es","de","fr","fi","it","nl","tr"};
        for (String a : allowed) if (a.equals(h)) return h;
        return "";
    }

    private String defaultHotelForDeviceLocale() {
        String lang = Locale.getDefault().getLanguage();
        String country = Locale.getDefault().getCountry();
        if ("pt".equals(lang) || "BR".equalsIgnoreCase(country)) return "br";
        if ("es".equals(lang)) return "es";
        if ("de".equals(lang)) return "de";
        if ("fr".equals(lang)) return "fr";
        if ("fi".equals(lang)) return "fi";
        if ("it".equals(lang)) return "it";
        if ("nl".equals(lang)) return "nl";
        if ("tr".equals(lang)) return "tr";
        return "com";
    }

    private String hotelDomain(String key) {
        String h = normalizeHotelKey(key);
        if ("com".equals(h)) return "www.habbo.com";
        if ("es".equals(h)) return "www.habbo.es";
        if ("de".equals(h)) return "www.habbo.de";
        if ("fr".equals(h)) return "www.habbo.fr";
        if ("fi".equals(h)) return "www.habbo.fi";
        if ("it".equals(h)) return "www.habbo.it";
        if ("nl".equals(h)) return "www.habbo.nl";
        if ("tr".equals(h)) return "www.habbo.com.tr";
        return "www.habbo.com.br";
    }

    private String habbodexHotelCode(String key) {
        String h = normalizeHotelKey(key);
        return "com".equals(h) ? "us" : (h.isEmpty() ? "br" : h);
    }

    private String hotelLabel(String key) {
        String h = normalizeHotelKey(key);
        if ("com".equals(h)) return ".COM";
        if ("tr".equals(h)) return ".COM.TR";
        if (h.isEmpty()) h = "br";
        return "." + h.toUpperCase(Locale.ROOT);
    }

    private String hotelName(String key) {
        return "Habbo" + hotelLabel(key).toLowerCase(Locale.ROOT).replace(".com.tr", ".com.tr");
    }

    private String hotelFlag(String key) {
        return hotelLabel(key);
    }

    private String habboApiUrl(String path) {
        if (path == null) path = "";
        if (!path.startsWith("/")) path = "/" + path;
        return "https://" + hotelDomain(currentHotelKey) + path;
    }

    private String habboImagingUrl(String path) {
        if (path == null) path = "";
        if (!path.startsWith("/")) path = "/" + path;
        return "https://" + hotelDomain(currentHotelKey) + path;
    }

    private String badgeImageUrl(String code) {
        return "https://images.habbo.com/c_images/album1584/" + enc(code) + ".png";
    }

    private void addHotelButtonRow(LinearLayout grid, Dialog dialog, String a, String b, String c) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        grid.addView(row, lp(-1, dp(46), 0, 0, 0, 8));
        addHotelButton(row, dialog, a, 0);
        addHotelButton(row, dialog, b, 1);
        addHotelButton(row, dialog, c, 2);
    }

    private void addHotelButton(LinearLayout row, Dialog dialog, String hotelKey, int pos) {
        boolean active = hotelKey.equals(currentHotelKey);
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(6), 0, dp(6), 0);
        btn.setBackground(active ? grad(dp(12), purple2, purple) : round(lightTheme ? Color.rgb(250,250,250) : Color.argb(18,255,255,255), dp(12), lightTheme ? Color.rgb(218,218,218) : Color.argb(28,255,255,255), 1));

        ImageView flag = new ImageView(this);
        flag.setImageDrawable(new HotelFlagDrawable(hotelKey));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(24), dp(16));
        fp.rightMargin = dp(6);
        btn.addView(flag, fp);

        TextView label = text(hotelLabel(hotelKey), 13, active ? Color.WHITE : (lightTheme ? Color.rgb(33,33,33) : Color.argb(220,255,255,255)), true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        btn.addView(label, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(42), 1);
        if (pos > 0) bp.leftMargin = dp(6);
        row.addView(btn, bp);
        btn.setOnClickListener(v -> {
            currentHotelKey = normalizeHotelKey(hotelKey);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_HOTEL, currentHotelKey).apply();
            dialog.dismiss();
            activeSearchToken++;
            searchInProgress = false;
            currentLoadedNick = "";
            activeRenderedProfile = null;
            resultWrap.removeAllViews();
            rebuildUiPreservingProfile();
            toast("Hotel alterado para " + hotelLabel(currentHotelKey));
        });
    }

    private void rememberOpenedProfile(ProfileResult r) {
        if (r == null || r.name == null || r.name.trim().isEmpty()) return;
        String hotel = normalizeHotelKey(r.hotelKey);
        if (hotel.isEmpty()) hotel = currentHotelKey;
        String key = hotel + ":" + normalizeNickKey(r.name);
        for (int i = openedProfilesHistory.size() - 1; i >= 0; i--) {
            ProfileHistoryItem item = openedProfilesHistory.get(i);
            if ((item.hotelKey + ":" + normalizeNickKey(item.nick)).equals(key)) openedProfilesHistory.remove(i);
        }
        openedProfilesHistory.add(0, new ProfileHistoryItem(r.name, r.figure, hotel));
        while (openedProfilesHistory.size() > 50) openedProfilesHistory.remove(openedProfilesHistory.size() - 1);
        saveOpenedProfilesHistory();
    }

    private void showOpenedProfilesHistoryDialog() {
        final Dialog dialog = new Dialog(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(18), dp(18), dp(18), dp(18));
        wrap.setBackground(round(dialogFillColor(), dp(22), dialogStrokeColor(), 1));
        dialog.setContentView(wrap);

        TextView title = habboText("Histórico de perfis", 22, true);
        title.setGravity(Gravity.CENTER);
        wrap.addView(title, lp(-1, -2, 0, 0, 0, 12));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(true);
        sv.setScrollbarFadingEnabled(false);
        tintScrollBar(sv);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ScrollView.LayoutParams(-1, -2));
        wrap.addView(sv, lp(-1, dp(360), 0, 0, 0, 14));

        if (openedProfilesHistory.isEmpty()) {
            list.addView(centerNote("Nenhum perfil aberto ainda."));
        } else {
            for (ProfileHistoryItem item : new ArrayList<>(openedProfilesHistory)) {
                list.addView(openedProfileHistoryRow(item, dialog));
            }
        }

        TextView clear = dialogButton("Limpar histórico");
        clear.setBackground(grad(dp(14), Color.rgb(120, 36, 46), Color.rgb(210, 54, 77)));
        wrap.addView(clear, lp(-1, dp(48), 0, 0, 0, 0));
        clear.setOnClickListener(v -> {
            openedProfilesHistory.clear();
            saveOpenedProfilesHistory();
            dialog.dismiss();
            toast("Histórico limpo.");
        });

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(w.getAttributes());
            params.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(430));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            w.setAttributes(params);
        }
    }

    private LinearLayout openedProfileHistoryRow(ProfileHistoryItem item, Dialog dialog) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(round(lightTheme ? Color.rgb(245,245,245) : Color.argb(22,255,255,255), dp(16), lightTheme ? Color.rgb(224,224,224) : Color.argb(28,255,255,255), 1));
        row.setLayoutParams(lp(-1, dp(72), 0, 0, 0, 9));

        ImageView head = new ImageView(this);
        head.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(head, new LinearLayout.LayoutParams(dp(54), dp(58)));
        loadImage(head, avatarHeadByNameForHotel(item.nick, item.hotelKey));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        tp.leftMargin = dp(12);
        row.addView(texts, tp);
        TextView name = habboText(item.nick, 16, true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        LinearLayout hotelLine = new LinearLayout(this);
        hotelLine.setOrientation(LinearLayout.HORIZONTAL);
        hotelLine.setGravity(Gravity.CENTER_VERTICAL);
        ImageView smallFlag = new ImageView(this);
        smallFlag.setImageDrawable(new HotelFlagDrawable(item.hotelKey));
        LinearLayout.LayoutParams sfp = new LinearLayout.LayoutParams(dp(18), dp(12));
        sfp.rightMargin = dp(5);
        hotelLine.addView(smallFlag, sfp);
        hotelLine.addView(text(hotelLabel(item.hotelKey), 12, themeMutedColor(), false));
        texts.addView(hotelLine);

        row.setOnClickListener(v -> {
            if (dialog != null) dialog.dismiss();
            currentHotelKey = normalizeHotelKey(item.hotelKey);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_HOTEL, currentHotelKey).apply();
            if (searchInput != null) {
                searchInput.setText(item.nick);
                searchInput.setSelection(searchInput.getText().length());
            }
            currentLoadedNick = "";
            search();
        });
        return row;
    }

    private void showBadgeDialog(JSONObject badge) {
        if (badge == null) return;
        String code = firstText(badge, "code", "badgeCode");
        if (code.isEmpty()) return;
        String name = firstText(badge, "name", "title");
        if (name.isEmpty()) name = code;
        String desc = firstText(badge, "description", "desc");
        if (desc.isEmpty()) desc = "Sem descrição.";
        String created = firstText(badge, "creationTime", "createdAt", "date");

        final Dialog dialog = new Dialog(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        wrap.setBackground(round(dialogFillColor(), dp(22), dialogStrokeColor(), 1));
        dialog.setContentView(wrap);

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setPadding(dp(30), dp(24), dp(30), dp(24));
        img.setBackground(round(lightTheme ? Color.rgb(245,245,245) : Color.argb(20,255,255,255), dp(16), lightTheme ? Color.rgb(224,224,224) : Color.argb(28,255,255,255), 1));
        wrap.addView(img, lp(-1, dp(170), 0,0,0,12));
        loadImage(img, badgeImageUrl(code));

        LinearLayout infoGrid = new LinearLayout(this);
        infoGrid.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(infoGrid, lp(-1, -2, 0, 0, 0, 0));
        infoGrid.addView(photoInfoCard("Nome", name, "", ""));
        infoGrid.addView(photoInfoCard("Descrição", desc, "", ""));
        infoGrid.addView(photoInfoCard("Criado", created.isEmpty() ? "—" : niceDateOnly(created), "", ""));
        infoGrid.addView(photoInfoCard("Código", code, "", ""));

        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(shownWindow.getAttributes());
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            shownWindow.setAttributes(params);
        }
    }

    private boolean isSameProfileObject(JSONObject a, JSONObject b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        String aId = normalizeNickKey(firstText(a, "uniqueId", "id", "habboId"));
        String bId = normalizeNickKey(firstText(b, "uniqueId", "id", "habboId"));
        return !aId.isEmpty() && aId.equals(bId);
    }

    private void pushCurrentProfileToHistory(String nextNickKey) {
        if (activeRenderedProfile == null || activeRenderedProfile.name == null || activeRenderedProfile.name.trim().isEmpty()) return;
        String currentId = normalizeNickKey(activeRenderedProfile.uniqueId);
        String currentName = normalizeNickKey(activeRenderedProfile.name);
        if (normalizeHotelKey(activeRenderedProfile.hotelKey).equals(currentHotelKey) && ((!currentId.isEmpty() && currentId.equals(nextNickKey)) || (!currentName.isEmpty() && currentName.equals(nextNickKey)))) return;
        if (!profileHistory.isEmpty()) {
            ProfileResult last = profileHistory.peekLast();
            if (sameProfile(last, activeRenderedProfile)) return;
        }
        profileHistory.addLast(copyProfileResult(activeRenderedProfile));
        while (profileHistory.size() > PROFILE_HISTORY_LIMIT) profileHistory.removeFirst();
    }

    private boolean sameProfile(ProfileResult a, ProfileResult b) {
        if (a == null || b == null) return false;
        String aId = normalizeNickKey(a.uniqueId);
        String bId = normalizeNickKey(b.uniqueId);
        if (!aId.isEmpty() && !bId.isEmpty()) return aId.equals(bId);
        return normalizeNickKey(a.name).equals(normalizeNickKey(b.name));
    }

    private ProfileResult copyProfileResult(ProfileResult src) {
        ProfileResult c = new ProfileResult();
        if (src == null) return c;
        c.searchedNick = src.searchedNick; c.uniqueId = src.uniqueId; c.name = src.name; c.motto = src.motto; c.figure = src.figure; c.memberSince = src.memberSince; c.lastAccess = src.lastAccess; c.level = src.level; c.starGems = src.starGems; c.hotelKey = src.hotelKey;
        c.online = src.online; c.privateProfile = src.privateProfile; c.banned = src.banned;
        c.habboPublic = src.habboPublic; c.dex = src.dex; c.suggest = src.suggest; c.dexProfile = src.dexProfile; c.officialProfile = src.officialProfile;
        c.previousNames = new ArrayList<>(src.previousNames); c.previousMottos = new ArrayList<>(src.previousMottos); c.previousStyles = new ArrayList<>(src.previousStyles); c.photos = new ArrayList<>(src.photos); c.friends = new ArrayList<>(src.friends); c.oldFriends = new ArrayList<>(src.oldFriends); c.rooms = new ArrayList<>(src.rooms); c.oldRooms = new ArrayList<>(src.oldRooms); c.groups = new ArrayList<>(src.groups); c.selectedBadges = new ArrayList<>(src.selectedBadges);
        c.photosNextPage = src.photosNextPage; c.stylesNextPage = src.stylesNextPage; c.photosTotal = src.photosTotal; c.stylesTotal = src.stylesTotal;
        c.photosHasMore = src.photosHasMore; c.stylesHasMore = src.stylesHasMore; c.photosLoading = false; c.stylesLoading = false;
        return c;
    }

    @Override public void onBackPressed() {
        if (searchInput != null && searchInput.hasFocus()) {
            clearSearchFocus();
            return;
        }
        if (!profileHistory.isEmpty()) {
            activeSearchToken++;
            searchInProgress = false;
            activeSearchNick = "";
            inlineProgressPct = 0;
            inlineProgressMessage = "";
            ProfileResult previous = profileHistory.removeLast();
            String previousHotel = normalizeHotelKey(previous.hotelKey);
            if (!previousHotel.isEmpty()) {
                currentHotelKey = previousHotel;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_HOTEL, currentHotelKey).apply();
            }
            activeRenderedProfile = previous;
            currentLoadedNick = normalizeNickKey(previous.name);
            if (searchInput != null) {
                searchInput.setText(previous.name == null ? "" : previous.name);
                searchInput.setSelection(searchInput.getText().length());
                clearSearchFocus();
            }
            statusText.setText("");
            renderProfile(previous);
            return;
        }
        super.onBackPressed();
    }

    private ProfileResult mergeFreshIntoCachedSafely(ProfileResult cached, ProfileResult fresh) {
        if (fresh == null) return cached;
        if (cached == null) return fresh;

        String freshId = normalizeNickKey(fresh.uniqueId);
        String cachedId = normalizeNickKey(cached.uniqueId);
        if (!freshId.isEmpty() && !cachedId.isEmpty() && !freshId.equals(cachedId)) {
            return fresh;
        }

        ProfileResult merged = mergeFreshIntoCached(cached, fresh);

        // Fotos e visuais antigos são carregados por página. Não reaproveite estes blocos
        // do cache, para evitar mostrar histórico antigo antes da primeira página atual.
        merged.photos.clear();
        merged.previousStyles.clear();
        merged.photosNextPage = 0;
        merged.stylesNextPage = 0;
        merged.photosTotal = 0;
        merged.stylesTotal = 0;
        merged.photosHasMore = false;
        merged.stylesHasMore = false;
        merged.photosLoading = false;
        merged.stylesLoading = false;
        return merged;
    }

    private ProfileResult mergeFreshIntoCached(ProfileResult cached, ProfileResult fresh) {
        if (cached == null) return fresh;
        if (fresh == null) return cached;

        cached.searchedNick = pickText(fresh.searchedNick, cached.searchedNick);
        cached.uniqueId = pickText(fresh.uniqueId, cached.uniqueId);
        cached.name = pickText(fresh.name, cached.name);
        cached.motto = pickText(fresh.motto, cached.motto);
        cached.figure = pickText(fresh.figure, cached.figure);
        cached.memberSince = pickText(fresh.memberSince, cached.memberSince);
        cached.lastAccess = pickText(fresh.lastAccess, cached.lastAccess);
        cached.level = pickText(fresh.level, cached.level);
        cached.starGems = pickText(fresh.starGems, cached.starGems);
        cached.hotelKey = pickText(fresh.hotelKey, cached.hotelKey);
        cached.online = fresh.online;
        cached.privateProfile = fresh.privateProfile;
        cached.banned = fresh.banned;

        if (fresh.habboPublic != null) cached.habboPublic = fresh.habboPublic;
        if (fresh.dex != null) cached.dex = fresh.dex;
        if (fresh.suggest != null) cached.suggest = fresh.suggest;
        if (fresh.dexProfile != null) cached.dexProfile = fresh.dexProfile;
        if (fresh.officialProfile != null) cached.officialProfile = fresh.officialProfile;

        cached.previousNames = mergeLists(fresh.previousNames, cached.previousNames);
        cached.previousMottos = mergeLists(fresh.previousMottos, cached.previousMottos);
        cached.previousStyles = mergeLists(fresh.previousStyles, cached.previousStyles);
        cached.photos = mergeLists(fresh.photos, cached.photos);
        cached.friends = mergeLists(fresh.friends, cached.friends);
        cached.oldFriends = mergeLists(fresh.oldFriends, cached.oldFriends);
        cached.rooms = mergeLists(fresh.rooms, cached.rooms);
        cached.oldRooms = mergeLists(fresh.oldRooms, cached.oldRooms);
        cached.groups = mergeLists(fresh.groups, cached.groups);
        cached.selectedBadges = mergeLists(fresh.selectedBadges, cached.selectedBadges);
        return cached;
    }

    private String pickText(String fresh, String old) {
        if (fresh != null && !fresh.trim().isEmpty() && !"null".equalsIgnoreCase(fresh.trim())) return fresh;
        return old == null ? "" : old;
    }

    private String readFile(File file) throws IOException {
        if (file == null || !file.isFile()) return "";
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } finally {
            try { br.close(); } catch(Exception ignored) {}
        }
        return sb.toString();
    }

    private ProfileResult profileFromJson(JSONObject json) {
        // Cache em disco está desativado nesta versão; este parser mínimo existe apenas para compatibilidade de compilação.
        return null;
    }

    private TextView dialogButton(String label) {
        TextView v = habboText(label, 15, true);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        v.setPadding(dp(12), 0, dp(12), 0);
        v.setBackground(grad(dp(14), purple2, purple));
        return v;
    }



    private static class ProfileHistoryItem {
        final String nick;
        final String figure;
        final String hotelKey;
        ProfileHistoryItem(String nick, String figure, String hotelKey) {
            this.nick = nick == null ? "" : nick;
            this.figure = figure == null ? "" : figure;
            this.hotelKey = hotelKey == null || hotelKey.trim().isEmpty() ? "br" : hotelKey;
        }
    }

    private static class ProfileResult {
        String searchedNick = "", uniqueId = "", name = "", motto = "", figure = "", memberSince = "", lastAccess = "", level = "", starGems = "", hotelKey = "br";
        boolean online = false, privateProfile = false, banned = false;
        JSONObject habboPublic, dex, suggest, dexProfile, officialProfile;
        ArrayList<JSONObject> previousNames = new ArrayList<>(), previousMottos = new ArrayList<>(), previousStyles = new ArrayList<>(), photos = new ArrayList<>(), friends = new ArrayList<>(), oldFriends = new ArrayList<>(), rooms = new ArrayList<>(), oldRooms = new ArrayList<>(), groups = new ArrayList<>(), selectedBadges = new ArrayList<>();
        int photosNextPage = 0, stylesNextPage = 0, photosTotal = 0, stylesTotal = 0;
        boolean photosHasMore = false, stylesHasMore = false, photosLoading = false, stylesLoading = false;
    }

    private static class PageResult {
        ArrayList<JSONObject> items = new ArrayList<>();
        int page = 1, nextPage = 0, total = 0;
        boolean hasMore = false;
    }




    public class HotelFlagDrawable extends Drawable {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        String hotel;
        HotelFlagDrawable(String hotelKey) { hotel = normalizeHotelKey(hotelKey); if (hotel.isEmpty()) hotel = "br"; }
        @Override public int getIntrinsicWidth() { return dp(24); }
        @Override public int getIntrinsicHeight() { return dp(16); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            RectF r = new RectF(b.left, b.top, b.right, b.bottom);
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            c.save();
            Path clip = new Path();
            clip.addRoundRect(r, dp(3), dp(3), Path.Direction.CW);
            c.clipPath(clip);
            float w = r.width(), h = r.height(), x = r.left, y = r.top;
            if ("br".equals(hotel)) {
                p.setColor(Color.rgb(34, 166, 74)); c.drawRect(r, p);
                p.setColor(Color.rgb(255, 223, 64));
                Path d = new Path(); d.moveTo(x+w*.50f,y+h*.10f); d.lineTo(x+w*.90f,y+h*.50f); d.lineTo(x+w*.50f,y+h*.90f); d.lineTo(x+w*.10f,y+h*.50f); d.close(); c.drawPath(d,p);
                p.setColor(Color.rgb(39, 74, 160)); c.drawCircle(x+w*.50f, y+h*.50f, Math.min(w,h)*.20f, p);
            } else if ("com".equals(hotel)) {
                for (int i=0;i<7;i++){ p.setColor(i%2==0?Color.rgb(188,10,48):Color.WHITE); c.drawRect(x, y+h*i/7f, x+w, y+h*(i+1)/7f, p); }
                p.setColor(Color.rgb(40,60,130)); c.drawRect(x,y,x+w*.42f,y+h*.54f,p);
            } else if ("es".equals(hotel)) {
                p.setColor(Color.rgb(198, 0, 43)); c.drawRect(r,p); p.setColor(Color.rgb(255, 206, 0)); c.drawRect(x,y+h*.25f,x+w,y+h*.75f,p);
            } else if ("de".equals(hotel)) {
                p.setColor(Color.BLACK); c.drawRect(x,y,x+w,y+h/3f,p); p.setColor(Color.rgb(221,0,0)); c.drawRect(x,y+h/3f,x+w,y+2*h/3f,p); p.setColor(Color.rgb(255,206,0)); c.drawRect(x,y+2*h/3f,x+w,y+h,p);
            } else if ("fr".equals(hotel)) {
                p.setColor(Color.rgb(0,35,149)); c.drawRect(x,y,x+w/3f,y+h,p); p.setColor(Color.WHITE); c.drawRect(x+w/3f,y,x+2*w/3f,y+h,p); p.setColor(Color.rgb(237,41,57)); c.drawRect(x+2*w/3f,y,x+w,y+h,p);
            } else if ("fi".equals(hotel)) {
                p.setColor(Color.WHITE); c.drawRect(r,p); p.setColor(Color.rgb(0,53,128)); c.drawRect(x+w*.30f,y,x+w*.46f,y+h,p); c.drawRect(x,y+h*.38f,x+w,y+h*.58f,p);
            } else if ("it".equals(hotel)) {
                p.setColor(Color.rgb(0,146,70)); c.drawRect(x,y,x+w/3f,y+h,p); p.setColor(Color.WHITE); c.drawRect(x+w/3f,y,x+2*w/3f,y+h,p); p.setColor(Color.rgb(206,43,55)); c.drawRect(x+2*w/3f,y,x+w,y+h,p);
            } else if ("nl".equals(hotel)) {
                p.setColor(Color.rgb(174,28,40)); c.drawRect(x,y,x+w,y+h/3f,p); p.setColor(Color.WHITE); c.drawRect(x,y+h/3f,x+w,y+2*h/3f,p); p.setColor(Color.rgb(33,70,139)); c.drawRect(x,y+2*h/3f,x+w,y+h,p);
            } else if ("tr".equals(hotel)) {
                p.setColor(Color.rgb(227,10,23)); c.drawRect(r,p); p.setColor(Color.WHITE); c.drawCircle(x+w*.43f,y+h*.50f,h*.25f,p); p.setColor(Color.rgb(227,10,23)); c.drawCircle(x+w*.50f,y+h*.50f,h*.20f,p); p.setColor(Color.WHITE); Path star=new Path(); float cx=x+w*.64f, cy=y+h*.50f, rr=h*.15f; for(int i=0;i<10;i++){ double a=-Math.PI/2+i*Math.PI/5; float rad=(i%2==0)?rr:rr*.42f; float px=cx+(float)Math.cos(a)*rad, py=cy+(float)Math.sin(a)*rad; if(i==0) star.moveTo(px,py); else star.lineTo(px,py);} star.close(); c.drawPath(star,p);
            }
            c.restore();
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(Color.argb(90,0,0,0)); c.drawRoundRect(r, dp(3), dp(3), p);
        }
        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public class AddButtonDrawable extends Drawable {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float x = b.left, y = b.top, w = b.width(), h = b.height();
            float size = Math.min(w, h);
            float left = x + (w - size) / 2f;
            float top = y + (h - size) / 2f;
            RectF r = new RectF(left, top, left + size, top + size);
            p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, purple2, purple, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(r, dp(7), dp(7), p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(Color.argb(85,255,255,255));
            c.drawRoundRect(new RectF(r.left+1, r.top+1, r.right-1, r.bottom-1), dp(7), dp(7), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.WHITE);
            float cx = r.centerX(), cy = r.centerY();
            float len = size * 0.22f;
            c.drawLine(cx - len, cy, cx + len, cy, p);
            c.drawLine(cx, cy - len, cx, cy + len, p);
        }
        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public class HistoryClockDrawable extends Drawable {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height(), x = b.left, y = b.top;
            float cx = x + w / 2f, cy = y + h / 2f;
            float radius = Math.min(w, h) * 0.28f;
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(lightTheme ? Color.argb(0,0,0,0) : Color.argb(0,255,255,255));
            c.drawRect(x, y, x+w, y+h, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(lightTheme ? Color.rgb(45,45,45) : Color.argb(235,255,255,255));
            RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            c.drawArc(oval, 35, 285, false, p);

            Path arrow = new Path();
            double a = Math.toRadians(35);
            float ax = cx + (float)Math.cos(a) * radius;
            float ay = cy + (float)Math.sin(a) * radius;
            arrow.moveTo(ax, ay);
            arrow.lineTo(ax - dp(8), ay - dp(1));
            arrow.moveTo(ax, ay);
            arrow.lineTo(ax - dp(3), ay + dp(7));
            c.drawPath(arrow, p);

            c.drawLine(cx, cy, cx, cy - radius * 0.52f, p);
            c.drawLine(cx, cy, cx + radius * 0.48f, cy + radius * 0.18f, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, dp(2), p);
        }
        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public class ArrowButtonDrawable extends Drawable {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); boolean left;
        ArrowButtonDrawable(boolean left){ this.left = left; }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); float w=b.width(), h=b.height(), x=b.left, y=b.top;
            RectF r = new RectF(x, y, x+w, y+h);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(38, 35, 45));
            c.drawRoundRect(r, dp(11), dp(11), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(Color.argb(70,255,255,255));
            c.drawRoundRect(new RectF(x+1,y+1,x+w-1,y+h-1), dp(11), dp(11), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(Color.WHITE);
            float yMid = y + h * .50f;
            float startX = x + w * .26f, endX = x + w * .74f;
            if (left) {
                c.drawLine(endX, yMid, startX, yMid, p);
                Path path = new Path();
                path.moveTo(x+w*.42f, y+h*.30f);
                path.lineTo(startX, yMid);
                path.lineTo(x+w*.42f, y+h*.70f);
                c.drawPath(path, p);
            } else {
                c.drawLine(startX, yMid, endX, yMid, p);
                Path path = new Path();
                path.moveTo(x+w*.58f, y+h*.30f);
                path.lineTo(endX, yMid);
                path.lineTo(x+w*.58f, y+h*.70f);
                c.drawPath(path, p);
            }
        }
        @Override public void setAlpha(int a){p.setAlpha(a);} @Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    public class ShirtDrawable extends Drawable {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); float w=b.width(), h=b.height(), x=b.left, y=b.top;
            RectF r = new RectF(x, y, x+w, y+h);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(38, 35, 45));
            c.drawRoundRect(r, dp(11), dp(11), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(Color.argb(70,255,255,255));
            c.drawRoundRect(new RectF(x+1,y+1,x+w-1,y+h-1), dp(11), dp(11), p);

            float ox = x + w * .18f, oy = y + h * .15f, sw = w * .64f, sh = h * .68f;
            Path leftSleeve = new Path();
            leftSleeve.moveTo(ox+sw*.22f, oy+sh*.10f); leftSleeve.lineTo(ox+sw*.02f, oy+sh*.22f); leftSleeve.lineTo(ox+sw*.15f, oy+sh*.42f); leftSleeve.lineTo(ox+sw*.33f, oy+sh*.28f); leftSleeve.close();
            Path rightSleeve = new Path();
            rightSleeve.moveTo(ox+sw*.78f, oy+sh*.10f); rightSleeve.lineTo(ox+sw*.98f, oy+sh*.22f); rightSleeve.lineTo(ox+sw*.85f, oy+sh*.42f); rightSleeve.lineTo(ox+sw*.67f, oy+sh*.28f); rightSleeve.close();
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(255,107,122)); c.drawPath(leftSleeve,p); c.drawPath(rightSleeve,p);
            Path body = new Path();
            body.moveTo(ox+sw*.34f, oy+sh*.10f); body.lineTo(ox+sw*.22f, oy+sh*.16f); body.lineTo(ox+sw*.22f, oy+sh*.38f); body.lineTo(ox+sw*.28f, oy+sh*.44f); body.lineTo(ox+sw*.28f, oy+sh*.95f); body.lineTo(ox+sw*.72f, oy+sh*.95f); body.lineTo(ox+sw*.72f, oy+sh*.44f); body.lineTo(ox+sw*.78f, oy+sh*.38f); body.lineTo(ox+sw*.78f, oy+sh*.16f); body.lineTo(ox+sw*.66f, oy+sh*.10f); body.close();
            p.setColor(Color.rgb(217,75,66)); c.drawPath(body,p);
            p.setColor(Color.rgb(182,58,51)); c.drawRect(ox+sw*.28f, oy+sh*.44f, ox+sw*.34f, oy+sh*.95f, p); c.drawRect(ox+sw*.66f, oy+sh*.44f, ox+sw*.72f, oy+sh*.95f, p);
            p.setColor(Color.rgb(255,107,122)); c.drawRect(ox+sw*.36f, oy+sh*.84f, ox+sw*.64f, oy+sh*.91f, p);
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
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth(), h=getHeight(), cx=w/2f, cy=h/2f, m=Math.min(w,h);
            p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, m*.11f)); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(lightTheme ? Color.rgb(33, 33, 33) : Color.WHITE);
            if ("dot".equals(type)) { p.setStyle(Paint.Style.FILL); p.setColor(green); c.drawCircle(cx,cy,m*.28f,p); return; }
            if ("lock".equals(type)) { RectF body = new RectF(cx-m*.26f, cy-m*.02f, cx+m*.26f, cy+m*.30f); c.drawRoundRect(body, m*.08f, m*.08f, p); c.drawArc(new RectF(cx-m*.22f, cy-m*.36f, cx+m*.22f, cy+m*.12f), 200, 140, false, p); return; }
            if ("status".equals(type)) { p.setColor(Color.rgb(255,120,135)); c.drawCircle(cx,cy,m*.34f,p); p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(240,40,54)); c.drawCircle(cx,cy,m*.18f,p); return; }
            if ("clock".equals(type)) { c.drawCircle(cx,cy,m*.36f,p); c.drawLine(cx,cy,cx,cy-m*.20f,p); c.drawLine(cx,cy,cx+m*.17f,cy+m*.11f,p); return; }
            if ("calendar".equals(type)) { RectF r=new RectF(w*.16f,h*.22f,w*.84f,h*.82f); c.drawRoundRect(r,m*.10f,m*.10f,p); c.drawLine(w*.16f,h*.42f,w*.84f,h*.42f,p); c.drawLine(w*.32f,h*.12f,w*.32f,h*.30f,p); c.drawLine(w*.68f,h*.12f,w*.68f,h*.30f,p); return; }
            if ("friends".equals(type)) { c.drawCircle(cx-m*.18f,cy-m*.08f,m*.11f,p); c.drawCircle(cx+m*.18f,cy-m*.08f,m*.11f,p); c.drawArc(new RectF(cx-m*.43f,cy+m*.05f, cx-m*.02f, cy+m*.46f),205,130,false,p); c.drawArc(new RectF(cx+m*.02f,cy+m*.05f, cx+m*.43f, cy+m*.46f),205,130,false,p); return; }
            if ("rooms".equals(type)) { Path path=new Path(); path.moveTo(cx,h*.14f); path.lineTo(w*.82f,h*.38f); path.lineTo(w*.82f,h*.68f); path.lineTo(cx,h*.86f); path.lineTo(w*.18f,h*.68f); path.lineTo(w*.18f,h*.38f); path.close(); c.drawPath(path,p); return; }
            if ("groups".equals(type)) { c.drawCircle(cx,cy,m*.36f,p); c.drawCircle(cx,cy,m*.17f,p); Path chk=new Path(); chk.moveTo(cx-m*.10f,cy); chk.lineTo(cx-m*.02f,cy+m*.09f); chk.lineTo(cx+m*.15f,cy-m*.11f); c.drawPath(chk,p); return; }
            if ("photos".equals(type)) { RectF r=new RectF(w*.16f,h*.22f,w*.84f,h*.78f); c.drawRoundRect(r,m*.09f,m*.09f,p); c.drawCircle(w*.32f,h*.38f,m*.06f,p); c.drawLine(w*.22f,h*.68f,w*.43f,h*.52f,p); c.drawLine(w*.43f,h*.52f,w*.78f,h*.68f,p); return; }
            if ("star".equals(type)) { Path path=new Path(); for(int i=0;i<10;i++){ double a=-Math.PI/2+i*Math.PI/5; float rr=(i%2==0)?m*.40f:m*.17f; float x=cx+(float)Math.cos(a)*rr, y=cy+(float)Math.sin(a)*rr; if(i==0) path.moveTo(x,y); else path.lineTo(x,y);} path.close(); c.drawPath(path,p); return; }
            if ("level".equals(type)) { p.setStyle(Paint.Style.FILL); Path path=new Path(); path.moveTo(cx,h*.16f); path.lineTo(w*.80f,h*.48f); path.lineTo(w*.62f,h*.48f); path.lineTo(w*.62f,h*.84f); path.lineTo(w*.38f,h*.84f); path.lineTo(w*.38f,h*.48f); path.lineTo(w*.20f,h*.48f); path.close(); c.drawPath(path,p); }
        }
    }

    public class RewardVideoDrawable extends Drawable {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height(), cx = b.centerX(), cy = b.centerY(), m = Math.min(w, h);
            RectF bgRect = new RectF(b.left + m*.10f, b.top + m*.10f, b.right - m*.10f, b.bottom - m*.10f);
            p.setShader(new LinearGradient(bgRect.left, bgRect.top, bgRect.right, bgRect.bottom, purple2, purple, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(bgRect, m*.24f, m*.24f, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, m*.035f));
            p.setColor(Color.argb(80,255,255,255));
            c.drawRoundRect(bgRect, m*.24f, m*.24f, p);

            RectF screenRect = new RectF(cx-m*.25f, cy-m*.17f, cx+m*.25f, cy+m*.17f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, m*.06f));
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(Color.WHITE);
            c.drawRoundRect(screenRect, m*.06f, m*.06f, p);

            Path play = new Path();
            play.moveTo(cx-m*.055f, cy-m*.080f);
            play.lineTo(cx-m*.055f, cy+m*.080f);
            play.lineTo(cx+m*.100f, cy);
            play.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawPath(play, p);

            p.setStrokeWidth(Math.max(1.5f, m*.035f));
            c.drawLine(cx-m*.09f, cy+m*.26f, cx+m*.09f, cy+m*.26f, p);
            c.drawLine(cx, cy+m*.16f, cx, cy+m*.26f, p);
        }
        @Override public void setAlpha(int a){p.setAlpha(a);}
        @Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }


}
