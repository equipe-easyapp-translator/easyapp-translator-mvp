# Quickstart do protótipo ScreenTranslator

## Objetivo

Executar uma prova técnica em aparelho Android real para validar o caminho de captura consentida de tela, OCR, tradução para PT-BR e sobreposição.

## Pré-requisitos previstos

- Android Studio e SDK compatível com Android 11+.
- Aparelho Android físico com depuração USB habilitada.
- Contas/chaves de provedor somente se pesquisa aprovar serviço remoto; não incluir chaves no app ou no Git.

## Fluxo de demonstração

1. Abrir o ScreenTranslator e selecionar o modo de qualidade.
2. Ler a explicação de captura e conceder as permissões solicitadas pelo sistema.
3. Iniciar a sessão e confirmar o indicador persistente de captura.
4. Abrir uma tela de teste com texto em inglês ou espanhol.
5. Conferir tradução PT-BR sobreposta e verificar que texto repetido não gera novas traduções sem mudança.
6. Ajustar tamanho/transparência da sobreposição.
7. Revogar rede ou usar uma fonte de áudio não capturável e conferir a degradação informativa.
8. Parar a sessão e conferir remoção da sobreposição e dos dados temporários.

## Pronto para avançar quando

- O usuário consegue iniciar e parar claramente.
- O fluxo de texto funciona em aparelho de referência com latência e qualidade medidas.
- Revogação de permissão e falha de rede não travam o app.
- Os dados temporários desaparecem ao final da sessão.
- A prova de áudio demonstra suporte real em fontes selecionadas sem tentar contornar bloqueios.

## Observação

Este documento descreve o roteiro desejado. O repositório Android ainda precisa ser criado; comandos de compilação e execução serão adicionados quando a estrutura de código existir.
