package pl.piko.android;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String BASE_URL = "https://piko.szkola.pl/";
    private static final int REQ_MEDIA = 701;
    private static final int REQ_FILE = 702;

    private WebView webView;
    private Button routeButton;

    private PermissionRequest pendingWebPermission;
    private ValueCallback<Uri[]> pendingFileCallback;

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private int oldAudioMode = AudioManager.MODE_NORMAL;

    private boolean inCallMode = false;
    private boolean speakerEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7, 11, 20));

        webView = new WebView(this);
        FrameLayout.LayoutParams webLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        root.addView(webView, webLp);

        routeButton = new Button(this);
        routeButton.setText("SŁUCHAWKA");
        routeButton.setTextColor(Color.WHITE);
        routeButton.setTextSize(11f);
        routeButton.setAllCaps(false);
        routeButton.setBackgroundColor(Color.argb(225, 31, 37, 58));
        routeButton.setVisibility(View.GONE);
        routeButton.setOnClickListener(v -> {
            speakerEnabled = !speakerEnabled;
            routeCommunicationAudio(speakerEnabled);
            updateRouteButton();
        });

        FrameLayout.LayoutParams routeLp = new FrameLayout.LayoutParams(
                dp(116), dp(52)
        );
        routeLp.gravity = Gravity.TOP | Gravity.END;
        routeLp.topMargin = dp(52);
        routeLp.rightMargin = dp(12);
        root.addView(routeButton, routeLp);

        setContentView(root);
        configureWebView();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(BASE_URL);
        }
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(true);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri != null && isPikoHost(uri)) {
                    updateCallMode(uri.toString());
                    return false;
                }

                if (uri != null) {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                updateCallMode(url);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                updateCallMode(url);
                super.onPageFinished(view, url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermission(request));
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, REQ_FILE);
                } catch (Exception e) {
                    pendingFileCallback = null;
                    Toast.makeText(MainActivity.this, "Nie można otworzyć plików.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) req.addRequestHeader("Cookie", cookie);
                if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);

                req.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );
                req.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "piko-pobranie"
                );

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(req);
                Toast.makeText(this, "Pobieranie rozpoczęte.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
    }

    private boolean isPikoHost(Uri uri) {
        String host = uri.getHost();
        return host != null && (
                host.equals("piko.szkola.pl") ||
                host.endsWith(".piko.szkola.pl")
        );
    }

    private void handleWebPermission(PermissionRequest request) {
        List<String> androidPermissions = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                androidPermissions.add(Manifest.permission.RECORD_AUDIO);
            }

            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) &&
                    checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                androidPermissions.add(Manifest.permission.CAMERA);
            }
        }

        if (!androidPermissions.isEmpty()) {
            pendingWebPermission = request;
            requestPermissions(androidPermissions.toArray(new String[0]), REQ_MEDIA);
            return;
        }

        grantSafeWebResources(request);
    }

    private void grantSafeWebResources(PermissionRequest request) {
        List<String> grants = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                grants.add(resource);
            }

            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) &&
                    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                grants.add(resource);
            }
        }

        if (grants.isEmpty()) {
            request.deny();
        } else {
            request.grant(grants.toArray(new String[0]));
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_MEDIA && pendingWebPermission != null) {
            PermissionRequest request = pendingWebPermission;
            pendingWebPermission = null;
            grantSafeWebResources(request);
        }
    }

    private void updateCallMode(String url) {
        boolean shouldBeInCall = url != null &&
                (url.contains("/call.php") || url.contains("/video.php"));

        if (shouldBeInCall && !inCallMode) {
            enterCommunicationMode();
        } else if (!shouldBeInCall && inCallMode) {
            leaveCommunicationMode();
        }
    }

    private void enterCommunicationMode() {
        inCallMode = true;
        speakerEnabled = false;

        oldAudioMode = audioManager.getMode();

        requestCallAudioFocus();

        audioManager.setMicrophoneMute(false);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        routeCommunicationAudio(false);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        routeButton.setVisibility(View.VISIBLE);
        updateRouteButton();
    }

    private void leaveCommunicationMode() {
        inCallMode = false;

        clearCommunicationRoute();

        try {
            audioManager.setMode(oldAudioMode == AudioManager.MODE_INVALID
                    ? AudioManager.MODE_NORMAL
                    : oldAudioMode);
        } catch (Exception ignored) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }

        abandonCallAudioFocus();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        routeButton.setVisibility(View.GONE);
    }

    private void requestCallAudioFocus() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusChange -> {})
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(
                    focusChange -> {},
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            );
        }
    }

    private void abandonCallAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        } else {
            audioManager.abandonAudioFocus(null);
        }
    }

    private void routeCommunicationAudio(boolean toSpeaker) {
        if (!inCallMode) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioDeviceInfo selected = null;
            List<AudioDeviceInfo> devices = audioManager.getAvailableCommunicationDevices();

            int wantedType = toSpeaker
                    ? AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    : AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;

            for (AudioDeviceInfo device : devices) {
                if (device.getType() == wantedType) {
                    selected = device;
                    break;
                }
            }

            if (selected != null) {
                audioManager.setCommunicationDevice(selected);
            } else if (!toSpeaker) {
                audioManager.clearCommunicationDevice();
            }
        } else {
            audioManager.setSpeakerphoneOn(toSpeaker);
        }
    }

    private void clearCommunicationRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice();
        } else {
            audioManager.setSpeakerphoneOn(false);
        }
    }

    private void updateRouteButton() {
        routeButton.setText(speakerEnabled ? "🔊 Głośnik" : "📞 Słuchawka");
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();

        String url = webView.getUrl();
        if (url != null) {
            updateCallMode(url);
            if (inCallMode) routeCommunicationAudio(speakerEnabled);
        }
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (inCallMode) {
            leaveCommunicationMode();
        }

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (pendingFileCallback != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                pendingFileCallback.onReceiveValue(results);
                pendingFileCallback = null;
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
