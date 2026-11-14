# Especificação de funcionalidade: MVP EasyApp / ScreenTranslator

**Feature Branch**: `001-screen-translator-mvp`  
**Criado**: 2026-11-06  
**Atualizado**: 2026-11-13 — História 6 (tradução refinada e explicação com Claude, opcional) e História 7 (traduzir a tela no lugar do texto, por botão)  
**Status**: Rascunho para validação  
**Entrada**: Estruturar o MVP Android EasyApp / ScreenTranslator, unificando tradução de tela/apps e conversa presencial por voz.

## Contexto e objetivo

O EasyApp (nome técnico ScreenTranslator) tem dois modos no mesmo aplicativo Android. Em **Tradução de Tela e Apps**, o usuário inicia uma sessão e autoriza a captura necessária para reconhecer texto visível via OCR e exibir tradução em sobreposição; legendas traduzidas do áudio de outros apps aparecem somente quando o Android e a origem permitem a captura. Em **Conversa Presencial por Voz**, o usuário autoriza o microfone e acompanha turnos de fala transcritos, traduzidos e sintetizados em voz no idioma do interlocutor.

PT-BR é o idioma local de referência. Inglês e espanhol são os idiomas estrangeiros iniciais; a primeira prova vertical deve começar por inglês e adicionar espanhol após validar o fluxo. Na conversa, a tradução é bidirecional PT-BR ↔ idioma selecionado. Compatibilidade universal não é garantida: aplicativos podem bloquear captura de tela ou áudio, especialmente conteúdo protegido por DRM.

## Cenários de usuário

### História 1 — Traduzir texto visível (P1)

Como pessoa que não compreende o idioma de um conteúdo na tela, quero ver a tradução em PT-BR sobreposta ao aplicativo atual, para acompanhar o conteúdo sem alternar continuamente entre aplicativos.

**Prioridade**: É o fluxo central, com menor dependência de captura de áudio.

**Teste independente**: Em uma tela de teste com texto em inglês ou espanhol, iniciar a sessão e observar o texto reconhecido e sua tradução em PT-BR na sobreposição.

**Cenários de aceitação**:

1. **Dado** que o usuário concedeu as permissões solicitadas, **quando** iniciar a tradução, **então** o aplicativo captura e processa apenas o conteúdo necessário para reconhecer texto.
2. **Dado** que texto legível em inglês ou espanhol aparece ou muda, **quando** o reconhecimento terminar, **então** sua tradução correspondente aparece na sobreposição.
3. **Dado** que o conteúdo permanece igual, **quando** novos quadros forem avaliados, **então** o mesmo texto não é traduzido repetidamente sem necessidade.
4. **Dado** que o usuário encerra a sessão, **quando** a sessão parar, **então** a sobreposição fecha e o conteúdo temporário da sessão é eliminado.

### História 2 — Conversar presencialmente por voz (P1)

Como turista ou recém-chegado que ainda não domina o português, quero falar em meu idioma e ouvir a tradução em português, e permitir que a resposta em português seja traduzida e falada no meu idioma, para conversar no cotidiano sem depender de outra pessoa.

**Base de produto**: Persona Marco, storyboard e validação da proposta de valor. A necessidade de conversa por voz tem evidência inicial de três entrevistas; tamanho de mercado, diversidade linguística e disposição a pagar permanecem hipóteses.

**Teste independente**: Em uma conversa simulada PT-BR↔EN ou PT-BR↔ES, autorizar o microfone, falar uma frase, observar a transcrição/tradução e ouvir a síntese no idioma oposto.

**Cenários de aceitação**:

