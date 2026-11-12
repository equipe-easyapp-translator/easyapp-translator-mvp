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
- Serviço em primeiro plano com notificação contínua e ações para pausar, retomar e parar; encerra e limpa recursos ao receber `MediaProjection.Callback.onStop()`.
- DataStore persiste somente modo, idioma escolhido e modo de qualidade. Não há entidade nem código de persistência para captura, áudio, transcrição ou tradução.
- No modo Tela, `MediaProjection` alimenta um `ImageReader` RGBA com processamento serial, descarte de frames repetidos, OCR ML Kit Latin, identificação de inglês/espanhol e tradução local para português.
- Os modelos de OCR (Play Services) e tradução (ML Kit) são obtidos automaticamente quando necessários. O app não grava capturas, texto original ou tradução em armazenamento persistente.
- A janela flutuante oferece tamanho de fonte, transparência, pausar/retomar e fechar. A faixa traduzida some quando a origem não aparece mais na imagem reconhecida.
- No modo Conversa, `SpeechRecognizer` on-device entrega resultados parciais e finais por turno; a direção PT-BR↔EN/ES é escolhida por quem está falando. ML Kit traduz localmente e `TextToSpeech` usa apenas vozes que o engine identifica como offline, com escolha de voz, velocidade, volume e repetição.
- Áudio, transcrição e tradução da conversa vivem apenas em memória durante a sessão e são limpos ao parar. Se o pacote local de fala ou uma voz offline faltar, a tela informa como habilitá-los; não há fallback silencioso para STT remoto.

## Limites atuais

O pipeline de tela e conversa ainda precisa ser validado em aparelhos físicos API 30+; apps protegidos, janelas que bloqueiam captura, pacotes de fala ausentes e frames pequenos/animados podem limitar OCR, reconhecimento ou tradução. O reconhecimento offline via factory on-device requer Android 12/API 31 ou posterior; em Android 11 a UI orienta atualizar o sistema em vez de enviar fala a um serviço remoto.

O modelo de tradução ML Kit usa o idioma genérico `pt`; ele não permite selecionar separadamente a variante `pt-BR`. O destino é português local, mas a localização brasileira da redação não é garantida por este modelo.
