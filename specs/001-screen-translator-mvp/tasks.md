# Tarefas: MVP ScreenTranslator

**Entrada**: Documentos de design em `specs/001-screen-translator-mvp/`.

**Nota**: Lista inicial de implementação; não inclui tarefas para criar ou executar testes.

## Fase 1 — Pesquisa e validação de escopo

- [x] T001 Confirmar o escopo unificado EasyApp / ScreenTranslator em um app Android com os modos Tela e Apps e Conversa Presencial por Voz.
- [x] T002 Documentar a matriz Android API 30+ para MediaProjection, captura de áudio, SYSTEM_ALERT_WINDOW, RECORD_AUDIO e serviços em primeiro plano.
- [x] T003 Definir stack inicial Kotlin/Jetpack Compose, ML Kit OCR/Translation, SpeechRecognizer/TTS local quando disponível e modo Qualidade remoto opcional.
- [x] T004 Estimar custos por 1, 10 e 60 minutos com premissas e tarifas oficiais em USD em `research.md`.
- [x] T005 Definir aparelho de referência proposto, cenários e critérios de medição; a disponibilidade do aparelho e as medições permanecem pendentes.

## Fase 2 — Fundação Android

- [x] T006 Criar projeto nativo Android em `android/` com Kotlin, Jetpack Compose, Gradle, `minSdk 30` e dependências iniciais de ML Kit OCR/Translation.
- [x] T007 Implementar tela inicial com os dois modos unificados, estado da sessão em memória (`IDLE`, `ACTIVE`, `PAUSED`) e controles explícitos iniciar/pausar/retomar/parar.
- [x] T008 Implementar explicações no momento de uso e fluxos para `SYSTEM_ALERT_WINDOW`, `RECORD_AUDIO`, notificação e consentimento `MediaProjection`.
- [x] T009 Implementar serviço em primeiro plano com notificação persistente e ações de pausa/retomada/parada, encerrando ao parar a captura ou o serviço.
- [x] T010 Implementar persistência DataStore apenas para modo preferido, idioma e qualidade (`Econômico`, `Equilibrado`, `Qualidade`), sem salvar conteúdo traduzido.

## Fase 3 — História P1: tradução de texto na tela

- [x] T011 Implementar captura consentida via `MediaProjection`, `VirtualDisplay` e `ImageReader` no serviço em primeiro plano.
- [x] T012 Implementar amostragem/detecção de mudança de frame e filtrar por região (barras superior/inferior), tamanho, repetição e limite de linhas por quadro.
- [x] T013 Integrar OCR local ML Kit Latin e identificar inglês/espanhol com ML Kit Language ID.
- [x] T014 Integrar tradução local ML Kit EN/ES→português (`pt`) com download automático dos modelos; a API não permite escolher especificamente a variante `pt-BR`.
- [x] T015 Implementar sobreposição flutuante e remover a faixa traduzida quando a origem deixa de ser reconhecida.
- [x] T016 Deduplicar por texto normalizado e aplicar intervalo entre quadros por modo de qualidade.
- [x] T017 Implementar controles de fonte, transparência, pausa/retomada e fechamento da sobreposição.
- [x] T018 Zerar buffers de imagem e descartar caches, segmentos e janela ao pausar, parar ou revogar a projeção.

## Fase 4 — História P2: legendas traduzidas de áudio

- [x] T019 Implementar captura PCM de reprodução via AudioPlaybackCaptureConfiguration/MediaProjection, com status de disponibilidade e fallback sem interromper OCR.
- [x] T020 Implementar VAD por energia e transcrição parcial/final em API 33+ via SpeechRecognizer on-device com ParcelFileDescriptor; API 30–32 informa que a API padrão não fornece entrada PCM externa.
- [x] T021 Traduzir segmentos EN/ES com o modelo local genérico `pt` do ML Kit e exibir legenda de origem/tradução com rótulo e turno na sobreposição (variante `pt-BR` não selecionável nesse modelo).
- [x] T022 Expirar legenda de áudio após a janela temporária do segmento e limpar legenda ao pausar/parar.
- [x] T023 Tratar erro, silêncio, fluxo vazio, bloqueio ou modelo ausente com mensagem que não confunde silêncio com DRM e não usa fallback remoto.
- [x] T024 No modo Tela e Apps, apresentar somente reprodução capturável como legenda; seleção de áudio original/tradução/ambos e síntese seguem no modo Conversa Presencial.

## Fase 5 — História P3: modos e recuperação

- [x] T025 Aplicar limiar de energia e janela VAD próprios dos modos Econômico, Equilibrado e Qualidade.
- [x] T026 Usar reconhecimento on-device quando disponível; manter fluxo local sem rede e orientar download de modelo local ausente, sem fallback remoto.
- [x] T027 Manter até 40 segmentos recentes somente em RAM durante a sessão, sem persistência ou salvamento manual de conteúdo.
- [x] T028 Permitir limpeza imediata do buffer de legendas na tela da sessão e descartar automaticamente ao pausar/parar/encerrar.
- [x] T029 Limpar buffers PCM e conteúdo volátil e evitar gravar áudio, transcrição ou tradução em logs/armazenamento persistente.