1. **Dado** que o usuário escolheu o modo e concedeu `RECORD_AUDIO`, **quando** iniciar um turno de fala, **então** o app transcreve a fala e apresenta a tradução no idioma do interlocutor.
2. **Dado** que há uma tradução concluída, **quando** a frase do turno terminar, **então** o app pode reproduzir a voz correspondente, permite ouvir original/tradução/ambos e selecionar voz/idioma instalado.
3. **Dado** que o usuário ouve a resposta em PT-BR, **quando** iniciar o próximo turno, **então** o app traduz a resposta para o idioma estrangeiro selecionado, mantendo o fluxo bidirecional.
4. **Dado** que reconhecimento local, rede ou voz não estejam disponíveis, **quando** a etapa falhar, **então** o app informa a limitação, oferece somente os recursos disponíveis e permite encerrar a captura.
5. **Dado** que o usuário encerra a conversa, **quando** a sessão termina, **então** áudio, transcrição e tradução temporários são descartados.

### História 3 — Traduzir áudio de outros apps em legendas (P2)

Como usuário acompanhando conteúdo falado, quero ver legendas em PT-BR quando o áudio puder ser capturado, para entender a fala sem depender de tradução de tela.

**Teste independente**: Iniciar uma sessão com uma fonte de áudio compatível e verificar transcrição, tradução e sincronização aproximada das legendas.

**Cenários de aceitação**:

1. **Dado** que a fonte permite captura de áudio e o usuário autorizou a sessão, **quando** houver fala reconhecida, **então** o aplicativo mostra a legenda traduzida em PT-BR.
2. **Dado** que o áudio não pode ser capturado, **quando** a sessão iniciar ou a captura falhar, **então** o aplicativo informa a limitação e continua com tradução de texto se possível.
3. **Dado** que a fala ou legenda correspondente termina, **quando** o conteúdo avança, **então** a legenda antiga desaparece em vez de acumular na tela.

### História 4 — Controlar a sessão e a sobreposição (P3)

Como usuário, quero iniciar, pausar e parar a tradução e ajustar controles essenciais da sobreposição, para manter controle sobre permissões, leitura e consumo de recursos.

**Teste independente**: Iniciar e parar uma sessão e ajustar tamanho e transparência da sobreposição.

**Cenários de aceitação**:

1. **Dado** que não existe sessão ativa, **quando** o usuário toca em Iniciar, **então** o app explica e solicita permissões necessárias antes de capturar.
2. **Dado** que uma sessão está ativa, **quando** o usuário usa o controle persistente de parada, **então** captura, processamento e sobreposição são encerrados.
3. **Dado** que a sessão está ativa, **quando** o usuário altera tamanho ou transparência, **então** a sobreposição reflete o ajuste sem reiniciar a captura.

### História 5 — Usar modos de qualidade e lidar com falta de rede (P3)

Como usuário, quero escolher entre Econômico, Equilibrado e Qualidade e manter recursos locais quando a internet falha, para adequar custo, velocidade e qualidade à situação.

**Teste independente**: Alternar os modos e simular indisponibilidade de rede, observando o modo efetivo e a indicação ao usuário.

**Cenários de aceitação**:

1. **Dado** que a tradução está ativa, **quando** o usuário seleciona um modo, **então** o app atualiza sua estratégia de processamento e apresenta o modo escolhido.
2. **Dado** que a rede fica indisponível, **quando** houver capacidades locais compatíveis, **então** o app continua essas capacidades e sinaliza quais funções em nuvem estão indisponíveis.

### História 6 — Tradução refinada e explicação com Claude (P3, opcional)

Como usuário que precisa de uma tradução mais confiável que a tradução linha a linha, quero que o modo Qualidade use um modelo de linguagem (Claude Haiku 5.5) para traduzir o texto da tela com contexto e quero tocar numa tradução para entender o que ela quer dizer, para compreender gírias, expressões e frases quebradas pelo OCR.

**Base de produto**: o storyboard da equipe já propõe "transcrição e tradução com IA" e "IA para entender as gírias regionais"; o E3 pediu tradução "com mais confiabilidade que o Google Tradutor". A melhora real de qualidade é hipótese a medir (T038).

**Teste independente**: Com uma chave de API configurada e o consentimento dado, iniciar o modo Tela em Qualidade sobre um texto com expressão idiomática, comparar com a tradução local e tocar na linha para ver a explicação. Repetir sem internet e confirmar a volta automática à tradução local.

