# Modelo de dados: MVP ScreenTranslator

Os dados de conteúdo são efêmeros por padrão. Não persistir capturas ou áudio bruto.

## Entidades

### TranslationSession

- `id`: identificador local aleatório da sessão.
- `state`: `idle`, `requesting_permissions`, `active`, `paused`, `stopping` ou `ended`.
- `sourceLanguages`: idiomas aceitos nesta versão (inglês e espanhol).
- `targetLanguage`: `pt-BR`.
- `qualityMode`: `economy`, `balanced` ou `quality`.
- `audioMode`: fonte original, tradução ou ambas, quando fala sintetizada estiver ativa.
- `startedAt` / `endedAt`: horários locais para controlar a sessão.
- `overlayPreferences`: tamanho, transparência e estilo essencial.

### RecognizedSegment

- `id`, `sessionId`.
- `kind`: `screen_text` ou `speech_transcript`.
- `sourceText`, `detectedLanguage`, `confidence`.
- `screenRegion` para OCR ou `startTime`/`endTime` para fala.
- `contentFingerprint`: chave temporária para deduplicação dentro da sessão.
- `status`: `recognized`, `translating`, `translated`, `unavailable` ou `expired`.
- `expiresAt`: expiração da memória temporária.

### TranslationResult

- `id`, `segmentId`, `targetText`, `providerMode` (`local` ou `cloud`).
- `createdAt`, `displayUntil`, `status`.
- Removido ao encerrar a sessão, salvo se o usuário salvar explicitamente.

### SavedTranslation

- `id`, `sourceText`, `translatedText`, idiomas, `savedAt`.
- Criado somente por ação explícita do usuário; pode ser removido pelo usuário.

### UserPreferences

- Idiomas habilitados, modo de qualidade padrão, voz PT-BR escolhida e preferências visuais da sobreposição.

## Regras de ciclo de vida

- Áudio bruto e quadros capturados devem permanecer em memória pelo tempo estritamente necessário ao processamento.
- Segmentos e resultados da sessão expiram ao encerrar/parar a sessão e também quando ela for encerrada pelo sistema.
- Um salvamento manual copia apenas o segmento e a tradução escolhidos, nunca a captura original.
- Logs podem guardar estado técnico e códigos de erro, nunca texto, áudio, imagem ou transcrição do usuário.
