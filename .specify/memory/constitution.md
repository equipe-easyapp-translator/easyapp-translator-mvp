# ScreenTranslator Constitution

## Princípios fundamentais

### I. Consentimento e privacidade por padrão
Captura de tela e áudio só começam após ação e consentimento explícitos do usuário. Processar o mínimo necessário, manter dados apenas durante a sessão e apagá-los ao encerrá-la. Conteúdo só pode ser salvo permanentemente por ação explícita do usuário.

### II. Transparência sobre compatibilidade
Nunca prometer tradução em qualquer aplicativo. Explicar limitações de captura, áudio protegido, DRM, permissões do Android e conexão antes e durante o uso.

### III. Degradação gradual
Quando captura de áudio, internet ou serviço de tradução falhar, manter as funções locais que ainda forem possíveis, informar claramente o estado e permitir parar a sessão sem perder o controle do dispositivo.

### IV. Eficiência antes de processamento contínuo
Detectar mudanças, evitar OCR e tradução repetidos, limitar trabalho em segundo plano e oferecer modos Econômico, Equilibrado e Qualidade.

### V. MVP pequeno e demonstrável
Priorizar um fluxo principal funcional no Android: iniciar sessão consentida, traduzir texto visível para PT-BR e apresentar tradução em sobreposição. Adicionar áudio e personalização em incrementos verificáveis.

## Restrições do produto

- Plataforma inicial: Android; nome técnico provisório: ScreenTranslator.
- Idiomas de origem iniciais: inglês e espanhol; idioma de destino: português brasileiro (PT-BR). Italiano e francês podem ser considerados após validação do MVP.
- Arquitetura híbrida entre processamento local e nuvem, com seleção automática e modos de qualidade controlados pelo usuário.
- Requer internet para funções em nuvem; sem conexão, continuar apenas com capacidades locais disponíveis.
- Sem gravação permanente automática. Histórico temporário pertence à sessão e deve ser removido ao encerrá-la.
- Captura de tela/áudio e sobreposição dependem de permissões e suporte do Android e do aplicativo de origem.

## Processo de desenvolvimento

- Cada incremento deve rastrear cenários e requisitos da especificação.
- Decisões ainda não validadas devem permanecer identificadas como hipóteses; não apresentar estimativas de custo ou compatibilidade como fatos sem medição.
- Revisar privacidade, permissões, consumo de bateria e comportamento em falhas em cada mudança que afete captura ou processamento.
- Não coletar nem persistir conteúdo de tela/áudio em logs de diagnóstico.

## Governança

Esta constituição orienta especificações, planos e implementação do ScreenTranslator. Alterações de escopo, retenção de dados ou permissões devem ser documentadas e refletidas nos artefatos do projeto.

**Versão**: 1.0.0 | **Ratificada**: 2026-11-06 | **Última alteração**: 2026-11-06