**Cenários de aceitação**:

1. **Dado** que o usuário não configurou uma chave, **quando** usar qualquer modo, **então** o app funciona como antes, só com processamento local, e nenhum texto sai do aparelho.
2. **Dado** que o usuário cola a própria chave e aceita o aviso sobre envio de texto, **quando** iniciar o modo Tela em Qualidade, **então** as linhas novas reconhecidas são traduzidas pelo Claude, e a sobreposição indica que a tradução é online.
3. **Dado** que a chamada ao Claude falha (sem rede, chave inválida, limite de gasto ou de taxa atingido), **quando** houver texto a traduzir, **então** o app usa a tradução local do ML Kit para essas linhas e informa o motivo, sem interromper a sessão.
4. **Dado** que há uma tradução na sobreposição, **quando** o usuário toca nela, **então** o app pede uma explicação curta (sentido, tom, expressão) e a mostra junto da linha; o pedido só acontece por esse toque.
5. **Dado** que a sessão usa o Claude, **quando** o usuário abre o app, **então** vê os tokens usados e o custo estimado da sessão em US$.
6. **Dado** que o usuário remove a chave, **quando** confirmar, **então** a chave é apagada do aparelho e o app volta ao processamento só local.

### História 7 — Traduzir a tela no lugar do texto, com um botão (P2)

Como usuário lendo uma conversa ou página em outro idioma (por exemplo, no WhatsApp), quero tocar num botão e ver o texto daquela tela trocado pela tradução no mesmo lugar, como o tradutor de páginas do navegador, para ler sem desviar o olhar para outra janela.

**Base de produto**: a conversa de definição do produto (`App_tradutora IT.docx`) já cogitava a opção de a tradução "cobrir/substituir visualmente o texto original, dando a impressão de que o app traduziu a tela diretamente".

**Teste independente**: Iniciar o modo Tela com o estilo "No lugar do texto", abrir uma conversa em inglês no WhatsApp, tocar em Traduzir e conferir que cada frase aparece traduzida sobre a original; rolar a conversa e conferir que a tradução some.

**Cenários de aceitação**:

1. **Dado** que a sessão está no estilo "No lugar do texto", **quando** o usuário toca em Traduzir, **então** o app lê a tela daquele instante e cobre cada linha reconhecida com a tradução, na mesma posição e com fundo e cor de texto parecidos com os originais.
2. **Dado** que a tradução está na tela, **quando** o usuário toca em qualquer ponto, **então** a tradução some e o app de origem volta a responder normalmente aos toques.
3. **Dado** que a tradução está na tela, **quando** o conteúdo da tela muda (rolagem, mensagem nova, troca de app), **então** a tradução some e o botão volta a oferecer Traduzir.
4. **Dado** que uma linha já está no idioma de destino, **quando** a tela for traduzida, **então** essa linha não é coberta.
5. **Dado** que o Claude está configurado e o modo é Qualidade, **quando** o usuário toca em Traduzir, **então** as linhas da tela vão juntas para o Claude, com contexto; sem Claude, a tradução é a do ML Kit.

### História 8 — Navegar por páginas e adaptar o app a mim (P2)

Como usuário com necessidades próprias (vista cansada, pouca familiaridade com tecnologia, conversa com alguém sentado à minha frente), quero que cada modo tenha a sua página, clara e sem excesso de opções, e quero escolher o tema, o tamanho dos textos e a disposição da conversa, para usar o app do jeito que funciona melhor para mim.

**Base de produto**: a persona Marco não fala português e usa o app em público, com pressa; o E1 só usaria "para conceitos importantes". O app precisa ser compreensível em segundos e legível em qualquer condição.

**Teste independente**: Sem ajuda, trocar o idioma, iniciar cada modo pela sua página, aumentar o texto do app e da tradução e conferir a mudança na prévia; numa conversa frente a frente, cada pessoa lê o seu lado sem girar o celular.

**Cenários de aceitação**:

