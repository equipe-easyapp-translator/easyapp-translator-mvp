# Roteiro da apresentação — EasyApp Translator (MVP)

**Duração estimada:** 10 minutos · **Equipe:** Brayan, Carlos, Sante e Tiago
**Formato:** quatro blocos de aproximadamente 2 min 30 s cada. Os textos abaixo são falas sugeridas; os trechos entre colchetes indicam ações de demonstração.

## Bloco 1 — Problema, proposta e método de desenvolvimento + CI/CD

**Apresentador: Brayan · slides 1 a 3 · 2 min 30 s**

### Slide 1 — Abertura (30 s)

“Olá. Nós somos Brayan, Carlos, Sante e Tiago, e vamos apresentar o EasyApp Translator, um MVP Android para reduzir a fricção de entender conteúdo em outro idioma. A proposta reúne tradução do que está na tela e conversa presencial por voz em um único aplicativo. Vamos mostrar o problema, como o app foi organizado e o que aprendemos nos testes em aparelho.”

### Slide 2 — Problema e proposta (1 min)

“Quando alguém encontra uma tela ou conversa em outro idioma, a solução comum é alternar entre o conteúdo e um tradutor. Essa troca interrompe a leitura e torna uma conversa presencial lenta. O EasyApp tenta manter a tradução perto do contexto: no modo Tela, reconhece texto visível e o mostra numa sobreposição; no modo Conversa, organiza a fala em turnos, transcreve, traduz e pode ler o resultado em voz alta. O foco do MVP é inglês e português, com seleção de outros idiomas suportados. A captura de tela depende de consentimento, e a captura de áudio de outros apps depende das políticas do Android e do app de origem.”

### Slide 3 — Especificação e entrega contínua (1 min)

“A equipe organizou o trabalho por especificação e tarefas verificáveis. No OpenSpec, a proposta, o desenho e as tarefas costumam aparecer em `proposal.md`, `design.md` e `tasks.md`. Neste repositório, o fluxo implementado usa Spec-Kit: `spec.md` registra o que o MVP deve fazer, `plan.md` e `research.md` registram decisões e contexto, e `tasks.md` acompanha a execução. Portanto, os conceitos de proposta e desenho estão cobertos, mas os dois arquivos OpenSpec com esses nomes não existem aqui. Para entrega, o GitHub Actions roda em commits na `main`, configura JDK 17, executa `assembleDebug` e publica o APK como artefato. Isso dá à equipe um caminho repetível para compartilhar builds sem exigir Gradle instalado em cada máquina.”

**Transição:** “Agora o Carlos mostra como esses dois fluxos foram separados dentro do app Android.”

## Bloco 2 — Arquitetura Android e stack técnico

**Apresentador: Carlos · slides 4 e 5 · 2 min 30 s**

### Slide 4 — Um app, duas sessões (1 min)

“A interface é feita em Kotlin com Jetpack Compose. A tela permite escolher modo, idiomas e qualidade e apresenta os controles da sessão. O container aplica `navigationBarsPadding()` para que o conteúdo rolável não fique por baixo da barra de navegação; isso trata o inset inferior sem afirmar que já ativamos a API completa `enableEdgeToEdge()`. Um `SessionService`, como serviço em primeiro plano, mantém a sessão visível por uma notificação persistente e coordena pausa, retomada e encerramento. As preferências ficam no DataStore. Frames, áudio e texto processado são voláteis e não são salvos como histórico persistente.”

### Slide 5 — Componentes e limites (1 min 30 s)

“No fluxo de tela, o Android pede consentimento de `MediaProjection`. O serviço liga a projeção a um `ImageReader`; o pipeline entrega frames ao OCR local do Google ML Kit, traduz o texto com o modelo on-device e encaminha os resultados à janela `WindowManager`. A sobreposição é uma janela de sistema separada da UI Compose. No fluxo de voz, `ConversationEngine` verifica `RECORD_AUDIO` e cria e opera o `SpeechRecognizer` na Main Thread. Quando o reconhecimento on-device está disponível, ele é preferido; se o pacote ou serviço falhar, o app tenta o serviço padrão do Android. Esse fallback pode exigir rede e depende do provedor configurado. Após a transcrição, o ML Kit traduz e o `TextToSpeech` sintetiza o idioma de saída. A disponibilidade de modelos, vozes e captura varia por aparelho; o app informa as limitações em vez de prometer compatibilidade universal.”

**Transição:** “Com a arquitetura em mente, o Sante vai mostrar os fluxos no celular e o estado real da validação.”

