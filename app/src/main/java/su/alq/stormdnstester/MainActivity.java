package su.alq.stormdnstester;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String PREFS = "tester";
    private static final String DEFAULT_DOMAIN = "";
    private static final String DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204";
    private static final String DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=200000";
    private static final String SPEEDTEST_URL = "https://www.speedtest.net/";
    private static final String YANDEX_URL = "https://yandex.com/internet/";
    private static final String[] SPEED_TEST_LABELS = {
            "Cloudflare 200 KB",
            "Speedtest.net page",
            "Yandex Internetometer",
            "Custom URL"
    };
    private static final String[] SPEED_TEST_URLS = {
            DEFAULT_DOWNLOAD_URL,
            SPEEDTEST_URL,
            YANDEX_URL,
            ""
    };
    private static final int COLOR_BACKGROUND = Color.rgb(9, 14, 25);
    private static final int COLOR_SURFACE = Color.rgb(17, 24, 39);
    private static final int COLOR_INPUT = Color.rgb(23, 31, 48);
    private static final int COLOR_PRIMARY = Color.rgb(59, 130, 246);
    private static final int COLOR_PRIMARY_DARK = Color.rgb(37, 99, 235);
    private static final int COLOR_DANGER = Color.rgb(239, 68, 68);
    private static final int COLOR_TEXT = Color.rgb(241, 245, 249);
    private static final int COLOR_MUTED = Color.rgb(148, 163, 184);
    private static final int COLOR_BORDER = Color.rgb(51, 65, 85);
    private static final int COLOR_LOG_BG = Color.rgb(2, 6, 23);
    private static final int COLOR_LOG_TEXT = Color.rgb(203, 213, 225);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StringBuilder logBuffer = new StringBuilder();
    private final List<TestResult> lastResults = new ArrayList<>();
    private final Set<Process> runningProcesses = Collections.synchronizedSet(new HashSet<>());

    private EditText domainInput;
    private EditText keyInput;
    private EditText subscriptionInput;
    private EditText encryptionMethodInput;
    private EditText resolversInput;
    private EditText minSpeedInput;
    private EditText retriesInput;
    private EditText parallelWorkersInput;
    private Spinner speedTestSpinner;
    private EditText downloadUrlInput;
    private Button startButton;
    private Button stopButton;
    private Button copyGoodButton;
    private Button clearAllButton;
    private TextView statusText;
    private TextView logText;

    private volatile boolean stopRequested;
    private volatile Process currentProcess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        loadPrefs();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        stopRequested = true;
        killCurrentProcess();
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("DNS Resolver Tester");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TEXT);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Checks real HTTPS traffic through DNS resolvers on the current mobile/Wi-Fi network.");
        subtitle.setTextSize(13);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setPadding(0, dp(6), 0, dp(16));
        root.addView(subtitle);

        addSectionTitle(root, "Connection");
        subscriptionInput = addInput(root, "Subscription URI", "", false);
        Button importSubscriptionButton = new Button(this);
        importSubscriptionButton.setText("Import subscription");
        styleButton(importSubscriptionButton, COLOR_SURFACE, COLOR_TEXT);
        importSubscriptionButton.setOnClickListener(v -> importSubscription());
        root.addView(importSubscriptionButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        domainInput = addInput(root, "DNS domain", DEFAULT_DOMAIN, false);
        keyInput = addInput(root, "Encryption key", "", false);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        encryptionMethodInput = addInput(root, "Encryption method: 0-5", "", true);

        addSectionTitle(root, "Test settings");
        minSpeedInput = addInput(root, "Minimum speed, KB/s", "20", true);
        retriesInput = addInput(root, "Full attempts per resolver", "2", true);
        parallelWorkersInput = addInput(root, "Parallel workers", "3", true);
        speedTestSpinner = addSpeedTestSpinner(root);
        downloadUrlInput = addInput(root, "Download test URL", DEFAULT_DOWNLOAD_URL, false);

        addSectionTitle(root, "Resolvers");
        resolversInput = addInput(root, "Resolvers: lines, commas, spaces", "1.1.1.1, 8.8.8.8, 9.9.9.9", false);
        resolversInput.setMinLines(8);
        resolversInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(10), 0, dp(8));
        root.addView(buttons);

        startButton = new Button(this);
        startButton.setText("Start");
        styleButton(startButton, COLOR_PRIMARY, Color.WHITE);
        startButton.setOnClickListener(v -> startTests());
        buttons.addView(startButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        stopButton = new Button(this);
        stopButton.setText("Stop");
        stopButton.setEnabled(false);
        styleButton(stopButton, COLOR_DANGER, Color.WHITE);
        stopButton.setOnClickListener(v -> {
            stopRequested = true;
            killCurrentProcess();
            appendLog("Stop requested");
        });
        buttons.addView(stopButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        clearAllButton = new Button(this);
        clearAllButton.setText("Clear all");
        styleButton(clearAllButton, COLOR_SURFACE, COLOR_TEXT);
        clearAllButton.setOnClickListener(v -> clearAll());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clearParams.setMargins(dp(8), 0, 0, 0);
        buttons.addView(clearAllButton, clearParams);

        copyGoodButton = new Button(this);
        copyGoodButton.setText("Copy good");
        styleButton(copyGoodButton, COLOR_PRIMARY_DARK, Color.WHITE);
        copyGoodButton.setOnClickListener(v -> copyGoodResolvers());
        root.addView(copyGoodButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        statusText = new TextView(this);
        statusText.setText("Idle");
        statusText.setTextSize(15);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setTextColor(COLOR_TEXT);
        statusText.setPadding(dp(12), dp(10), dp(12), dp(10));
        statusText.setBackground(makeRounded(COLOR_SURFACE, COLOR_BORDER, 12));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(12), 0, dp(10));
        root.addView(statusText, statusParams);

        addSectionTitle(root, "Log");

        logText = new TextView(this);
        logText.setTextSize(12);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextColor(COLOR_LOG_TEXT);
        logText.setTextIsSelectable(true);
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logText.setMinLines(10);
        logText.setBackground(makeRounded(COLOR_LOG_BG, COLOR_LOG_BG, 12));
        root.addView(logText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
    }

    private void addSectionTitle(LinearLayout root, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TEXT);
        title.setPadding(0, dp(12), 0, dp(2));
        root.addView(title);
    }

    private Spinner addSpeedTestSpinner(LinearLayout root) {
        TextView label = new TextView(this);
        label.setText("Speed test");
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(COLOR_MUTED);
        label.setPadding(0, dp(8), 0, dp(3));
        root.addView(label);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                SPEED_TEST_LABELS
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleSpinnerText(view, COLOR_TEXT, COLOR_INPUT);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleSpinnerText(view, COLOR_TEXT, COLOR_SURFACE);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(makeRounded(COLOR_INPUT, COLOR_BORDER, 10));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (downloadUrlInput != null && position >= 0 && position < SPEED_TEST_URLS.length && !SPEED_TEST_URLS[position].isEmpty()) {
                    downloadUrlInput.setText(SPEED_TEST_URLS[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        root.addView(spinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return spinner;
    }

    private void styleSpinnerText(View view, int textColor, int backgroundColor) {
        view.setBackgroundColor(backgroundColor);
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextColor(textColor);
            textView.setTextSize(14);
            textView.setPadding(dp(12), dp(8), dp(12), dp(8));
        }
    }

    private EditText addInput(LinearLayout root, String hint, String value, boolean numeric) {
        TextView label = new TextView(this);
        label.setText(hint);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(COLOR_MUTED);
        label.setPadding(0, dp(8), 0, dp(3));
        root.addView(label);

        EditText input = new EditText(this);
        input.setText(value);
        input.setTextColor(COLOR_TEXT);
        input.setTextSize(14);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setHintTextColor(COLOR_MUTED);
        input.setBackground(makeRounded(COLOR_INPUT, COLOR_BORDER, 10));
        input.setSingleLine(!hint.startsWith("Resolvers"));
        if (numeric) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return input;
    }

    private void styleButton(Button button, int backgroundColor, int textColor) {
        button.setTextColor(textColor);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(dp(44));
        button.setBackground(makeRounded(backgroundColor, backgroundColor == COLOR_SURFACE ? COLOR_BORDER : backgroundColor, 10));
    }

    private GradientDrawable makeRounded(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        subscriptionInput.setText(prefs.getString("subscription", ""));
        domainInput.setText(prefs.getString("domain", DEFAULT_DOMAIN));
        keyInput.setText(prefs.getString("key", ""));
        encryptionMethodInput.setText(prefs.getString("encryptionMethod", ""));
        resolversInput.setText(prefs.getString("resolvers", "1.1.1.1, 8.8.8.8, 9.9.9.9"));
        minSpeedInput.setText(prefs.getString("minSpeed", "20"));
        retriesInput.setText(prefs.getString("retries", "2"));
        parallelWorkersInput.setText(prefs.getString("parallelWorkers", "3"));
        int speedTestIndex = prefs.getInt("speedTestIndex", 0);
        if (speedTestIndex < 0 || speedTestIndex >= SPEED_TEST_LABELS.length) {
            speedTestIndex = 0;
        }
        speedTestSpinner.setSelection(speedTestIndex);
        downloadUrlInput.setText(prefs.getString("downloadUrl", DEFAULT_DOWNLOAD_URL));
    }

    private void savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("subscription", subscriptionInput.getText().toString().trim())
                .putString("domain", domainInput.getText().toString().trim())
                .putString("key", keyInput.getText().toString().trim())
                .putString("encryptionMethod", encryptionMethodInput.getText().toString().trim())
                .putString("resolvers", resolversInput.getText().toString())
                .putString("minSpeed", minSpeedInput.getText().toString().trim())
                .putString("retries", retriesInput.getText().toString().trim())
                .putString("parallelWorkers", parallelWorkersInput.getText().toString().trim())
                .putInt("speedTestIndex", speedTestSpinner.getSelectedItemPosition())
                .putString("downloadUrl", downloadUrlInput.getText().toString().trim())
                .apply();
    }

    private void importSubscription() {
        String raw = firstSubscriptionLine(subscriptionInput.getText().toString());
        if (raw.isEmpty()) {
            toast("Paste subscription URI");
            return;
        }

        try {
            Subscription subscription = parseSubscription(raw);
            if (!subscription.domain.isEmpty()) {
                domainInput.setText(subscription.domain);
            }
            if (!subscription.encryptionKey.isEmpty()) {
                keyInput.setText(subscription.encryptionKey);
            }
            if (subscription.encryptionMethod >= 0) {
                encryptionMethodInput.setText(String.valueOf(subscription.encryptionMethod));
            }
            if (!subscription.resolvers.isEmpty()) {
                resolversInput.setText(joinLines(subscription.resolvers));
            }
            savePrefs();

            StringBuilder message = new StringBuilder();
            message.append("Imported subscription");
            if (subscription.encryptionMethod >= 0) {
                message.append(", method ").append(subscription.encryptionMethod);
            }
            if (!subscription.resolvers.isEmpty()) {
                message.append(", resolvers ").append(subscription.resolvers.size());
            }
            appendLog(message.toString());
            toast("Subscription imported");
        } catch (Exception e) {
            appendLog("subscription import failed: " + e.getMessage());
            toast("Import failed: " + e.getMessage());
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getDataString() == null) {
            return;
        }
        String data = intent.getDataString().trim();
        if (isSubscriptionLink(data)) {
            subscriptionInput.setText(data);
            importSubscription();
        }
    }

    private void startTests() {
        String domain = domainInput.getText().toString().trim();
        String key = keyInput.getText().toString().trim();
        int encryptionMethod = parseEncryptionMethod(encryptionMethodInput.getText().toString());
        String downloadUrl = downloadUrlInput.getText().toString().trim();
        double minKbps = parseDouble(minSpeedInput.getText().toString(), 20.0);
        int retries = Math.max(1, (int) parseDouble(retriesInput.getText().toString(), 2.0));
        int parallelWorkers = clampInt((int) parseDouble(parallelWorkersInput.getText().toString(), 3.0), 1, 12);
        List<String> resolvers = parseResolvers(resolversInput.getText().toString());

        try {
            Subscription subscription = parseOptionalSubscription();
            if (subscription != null) {
                if (!subscription.domain.isEmpty()) {
                    domain = subscription.domain;
                    domainInput.setText(domain);
                }
                if (!subscription.encryptionKey.isEmpty()) {
                    key = subscription.encryptionKey;
                    keyInput.setText(key);
                }
                if (subscription.encryptionMethod >= 0) {
                    encryptionMethod = subscription.encryptionMethod;
                    encryptionMethodInput.setText(String.valueOf(encryptionMethod));
                }
                if (resolvers.isEmpty() && !subscription.resolvers.isEmpty()) {
                    resolvers = subscription.resolvers;
                    resolversInput.setText(joinLines(resolvers));
                }
            }
        } catch (Exception e) {
            appendLog("subscription parse failed: " + e.getMessage());
            toast("Subscription error: " + e.getMessage());
            return;
        }

        if (domain.isEmpty() || key.isEmpty() || resolvers.isEmpty()) {
            toast("Subscription or domain/key plus resolver list are required");
            return;
        }
        if (encryptionMethod < 0) {
            toast("Encryption method is required");
            return;
        }

        final String testDomain = domain;
        final String testKey = key;
        final int testEncryptionMethod = encryptionMethod;
        final List<String> testResolvers = resolvers;

        savePrefs();
        stopRequested = false;
        lastResults.clear();
        logBuffer.setLength(0);
        setRunning(true);
        appendLog("Starting test: " + testResolvers.size() + " resolvers, min speed " + minKbps
                + " KB/s, workers " + parallelWorkers);

        executor.execute(() -> {
            List<String> good = new ArrayList<>();
            ExecutorService workerPool = Executors.newFixedThreadPool(parallelWorkers);
            CompletionService<TestResult> completionService = new ExecutorCompletionService<>(workerPool);
            int submitted = 0;
            int completed = 0;
            try {
                for (int i = 0; i < testResolvers.size(); i++) {
                    final int resolverIndex = i;
                    final String resolver = testResolvers.get(i);
                    completionService.submit(() -> {
                        if (stopRequested) {
                            return TestResult.bad(resolver, "stopped_before_start");
                        }
                        updateStatus("Testing " + resolver + " (" + (resolverIndex + 1) + "/" + testResolvers.size() + ")");
                        return testResolver(resolverIndex, resolver, testDomain, testKey, testEncryptionMethod, downloadUrl, minKbps, retries);
                    });
                    submitted++;
                }

                while (completed < submitted && !stopRequested) {
                    Future<TestResult> future = completionService.take();
                    TestResult result = future.get();
                    completed++;
                    lastResults.add(result);
                    if (result.ok) {
                        good.add(result.resolver);
                    }
                    appendLog("completed " + completed + "/" + submitted + " " + result.toLogLine());
                    writeOutputs(good, lastResults);
                    updateStatus("Completed " + completed + "/" + submitted + ". Good: " + good.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appendLog("Interrupted");
            } catch (ExecutionException e) {
                appendLog("Worker failed: " + e.getMessage());
            } finally {
                workerPool.shutdownNow();
                killCurrentProcess();
            }
            updateStatus(stopRequested ? "Stopped" : "Done. Good: " + good.size() + "/" + testResolvers.size());
            mainHandler.post(() -> setRunning(false));
        });
    }

    private TestResult testResolver(
            int index,
            String resolver,
            String domain,
            String key,
            int encryptionMethod,
            String downloadUrl,
            double minKbps,
            int retries
    ) {
        TestResult best = null;
        TestResult last = TestResult.bad(resolver, "no_attempts");
        int attempts = Math.max(1, retries);
        for (int attempt = 1; attempt <= attempts && !stopRequested; attempt++) {
            appendLog("attempt " + attempt + "/" + attempts + " " + resolver);
            TestResult result = testResolverOnce(index, attempt, resolver, domain, key, encryptionMethod, downloadUrl, minKbps);
            appendLog("attempt " + attempt + "/" + attempts + " -> " + result.toLogLine());
            last = result;
            if (best == null || result.kbps > best.kbps || (result.ok && !best.ok)) {
                best = result;
            }
        }
        if (best == null) {
            best = last;
        }
        boolean ok = best.kbps >= minKbps;
        return new TestResult(
                resolver,
                ok,
                best.kbps,
                best.bytes,
                "attempts=" + attempts + " best=" + best.reason
        );
    }

    private TestResult testResolverOnce(
            int index,
            int attempt,
            String resolver,
            String domain,
            String key,
            int encryptionMethod,
            String downloadUrl,
            double minKbps
    ) {
        int port = 18080 + ((index * 17 + attempt) % 1000);
        File runDir = new File(getCacheDir(), "resolver-run-" + System.currentTimeMillis() + "-" + index + "-" + attempt);
        if (!runDir.mkdirs() && !runDir.isDirectory()) {
            return TestResult.bad(resolver, "cannot_create_run_dir");
        }

        File config = new File(runDir, "client_config.toml");
        File resolverFile = new File(runDir, "client_resolvers.txt");
        File logFile = new File(runDir, "client.log");
        try {
            writeText(resolverFile, resolver + "\n");
            writeText(config, renderConfig(domain, key, encryptionMethod, port));
        } catch (IOException e) {
            return TestResult.bad(resolver, "write_config_failed:" + e.getMessage());
        }

        File binary = new File(getApplicationInfo().nativeLibraryDir, "libstormdns_client.so");
        if (!binary.isFile()) {
            return TestResult.bad(resolver, "client_binary_not_found:" + binary.getAbsolutePath());
        }

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    binary.getAbsolutePath(),
                    "--config", config.getAbsolutePath(),
                    "--resolvers", resolverFile.getAbsolutePath()
            );
            pb.directory(runDir);
            pb.redirectErrorStream(true);
            pb.environment().put("HOME", getFilesDir().getAbsolutePath());
            pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
            process = pb.start();
            currentProcess = process;
            runningProcesses.add(process);
            startLogPump(process, logFile);

            if (!waitForPort("127.0.0.1", port, 60_000)) {
                return TestResult.bad(resolver, "socks_not_ready");
            }

            double bestKbps = 0.0;
            int bestBytes = 0;
            String reason = "no_success";
            HttpProbe probe = HttpProbe.fetch(DEFAULT_TEST_URL, port, 20_000, false);
            if (!probe.ok) {
                reason = probe.reason;
            } else {
                HttpProbe download = HttpProbe.fetch(downloadUrl, port, 30_000, true);
                reason = download.reason;
                if (download.ok && download.kbps > bestKbps) {
                    bestKbps = download.kbps;
                    bestBytes = download.bytes;
                }
            }

            boolean ok = bestKbps >= minKbps;
            return new TestResult(resolver, ok, bestKbps, bestBytes, ok ? "ok" : reason);
        } catch (IOException e) {
            return TestResult.bad(resolver, "process_failed:" + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
                try {
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(3, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            currentProcess = null;
            if (process != null) {
                runningProcesses.remove(process);
            }
        }
    }

    private void startLogPump(Process process, File logFile) {
        Thread thread = new Thread(() -> {
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile), StandardCharsets.UTF_8))
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    if (isImportantClientLine(line)) {
                        appendLog("client: " + stripAnsi(line));
                    }
                }
            } catch (IOException ignored) {
            }
        }, "client-log-pump");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean isImportantClientLine(String line) {
        return line.contains("Accepted")
                || line.contains("valid resolvers")
                || line.contains("ERROR")
                || line.contains("WARN")
                || line.contains("Started")
                || line.contains("Ready")
                || line.contains("Session");
    }

    private String renderConfig(String domain, String key, int encryptionMethod, int port) {
        return ""
                + "DOMAINS = [\"" + escapeToml(domain) + "\"]\n"
                + "DATA_ENCRYPTION_METHOD = " + clampInt(encryptionMethod, 0, 5) + "\n"
                + "ENCRYPTION_KEY = \"" + escapeToml(key) + "\"\n"
                + "PROTOCOL_TYPE = \"SOCKS5\"\n"
                + "LISTEN_IP = \"127.0.0.1\"\n"
                + "LISTEN_PORT = " + port + "\n"
                + "SOCKS5_AUTH = false\n"
                + "LOCAL_DNS_ENABLED = false\n"
                + "RESOLVER_BALANCING_STRATEGY = 3\n"
                + "UPLOAD_PACKET_DUPLICATION_COUNT = 1\n"
                + "DOWNLOAD_PACKET_DUPLICATION_COUNT = 6\n"
                + "UPLOAD_SETUP_PACKET_DUPLICATION_COUNT = 2\n"
                + "DOWNLOAD_SETUP_PACKET_DUPLICATION_COUNT = 7\n"
                + "STREAM_RESOLVER_FAILOVER_RESEND_THRESHOLD = 1\n"
                + "STREAM_RESOLVER_FAILOVER_COOLDOWN = 0.5\n"
                + "AUTO_DISABLE_TIMEOUT_SERVERS = true\n"
                + "MIN_UPLOAD_MTU = 60\n"
                + "MAX_UPLOAD_MTU = 120\n"
                + "MIN_DOWNLOAD_MTU = 500\n"
                + "MAX_DOWNLOAD_MTU = 1200\n"
                + "STARTUP_MODE = \"resolvers\"\n"
                + "LOG_LEVEL = \"INFO\"\n"
                + "LOG_TO_FILE = false\n"
                + "CONFIG_VERSION = \"10\"\n";
    }

    private boolean waitForPort(String host, int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!stopRequested && System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 500);
                return true;
            } catch (IOException ignored) {
                sleep(250);
            }
        }
        return false;
    }

    private void writeOutputs(List<String> good, List<TestResult> results) {
        try {
            writeText(new File(getFilesDir(), "good_resolvers.txt"), joinLines(good));
            StringBuilder csv = new StringBuilder("resolver,ok,kbps,bytes,reason\n");
            for (TestResult result : results) {
                csv.append(csv(result.resolver)).append(',')
                        .append(result.ok).append(',')
                        .append(format(result.kbps)).append(',')
                        .append(result.bytes).append(',')
                        .append(csv(result.reason)).append('\n');
            }
            writeText(new File(getFilesDir(), "resolver_results.csv"), csv.toString());
        } catch (IOException e) {
            appendLog("write output failed: " + e.getMessage());
        }
    }

    private List<String> parseResolvers(String text) {
        Set<String> seen = new LinkedHashSet<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine;
            int commentStart = line.indexOf('#');
            if (commentStart >= 0) {
                line = line.substring(0, commentStart);
            }
            for (String token : line.split("[,;\\s]+")) {
                String resolver = token.trim();
                if (!resolver.isEmpty()) {
                    seen.add(resolver);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    private String firstSubscriptionLine(String text) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (isSubscriptionLink(trimmed)) {
                return trimmed;
            }
        }
        return text.trim();
    }

    private Subscription parseOptionalSubscription() throws Exception {
        String raw = firstSubscriptionLine(subscriptionInput.getText().toString());
        if (!isSubscriptionLink(raw)) {
            return null;
        }
        return parseSubscription(raw);
    }

    private static boolean isSubscriptionLink(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.US);
        return lower.startsWith("stormdns://") || lower.startsWith("masterdns://");
    }

    private Subscription parseSubscription(String raw) throws Exception {
        String link = raw.trim();
        String lower = link.toLowerCase(Locale.US);
        String scheme;
        if (lower.startsWith("stormdns://")) {
            scheme = "stormdns";
        } else if (lower.startsWith("masterdns://")) {
            scheme = "masterdns";
        } else {
            throw new IllegalArgumentException("Unsupported URI scheme");
        }

        Exception jsonError = null;
        try {
            Subscription jsonSubscription = parseBase64JsonSubscription(scheme, link);
            if (jsonSubscription.hasRequiredFields()) {
                return jsonSubscription;
            }
        } catch (Exception e) {
            jsonError = e;
        }

        Subscription querySubscription = parseQuerySubscription(scheme, link);
        if (querySubscription.hasRequiredFields() || !querySubscription.resolvers.isEmpty()) {
            return querySubscription;
        }
        if (jsonError != null) {
            throw jsonError;
        }
        throw new IllegalArgumentException("Missing domain or encryption key");
    }

    private Subscription parseBase64JsonSubscription(String scheme, String link) throws Exception {
        String prefix = scheme + "://";
        String payload = link.substring(prefix.length()).trim();
        payload = substringBefore(substringBefore(payload, '#'), '?');
        if (payload.isEmpty()) {
            throw new IllegalArgumentException("Subscription payload is empty");
        }

        String decoded = decodeBase64(payload);
        JSONObject root = new JSONObject(decoded);
        JSONObject profile = root.optJSONObject("profile");
        JSONObject server = null;
        if (profile != null) {
            server = profile.optJSONObject("server");
        }
        if (server == null) {
            server = root.optJSONObject("server");
        }
        if (server == null) {
            server = root;
        }

        String domain = firstNonEmpty(
                optStringAny(server, "domain", "server_domain", "host"),
                optStringAny(root, "domain", "server_domain", "host")
        ).trim();
        String key = firstNonEmpty(
                optStringAny(server, "encryption_key", "encrypt_key", "key", "secret"),
                optStringAny(root, "encryption_key", "encrypt_key", "key", "secret")
        ).trim();
        int method = firstValidInt(
                optIntAny(server, "encryption_method", "method", "encryption"),
                optIntAny(root, "encryption_method", "method", "encryption")
        );

        List<String> resolvers = new ArrayList<>();
        JSONObject resolversObject = profile != null ? profile.optJSONObject("resolvers") : null;
        if (resolversObject == null) {
            resolversObject = root.optJSONObject("resolvers");
        }
        if (resolversObject != null) {
            resolvers.addAll(jsonStringList(resolversObject.optJSONArray("entries")));
            resolvers.addAll(parseResolvers(resolversObject.optString("text", "")));
        }
        resolvers.addAll(jsonStringList(root.optJSONArray("resolver_entries")));
        resolvers.addAll(jsonStringList(root.optJSONArray("resolvers")));
        resolvers.addAll(parseResolvers(root.optString("resolver_text", "")));

        return new Subscription(scheme, domain.trim().replaceAll("\\.$", ""), key, clampMethod(method), parseResolvers(joinLines(resolvers)));
    }

    private Subscription parseQuerySubscription(String scheme, String link) {
        String prefix = scheme + "://";
        String rest = link.substring(prefix.length()).trim();
        String query = "";
        int queryStart = rest.indexOf('?');
        if (queryStart >= 0) {
            query = substringBefore(rest.substring(queryStart + 1), '#');
            rest = rest.substring(0, queryStart);
        }
        rest = substringBefore(rest, '#');

        QueryParams params = parseQueryParams(query);
        String domain = firstNonEmpty(
                params.first("domain", "server_domain", "host"),
                rest.contains("=") ? "" : urlDecode(rest)
        ).trim().replaceAll("\\.$", "");
        String key = params.first("encryption_key", "encrypt_key", "key", "secret");
        int method = clampMethod(parseIntOrMinusOne(params.first("encryption_method", "method", "encryption")));
        String resolverText = firstNonEmpty(
                params.first("resolvers", "resolver_entries", "resolver", "dns"),
                ""
        );
        return new Subscription(scheme, domain, key.trim(), method, parseResolvers(resolverText));
    }

    private static String decodeBase64(String payload) {
        String padded = payload + "====".substring(0, (4 - payload.length() % 4) % 4);
        try {
            return new String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return new String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8);
        }
    }

    private static QueryParams parseQueryParams(String query) {
        QueryParams params = new QueryParams();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            params.add(urlDecode(name).toLowerCase(Locale.US), urlDecode(value));
        }
        return params;
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String substringBefore(String value, char delimiter) {
        int index = value.indexOf(delimiter);
        return index >= 0 ? value.substring(0, index) : value;
    }

    private static String optStringAny(JSONObject object, String... names) {
        if (object == null) {
            return "";
        }
        for (String name : names) {
            String value = object.optString(name, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static int optIntAny(JSONObject object, String... names) {
        if (object == null) {
            return -1;
        }
        for (String name : names) {
            if (object.has(name)) {
                int value = object.optInt(name, -1);
                if (value >= 0) {
                    return value;
                }
            }
        }
        return -1;
    }

    private static List<String> jsonStringList(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static int firstValidInt(int... values) {
        for (int value : values) {
            if (value >= 0) {
                return value;
            }
        }
        return -1;
    }

    private static int parseIntOrMinusOne(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static int clampMethod(int method) {
        if (method < 0) {
            return -1;
        }
        return Math.max(0, Math.min(5, method));
    }

    private void copyGoodResolvers() {
        List<String> good = new ArrayList<>();
        for (TestResult result : lastResults) {
            if (result.ok) {
                good.add(result.resolver);
            }
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("good_resolvers", joinLines(good)));
        toast("Copied " + good.size() + " resolvers");
    }

    private void clearAll() {
        if (stopButton.isEnabled()) {
            toast("Stop the test before clearing");
            return;
        }
        resolversInput.setText("");
        lastResults.clear();
        logBuffer.setLength(0);
        logText.setText("");
        statusText.setText("Idle");
        writeOutputs(new ArrayList<>(), new ArrayList<>());
        savePrefs();
        toast("Cleared");
    }

    private void killCurrentProcess() {
        synchronized (runningProcesses) {
            for (Process process : runningProcesses) {
                process.destroy();
            }
            for (Process process : runningProcesses) {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
            runningProcesses.clear();
        }
        Process process = currentProcess;
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int parseEncryptionMethod(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return -1;
        }
        try {
            int method = Integer.parseInt(value);
            return method >= 0 && method <= 5 ? method : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void setRunning(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        clearAllButton.setEnabled(!running);
    }

    private void updateStatus(String status) {
        mainHandler.post(() -> statusText.setText(status));
    }

    private void appendLog(String line) {
        mainHandler.post(() -> {
            logBuffer.append(line).append('\n');
            if (logBuffer.length() > 24000) {
                logBuffer.delete(0, logBuffer.length() - 20000);
            }
            logText.setText(logBuffer.toString());
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(text);
        }
    }

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String stripAnsi(String value) {
        return value.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    private static String escapeToml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static final class Subscription {
        final String scheme;
        final String domain;
        final String encryptionKey;
        final int encryptionMethod;
        final List<String> resolvers;

        Subscription(String scheme, String domain, String encryptionKey, int encryptionMethod, List<String> resolvers) {
            this.scheme = scheme;
            this.domain = domain == null ? "" : domain.trim();
            this.encryptionKey = encryptionKey == null ? "" : encryptionKey.trim();
            this.encryptionMethod = encryptionMethod;
            this.resolvers = resolvers == null ? new ArrayList<>() : resolvers;
        }

        boolean hasRequiredFields() {
            return !domain.isEmpty() && !encryptionKey.isEmpty();
        }
    }

    private static final class QueryParams {
        private final List<String[]> pairs = new ArrayList<>();

        void add(String name, String value) {
            pairs.add(new String[]{name, value});
        }

        String first(String... names) {
            for (String wanted : names) {
                for (String[] pair : pairs) {
                    if (pair[0].equals(wanted)) {
                        return pair[1];
                    }
                }
            }
            return "";
        }
    }

    private static final class HttpProbe {
        final boolean ok;
        final double kbps;
        final int bytes;
        final String reason;

        HttpProbe(boolean ok, double kbps, int bytes, String reason) {
            this.ok = ok;
            this.kbps = kbps;
            this.bytes = bytes;
            this.reason = reason;
        }

        static HttpProbe fetch(String urlText, int socksPort, int timeoutMs, boolean readBody) {
            long started = System.nanoTime();
            int bytes = 0;
            HttpURLConnection connection = null;
            try {
                Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", socksPort));
                URL url = new URL(urlText);
                connection = (HttpURLConnection) url.openConnection(proxy);
                connection.setConnectTimeout(timeoutMs / 2);
                connection.setReadTimeout(timeoutMs);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "DNS-Resolver-Tester/0.1");
                int code = connection.getResponseCode();
                boolean codeOk = readBody ? (code >= 200 && code < 300) : (code >= 200 && code < 400);
                if (readBody) {
                    try (InputStream input = codeOk ? connection.getInputStream() : connection.getErrorStream()) {
                        if (input != null) {
                            byte[] buffer = new byte[16 * 1024];
                            int read;
                            while ((read = input.read(buffer)) >= 0) {
                                bytes += read;
                            }
                        }
                    }
                }
                double seconds = Math.max(0.001, (System.nanoTime() - started) / 1_000_000_000.0);
                double kbps = bytes / 1024.0 / seconds;
                if (readBody && codeOk && bytes <= 0) {
                    return new HttpProbe(false, kbps, bytes, "http_" + code + "_empty_body");
                }
                return new HttpProbe(codeOk, kbps, bytes, "http_" + code);
            } catch (Exception e) {
                double seconds = Math.max(0.001, (System.nanoTime() - started) / 1_000_000_000.0);
                double kbps = bytes / 1024.0 / seconds;
                return new HttpProbe(false, kbps, bytes, e.getClass().getSimpleName() + ":" + safeMessage(e));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private static String safeMessage(Exception e) {
            String message = e.getMessage();
            if (message == null) {
                return "";
            }
            return message.replace('\n', ' ').replace('\r', ' ');
        }
    }

    private static final class TestResult {
        final String resolver;
        final boolean ok;
        final double kbps;
        final int bytes;
        final String reason;

        TestResult(String resolver, boolean ok, double kbps, int bytes, String reason) {
            this.resolver = resolver;
            this.ok = ok;
            this.kbps = kbps;
            this.bytes = bytes;
            this.reason = reason;
        }

        static TestResult bad(String resolver, String reason) {
            return new TestResult(resolver, false, 0.0, 0, reason);
        }

        String toLogLine() {
            return String.format(
                    Locale.US,
                    "%s %s %.2f KB/s %d bytes %s",
                    ok ? "OK " : "BAD",
                    resolver,
                    kbps,
                    bytes,
                    reason
            );
        }
    }
}