1. **Dado** que o app abriu, **quando** o usuário olha a tela, **então** vê uma barra com as páginas Tela, Conversa e Ajustes e sabe em qual está; o botão Voltar do Android volta para a página anterior.
2. **Dado** que o usuário está na página Tela ou Conversa, **quando** quiser trocar o idioma, **então** toca na barra de idiomas, escolhe numa lista com o nome de cada idioma escrito nele mesmo e volta à página.
3. **Dado** que o usuário abre os Ajustes, **quando** muda o tema ou o tamanho do texto, **então** o app inteiro e a prévia mudam na hora, e a escolha fica salva.
4. **Dado** que o usuário escolheu o tamanho da tradução, **quando** usar a janela flutuante ou a tradução no lugar do texto, **então** o texto traduzido respeita esse tamanho.
5. **Dado** que a conversa está no layout frente a frente, **quando** a sessão começa, **então** a metade de cima da tela fica virada para a outra pessoa, cada metade tem o seu botão de falar e mostra a última tradução no idioma de quem lê.
6. **Dado** que houve vários turnos na conversa, **quando** o usuário olha a página, **então** vê o histórico da sessão em balões, que é apagado ao encerrar.

### História 9 — Usar o app no meu idioma (P1)

Como turista ou recém-chegado que não lê português (persona Marco, italiano), quero que os botões, avisos e mensagens do app estejam no meu idioma, para entender o app que foi feito para mim.

**Base de produto**: a persona e os três entrevistados são italianos; o maior emissor de turistas para o Brasil é a Argentina (dado da Embratur em `Validação da proposta de valor.docx`). Uma interface só em português contradiz o público.

**Teste independente**: Num celular em italiano, abrir o app pela primeira vez e conferir que tudo está em italiano e que o par inicial é português → italiano; em Ajustes, trocar para English e depois para Português.

**Cenários de aceitação**:

1. **Dado** que o celular está em português, italiano ou inglês, **quando** o app abre, **então** a interface aparece nesse idioma.
2. **Dado** que o celular está em outro idioma (espanhol, alemão…), **quando** o app abre, **então** a interface aparece em inglês.
3. **Dado** que é o primeiro uso, **quando** o par de idiomas é definido, **então** o idioma do celular é o "seu" (destino) e o português é o da outra pessoa; num celular em português, o par é inglês → português.
4. **Dado** que é a primeira abertura, **quando** o app abre, **então** uma tela de boas-vindas, escrita nos três idiomas ao mesmo tempo, pede o idioma antes de qualquer outra coisa.
5. **Dado** qualquer versão do Android, **quando** o usuário escolhe o idioma (na boas-vindas ou em Ajustes), **então** o app mostra "Aplicando o idioma…" já no idioma escolhido, recarrega a interface sozinho e mantém a escolha.

## Limites do MVP

Inclui um app Android EasyApp / ScreenTranslator com dois modos: (1) tela/apps, com OCR, detecção de mudanças, tradução em sobreposição e legendas de áudio de reprodução quando capturável; (2) conversa presencial por voz, com microfone, transcrição, tradução bidirecional PT-BR↔EN/ES e síntese de fala. Inclui modos de processamento, histórico efêmero e opção explícita para salvar uma tradução selecionada.

Fora do MVP: garantia de funcionamento em qualquer app, contorno de DRM ou bloqueios de captura, publicação em loja, suporte a iOS, dublagem que substitua o áudio original de outros apps no modo Tela e Apps, identificação individual de falantes, tradução totalmente offline garantida em todo aparelho, personalização visual completa e retenção automática permanente.

## Requisitos funcionais

