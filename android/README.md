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
- No modo Tela, há tentativa de captura de áudio de reprodução permitida usando `AudioPlaybackCaptureConfiguration` associado ao consentimento `MediaProjection`, VAD por energia PCM e legendas traduzidas no overlay. O buffer de legendas recente fica somente em RAM, pode ser limpo manualmente e é descartado ao parar.
- A transcrição de reprodução usa a entrada PCM por `ParcelFileDescriptor` suportada a partir do Android 13/API 33 e depende de um serviço on-device compatível com áudio externo. Em Android 11/12, captura e VAD podem funcionar, mas a API padrão não oferece essa entrada de áudio ao `SpeechRecognizer`; OCR continua disponível.
- Para iniciar legendas de reprodução o app pede `RECORD_AUDIO`, exigido pelo Android também para playback capture. O pipeline fornece PCM de reprodução por `AudioRecord`; porém, o Android documenta que um serviço de reconhecimento que não suporte `EXTRA_AUDIO_SOURCE` pode abrir o microfone. O diálogo de permissão informa esse comportamento; negue a permissão se não aceitar essa possibilidade. O app de origem também pode bloquear a reprodução por política/DRM; silêncio não permite ao Android distinguir bloqueio de ausência de som.
- O modo Econômico, Equilibrado e Qualidade ajusta limiar e janela do VAD; a captura só alimenta o reconhecedor quando há atividade detectada. Se um modelo local de tradução faltar, a mensagem orienta baixar o modelo quando houver conexão, sem enviar conteúdo para um serviço remoto.

## Abrir, compilar e instalar

1. No Android Studio, escolha **Open** e selecione a pasta `android/` (a que contém `settings.gradle.kts`).
2. Use JDK 17 e aceite a sincronização Gradle. Instale a plataforma e Build Tools pedidos pela configuração do projeto caso faltem no SDK Manager.
3. Para gerar um APK, escolha **Build > Build Bundle(s) / APK(s) > Build APK(s)**. O APK de depuração fica em `app/build/outputs/apk/debug/app-debug.apk`.
4. Ative depuração USB no aparelho Android 11/API 30 ou superior e instale o APK pelo aviso do Android Studio ou com `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Baixar o APK compilado no GitHub Actions

Cada `push` para `main` inicia o workflow **Android debug APK**; também é possível iniciá-lo manualmente pela aba **Actions** do repositório, escolhendo **Android debug APK > Run workflow**. Abra a execução concluída, localize **Artifacts** no fim da página e baixe `easyapp-debug-apk`. Descompacte o arquivo baixado para obter `app-debug.apk`. O artefato fica disponível por 14 dias.

## Roteiro manual de validação e demo

Esses passos são validações a executar em aparelho; esta lista não afirma que já foram executados. Registre modelo do aparelho, versão Android, idioma/pacote on-device, tempos percebidos e mensagens exibidas.

1. Inicie **Tradução de Tela e Apps** e aceite notificação, sobreposição, `RECORD_AUDIO` e compartilhamento de tela. Revogue qualquer consentimento e confirme encerramento/limpeza; pause, retome e pare pela tela e notificação.
2. Mostre frases em inglês e espanhol na tela. Confirme OCR/tradução, deduplicação, desaparecimento quando a origem sai da tela e ajuste de fonte/transparência.
3. Reproduza áudio em um app conhecido por permitir captura. Em API 33+, verifique atividade VAD, transcrição, tradução e legenda temporizada/identificada como turno na sobreposição e no buffer do app. Repita em API 30/31/32 e confirme que o aviso informa a limitação de transcrição PCM.
4. Repita com áudio parado/silencioso e com um app que bloqueie captura (por exemplo, conteúdo protegido). Confirme que a mensagem diz “sem áudio capturável” e explica que a fonte pode estar silenciosa ou bloquear captura, sem atribuir certeza a DRM.
5. Com os modelos OCR/tradução/fala já instalados, desligue a rede e repita tela e reprodução. Depois limpe os dados/modelos de tradução ou remova o pacote offline de fala e confirme orientação de download/indisponibilidade, sem fallback remoto.
6. Use **Limpar agora** durante a sessão e confirme que as legendas desaparecem do buffer. Pare a sessão, inicie outra e confirme buffer vazio; confira também que nenhum conteúdo aparece em preferências, arquivos ou logs do app.
7. **Demo persona Marco (T037):** simule a atividade de viagem/atendimento do storyboard: Marco aponta o telefone para instruções em inglês/espanhol e depois ouve um interlocutor estrangeiro. Repita em ambiente silencioso e ruidoso, conte pedidos de repetição, observe compreensão e latência ponta a ponta, anote consumo/falhas e compare os modos Econômico, Equilibrado e Qualidade. A disponibilidade do aparelho e os resultados reais devem ser registrados após executar o roteiro.

Para a compilação via terminal, com JDK 17 e SDK configurados, use `cd android && ./gradlew assembleDebug` (Windows: `cd android; .\gradlew.bat assembleDebug`). Testes locais existentes podem ser chamados com `./gradlew testDebugUnitTest` dentro de `android/`.

## Limites atuais

O pipeline de tela e conversa ainda precisa ser validado em aparelhos físicos API 30+; apps protegidos, janelas que bloqueiam captura, pacotes de fala ausentes e frames pequenos/animados podem limitar OCR, reconhecimento ou tradução. O reconhecimento offline via factory on-device requer Android 12/API 31 ou posterior; em Android 11 a UI orienta atualizar o sistema em vez de enviar fala a um serviço remoto.

O modelo de tradução ML Kit usa o idioma genérico `pt`; ele não permite selecionar separadamente a variante `pt-BR`. O destino é português local, mas a localização brasileira da redação não é garantida por este modelo.

A transcrição de reprodução também depende da compatibilidade do serviço on-device do aparelho com a fonte PCM externa. O Android documenta que serviços sem suporte para essa fonte podem recorrer ao microfone.
