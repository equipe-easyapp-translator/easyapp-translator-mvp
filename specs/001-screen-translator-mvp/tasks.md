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

- [ ] T006 Criar projeto nativo Android em `android/` com Kotlin e versão mínima acordada.
- [ ] T007 Implementar fluxo de entrada, estado da sessão e controles explícitos iniciar/pausar/parar.
- [ ] T008 Implementar explicações e solicitações de permissões no momento de uso.
- [ ] T009 Implementar indicação persistente de sessão de captura e tratamento de revogação/encerramento.
- [ ] T010 Implementar armazenamento de preferências e modo de qualidade sem persistir conteúdo traduzido.

## Fase 3 — História P1: tradução de texto na tela

- [ ] T011 Implementar captura de tela consentida e liberação imediata de recursos ao parar.
- [ ] T012 Implementar análise de mudança e filtro de regiões/texto relevantes.
- [ ] T013 Integrar OCR local e detecção de inglês/espanhol.
- [ ] T014 Definir interface de tradução e integrar o provedor aprovado para PT-BR.
- [ ] T015 Implementar sobreposição de resultados com sincronização de expiração ao conteúdo.
- [ ] T016 Implementar deduplicação de segmentos e limite de trabalho/requisições repetidas.
- [ ] T017 Implementar controles essenciais de tamanho e transparência.
- [ ] T018 Remover buffers, segmentos e resultados efêmeros ao encerrar a sessão.

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

- [ ] T030 Implementar seleção do modo Conversa Presencial e do idioma estrangeiro (inglês ou espanhol), com fluxo bidirecional para PT-BR.
- [ ] T031 Implementar explicação, solicitação runtime de RECORD_AUDIO e controles explícitos para iniciar, pausar e encerrar os turnos de microfone.
- [ ] T032 Integrar SpeechRecognizer on-device quando disponível, detectar disponibilidade/idioma e apresentar transcrição incremental; definir fallback de Qualidade para STT remoto.
- [ ] T033 Integrar tradução de cada turno PT-BR↔EN/ES, usando ML Kit local como padrão e serviço remoto somente no modo Qualidade aprovado.
- [ ] T034 Integrar Android TextToSpeech, verificar vozes instaladas para PT-BR/EN/ES e permitir seleção; documentar indisponibilidade e fallback de voz.
- [ ] T035 Implementar reprodução da frase traduzida após o fim do turno reconhecido, opção de repetir/interromper e indicação clara do idioma de saída.
- [ ] T036 Manter áudio, transcrição e resultados efêmeros; liberar o recognizer e apagar buffers ao parar ou revogar a permissão.
- [ ] T037 Validar o fluxo com o roteiro da persona Marco em ambiente silencioso e ruidoso, registrando compreensão, latência, repetições, consumo e falhas.

## Fase 7 — Avaliação do MVP

- [ ] T038 Medir latência e qualidade nos cenários de referência e atualizar critérios da especificação.
- [ ] T039 Medir consumo de bateria e dados em sessões curtas e longas.
- [ ] T040 Registrar compatibilidade observada por aplicativo e dispositivo, incluindo limitações de DRM/captura.
- [ ] T041 Atualizar política de privacidade, explicações de permissões e limitações antes de qualquer distribuição externa.