- **FR-001**: O sistema DEVE exigir início explícito da sessão e consentimentos/permissões do sistema necessários antes de capturar tela ou áudio.
- **FR-002**: O sistema DEVE permitir parar a captura de forma visível e imediata, inclusive com sessão em segundo plano.
- **FR-003**: O sistema DEVE reconhecer texto visível em inglês e espanhol e solicitar tradução para PT-BR.
- **FR-004**: O sistema DEVE exibir o resultado como sobreposição, sem alterar o app de origem: numa janela flutuante ou cobrindo visualmente o texto original no mesmo lugar (História 7), à escolha do usuário. *(Alterado em 2026-11-13; a versão anterior previa só a janela.)*
- **FR-005**: O sistema DEVE detectar mudanças relevantes e evitar OCR/tradução redundantes de conteúdo inalterado.
- **FR-006**: O sistema DEVE remover traduções visuais quando o conteúdo original correspondente desaparecer ou mudar.
- **FR-007**: No modo Tela e Apps, o sistema DEVE capturar áudio de reprodução somente quando o Android e o app de origem permitirem; não deve tentar contornar bloqueios.
- **FR-008**: No modo Tela e Apps, o sistema DEVE mostrar legendas traduzidas quando áudio puder ser capturado e transcrito; indisponibilidade de áudio não deve impedir OCR quando possível.
- **FR-009**: O sistema DEVE comunicar limitações de compatibilidade, conexão, permissão e serviços sem interromper funções independentes que ainda operem.
- **FR-010**: O sistema DEVE oferecer modos Econômico, Equilibrado e Qualidade e respeitar a escolha do usuário.
- **FR-011**: O sistema DEVE manter histórico apenas durante a sessão e apagá-lo quando ela terminar.
- **FR-012**: O usuário DEVE poder salvar manualmente uma tradução escolhida; nenhuma tradução deve ser salva permanentemente por padrão.
- **FR-013**: O sistema DEVE permitir controles essenciais de tamanho e transparência da sobreposição.
- **FR-014**: No modo Conversa Presencial por Voz, o sistema DEVE sintetizar a tradução no idioma do interlocutor quando houver voz compatível disponível e permitir selecionar entre vozes instaladas.
- **FR-015**: O sistema NÃO DEVE incluir conteúdo de tela, áudio ou transcrição em logs de diagnóstico.
- **FR-016**: O sistema DEVE oferecer dois modos claramente identificados: Tradução de Tela e Apps e Conversa Presencial por Voz.
- **FR-017**: No modo Conversa Presencial, o sistema DEVE traduzir turnos de fala nos dois sentidos entre PT-BR e o idioma estrangeiro selecionado (inglês ou espanhol).
- **FR-018**: O sistema DEVE indicar se transcrição é local ou remota e solicitar consentimento/avisar antes de enviar áudio ou texto a um serviço remoto.
- **FR-019**: O sistema NÃO DEVE embutir chave de API no código, no APK ou no repositório. O uso do Claude só é possível com a chave do próprio usuário, colada por ele no app.
- **FR-020**: O sistema DEVE guardar a chave do usuário criptografada com o Android Keystore, nunca mostrá-la inteira depois de salva e permitir apagá-la a qualquer momento.
- **FR-021**: Com chave e consentimento, o modo Qualidade da Tradução de Tela DEVE traduzir pelo Claude apenas o texto reconhecido (nunca imagens nem áudio), e só as linhas que ainda não foram traduzidas na sessão.
- **FR-022**: O sistema DEVE oferecer "O que isso quer dizer?" ao tocar numa tradução da sobreposição, com chamada ao Claude apenas nesse toque.
- **FR-023**: Qualquer falha do Claude DEVE cair para a tradução local do ML Kit, com aviso do motivo, sem encerrar a sessão (Princípio III).
- **FR-024**: O sistema DEVE mostrar os tokens usados e o custo estimado da sessão, calculados a partir do consumo que a API devolve em cada resposta.
- **FR-025**: No estilo "No lugar do texto", a leitura da tela DEVE acontecer só quando o usuário toca em Traduzir, sem OCR contínuo.
- **FR-026**: A camada de tradução no lugar do texto NÃO DEVE prender o usuário: o primeiro toque em qualquer ponto a fecha e devolve o app de origem. *(Decisão técnica: o Android 12+ bloqueia toques que atravessam janelas sobrepostas opacas de outro app, então a camada não pode ser opaca e deixar o toque passar ao mesmo tempo; segue-se o comportamento do Google Lens.)*
- **FR-027**: A camada DEVE sumir quando o conteúdo da tela mudar, para nunca mostrar tradução sobre um texto que já não está ali.
- **FR-028**: O app DEVE ter uma página por modo (Tela, Conversa) e uma de Ajustes, acessíveis por uma barra de navegação fixa, e respeitar o botão Voltar do sistema.
- **FR-029**: O usuário DEVE poder escolher o tema (seguir o sistema, claro ou escuro), o tamanho do texto do app e o tamanho do texto da tradução sobre a tela, com prévia imediata; as escolhas ficam salvas no aparelho.
- **FR-030**: O modo Conversa DEVE oferecer o layout frente a frente (metade da tela invertida para o interlocutor, um botão de falar por pessoa) e o layout lado a lado, à escolha do usuário.
- **FR-031**: O modo Conversa DEVE mostrar o histórico de turnos da sessão, só em memória, apagado ao encerrar (FR-011).
- **FR-032**: Todo controle DEVE ter área de toque de pelo menos 48 dp e descrição para leitor de tela.
- **FR-033**: A interface DEVE estar disponível em português, italiano e inglês, com o inglês como idioma de reserva para qualquer outro idioma do aparelho; nenhum texto de interface fica fixo no código.
- **FR-034**: Nenhuma decisão do app pode depender do texto de uma mensagem (o texto muda com o idioma); estados vêm de valores próprios.
- **FR-035**: No primeiro uso, o par de idiomas DEVE partir do idioma do aparelho (FR-033, cenário 3 da História 9).

