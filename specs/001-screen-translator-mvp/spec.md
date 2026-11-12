# Especificação de funcionalidade: MVP ScreenTranslator

**Feature Branch**: `001-screen-translator-mvp`  
**Criado**: 2026-11-06  
**Status**: Rascunho para validação  
**Entrada**: Estruturar o MVP Android do aplicativo que reconhece e traduz texto na tela e áudio de outros aplicativos.

## Contexto e objetivo

O ScreenTranslator ajuda uma pessoa a acompanhar conteúdo em outro idioma enquanto usa um aplicativo compatível. Na primeira versão, o usuário inicia uma sessão, autoriza a captura necessária, vê traduções de texto em uma sobreposição e pode receber legendas traduzidas do áudio quando a fonte e o Android permitem a captura.

O idioma de destino inicial é PT-BR. Inglês e espanhol são os idiomas de origem prioritários. Compatibilidade universal não é garantida: aplicativos podem bloquear captura de tela ou áudio, especialmente conteúdo protegido por DRM.

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

### História 2 — Traduzir áudio em legendas (P2)

Como usuário acompanhando conteúdo falado, quero ver legendas em PT-BR quando o áudio puder ser capturado, para entender a fala sem depender de tradução de tela.

**Teste independente**: Iniciar uma sessão com uma fonte de áudio compatível e verificar transcrição, tradução e sincronização aproximada das legendas.

**Cenários de aceitação**:

1. **Dado** que a fonte permite captura de áudio e o usuário autorizou a sessão, **quando** houver fala reconhecida, **então** o aplicativo mostra a legenda traduzida em PT-BR.
2. **Dado** que o áudio não pode ser capturado, **quando** a sessão iniciar ou a captura falhar, **então** o aplicativo informa a limitação e continua com tradução de texto se possível.
3. **Dado** que a fala ou legenda correspondente termina, **quando** o conteúdo avança, **então** a legenda antiga desaparece em vez de acumular na tela.

### História 3 — Controlar a sessão e a sobreposição (P3)

Como usuário, quero iniciar, pausar e parar a tradução e ajustar controles essenciais da sobreposição, para manter controle sobre permissões, leitura e consumo de recursos.

**Teste independente**: Iniciar e parar uma sessão e ajustar tamanho e transparência da sobreposição.

**Cenários de aceitação**:

1. **Dado** que não existe sessão ativa, **quando** o usuário toca em Iniciar, **então** o app explica e solicita permissões necessárias antes de capturar.
2. **Dado** que uma sessão está ativa, **quando** o usuário usa o controle persistente de parada, **então** captura, processamento e sobreposição são encerrados.
3. **Dado** que a sessão está ativa, **quando** o usuário altera tamanho ou transparência, **então** a sobreposição reflete o ajuste sem reiniciar a captura.

### História 4 — Usar modos de qualidade e lidar com falta de rede (P3)

Como usuário, quero escolher entre Econômico, Equilibrado e Qualidade e manter recursos locais quando a internet falha, para adequar custo, velocidade e qualidade à situação.

**Teste independente**: Alternar os modos e simular indisponibilidade de rede, observando o modo efetivo e a indicação ao usuário.

**Cenários de aceitação**:

1. **Dado** que a tradução está ativa, **quando** o usuário seleciona um modo, **então** o app atualiza sua estratégia de processamento e apresenta o modo escolhido.
2. **Dado** que a rede fica indisponível, **quando** houver capacidades locais compatíveis, **então** o app continua essas capacidades e sinaliza quais funções em nuvem estão indisponíveis.

## Limites do MVP

Inclui Android, tradução para PT-BR, inglês e espanhol como fontes iniciais, OCR com detecção de mudanças, sobreposição, áudio para legendas quando suportado, modos de processamento, histórico temporário da sessão e opção explícita para salvar uma tradução selecionada.

Fora do MVP: garantia de funcionamento em qualquer app, contorno de DRM ou bloqueios de captura, publicação em loja, suporte a iOS, dublagem que substitua a fonte, identificação individual de falantes, tradução totalmente offline, personalização visual completa e retenção automática permanente.

## Requisitos funcionais

- **FR-001**: O sistema DEVE exigir início explícito da sessão e consentimentos/permissões do sistema necessários antes de capturar tela ou áudio.
- **FR-002**: O sistema DEVE permitir parar a captura de forma visível e imediata, inclusive com sessão em segundo plano.
- **FR-003**: O sistema DEVE reconhecer texto visível em inglês e espanhol e solicitar tradução para PT-BR.
- **FR-004**: O sistema DEVE exibir o resultado como sobreposição sem substituir o conteúdo original.
- **FR-005**: O sistema DEVE detectar mudanças relevantes e evitar OCR/tradução redundantes de conteúdo inalterado.
- **FR-006**: O sistema DEVE remover traduções visuais quando o conteúdo original correspondente desaparecer ou mudar.
- **FR-007**: O sistema DEVE capturar áudio de reprodução somente quando o Android e o app de origem permitirem; não deve tentar contornar bloqueios.
- **FR-008**: O sistema DEVE mostrar legendas traduzidas quando fala puder ser capturada e transcrita.
- **FR-009**: O sistema DEVE comunicar limitações de compatibilidade, conexão, permissão e serviços sem interromper funções independentes que ainda operem.
- **FR-010**: O sistema DEVE oferecer modos Econômico, Equilibrado e Qualidade e respeitar a escolha do usuário.
- **FR-011**: O sistema DEVE manter histórico apenas durante a sessão e apagá-lo quando ela terminar.
- **FR-012**: O usuário DEVE poder salvar manualmente uma tradução escolhida; nenhuma tradução deve ser salva permanentemente por padrão.
- **FR-013**: O sistema DEVE permitir controles essenciais de tamanho e transparência da sobreposição.
- **FR-014**: O sistema DEVE oferecer escolha entre vozes PT-BR para leitura de fala traduzida, se a saída de voz for habilitada no incremento correspondente.
- **FR-015**: O sistema NÃO DEVE incluir conteúdo de tela, áudio ou transcrição em logs de diagnóstico.