## Bloco 3 — Demonstração e validação em aparelho físico

**Apresentador: Sante · slides 6 e 7 · 2 min 30 s**

### Slide 6 — Tradução de Tela (1 min 15 s)

“Vou começar pelo modo Tradução de Tela. Primeiro escolho origem e destino e inicio a sessão. O Android apresenta os pedidos de sobreposição e compartilhamento de tela; aceito o consentimento e retorno ao app que quero ler. O EasyApp avalia frames em ciclos de aproximadamente 1,3 segundo, reconhece o texto e mostra a tradução. [Abrir uma tela de teste com texto estrangeiro.] A barra superior serve para arrastar a janela; um toque curto recolhe ou expande. `A-` e `A+` alteram a fonte, `PAUSAR` interrompe a captura e `FECHAR` encerra a sessão. Em aparelho físico, a equipe confirmou a captura, o OCR e a tradução offline com ML Kit, além do comportamento de arrastar e expandir da janela.”

### Slide 7 — Conversa Presencial por Voz (1 min 15 s)

“Agora seleciono Conversa Presencial por Voz e inicio a sessão com `RECORD_AUDIO` autorizado. Para este exemplo, eu falo português e escolho **Você · Português**; o interlocutor estrangeiro seleciona **Interlocutor · Inglês**. [Iniciar um turno e falar uma frase curta.] A interface deve indicar que está ouvindo, exibir a transcrição e a tradução e ler a tradução quando houver uma voz instalada. O outro participante responde e alternamos o turno. Podemos repetir o áudio traduzido e ajustar voz, velocidade e volume. O teste físico anterior encontrou uma falha: tocar em Ouvir não iniciava a captura. A equipe corrigiu a chamada na Main Thread e adicionou fallback e feedback, mas esta correção precisa ser confirmada nesta build durante a demonstração.”

**Transição:** “O Tiago encerra com os problemas que esses testes revelaram e o que ainda precisamos medir.”

## Bloco 4 — Desafios resolvidos e evoluções futuras

**Apresentador: Tiago · slides 8 e 9 · 2 min 30 s**

### Slide 8 — O que os testes ensinaram (1 min 15 s)

“Os testes reais foram úteis para encontrar falhas de integração que não aparecem só olhando os componentes. No OCR, as linhas eram enviadas à tradução antes de entrar no conjunto de linhas visíveis; o filtro descartava tudo. Corrigimos a ordem, iniciamos a preparação do modelo no começo da sessão e mantivemos a captura em polling. Na sobreposição, usamos `scaledTouchSlop` para distinguir um toque curto de um arraste. No fluxo de voz, `SpeechRecognizer` precisa ser criado e usado na Main Thread; o botão agora informa o estado e há uma tentativa pelo reconhecedor padrão caso o modo offline falhe. Também incluímos `navigationBarsPadding()` para o conteúdo não ficar atrás da navegação. O último ajuste de voz ainda precisa ser repetido em aparelho.”

### Slide 9 — Próximos passos e encerramento (1 min 15 s)

“Como próximos passos, precisamos repetir a validação dos turnos de voz e registrar latência, compreensão, repetição, bateria e falhas em condições silenciosas e ruidosas. Legendas de áudio interno já têm uma implementação inicial, mas dependem de Android 13 ou superior, do serviço de fala e da política do app que fornece o áudio; queremos testar essa compatibilidade em mais aparelhos e apps. Também podemos avaliar um histórico de sessão mais útil, mantendo a regra atual de que o conteúdo não persiste: primeiro precisamos definir necessidade, consentimento e limites de retenção. Por fim, vamos atualizar a matriz de compatibilidade e os critérios de qualidade com medidas reais. O EasyApp já demonstra os dois caminhos principais, e o próximo passo é comprovar sua confiabilidade em condições variadas. Obrigado.”

## Referências para a equipe antes de apresentar

- A documentação do repositório está em `.specify/` e `specs/001-screen-translator-mvp/`; não descreva `proposal.md` ou `design.md` como arquivos existentes.
- A sobreposição de tela, OCR e tradução offline foram reportados como funcionais em aparelho físico. O fluxo de voz teve uma falha reportada e corrigida no commit `dc60914`; confirme a correção no APK atual antes de dizer que ela está validada.
- Legendas de áudio interno e buffer temporário existem no código, mas sua compatibilidade depende da API do Android, do serviço de reconhecimento e do app de origem.
- A especificação ainda lista medições de latência, qualidade, bateria e compatibilidade como trabalho futuro.
