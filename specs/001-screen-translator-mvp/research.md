# Pesquisa técnica e decisões de escopo — EasyApp / ScreenTranslator

**Status**: Fase 1 (T001–T005) documentada; T042 (provedor remoto: Claude Haiku 5.5) adicionada em 2026-11-13. As escolhas técnicas e os custos abaixo são uma base para prototipagem, não resultados de testes em aparelho nem cotação contratual.

## Decisão T001 — escopo unificado

O MVP é um único aplicativo Android, chamado **EasyApp** para o produto e **ScreenTranslator** como nome técnico já usado nos artefatos. Ele terá dois modos selecionáveis:

1. **Tradução de Tela e Apps**: captura consentida via `MediaProjection`; OCR do texto visível; tradução em sobreposição flutuante; legendas traduzidas para áudio de reprodução quando a captura pelo sistema e pelo app de origem forem permitidas. A compatibilidade com qualquer app não é garantida.
2. **Conversa Presencial por Voz**: captura do microfone após ação explícita e permissão; transcrição por turnos; tradução bidirecional entre português brasileiro e idioma escolhido (inglês ou espanhol); TTS no idioma do interlocutor após a frase completa. A pessoa pode optar por ouvir a fala original, a tradução ou ambas, e selecionar uma voz disponível. A experiência deve apoiar o caso da persona Marco sem exigir ajuda de outra pessoa.

O modo de conversa incorpora a proposta de valor, o storyboard de Marco e o Canvas: turistas e recém-chegados precisam compreender e responder à fala em situações cotidianas. O modo de tela preserva a proposta técnica já especificada. As entrevistas de três pessoas não validam a demanda conjunta nem o mercado do modo de tela; isso continua uma hipótese a testar.

## T002 — matriz Android e permissões

| Área | API / requisito | Consequência para o MVP |
|---|---|---|
| Base do app | API 30+ (Android 11+) | `minSdk 30` é a base proposta para esta fase. O `targetSdk` deve acompanhar a versão estável exigida no momento da implementação e ser revisado antes de distribuir. |
| Captura da tela | `MediaProjection` existe desde API 21; exige consentimento do sistema para cada sessão. Para manter captura ativa, usar serviço em primeiro plano do tipo `mediaProjection` e encerrar/liberar recursos em `MediaProjection.Callback.onStop()`. | Botão iniciar explica a captura e abre o consentimento do sistema; parada visível e imediata. Conteúdo protegido ou apps que bloqueiam captura ficam sem suporte. |
| Android 14+ / API 34+ | Para apps com target 34+, cada sessão precisa de novo consentimento e token de projeção de uso único; declarar `FOREGROUND_SERVICE_MEDIA_PROJECTION` e `foregroundServiceType="mediaProjection"`. Não reutilizar token nem criar outra projeção com a mesma instância. | Tratar nova tentativa como sessão nova; testar revogação, parada pelo chip do sistema e bloqueio de tela. |
| Áudio de outros apps | `AudioPlaybackCapture` (API 29+) requer `RECORD_AUDIO`, consentimento `MediaProjection` e mesmo perfil de usuário. O player precisa usar `USAGE_MEDIA`, `USAGE_GAME` ou `USAGE_UNKNOWN` e permitir captura; a política mais restritiva prevalece. | Áudio interno é oportunista. Não contornar DRM, política de captura ou bloqueios do app de origem. Se não capturável, informar e manter OCR quando disponível. |
| Microfone | `RECORD_AUDIO` é permissão perigosa, solicitada em tempo de execução. Para serviço de microfone em foreground em Android 14+ com target 34+, declarar tipo `microphone` e `FOREGROUND_SERVICE_MICROPHONE`; obedecer às restrições de uso em primeiro plano. | Solicitar permissão somente ao entrar no modo de conversa e iniciar após consentimento claro. Nunca manter gravação oculta. |
| Sobreposição | `SYSTEM_ALERT_WINDOW` exige autorização especial do usuário nas Configurações para apps que miram API 23+. Usar `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` (API 26+), abaixo de janelas críticas do sistema; o Android pode reposicionar/ocultar a janela. | Explicar e solicitar permissão separadamente da captura. A sobreposição não pode cobrir controles críticos nem prometer posicionamento invariável. |
| Reconhecimento de fala | `SpeechRecognizer` requer `RECORD_AUDIO`; incluir consulta ao serviço de reconhecimento no manifest ao mirar API 30+. `createOnDeviceSpeechRecognizer` e a verificação de disponibilidade existem desde API 31. | Preferir reconhecimento local quando `isOnDeviceRecognitionAvailable()` retornar verdadeiro; não presumir que o serviço padrão seja local ou funcione offline. Encerrar o recognizer ao terminar. |
| TTS | `TextToSpeech` é API do Android; enumerar vozes e conferir idioma PT-BR/idiomas escolhidos no mecanismo instalado. Apps que miram Android 11+ declaram a consulta ao serviço TTS no manifest. | Usar voz local por padrão; oferta e qualidade variam por aparelho/engine e podem exigir download de voz. |

