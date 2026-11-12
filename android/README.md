# EasyApp Android

Base nativa do EasyApp / ScreenTranslator em Kotlin e Jetpack Compose.

## Requisitos

- Android Studio compatível com Android Gradle Plugin 9.5.1
- JDK 17
- Gradle 9.6.0
- Android SDK Platform 37 e Build Tools compatíveis
- `minSdk` 30 (Android 11)

Abra a pasta `android/` no Android Studio e sincronize o projeto Gradle. A configuração fixa versões no catálogo `gradle/libs.versions.toml`.

## Estado desta fundação

- Tela Compose para escolher Tradução de Tela e Apps ou Conversa Presencial por Voz.
- Estado de sessão em memória (`IDLE`, `ACTIVE`, `PAUSED`), com iniciar, pausar, retomar e parar.
- Explicação antes das permissões de notificação, sobreposição, microfone e MediaProjection.
- Serviço em primeiro plano com notificação contínua e ações para pausar, retomar e parar; encerra ao receber `MediaProjection.Callback.onStop()`.
- DataStore persiste somente modo, idioma escolhido e modo de qualidade. Não há entidade nem código de persistência para captura, áudio, transcrição ou tradução.
- Dependências de ML Kit estão declaradas. OCR, tradução, reconhecimento de fala e captura efetiva ainda serão ligados nas fases correspondentes.

## Limites atuais

A solicitação de MediaProjection obtém o consentimento e inicia o serviço, mas ainda não cria um `VirtualDisplay` nem lê quadros. O modo de conversa solicita `RECORD_AUDIO`, mas ainda não inicia SpeechRecognizer/AudioRecord nem sintetiza fala. O serviço e a notificação estabelecem o ciclo de vida que será conectado aos pipelines nas próximas fases.