## Requisitos não funcionais

- **NFR-001**: A sessão deve indicar claramente quando captura ou processamento estiver ativo.
- **NFR-002**: A experiência deve buscar atraso de 1–2 segundos para conteúdo curto em condições favoráveis; esse objetivo é uma hipótese de desempenho a medir, não uma garantia.
- **NFR-003**: O processamento deve reduzir atividade quando a tela e o áudio não apresentarem mudanças relevantes.
- **NFR-004**: Falhas de OCR, rede, tradução ou áudio não devem fechar inesperadamente a sessão nem impedir a parada pelo usuário.
- **NFR-005**: Dados enviados à nuvem devem limitar-se ao necessário para a operação solicitada e seguir política de descarte após processamento, sujeita à confirmação técnica do provedor escolhido. Para o Claude, a política confirmada está em `research.md` (T042).
- **NFR-006**: O custo do Claude deve ser mínimo: modelo Claude Haiku 5.5, raciocínio estendido desligado, esforço baixo, teto de tokens por resposta e intervalo mínimo entre chamadas. Meta inicial a medir: até US$ 0,15 por hora de uso contínuo do modo Qualidade.

## Casos de borda

- Usuário recusa ou revoga uma permissão durante a sessão.
- Tela protegida, em branco, muito rápida, girada ou com texto pequeno/ambíguo.
- Conteúdo em idioma não suportado ou mistura de idiomas.
- Áudio protegido, sem fala, com ruído, sobreposição de falantes ou idioma não reconhecido.
- Usuário conversa em local ruidoso, alterna idiomas, interrompe a síntese ou revoga o microfone durante um turno.
- Internet intermitente, serviço remoto indisponível ou cota/custo atingido.
- Mudança de orientação, troca de aplicativo, bloqueio de tela ou encerramento pelo sistema.
- Usuário fecha a sessão antes de uma resposta de tradução chegar.
- Chave de API inválida, revogada, sem crédito ou com limite de gasto mensal atingido.
- Tela com dado sensível (banco, senha, mensagens privadas) no modo Qualidade com Claude; apps com `FLAG_SECURE` já aparecem em preto, mas outros não.
- No estilo "No lugar do texto": tradução bem mais longa que o original, texto sobre foto ou degradê, emojis no meio da frase, conteúdo animado (GIF, vídeo, "digitando…") que faz a tradução sumir sozinha, teclado aberto e rotação da tela.

## Dados e entidades

