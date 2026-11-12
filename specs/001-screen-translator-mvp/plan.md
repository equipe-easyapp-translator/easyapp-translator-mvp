# Plano de implementação: MVP ScreenTranslator

**Branch**: `001-screen-translator-mvp` | **Data**: 2026-11-06 | **Spec**: [spec.md](spec.md)

## Resumo

Construir um protótipo Android que inicia uma sessão de captura consentida, reconhece mudanças de texto na tela, traduz texto em inglês/espanhol para PT-BR e mostra resultados em uma sobreposição controlável. Depois, acrescentar legendas traduzidas de áudio onde suportado, modos de processamento e tratamento de falhas. Compatibilidade, custo e latência precisam ser medidos antes de prometer comportamento universal.

## Contexto técnico

**Linguagem/versão**: Kotlin; versão a confirmar ao criar o projeto  
**Dependências principais**: Android SDK; biblioteca de OCR local e provedores de tradução, fala-para-texto e texto-para-fala a selecionar após pesquisa  
**Armazenamento**: Preferências locais; conteúdo da sessão somente em memória; armazenamento local explícito para itens salvos  
**Testes**: Testes em aparelho Android físico e testes automatizados a definir com o projeto  
**Plataforma**: Android 11+ como alvo inicial do protótipo  
**Tipo de projeto**: Aplicativo móvel nativo  
**Metas de desempenho**: Alvo de 1–2 segundos para conteúdo curto sob condições favoráveis, ainda não validado  
**Restrições**: Permissões explícitas; projeção de mídia e serviços em primeiro plano quando aplicáveis; limite de captura definido pelo app de origem; bateria e rede; não contornar DRM  
**Escopo**: Fluxo de captura → OCR/detecção de mudança → tradução → sobreposição, seguido do pipeline de áudio

## Verificação da constituição

- Consentimento explícito e controle de parada: obrigatório.
- Privacidade e descarte após sessão: obrigatório.
- Compatibilidade limitada e degradação gradual: obrigatório.
- Eficiência e modos de qualidade: obrigatório, com implementação incremental.
- **Resultado**: compatível, sujeito a pesquisa técnica sobre APIs, versões Android, políticas de distribuição e provedores antes da implementação.

## Estratégia de entrega

1. Prova técnica no aparelho para permissão/captura de tela e sobreposição.
2. História P1: OCR local, detecção de mudança, tradução para PT-BR e apresentação na sobreposição.
3. Controles de sessão, tratamento de falhas e descarte dos dados temporários.
4. Modos Econômico/Equilibrado/Qualidade e seleção local/nuvem.
5. Áudio capturável → transcrição → tradução → legendas; voz sintetizada fica condicionada à decisão de escopo e medição de custo/latência.
6. Avaliação de compatibilidade, privacidade, bateria e custos com fontes e aparelhos documentados.

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

**Decisão estrutural**: Um único aplicativo Android nativo para o protótipo. Não criar backend próprio até a comparação de provedores demonstrar necessidade; qualquer integração em nuvem deve ser encapsulada por interfaces e usar credenciais fora do cliente.

## Dependências e riscos técnicos

- A projeção de mídia exige consentimento e pode ser revogada; validação em Android 11+.
- Sobreposição requer permissão especial concedida pelo usuário.
- Áudio reproduzido por outros apps pode ser bloqueado pelo app de origem ou pelo sistema.
- Serviços de OCR, tradução, reconhecimento de fala e síntese diferem em suporte linguístico, processamento local, retenção e preço.
- Avaliar impacto de bateria, latência e rede; estabelecer limites para chamadas repetidas.
- Revisar documentação atual do Android e políticas de distribuição antes de publicação; este plano não é uma aprovação de loja.