**Matriz de versões a validar**: API 30–33: fluxo base e diferenças entre fabricantes; API 34: consentimento/token de uso único e tipos de foreground service; API 35+ (e versão estável vigente): novas restrições de serviço, chip/encerramento de projeção e comportamento real no aparelho. O mínimo 30 não elimina diferenças entre versões. A compatibilidade de áudio precisa ser mapeada por app de origem.

## T003 — stack inicial e divisão local/nuvem

| Camada | Caminho local/econômico | Qualidade opcional / observações |
|---|---|---|
| App | Kotlin + Jetpack Compose para telas e estados; componentes Android nativos para permissões, serviços e captura. Sobreposição usa janela `TYPE_APPLICATION_OVERLAY` (pode hospedar UI Android/Compose). | Sem backend próprio no protótipo; qualquer segredo de serviço remoto deve ficar fora do app e a arquitetura de credenciais precisa ser aprovada antes de integrar. |
| Captura/OCR | `MediaProjection` → quadros em memória → Google ML Kit Text Recognition para OCR local e detecção de mudança/deduplicação. | OCR é processamento no dispositivo. Modelo pode ser empacotado ou baixado pelo Google Play Services; avaliar tamanho, disponibilidade offline e versões latinas em aparelho. |
| Tradução | Google ML Kit Translation no dispositivo; baixar os modelos EN↔PT e ES↔PT necessários. | Tradução em nuvem pode ser habilitada no modo Qualidade, com indicador, rede e controle de custo. A qualidade real deve ser comparada com frases do roteiro de validação. |
| Conversa pelo microfone | `SpeechRecognizer.createOnDeviceSpeechRecognizer()` somente após verificar disponibilidade; reconhecimento incremental e limpeza da sessão. Android `TextToSpeech` local, checando voz/locale. | Em modo Qualidade, usar STT/translation/TTS em nuvem somente com escolha/aviso do usuário, política de retenção confirmada e credenciais seguras. O recognizer padrão do Android não é garantia de processamento local. |
| Áudio de apps | `AudioPlaybackCapture` + `AudioRecord` com consentimento, se origem permitir; encaminhar a um reconhecedor compatível com o fluxo de áudio bruto. | Não tratar `SpeechRecognizer` como uma API genérica para PCM de áudio interno. STT em nuvem é alternativa opcional para áudio permitido, com custo por minuto e divulgação. |
| Modos | **Econômico**: OCR, tradução e fala local quando disponíveis. **Equilibrado**: local primeiro, nuvem apenas para lacunas autorizadas. | **Qualidade**: permite serviços remotos aprovados para transcrição/tradução e, se selecionado, voz remota; exibir estado e estimativa de consumo. Não é modo já medido. |

ML Kit Text Recognition suporta Android API 23+ e modelos latinos em tempo real na maioria dos dispositivos, condicionado à qualidade/resolução do quadro. ML Kit Translation oferece tradução no dispositivo em mais de 50 idiomas, baixa modelos conforme necessários e recomenda avaliar qualidade por caso de uso. Os modelos locais podem usar inglês como idioma intermediário em algumas combinações, portanto EN↔PT e ES↔PT devem ser avaliados separadamente. Local não significa custo zero de bateria, armazenamento, dados de download ou suporte.

## T004 — estimativa de custo de referência

Estimativa em **USD**, para o modo de Conversa Presencial por Voz com o caminho remoto opcional, sem impostos, câmbio, tráfego, infraestrutura própria, tentativas repetidas ou custos de operação. Premissas: 1 minuto de áudio total processado por minuto de sessão (somadas as falas de ambos os lados); cada fala é transcrita/traduzida uma vez; cerca de 120 palavras/min × 5 caracteres = **600 caracteres de entrada/min**; cerca de **650 caracteres de tradução PT-BR/min** enviados ao TTS. Speech-to-Text V2 Standard a US$ 0,016/min; Cloud Translation NMT a US$ 20 por milhão de caracteres; Cloud Text-to-Speech Standard a US$ 4 por milhão de caracteres. Valores calculados sobre tarifas públicas sem aplicar cotas/créditos mensais:

