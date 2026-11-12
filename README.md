# EasyApp Translator (MVP)

Aplicativo Android para traduzir texto visível em outros apps e facilitar conversas presenciais por voz. Este repositório reúne o app nativo, as especificações do MVP, o protótipo web inicial e os documentos de negócio usados pela equipe.

## Equipe

- **Brayan:** [brayamartins9@gmail.com](mailto:brayamartins9@gmail.com)
- **Carlos:** [carlosdanielmds32@gmail.com](mailto:carlosdanielmds32@gmail.com)
- **Sante:** [santebiondi.fc@gmail.com](mailto:santebiondi.fc@gmail.com)
- **Tiago Morocini**

## 1. Visão Geral do Projeto

O app nativo é escrito em **Kotlin** e usa **Jetpack Compose** na interface. O processamento é feito prioritariamente no aparelho:

- **Google ML Kit Text Recognition** lê texto dos frames da tela.
- **Google ML Kit On-Device Translation** traduz os idiomas selecionados após o modelo necessário estar disponível no aparelho.
- **`MediaProjection` + `ImageReader`** capturam a tela somente depois do consentimento do Android.
- **`SpeechRecognizer`** reconhece fala em turnos. O app tenta reconhecimento on-device primeiro quando disponível; se o pacote offline não estiver disponível, tenta o serviço padrão do Android. Esse serviço pode depender de rede ou de um provedor externo, conforme a configuração do aparelho.
- **`TextToSpeech`** lê a tradução em voz alta e oferece vozes offline instaladas, quando disponíveis.

O app requer **Android 11 (API 30) ou superior**. O reconhecimento on-device do Android requer API 31+; a transcrição de áudio interno por entrada PCM depende de API 33+ e de um serviço de reconhecimento compatível. Os modelos do ML Kit podem precisar de uma conexão na primeira utilização para download.

As preferências de modo, idiomas e qualidade ficam no aparelho. Capturas, áudio, transcrições e traduções são mantidos somente em memória durante a sessão EasyApp e descartados ao encerrá-la. Se o fallback de voz usar o serviço padrão do Android, o tratamento da fala também depende das configurações e políticas desse serviço.

## 2. Funcionalidades Implementadas e Validadas em Aparelho Físico

O status abaixo reflete os testes físicos reportados pela equipe. A validação do pipeline OCR e da sobreposição foi confirmada. O teste de voz revelou uma falha no disparo da escuta, corrigida no commit `dc60914`; a correção precisa ser retestada no APK gerado por esse commit.

### Tradução de Tela (OCR + Sobreposição Flutuante)

- Captura de tela consentida com `MediaProjection` e `ImageReader`, com verificação periódica de frames a cada **1,3 s**.
- OCR e tradução on-device para o par de idiomas selecionado; o texto reconhecido aparece enquanto o modelo ou a tradução termina.
- Sobreposição que pode ser arrastada; toque curto recolhe ou expande o painel.
- Controles `A-`, `A+`, `PAUSAR` e `FECHAR`, além de ajuste de transparência.
- O time confirmou em aparelho físico a captura, o OCR e a tradução offline com ML Kit. A janela também foi confirmada como expandida e arrastável.
- Legendas de áudio interno são opcionais e ficam desativadas por padrão; apps de origem podem bloquear a captura.

### Conversa Presencial por Voz (STT + TTS)

- Conversa por turnos alternados: **Você** fala no idioma de destino (por exemplo, português) e o **Interlocutor** no idioma de origem (por exemplo, inglês).
- O botão **Ouvir turno em…** verifica `RECORD_AUDIO`, mostra o estado da escuta e inicia o reconhecimento na Main Thread do Android.
- Prioriza o `SpeechRecognizer` on-device quando disponível. Se o pacote offline falhar por idioma, rede ou serviço, tenta o reconhecedor padrão do Android e mostra o erro se a tentativa também falhar.
- Mostra transcrição parcial/final e tradução; inicia a leitura automática com `TextToSpeech` quando há voz disponível.
- Inclui botão **Repetir áudio traduzido**, seleção de voz offline, velocidade e volume.
- **Status físico:** o teste da equipe chegou à tela de conversa, identificou que a escuta não iniciava, e motivou a correção mais recente. Confirme o funcionamento da nova tentativa offline/fallback no APK atual antes de considerar esse fluxo validado novamente.

### Permissões e Serviço em Primeiro Plano

- Fluxos guiados para `RECORD_AUDIO`, `POST_NOTIFICATIONS` (Android 13+) e `SYSTEM_ALERT_WINDOW`.
- O Android solicita separadamente o consentimento de compartilhamento de tela via `MediaProjection`.
- `SessionService` mantém a sessão ativa em primeiro plano com notificação persistente e ações para pausar, retomar e parar.

