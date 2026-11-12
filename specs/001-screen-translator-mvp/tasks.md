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

- [ ] T019 Criar prova de captura de áudio em fontes compatíveis e apresentar status de suporte.
- [ ] T020 Integrar transcrição para os idiomas de origem aprovados.
- [ ] T021 Encaminhar transcrição ao serviço de tradução e exibir legenda PT-BR temporizada.
- [ ] T022 Fazer legenda desaparecer junto com o segmento de fala correspondente.
- [ ] T023 Tratar captura bloqueada, áudio ausente, rede indisponível e idioma não suportado com degradação clara.
- [ ] T024 No modo Tela e Apps, apresentar apenas legendas para áudio capturável; a escolha de áudio original/tradução/ambos e síntese após frase completa pertence ao modo Conversa Presencial.

## Fase 5 — História P3: modos e recuperação

- [ ] T025 Implementar modos Econômico, Equilibrado e Qualidade sobre políticas de execução documentadas.
- [ ] T026 Continuar recursos locais disponíveis ao perder conexão e indicar recursos remotos pausados.
- [ ] T027 Implementar histórico temporário da sessão e salvamento manual de uma tradução selecionada.
- [ ] T028 Implementar remoção de itens salvos e confirmar que nenhum conteúdo é salvo automaticamente.
- [ ] T029 Revisar logs, descarte e tratamento de erros para impedir persistência de conteúdo de tela/áudio.

## Fase 6 — Modo Conversa Presencial por Voz

- [x] T030 Implementar seleção de interlocutor e idioma (inglês ou espanhol), com turnos bidirecionais entre português e o idioma selecionado.
- [x] T031 Implementar explicação/permissão `RECORD_AUDIO` já existente e controles explícitos para iniciar, cancelar, pausar e encerrar turnos.
- [x] T032 Integrar `SpeechRecognizer` on-device quando disponível, receber transcrição parcial/final e orientar sobre pacote offline ausente, silêncio, ruído e falhas de serviço/rede.
- [x] T033 Integrar tradução local ML Kit PT→EN/ES e EN/ES→português, sem fallback remoto silencioso.
- [x] T034 Integrar `TextToSpeech`, listar e selecionar apenas vozes offline instaladas para o idioma traduzido; orientar quando não houver uma voz compatível.
- [x] T035 Sintetizar a tradução final, permitir repetir áudio, ajustar velocidade/volume e alternar rapidamente quem fala.
- [x] T036 Manter captura, transcrição, tradução e repetição apenas em memória; cancelar recognizer, parar TTS, fechar translators e limpar estado ao encerrar/revogar.
- [ ] T037 Executar validação do roteiro da persona Marco em dispositivo real, em ambiente silencioso e ruidoso, registrando compreensão, latência, repetições, consumo e falhas (pendente: ambiente sem JDK/SDK/emulador/aparelho conectado).

## Fase 7 — Avaliação do MVP

- [ ] T038 Medir latência e qualidade nos cenários de referência e atualizar critérios da especificação.
- [ ] T039 Medir consumo de bateria e dados em sessões curtas e longas.
- [ ] T040 Registrar compatibilidade observada por aplicativo e dispositivo, incluindo limitações de DRM/captura.
- [ ] T041 Atualizar política de privacidade, explicações de permissões e limitações antes de qualquer distribuição externa.