- **Sessão de tradução**: estado ativo/pausado/encerrado, modo de qualidade, idiomas e configurações efêmeras.
- **Segmento reconhecido**: texto OCR ou transcrição, idioma detectado, região/tempo e estado de processamento; efêmero por padrão.
- **Resultado de tradução**: texto traduzido, vínculo com o segmento, tempos de exibição e origem local/nuvem; efêmero por padrão.
- **Tradução salva**: texto de origem e tradução escolhidos explicitamente pelo usuário, com data e idioma.
- **Preferências**: idioma de destino, modo, voz e controles da sobreposição.
- **Credencial do Claude**: chave de API do próprio usuário, criptografada pelo Android Keystore; nunca sai do aparelho exceto no cabeçalho da chamada à API.
- **Consumo da sessão**: tokens de entrada e saída e custo estimado; em memória, zerado ao encerrar a sessão.

## Critérios de sucesso

- **SC-001**: Em um conjunto de cenários de demonstração com texto legível em inglês/espanhol, pelo menos 90% dos segmentos definidos como essenciais são apresentados com tradução correta para PT-BR; metodologia e amostra serão definidas no plano de validação.
- **SC-002**: Em condições de rede favoráveis, o percentil 90 do atraso entre texto novo legível e tradução exibida é de até 2 segundos, a validar em aparelho de referência.
- **SC-003**: Em teste guiado, pelo menos 4 de 5 participantes iniciam e encerram uma sessão sem ajuda após explicação inicial.
- **SC-004**: Após encerrar uma sessão, não permanecem segmentos ou resultados temporários no armazenamento da aplicação.
- **SC-005**: Quando captura de áudio de outro app não é suportada, o usuário recebe indicação da limitação e a captura de texto permanece utilizável quando disponível.
- **SC-006**: Em um roteiro guiado de conversa PT-BR↔EN/ES, registrar taxa de turnos transcritos e traduzidos compreensivelmente, latência de cada etapa e turnos que exigiram repetição; definir limiar de aprovação após estabelecer a amostra de referência.

## Hipóteses e questões em aberto

- A decisão de escopo desta versão unifica conversa presencial e tradução de tela/apps em um único produto, com modos separados. A validação de mercado foi feita com apenas três entrevistados italianos e apoia principalmente o modo de conversa; não comprova demanda pelo modo de tela/apps nem disposição a pagar.
- Confirmar em aparelho combinações de Android/API, permissões, disponibilidade de captura de áudio, restrições de loja, provedores, descarte de dados e custos reais por sessão.
- Validar sequência de turnos, fallback local/remoto e comportamento de saída de voz em ambiente real; a decisão atual inclui síntese de fala no modo de conversa e mantém áudio de outros apps como legenda apenas onde capturável.
- Definir comportamento e retenção de traduções salvas manualmente.

## Assunções

- Android 11 ou superior (API 30+); o aparelho de referência proposto é Pixel 8a com Android 15/API 35, sujeito a disponibilidade e teste.
- EasyApp é o nome de produto; ScreenTranslator permanece como nome técnico/legado dos artefatos.
- Idiomas iniciais: inglês e espanhol, com PT-BR como idioma local; a primeira prova vertical começa em inglês.
- Áudio interno é oportunista; conversa usa microfone somente após escolha explícita e autorização.
- A arquitetura é híbrida e decide entre local e nuvem considerando qualidade, conectividade, bateria e custo.
- A estimativa inicial é uma ordem de grandeza em USD documentada em `research.md`, não preço final nem medição.
- No MVP, cada pessoa usa a própria chave de API da Anthropic, sem backend da equipe. Distribuir o app para usuários sem chave exigiria um backend que guarde a chave, o que fica fora do MVP.
- O Claude entra só no modo Tela (Qualidade e "O que isso quer dizer?"). A conversa por voz continua 100% local nesta versão.
- No estilo "No lugar do texto", "O que isso quer dizer?" e as legendas de áudio de outros apps não estão disponíveis nesta versão: a camada deixa os toques passarem, e as legendas dependem da janela flutuante.