## 3. Estrutura de Pastas do Repositório

```text
.
├── android/                         # App nativo Kotlin/Compose e Gradle Wrapper
│   └── app/src/main/java/com/easyapp/translator/
│       ├── data/                    # Preferências locais (DataStore)
│       ├── session/                 # Serviço, captura, OCR, tradução, voz e overlay
│       └── ui/                      # Telas Compose e tema
├── specs/001-screen-translator-mvp/ # Spec, plano, pesquisa e tarefas do MVP
├── .specify/                       # Memória e configuração Spec-Kit
├── .github/workflows/              # Build Android no GitHub Actions
├── prototype/                      # Protótipo web inicial
└── README.md                       # Este guia
```

Os documentos de negócio (`.docx`, `.pdf` e `.pptx`) estão na raiz. O projeto Android que deve ser aberto no Android Studio é a pasta `android/`.

## 4. Como Baixar e Instalar o APK pelo GitHub Actions

Não é necessário compilar localmente. O workflow **Android debug APK** gera o APK em todo `push` para `main` e também pode ser iniciado manualmente.

1. Acesse [Actions do repositório EasyApp Translator](https://github.com/equipe-easyapp-translator/easyapp-translator-mvp/actions).
2. Abra uma execução concluída do workflow **Android debug APK**. Para iniciar uma execução, escolha o workflow e clique em **Run workflow**.
3. Na página da execução, localize **Artifacts** e baixe **`easyapp-debug-apk`**.
4. Descompacte o artefato. O arquivo dentro dele é `app-debug.apk`.
5. Transfira o APK para o aparelho Android 11+ e abra-o. Se o Android solicitar, permita a instalação de apps dessa fonte.

O workflow usa Ubuntu e JDK 17, executa `./gradlew assembleDebug` na pasta `android/` e mantém o artefato por **14 dias**.

## 5. Guia Rápido de Uso no Celular

### Tradução de Tela

1. Abra o EasyApp e selecione **Tradução de Tela e Apps**.
2. Escolha os idiomas de origem e destino. O botão `⇄` inverte o par. Aguarde a primeira utilização baixar os modelos necessários, se ainda não estiverem no aparelho.
3. Mantenha **Legendas de áudio interno** desligadas para usar somente OCR. Ative essa opção apenas se quiser tentar legendar áudio reproduzido por outros apps.
4. Toque em **Iniciar sessão** e aceite o pedido do Android para sobrepor a outros apps e compartilhar a tela. Autorize as notificações quando solicitadas.
5. Abra ou retorne ao app que contém o texto. O EasyApp apresenta a tradução na janela flutuante. Arraste pela barra superior, toque nela para recolher/expandir, ajuste o tamanho com `A-`/`A+`, ou use **PAUSAR** e **FECHAR**.

### Conversa Presencial por Voz

1. Selecione **Conversa Presencial por Voz**, defina origem e destino e inicie a sessão. Conceda `RECORD_AUDIO` e notificações.
2. Escolha quem vai falar: **Você · Português** para falar em português ou **Interlocutor · Inglês/Espanhol/etc.** para a outra pessoa falar no idioma de origem.
3. Toque em **Ouvir turno em…** e fale depois que a tela indicar que está ouvindo. A transcrição e a tradução aparecem na tela; quando houver uma voz compatível, o EasyApp lê a tradução automaticamente.
4. Passe o aparelho ao outro interlocutor e alterne a seleção do falante para o próximo turno. Use **Repetir áudio traduzido** quando quiser ouvir a última tradução outra vez.
5. Para uso offline, baixe os idiomas de reconhecimento de fala e as vozes de texto para fala nas configurações do serviço Android instalado. Os nomes dos menus variam por fabricante; procure por **Reconhecimento de fala** e **Texto para fala** em **Configurações > Sistema > Idiomas e entrada**.
6. Se a fala offline não estiver disponível, o app tentará o serviço padrão de reconhecimento do Android. Esse serviço pode exigir internet; verifique as configurações de privacidade do provedor ativo.

Ao terminar qualquer modo, pare a sessão pela tela ou pela notificação persistente para encerrar a captura e descartar o conteúdo temporário.

## Apresentação do projeto

- [Roteiro acadêmico de 10 minutos, dividido entre os quatro integrantes](docs/APRESENTACAO.md)
- [Slides interativos em HTML, autocontidos e utilizáveis offline](docs/apresentacao.html)
