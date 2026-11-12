# Tarefas: MVP ScreenTranslator

**Entrada**: Documentos de design em `specs/001-screen-translator-mvp/`.

**Nota**: Lista inicial de implementação; não inclui tarefas para criar ou executar testes.

## Fase 1 — Pesquisa e validação de escopo

- [ ] T001 Confirmar se ScreenTranslator de tela/áudio e tradutor de conversa presencial são o mesmo produto ou propostas diferentes.
- [ ] T002 Documentar matriz oficial de captura de tela, áudio, sobreposição e serviços em primeiro plano por versão do Android.
- [ ] T003 Comparar opções de OCR, tradução, fala-para-texto e texto-para-fala para inglês/espanhol → PT-BR.
- [ ] T004 Estimar custo por 1, 10 e 60 minutos e registrar premissas, retenção e qualidade.
- [ ] T005 Definir aparelho de referência, conjunto de cenários e critérios de medição de qualidade, latência e bateria.

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
- [ ] T024 Implementar alternância de áudio original/tradução/ambos somente quando a síntese de voz estiver aprovada no escopo.

## Fase 5 — História P3: modos e recuperação

- [ ] T025 Implementar modos Econômico, Equilibrado e Qualidade sobre políticas de execução documentadas.
- [ ] T026 Continuar recursos locais disponíveis ao perder conexão e indicar recursos remotos pausados.
- [ ] T027 Implementar histórico temporário da sessão e salvamento manual de uma tradução selecionada.
- [ ] T028 Implementar remoção de itens salvos e confirmar que nenhum conteúdo é salvo automaticamente.
- [ ] T029 Revisar logs, descarte e tratamento de erros para impedir persistência de conteúdo de tela/áudio.

## Fase 6 — Avaliação do MVP

- [ ] T030 Medir latência e qualidade nos cenários de referência e atualizar critérios da especificação.
- [ ] T031 Medir consumo de bateria e dados em sessões curtas e longas.
- [ ] T032 Registrar compatibilidade observada por aplicativo e dispositivo, incluindo limitações de DRM/captura.
- [ ] T033 Atualizar política de privacidade, explicações de permissões e limitações antes de qualquer distribuição externa.
