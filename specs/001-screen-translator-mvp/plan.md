# Plano de implementação: MVP EasyApp / ScreenTranslator

**Branch**: `001-screen-translator-mvp` | **Data**: 2026-11-06 | **Spec**: [spec.md](spec.md)

## Resumo

Construir um único aplicativo Android EasyApp / ScreenTranslator com dois modos: (1) Tradução de Tela e Apps, usando MediaProjection consentida, OCR local, tradução em sobreposição e legendas para áudio de reprodução quando a origem permitir; (2) Conversa Presencial por Voz, com microfone consentido, transcrição, tradução bidirecional PT-BR↔EN/ES e síntese de fala. O idioma inicial da prova vertical é inglês. Compatibilidade, qualidade, custo, bateria e latência serão medidos antes de qualquer promessa de suporte universal.

## Contexto técnico

**Linguagem/versão**: Kotlin; versão a confirmar ao criar o projeto  
**UI**: Jetpack Compose para o app; janela `TYPE_APPLICATION_OVERLAY` para a sobreposição
**Dependências principais**: Android SDK; Google ML Kit Text Recognition e Translation no dispositivo; `SpeechRecognizer` local quando disponível; `TextToSpeech` e vozes instaladas; no modo Qualidade, tradução opcional pelo Claude Haiku 5.5 via SDK Java oficial da Anthropic, com a chave do próprio usuário (decisão T042 em `research.md`)
**Armazenamento**: Preferências locais; áudio, quadros, transcrição e traduções da sessão somente em memória; itens salvos apenas por ação explícita
**Testes**: Testes em aparelho Android físico e testes automatizados a definir com o projeto  
**Plataforma**: API mínima 30 (Android 11+); target SDK a definir conforme requisito estável vigente na implementação
**Tipo de projeto**: Aplicativo móvel nativo  
**Metas de desempenho**: Alvo de 1–2 segundos para conteúdo curto sob condições favoráveis, ainda não validado  
**Restrições**: Consentimento separado para MediaProjection, microfone e sobreposição; serviço em primeiro plano conforme API; áudio de reprodução sujeito às políticas da origem; bateria/rede; sem contornar DRM
**Escopo**: Dois modos selecionáveis — captura → OCR/detecção de mudança → tradução → sobreposição/legenda; e microfone → transcrição → tradução bidirecional → TTS — seguidos de pesquisa e avaliação incremental

## Verificação da constituição

- Consentimento explícito e controle de parada: obrigatório.
- Privacidade e descarte após sessão: obrigatório.
- Compatibilidade limitada e degradação gradual: obrigatório.
- Eficiência e modos de qualidade: obrigatório, com implementação incremental.
- **Resultado**: compatível, sujeito a pesquisa técnica sobre APIs, versões Android, políticas de distribuição e provedores antes da implementação.

## Estratégia de entrega

1. Prova técnica em aparelho para consentimento de MediaProjection, overlay e diferenças API 30/34+.
2. Modo Tela e Apps: OCR local, detecção de mudança, tradução EN→PT-BR e overlay; adicionar ES depois da primeira prova vertical.
3. Modo Conversa Presencial: permissão do microfone, turnos de fala EN↔PT-BR, SpeechRecognizer local quando disponível, tradução e saída TTS; adicionar ES e fallback de Qualidade após validação.
4. Captura opcional de áudio de outros apps com AudioPlaybackCapture, STT e legendas somente em fontes compatíveis.
5. Ciclo de vida, controles, seleção de vozes, degradação gradual e descarte de dados temporários nos dois modos.
6. Modos Econômico/Equilibrado/Qualidade; a rota remota exige consentimento, política de retenção e gestão segura de credenciais. Implementada com o Claude Haiku 5.5 (Fase 8 de `tasks.md`): chave do usuário no Android Keystore, consentimento explícito, só texto enviado, fallback para o ML Kit e contador de custo.
7. Avaliação de compatibilidade, privacidade, qualidade, latência, bateria, tráfego e custos nos aparelhos/cenários definidos em `research.md`.
8. Estilo "No lugar do texto" (História 7, Fase 9 de `tasks.md`): um botão flutuante lê um único quadro, e uma camada de tela cheia que não recebe toques cobre cada linha com a tradução, na mesma posição. A captura vê as próprias janelas do app, então elas são escondidas antes de ler o quadro, e a camada usa `FLAG_SECURE` para não ser lida de volta; ela some quando a tela muda. Ler por toque, e não continuamente, também economiza bateria e tokens.

## Estrutura de projeto proposta

```text
android/
└── app/src/main/java/<namespace>/screentranslator/
    ├── session/       # ciclo de vida e controles da sessão
    ├── capture/       # projeção/captura e estados de permissão
    ├── text/          # análise de quadros, OCR e detecção de mudanças
    ├── audio/         # captura permitida, transcrição e TTS
    ├── translation/   # interface e seleção de execução local/remota
    ├── overlay/       # apresentação e controles de sobreposição
    ├── privacy/       # descarte e regras de retenção
    └── settings/      # idioma, modo e voz
```

**Decisão estrutural**: Um aplicativo Android nativo com modos independentes. Não criar backend até a comparação de provedores demonstrar necessidade; qualquer integração em nuvem deve ser encapsulada por interfaces e não pode expor credenciais permanentes no cliente.

**Decisão de credenciais (2026-11-13)**: o app não traz chave nenhuma. Quem quiser o modo Qualidade com Claude cola a própria chave de API, criptografada no aparelho pelo Android Keystore. Isso evita backend no MVP e mantém o repositório público e os APKs do CI sem segredo. A tradução remota fica atrás de uma interface (`translation/`), então trocar para um backend que guarde a chave, se o app for distribuído, não muda o pipeline de tela.

## Dependências e riscos técnicos

- A projeção de mídia exige consentimento por sessão e pode ser revogada; Android 14+ impõe token de uso único e declarações específicas de foreground service.
- Sobreposição requer permissão especial concedida pelo usuário.
- Áudio reproduzido por outros apps exige MediaProjection e RECORD_AUDIO, mesmo perfil e política de captura permissiva da origem.
- Reconhecimento Android on-device depende de disponibilidade do serviço do aparelho; SpeechRecognizer padrão pode não ser local.
- Serviços/modelos de OCR, tradução, reconhecimento de fala e síntese diferem em suporte linguístico, retenção e preço.
- Avaliar impacto de bateria, latência e rede; estabelecer limites para chamadas repetidas.
- Claude: latência de rede (o modo Qualidade pode ficar mais lento que o ML Kit), custo por token, chave revogada ou sem crédito e indisponibilidade do serviço. Todos caem para o ML Kit (FR-023).
- Revisar documentação atual do Android e políticas de distribuição antes de publicação; este plano não é uma aprovação de loja.