## Fase 6 — Modo Conversa Presencial por Voz

- [x] T030 Implementar seleção de interlocutor e idioma (inglês ou espanhol), com turnos bidirecionais entre português e o idioma selecionado.
- [x] T031 Implementar explicação/permissão `RECORD_AUDIO` já existente e controles explícitos para iniciar, cancelar, pausar e encerrar turnos.
- [x] T032 Integrar `SpeechRecognizer` on-device quando disponível, receber transcrição parcial/final e orientar sobre pacote offline ausente, silêncio, ruído e falhas de serviço/rede.
- [x] T033 Integrar tradução local ML Kit PT→EN/ES e EN/ES→português, sem fallback remoto silencioso.
- [x] T034 Integrar `TextToSpeech`, listar e selecionar apenas vozes offline instaladas para o idioma traduzido; orientar quando não houver uma voz compatível.
- [x] T035 Sintetizar a tradução final, permitir repetir áudio, ajustar velocidade/volume e alternar rapidamente quem fala.
- [x] T036 Manter captura, transcrição, tradução e repetição apenas em memória; cancelar recognizer, parar TTS, fechar translators e limpar estado ao encerrar/revogar.
- [x] T037 Validar o fluxo da persona Marco em dispositivo Android físico; teste presencial da tela de Conversa por Voz concluído (relato do usuário).

## Fase 7 — Avaliação do MVP

- [ ] T038 Medir latência e qualidade nos cenários de referência e atualizar critérios da especificação.
- [ ] T039 Medir consumo de bateria e dados em sessões curtas e longas.
- [ ] T040 Registrar compatibilidade observada por aplicativo e dispositivo, incluindo limitações de DRM/captura.
- [ ] T041 Atualizar política de privacidade, explicações de permissões e limitações antes de qualquer distribuição externa.

## Fase 8 — História P3: tradução refinada e explicação com Claude (opcional)

- [x] T042 Escolher o provedor da rota remota (Claude Haiku 5.5), confirmar retenção e tarifa oficiais e estimar custo por operação e por hora em `research.md`.
- [x] T043 Adicionar o SDK Java oficial da Anthropic ao projeto Android e confirmar o build no CI. Build do CI aprovado em 2026-11-13 (run 43526597452).
- [x] T044 Guardar a chave do usuário cifrada pelo Android Keystore, com tela para colar, testar, mostrar só o final e apagar; excluir do backup.
- [x] T045 Mostrar o aviso de consentimento antes do primeiro uso (o que é enviado, retenção de 30 dias, sem treinamento por padrão) e permitir revogar.
- [x] T046 No modo Qualidade com chave e consentimento, traduzir pelo Claude só as linhas novas da tela, em lote, com intervalo mínimo entre chamadas, `max_tokens` baixo, esforço baixo e raciocínio desligado.
- [x] T047 Cair para o ML Kit em qualquer falha do Claude (rede, 401, 429, sem crédito, recusa) e mostrar o motivo sem encerrar a sessão.
- [x] T048 Indicar na sobreposição quando a tradução é online e implementar "O que isso quer dizer?" ao tocar numa linha.
- [x] T049 Somar tokens e custo estimado da sessão a partir do `usage` de cada resposta e mostrar no app; zerar ao encerrar.
- [ ] T050 (T044–T049 estão implementadas e compilam, mas só contam como validadas depois deste teste.) Validar no aparelho: com e sem chave, sem internet, chave inválida e comparação de qualidade com o ML Kit; registrar custo real da sessão (alimenta T038).

## Fase 9 — História P2: traduzir a tela no lugar do texto, por botão

- [x] T051 Adicionar a preferência de estilo (`No lugar do texto` / `Janela flutuante`) no setup do modo Tela e guardar no DataStore.
- [x] T052 No estilo "No lugar do texto", manter a captura ativa sem OCR contínuo e ler um único quadro quando o usuário tocar em Traduzir, depois de esconder as janelas do próprio app.
- [x] T053 Guardar a posição de cada linha do OCR, converter da resolução da captura para a da tela e amostrar a cor de fundo e a cor de texto em volta de cada linha.
- [x] T054 Traduzir as linhas do quadro (Claude em lote no modo Qualidade com chave; ML Kit no resto), com cache por linha na sessão e sem cobrir linhas que já estão no idioma de destino.
- [x] T055 Desenhar a camada de tela cheia, que fecha ao primeiro toque (FR-026), com cada tradução ajustada ao tamanho da linha original.
- [x] T056 Mostrar o botão flutuante e arrastável com Traduzir/Limpar, o estado ("Lendo a tela…", "Traduzindo…") e Parar.
- [x] T057 Esconder a camada quando o conteúdo da tela mudar (rolagem, mensagem nova, troca de app), sem disparar ao arrastar o botão.
- [ ] T058 (T051–T057 estão implementadas e compilam, run 43532819425; só contam como validadas depois deste teste.) Validar no aparelho com WhatsApp e navegador: alinhamento com barra de status e entalhe, tema claro/escuro do app de origem, textos longos e rolagem.
