# Piko Android — native audio shell

Cel tej wersji: zachować aktualny interfejs i serwer Piko, ale podczas rozmów
oddać routing audio natywnemu Androidowi.

## Co robi natywnie

- ustawia `AudioManager.MODE_IN_COMMUNICATION` na stronach `/call.php` i `/video.php`;
- pobiera audio focus jako `USAGE_VOICE_COMMUNICATION`;
- Android 12+ używa `setCommunicationDevice()`:
  - domyślnie górna słuchawka (`TYPE_BUILTIN_EARPIECE`);
  - ręcznie można przełączyć na głośnik (`TYPE_BUILTIN_SPEAKER`);
- starszy Android używa `setSpeakerphoneOn(false/true)`;
- mikrofon i kamera są przyznawane WebRTC przez natywny `WebChromeClient`;
- sesja/logowanie pozostają w Piko przez cookies WebView;
- zdjęcia i pliki nadal można wybierać z telefonu;
- ekran nie wygasza się w trakcie rozmowy.

## Dlaczego to jest istotne

Web/PWA nie może wymusić Androidowego trybu komunikacyjnego tak jak aplikacja
natywna. Ta aplikacja robi to natywnie, nawet jeśli aktualny interfejs rozmowy
pozostaje stroną Piko.

## Ograniczenie wersji 0.1

To jest etap testowy dla jakości/routingu audio. WebRTC i sygnalizacja nadal
są wykonywane przez istniejącą stronę `call.php` w WebView.

Jeżeli ten test potwierdzi prawidłową słuchawkę, głośność i brak sprzężeń,
kolejnym etapem może być całkowicie natywny ekran rozmowy WebRTC.

## APK

Projekt zawiera workflow GitHub Actions `.github/workflows/build-apk.yml`.
Po umieszczeniu projektu w repozytorium GitHub workflow buduje instalowalny
`app-debug.apk` jako artefakt `Piko-Android-APK`.