| Duração | STT | Tradução (600 caracteres/min) | TTS (650 caracteres/min) | Total remoto bruto estimado |
|---:|---:|---:|---:|---:|
| 1 min | US$ 0,0160 | US$ 0,000012 | US$ 0,0026 | **US$ 0,0186** |
| 10 min | US$ 0,1600 | US$ 0,000120 | US$ 0,0260 | **US$ 0,1861** |
| 60 min | US$ 0,9600 | US$ 0,000720 | US$ 0,1560 | **US$ 1,1167** |

Para o modo **Tela e Apps**, OCR + ML Kit Translation locais custam US$ 0 em tarifas diretas por API. Se legendas de áudio interno exigirem STT remoto, estime US$ 0,016/minuto de áudio mais a tradução de cerca de 600 caracteres/minuto: **US$ 0,0160 / US$ 0,1601 / US$ 0,9607** para 1 / 10 / 60 minutos, antes de créditos mensais. O modo de tela não inclui TTS.

No caminho estritamente local (ML Kit + recognizer on-device disponível + TTS local), a cobrança direta dessas APIs é **US$ 0**; não inclui custo indireto de bateria, downloads, armazenamento ou dados. Preços e cotas mudam: a página de Cloud Translation informa franquia/crédito mensal para NMT, e Cloud TTS Standard indica até 4 milhões de caracteres mensais sem custo; esses benefícios dependem da conta e do uso mensal. A estimativa bruta facilita comparar a carga sem presumir que a franquia ainda está disponível. O STT remoto é o maior item. Se a fala usar recognizer local e TTS local, o valor cai para principalmente a tradução de nuvem; se todo o pipeline for local, não há cobrança de API. Essa conta não é preço de venda nem custo final do produto.

## T005 — aparelho e cenários de referência

**Aparelho-alvo proposto para os ensaios**: Pixel 8a físico com Android 15/API 35, aparelho de referência a confirmar/disponibilizar antes da execução. A seleção define uma base repetível; não afirma que esse aparelho esteja disponível ou que tenha sido testado. Registrar fabricante/modelo, build, patch de segurança, RAM, engine de voz e versão dos modelos em cada medição. Acrescentar ao menos um dispositivo de fabricante diferente durante validação de compatibilidade.

**Cenários**:

1. Tela: página de teste EN e ES, texto curto/longo, legenda que muda e conteúdo sem alteração; medir OCR, tradução duplicada e latência.
2. Apps/áudio: áudio com captura permitida, app que bloqueia captura e conteúdo protegido; verificar legenda, aviso de limitação e continuidade do OCR.
3. Conversa: turnos PT-BR↔EN e PT-BR↔ES, em local silencioso e com ruído moderado; verificar transcrição, tradução, voz PT-BR e voz no idioma selecionado.
4. Permissões e sessão: negar/revogar microfone, projeção e sobreposição; parar na UI e no controle do sistema; bloquear/desbloquear tela e girar o aparelho.
5. Rede e duração: sem internet, rede intermitente e sessões de 1, 10 e 60 minutos; observar degradação, tráfego, bateria e descarte dos buffers ao parar.

**Critérios de medição**: manter os objetivos de spec de ≥90% dos segmentos essenciais corretos e p90 ≤2 s em condições favoráveis como hipóteses a medir; registrar taxa de transcrição compreensível e correções por idioma, falhas por permissão/origem, consumo de bateria/dados e custo estimado. Definir amostra/roteiro e calcular métricas antes de declarar sucesso; nenhum resultado de aparelho foi obtido nesta fase.

## T042 — provedor da rota remota: Claude Haiku 5.5 (decisão de 2026-11-13)

**Decisão**: a tradução remota do modo Qualidade e o recurso "O que isso quer dizer?" usam o **Claude Haiku 5.5** (`claude-haiku-5-5`), da Anthropic. Transcrição (STT) e síntese (TTS) continuam locais, então só texto vai para a nuvem. O Claude não é usado na conversa por voz nesta versão.

**Por que um modelo de linguagem, e não um tradutor de máquina**: o ML Kit traduz linha por linha, sem contexto. Um modelo de linguagem recebe as linhas da tela juntas, junta frases que o OCR quebrou, entende gíria e expressões e consegue explicar o sentido, o que atende diretamente ao storyboard ("IA para entender as gírias regionais") e ao pedido do E3 por mais confiabilidade. Escolheu-se o Haiku, o modelo mais barato e rápido da família, pela restrição de orçamento da equipe.