## Requisitos não funcionais

- **NFR-001**: A sessão deve indicar claramente quando captura ou processamento estiver ativo.
- **NFR-002**: A experiência deve buscar atraso de 1–2 segundos para conteúdo curto em condições favoráveis; esse objetivo é uma hipótese de desempenho a medir, não uma garantia.
- **NFR-003**: O processamento deve reduzir atividade quando a tela e o áudio não apresentarem mudanças relevantes.
- **NFR-004**: Falhas de OCR, rede, tradução ou áudio não devem fechar inesperadamente a sessão nem impedir a parada pelo usuário.
- **NFR-005**: Dados enviados à nuvem devem limitar-se ao necessário para a operação solicitada e seguir política de descarte após processamento, sujeita à confirmação técnica do provedor escolhido.

## Casos de borda

- Usuário recusa ou revoga uma permissão durante a sessão.
- Tela protegida, em branco, muito rápida, girada ou com texto pequeno/ambíguo.
- Conteúdo em idioma não suportado ou mistura de idiomas.
- Áudio protegido, sem fala, com ruído, sobreposição de falantes ou idioma não reconhecido.
- Internet intermitente, serviço remoto indisponível ou cota/custo atingido.
- Mudança de orientação, troca de aplicativo, bloqueio de tela ou encerramento pelo sistema.
- Usuário fecha a sessão antes de uma resposta de tradução chegar.

## Dados e entidades

- **Sessão de tradução**: estado ativo/pausado/encerrado, modo de qualidade, idiomas e configurações efêmeras.
- **Segmento reconhecido**: texto OCR ou transcrição, idioma detectado, região/tempo e estado de processamento; efêmero por padrão.
- **Resultado de tradução**: texto traduzido, vínculo com o segmento, tempos de exibição e origem local/nuvem; efêmero por padrão.
- **Tradução salva**: texto de origem e tradução escolhidos explicitamente pelo usuário, com data e idioma.
- **Preferências**: idioma de destino, modo, voz e controles da sobreposição.

## Critérios de sucesso

- **SC-001**: Em um conjunto de cenários de demonstração com texto legível em inglês/espanhol, pelo menos 90% dos segmentos definidos como essenciais são apresentados com tradução correta para PT-BR; metodologia e amostra serão definidas no plano de validação.
- **SC-002**: Em condições de rede favoráveis, o percentil 90 do atraso entre texto novo legível e tradução exibida é de até 2 segundos, a validar em aparelho de referência.
- **SC-003**: Em teste guiado, pelo menos 4 de 5 participantes iniciam e encerram uma sessão sem ajuda após explicação inicial.
- **SC-004**: Após encerrar uma sessão, não permanecem segmentos ou resultados temporários no armazenamento da aplicação.
- **SC-005**: Quando captura de áudio não é suportada, o usuário recebe indicação da limitação e a captura de texto permanece utilizável quando disponível.

## Hipóteses e questões em aberto

- Os documentos de validação com clientes descrevem outro foco de valor: conversa presencial por microfone e voz, para turistas e recém-chegados. Esta especificação segue o documento técnico do ScreenTranslator. Validar se é o mesmo produto ou uma ideia separada antes de investir em implementação completa.
- A validação de mercado foi feita com apenas três entrevistados italianos; não comprova demanda por tradução de conteúdo de tela nem disposição a pagar.
- Confirmar combinações de Android/API, permissões, disponibilidade de captura de áudio, restrições de loja, provedores, descarte de dados, custos por minuto e custo por volume de OCR.
- Definir se fala traduzida em voz alta pertence à primeira demonstração ou a uma fase posterior; a prioridade explícita mais clara é legenda traduzida e controle de fonte original/tradução/ambos.
- Definir comportamento e retenção de traduções salvas manualmente.

## Assunções

- Android 11 ou superior e aparelho físico para o protótipo, conforme as decisões registradas.
- Nome provisório ScreenTranslator; pode mudar.
- Idiomas de entrada iniciais: inglês e espanhol; destino: PT-BR.
- Áudio interno é oportunista; microfone pode ser uma alternativa apenas se definida e autorizada pelo usuário.
- A arquitetura é híbrida e decide entre local e nuvem considerando qualidade, conectividade, bateria e custo.
- Estimativas de custo não foram consolidadas e não são critério de preço do produto nesta etapa.