**Credenciais (sem backend)**: cada pessoa cola a própria chave de API no app; ela é criptografada pelo Android Keystore. O app e o repositório não contêm chave nenhuma, o que cumpre o `plan.md` ("não pode expor credenciais permanentes no cliente") e o `quickstart.md` ("não incluir chaves no app ou no Git"). Um backend próprio só será considerado se o app for distribuído a quem não tem chave. Recomendação fixa para quem usa: definir um limite de gasto mensal no Console da Anthropic.

**Retenção e treinamento** (NFR-005): para usuários da API, a Anthropic apaga entradas e saídas do backend em até 30 dias, com exceções para acordos de retenção zero, aplicação da Política de Uso (conteúdo sinalizado pode ser guardado por até 2 anos) e obrigação legal. Por padrão, a Anthropic não treina modelos com entradas ou saídas de produtos comerciais, o que inclui a API. O aviso de consentimento do app deve dizer isso de forma simples.

**Tarifa oficial** (Haiku 5.5, prompts de até 100 mil tokens): US$ 0,10 por milhão de tokens de entrada e US$ 0,50 por milhão de tokens de saída. O raciocínio estendido é cobrado como saída, então o app o desliga.

**Estimativa por operação** (premissas: tela típica com até 18 linhas; português e italiano usam um pouco mais de tokens que inglês):

| Operação | Tokens (entrada / saída) | Custo estimado |
|---|---:|---:|
| Tela cheia (~18 linhas) | ~500 / ~400 | US$ 0,00025 |
| Só linhas novas (legenda que mudou) | ~120 / ~60 | US$ 0,00004 |
| "O que isso quer dizer?" (um toque) | ~300 / ~250 | US$ 0,00016 |

**Por duração**, uso contínuo do modo Qualidade com telas mudando com frequência: **~US$ 0,002/min, ~US$ 0,02 em 10 min e ~US$ 0,10–0,15 por hora**. US$ 5 de crédito cobrem algo entre 35 e 50 horas, ou cerca de 20 mil telas cheias. Comparada à rota remota da T004, que tinha STT como maior item de custo, esta fica mais barata porque o áudio nunca sai do aparelho.

**Controles de custo implementados**: só linhas ainda não traduzidas na sessão vão para o Claude (cache em memória); intervalo mínimo entre chamadas; teto de `max_tokens` por resposta; esforço baixo; raciocínio desligado; Claude só no modo Qualidade ou por toque explícito; contador de tokens e custo da sessão a partir do `usage` devolvido pela API. Os valores reais devem ser medidos na T038.

## Pendências que permanecem após a Fase 1

- Confirmar disponibilidade do aparelho proposto e comparar pelo menos um fabricante adicional.
- Testar qualidade dos modelos locais em PT-BR, EN e ES, incluindo variação regional/ruído, e documentar a disponibilidade de vozes.
- Verificar políticas de retenção, termos de uso, autorização, credenciais e custos reais dos fornecedores antes de ativar modo Qualidade. *Feito para a Anthropic na T042; a medição do custo real continua na T038.*
- Provar captura de áudio interno em apps de origem selecionados. A matriz legal/técnica não é garantia de funcionamento universal.
- Executar os cenários e medições do T005; as estimativas de preço não substituem medição de latência, bateria, tráfego ou qualidade.

## Fontes oficiais consultadas

- [Media projection — Android Developers](https://developer.android.com/media/grow/media-projection)
- [Android 14 behavior changes](https://developer.android.com/about/versions/14/behavior-changes-14)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Capture video and audio playback](https://developer.android.com/media/platform/av-capture)
- [Manifest.permission — Android API](https://developer.android.com/reference/android/Manifest.permission)
- [WindowManager.LayoutParams — Android API](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [SpeechRecognizer — Android API](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [TextToSpeech — Android API](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [Jetpack Compose](https://developer.android.com/develop/ui/compose)
- [ML Kit Text Recognition on Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [ML Kit Translation on Android](https://developers.google.com/ml-kit/language/translation/android)
- [Cloud Speech-to-Text pricing](https://cloud.google.com/speech-to-text/pricing)
- [Cloud Translation pricing](https://cloud.google.com/products/translate/pricing)
- [Cloud Text-to-Speech pricing](https://cloud.google.com/text-to-speech/pricing)
- [Claude — Pricing](https://platform.claude.com/docs/en/about-claude/pricing) (consultado em 2026-11-13)
- [Anthropic Privacy — How long do you store my organization's data?](https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data) (consultado em 2026-11-13)
- [Anthropic Privacy — Is my data used for model training?](https://privacy.claude.com/en/articles/7996868-is-my-data-used-for-model-training) (consultado em 2026-11-13)
